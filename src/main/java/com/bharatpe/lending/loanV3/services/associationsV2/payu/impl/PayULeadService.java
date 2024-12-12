package com.bharatpe.lending.loanV3.services.associationsV2.payu.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingGstDetail;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.dao.LendingRiskVariablesSnapshotDao;
import com.bharatpe.lending.common.dao.LendingShopDocumentsDao;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.entity.LendingRiskVariablesSnapshot;
import com.bharatpe.lending.common.entity.LendingShopDocuments;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.common.service.merchant.constants.Constants;
import com.bharatpe.lending.common.service.merchant.dto.MerchantDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingGstDao;
import com.bharatpe.lending.handlers.DsHandler;
import com.bharatpe.lending.loanV3.dto.CKycResponseDto;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsDto;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.dto.request.payu.PayUCreateLeadRequestDTO;
import com.bharatpe.lending.loanV3.dto.request.payu.PayUUpdateLeadRequestDTO;
import com.bharatpe.lending.loanV3.dto.response.payu.PayUCommonResponseDTO;
import com.bharatpe.lending.loanV3.dto.response.payu.PayUCreateLeadResponseDTO;
import com.bharatpe.lending.loanV3.dto.response.payu.PayUUpdateLeadResponseDTO;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.bharatpe.lending.loanV3.utils.ConverterUtils;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.transaction.Transactional;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
public class PayULeadService {

    @Autowired
    KycUtils kycUtils;

    @Autowired
    CommonService commonService;

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    ConverterUtils converterUtils;

    @Autowired
    MerchantService merchantService;

    @Autowired
    DsHandler dsHandler;

    @Autowired
    LendingShopDocumentsDao lendingShopDocumentsDao;

    @Autowired
    LendingRiskVariablesSnapshotDao lendingRiskVariablesSnapshotDao;

    @Autowired
    LendingGstDao lendingGstDao;

    @Value("${payu.channel.code:edi_bhp_01}")
    String payuChannelCode;

    @Transactional
    public boolean invokeCreateLead(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto) {
        try {
            if (ObjectUtils.isEmpty(lenderAssociationDetailsDto.getApplicationId()) || ObjectUtils.isEmpty(lenderAssociationDetailsDto.getLendingApplication())) {
                log.info("Application Id not found for merchant: {}", lenderAssociationDetailsDto.getMerchantId());
                return false;
            }
            if (ObjectUtils.isEmpty(lenderAssociationDetailsDto.getCKycResponseDto())) {
                lenderAssociationDetailsDto.setCKycResponseDto(kycUtils.getKycData(lenderAssociationDetailsDto.getMerchantId()));
            }
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStages.PiramalAssociationStages.LEAD_CREATION.name());
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.LEAD_CREATION_PENDING.name());
            commonService.manageApplicationState(lenderAssociationDetailsDto);
            LendingApplication lendingApplication = lenderAssociationDetailsDto.getLendingApplication();

            NBFCRequestDTO createLeadRequest = getCreateLeadPayload(lenderAssociationDetailsDto);

            if (Objects.isNull(createLeadRequest)) {
                log.info("error in create lead payload of PayU for applicationId: {}", lendingApplication.getId());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.LEAD_CREATION_FAILED.name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, LenderAssociationStatus.LEAD_CREATION_FAILED);
                return false;
            }
            NBFCResponseDTO nbfcResponseDto = lenderAPIGateway.invokeStage(createLeadRequest, LenderAssociationStages.CREATE_LEAD);
            log.info("create lead response of payU from nbfc {} with applicationId: {}", nbfcResponseDto, lenderAssociationDetailsDto.getApplicationId());
            if (Objects.nonNull(nbfcResponseDto) && nbfcResponseDto.getSuccess() && Objects.nonNull(nbfcResponseDto.getData())) {
                log.info("createLead request of payU success for {}", lenderAssociationDetailsDto.getApplicationId());

                PayUCommonResponseDTO commonResponseDTO = objectMapper.convertValue(nbfcResponseDto.getData(), PayUCommonResponseDTO.class);

                PayUCreateLeadResponseDTO createLeadResponseDTO =  objectMapper.convertValue(commonResponseDTO.getApiResponse(), PayUCreateLeadResponseDTO.class);


                if ("SUCCESS".equalsIgnoreCase(commonResponseDTO.getApiStatus()) && StringUtils.isNotBlank(createLeadResponseDTO.getApplicationId())) { // remove literal constant
                    lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadId(createLeadResponseDTO.getApplicationId());
                    lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setSmbId(createLeadResponseDTO.getSmbUserId());
                    lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.LEAD_CREATION_SUCCESS.name());
                    commonService.manageApplicationState(lenderAssociationDetailsDto);
                    return true;
                }
            }
        } catch (Exception e) {
            log.error("exception occurred while processing create lead of PayU for {} {} {}", lenderAssociationDetailsDto.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.LEAD_CREATION_FAILED.name());
        commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, LenderAssociationStatus.LEAD_CREATION_FAILED);
        return false;
    }

    private NBFCRequestDTO getCreateLeadPayload(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto) {
        CKycResponseDto cKycResponseDto = lenderAssociationDetailsDto.getCKycResponseDto();
        LendingApplication lendingApplication = lenderAssociationDetailsDto.getLendingApplication();
        try {
            return NBFCRequestDTO.builder()
                    .applicationId(lendingApplication.getId())
                    .productName("LENDING")
                    .lender(lendingApplication.getLender())
                    .payload(PayUCreateLeadRequestDTO.builder()
                            .pan(cKycResponseDto.getPanNumber())
                            .mobile(kycUtils.getMobileFromKycData(cKycResponseDto))
                            .channelCode(payuChannelCode)
                            .externalRefId(lendingApplication.getExternalLoanId())
                            .entityType("Proprietorship")
                            .compliance(getComplianceForCreateLead())
                            .isMobileVerified(true)
                            .build())
                    .build();
        } catch (Exception e) {
            log.error("exception occurred while creating request payload for createLead of PayU for applicationId: {}, {}, {}", lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    @Transactional
    public Boolean invokeUpdateLead(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto) {
        try {
            lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStages.UPDATE_LEAD.name());
            lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.UPDATE_LEAD_PENDING.name());
            commonService.manageApplicationState(lenderAssociationDetailsRequestDto);

            lenderAssociationDetailsRequestDto.setCKycResponseDto(kycUtils.getKycData(lenderAssociationDetailsRequestDto.getMerchantId()));
            NBFCRequestDTO updateLeadRequestDto = getUpdateLeadPayload(lenderAssociationDetailsRequestDto);
            if (Objects.isNull(updateLeadRequestDto)) {
                log.info("error in update lead payload of PayU for applicationId: {}", lenderAssociationDetailsRequestDto.getApplicationId());
                lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.UPDATE_LEAD_FAILED.name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequestDto, LenderAssociationStatus.UPDATE_LEAD_FAILED);

                return false;
            }
            NBFCResponseDTO nbfcResponseDto = lenderAPIGateway.invokeStage(updateLeadRequestDto, LenderAssociationStages.UPDATE_LEAD);
            log.info("update lead response of PayU from nbfc: {} with applicationId: {}", nbfcResponseDto, lenderAssociationDetailsRequestDto.getApplicationId());
            if (Objects.nonNull(nbfcResponseDto) && nbfcResponseDto.getSuccess() && Objects.nonNull(nbfcResponseDto.getData())) {
                log.info("updateLead request of payU success for {}", lenderAssociationDetailsRequestDto.getApplicationId());

                PayUCommonResponseDTO commonResponseDTO = objectMapper.convertValue(nbfcResponseDto.getData(), PayUCommonResponseDTO.class);

                PayUUpdateLeadResponseDTO updateLeadResponseDTO =  objectMapper.convertValue(commonResponseDTO.getApiResponse(), PayUUpdateLeadResponseDTO.class);

                if ("SUCCESS".equalsIgnoreCase(commonResponseDTO.getApiStatus()) && StringUtils.isNotBlank(updateLeadResponseDTO.getApplicationId())) {
                    lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setLeadId(updateLeadResponseDTO.getApplicationId());
                    lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.UPDATE_LEAD_COMPLETED.name());
                    commonService.manageApplicationState(lenderAssociationDetailsRequestDto);
                    return true;
                }
            }
        } catch (Exception e) {
            log.error("error while pushing update lead of PayU for  {} {} {}", lenderAssociationDetailsRequestDto.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }

        lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.UPDATE_LEAD_FAILED.name());
        commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequestDto, LenderAssociationStatus.UPDATE_LEAD_FAILED);

        return Boolean.FALSE;
    }

    private NBFCRequestDTO getUpdateLeadPayload(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto) {

        LendingApplication lendingApplication = lenderAssociationDetailsDto.getLendingApplication();

        try {

            PayUUpdateLeadRequestDTO payload = PayUUpdateLeadRequestDTO.builder()
                    .applicationId(lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getLeadId())
                    .applicantDetails(getApplicantDetails(lenderAssociationDetailsDto))
                    .bankDetails(getBankDetails(lendingApplication))
                    .companyDetails(getCompanyDetails(lenderAssociationDetailsDto))
                    .loanRequirement(getLoanRequirements(lendingApplication, lenderAssociationDetailsDto.getLendingApplicationLenderDetails()))
                    .compliance(getCompliance())
                    .location(getLocation(lendingApplication.getMerchantId(), lendingApplication))
                    .build();


            return NBFCRequestDTO.builder()
                    .applicationId(lendingApplication.getId())
                    .productName("LENDING")
                    .lender(lendingApplication.getLender())
                    .payload(payload)
                    .build();

        } catch (Exception e) {
            log.info("exception occurred while creating request payload for updateLead of PayU for applicationId: {}, {}", lendingApplication.getId(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    private List<PayUUpdateLeadRequestDTO.ApplicantDetailsDTO> getApplicantDetails(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto) {

        List<PayUUpdateLeadRequestDTO.ApplicantDetailsDTO> applicantDataList = new ArrayList<>();

        PayUUpdateLeadRequestDTO.ApplicantDetailsDTO applicantDetails;
        LendingApplication lendingApplication = lenderAssociationDetailsRequestDto.getLendingApplication();

        CKycResponseDto cKycResponseDto = lenderAssociationDetailsRequestDto.getCKycResponseDto();

        DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        applicantDetails = PayUUpdateLeadRequestDTO.ApplicantDetailsDTO.builder()
                .applicantId(null)
                .firstName(kycUtils.getNameAndDobValues(cKycResponseDto, lendingApplication.getMerchantId()).getFirstName())
                .lastName(kycUtils.getNameAndDobValues(cKycResponseDto, lendingApplication.getMerchantId()).getLastName())
                .mobileNumber(kycUtils.getMobileFromKycData(cKycResponseDto))
                .pan(cKycResponseDto.getPanNumber())
                .dob(LocalDate.parse(cKycResponseDto.getDob(), inputFormatter).format(outputFormatter))
                .gender(getGender(kycUtils.getGender(cKycResponseDto.getGender()))) // TODO check if gender value is from correct db and is required
                .address(getAddress(lenderAssociationDetailsRequestDto, "applicant_address"))
                .isMainApplicant(true)
                .build();

        applicantDataList.add(applicantDetails);
        return applicantDataList;

    }

    private List<PayUUpdateLeadRequestDTO.AddressDTO> getAddress(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto, String type) {

        List<PayUUpdateLeadRequestDTO.AddressDTO> addressDataList = new ArrayList<>();
        PayUUpdateLeadRequestDTO.AddressDTO currentAddress = null;

        String address = "", address1 = "", address2 = "";
        int addressSize;

        LendingApplication lendingApplication = lenderAssociationDetailsRequestDto.getLendingApplication();

        CKycResponseDto cKycResponseDto = lenderAssociationDetailsRequestDto.getCKycResponseDto();

        switch (type) {
            case "applicant_address":

                address = converterUtils.parseData(cKycResponseDto.getAddress());
                addressSize = address.length();
                if (addressSize <= 40) {
                    address1 = address;
                } else {
                    address1 = address.substring(0, 40);
                    address2 = address.substring(40, addressSize);
                }

                currentAddress = PayUUpdateLeadRequestDTO.AddressDTO.builder()
                        .line1(address1)
                        .line2(address2)
                        .city(cKycResponseDto.getCity())
                        .state(cKycResponseDto.getState())
                        .locality(null)
                        .pincode(cKycResponseDto.getPincode())
                        .ownershipIndicator("owned")
                        .addressType("PERMANENT")
                        .build();

                addressDataList.add(currentAddress);
                break;

            case "company_address":
                address = constructShopAddress(lendingApplication);
                addressSize = address.length();

                if (addressSize <= 40) {
                    address1 = address;
                } else {
                    address1 = address.substring(0, 40);
                    address2 = address.substring(40, addressSize);
                }

                currentAddress = PayUUpdateLeadRequestDTO.AddressDTO.builder()
                        .line1(address1)
                        .line2(address2)
                        .city(lendingApplication.getCity())
                        .locality(null)
                        .state(lendingApplication.getState())
                        .pincode(lendingApplication.getPincode().toString())
                        .ownershipIndicator("owned")
                        .addressType("OPERATING")
                        .build();

                addressDataList.add(currentAddress);

                break;
            default:
                addressDataList.add(currentAddress);
        }

        return addressDataList;
    }

    private List<PayUUpdateLeadRequestDTO.BankDetailsDTO> getBankDetails(LendingApplication lendingApplication){

        final MerchantDetailsDto merchantDetailsDto = merchantService.fetchMerchantDetails(lendingApplication.getMerchantId(), Arrays.asList(
                Constants.MerchantUtil.Scope.BANK_DETAIL,
                Constants.MerchantUtil.Scope.MERCHANT_USER
        ));
        if(ObjectUtils.isEmpty(merchantDetailsDto)) {
            log.info("merchant bank details not found for application {} with merchantId {}", lendingApplication.getId(), lendingApplication.getMerchantId());
            throw new RuntimeException("merchant bank details not found for application " + lendingApplication.getId());
        }

        List<PayUUpdateLeadRequestDTO.BankDetailsDTO> applicantDataList = new ArrayList<>();
        PayUUpdateLeadRequestDTO.BankDetailsDTO clientIdentifierPan = PayUUpdateLeadRequestDTO.BankDetailsDTO.builder()
                .id(null)
                .accountTypeId("CURRENT".equalsIgnoreCase(merchantDetailsDto.getBankDetail().getAccountType()) ? "CURRENT" : "SAVINGS")
                .bankAccountName(merchantDetailsDto.getBankDetail().getBeneficiaryName())
                .accountNumber(merchantDetailsDto.getBankDetail().getAccountNumber())
                .bankName(merchantDetailsDto.getBankDetail().getBankName())
                .ifscCode(merchantDetailsDto.getBankDetail().getIfsc())
                .disbursementAccount(true)
                .virtualAccount(false)
                .build();
        applicantDataList.add(clientIdentifierPan);
        return applicantDataList;

    }

    private List<PayUUpdateLeadRequestDTO.CompanyDetailsDTO> getCompanyDetails(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto){

        LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = lendingRiskVariablesSnapshotDao.findByApplicationId(lenderAssociationDetailsRequestDto.getLendingApplication().getId());

        CKycResponseDto cKycResponseDto = kycUtils.getGstData(lenderAssociationDetailsRequestDto.getMerchantId());

        log.info("cKycResponseDto in payu lead service for application Id - {} is - {}",lenderAssociationDetailsRequestDto.getApplicationId(), cKycResponseDto);

        LendingGstDetail lendingGstDetail = lendingGstDao.findByApplicationId(lenderAssociationDetailsRequestDto.getApplicationId());

        if (ObjectUtils.isEmpty(lendingGstDetail)) {
            lendingGstDetail = new LendingGstDetail();
            lendingGstDetail.setMerchantId(lenderAssociationDetailsRequestDto.getMerchantId());
            lendingGstDetail.setApplicationId(lenderAssociationDetailsRequestDto.getApplicationId());
        }

        if(ObjectUtils.isEmpty(lendingGstDetail.getGstNumber())){
            lendingGstDetail.setGstNumber(cKycResponseDto.getGstNumber());
        }

        lendingGstDao.save(lendingGstDetail);

        List<PayUUpdateLeadRequestDTO.CompanyDetailsDTO> applicantDataList = new ArrayList<>();
        PayUUpdateLeadRequestDTO.CompanyDetailsDTO companyDetails = PayUUpdateLeadRequestDTO.CompanyDetailsDTO.builder()
                .companyName(lenderAssociationDetailsRequestDto.getLendingApplication().getBusinessName())
                .typeOfBusiness("TRADING")
                .natureOfIndustry("OTHERS")
                .entityType("Proprietorship")
                .address(getAddress(lenderAssociationDetailsRequestDto,"company_address"))
                .partnerVintage(getPartnerVintageDate(lendingRiskVariablesSnapshot.getVintage()))
                .gst(ObjectUtils.isEmpty(cKycResponseDto.getGstNumber()) ? null : cKycResponseDto.getGstNumber())
                .build();
        applicantDataList.add(companyDetails);
        return applicantDataList;

    }

    private PayUUpdateLeadRequestDTO.LoanRequirementDTO getLoanRequirements(LendingApplication lendingApplication, LendingApplicationLenderDetails lendingApplicationLenderDetails){

        return PayUUpdateLeadRequestDTO.LoanRequirementDTO.builder()
                .amount(lendingApplication.getLoanAmount())
                .tenure(lendingApplication.getTenureInMonths() + "_Months")
                .roi(lendingApplicationLenderDetails.getAnnualRoi())
                .roiType("REDUCING")
                .purpose("business_purpose")
                .loanTypeId("EDITERMLOAN")
                .build();
    }

    private PayUUpdateLeadRequestDTO.ComplianceDTO getCompliance(){

        return PayUUpdateLeadRequestDTO.ComplianceDTO.builder()
                .bureauConsent(true)
                .generalConsent(true)
                .kycConsent(true)
                .build();
    }

    private PayUUpdateLeadRequestDTO.Location getLocation(Long merchantId, LendingApplication lendingApplication){

        Long applicationId = lendingApplication.getId();

        try {
            List<LendingShopDocuments> lendingShopDocumentsList = lendingShopDocumentsDao.findByMerchantIdAndLendingApplicationId(merchantId, applicationId);
            for (LendingShopDocuments lendingShopDocuments : lendingShopDocumentsList) {
                if (!ObjectUtils.isEmpty(lendingShopDocuments) && !ObjectUtils.isEmpty(lendingShopDocuments.getLatitude()) && !ObjectUtils.isEmpty(lendingShopDocuments.getLongitude())) {
                    return PayUUpdateLeadRequestDTO.Location.builder()
                            .latitude(lendingShopDocuments.getLatitude())
                            .longitude(lendingShopDocuments.getLongitude())
                            .ipAddress(lendingShopDocuments.getIp())
                            .build();
                }
            }

            Map<String, Double> dsResponse = dsHandler.fetchDsLocation(merchantId);
            if (!ObjectUtils.isEmpty(dsResponse) || dsResponse.containsKey("latitude") || !ObjectUtils.isEmpty(dsResponse.get("latitude")) || dsResponse.containsKey("longitude") || !ObjectUtils.isEmpty(dsResponse.get("longitude"))) {
                return PayUUpdateLeadRequestDTO.Location.builder()
                        .latitude(String.valueOf(dsResponse.get("latitude")))
                        .longitude(String.valueOf(dsResponse.get("longitude")))
                        .ipAddress(lendingApplication.getIp())
                        .build();
            }
        } catch (Exception e) {
            log.error("error while getting latitude and longitude for application Id {} and merchantId {}, {}, {}", applicationId, merchantId, e, Arrays.asList(e.getStackTrace()));
        }

        log.info("couldn't get lat long for applicationId {} and merchantId {} from shop picture and fetch DS location", applicationId, merchantId);

        return PayUUpdateLeadRequestDTO.Location.builder().latitude("0.0").longitude("0.0").build();

    }

    private PayUCreateLeadRequestDTO.ComplianceDTO getComplianceForCreateLead(){

        return PayUCreateLeadRequestDTO.ComplianceDTO.builder()
                .bureauConsent(true)
                .generalConsent(true)
                .kycConsent(true)
                .build();
    }

    private String getGender(String gender) {
        if(!ObjectUtils.isEmpty(gender)) {
            if("female".equalsIgnoreCase(gender)) {
                return "1";
            }
            else if("male".equalsIgnoreCase(gender)) {
                return "2";
            }
            return "3";
        }
        return "2";
    }

    private String constructShopAddress(LendingApplication lendingApplication) {
        return (ObjectUtils.isEmpty(lendingApplication.getShopNumber()) ? "" : lendingApplication.getShopNumber()) + "," +
                (ObjectUtils.isEmpty(lendingApplication.getStreetAddress()) ? "" : lendingApplication.getStreetAddress()) + "," +
                (ObjectUtils.isEmpty(lendingApplication.getLandmark()) ? "" : lendingApplication.getLandmark()) + "," +
                (ObjectUtils.isEmpty(lendingApplication.getCity()) ? "" : lendingApplication.getCity()) + "," +
                (ObjectUtils.isEmpty(lendingApplication.getState()) ? "" : lendingApplication.getState()) + "," +
                (ObjectUtils.isEmpty(lendingApplication.getPincode()) ? "" : lendingApplication.getPincode());

    }

    private String getPartnerVintageDate(Long partnerVintage){

        LocalDate currentDate = LocalDate.now();

        // Subtract partnerVintage days from the current date
        LocalDate dateBeforePartnerVintageDaysCount = currentDate.minusDays((partnerVintage));

        // Format the date as needed (e.g., YYYY-MM-DD)
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        return dateBeforePartnerVintageDaysCount.format(formatter);
    }
}
