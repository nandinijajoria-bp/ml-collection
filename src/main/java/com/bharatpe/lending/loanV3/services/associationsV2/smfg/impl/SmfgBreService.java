package com.bharatpe.lending.loanV3.services.associationsV2.smfg.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.dao.LendingRiskVariablesSnapshotDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.entity.LendingRiskVariablesSnapshot;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.RiskSegment;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.common.service.merchant.dto.BankDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dto.PanFetchKYCResponseDto;
import com.bharatpe.lending.enums.KycDocType;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.handlers.KycHandler;
import com.bharatpe.lending.loanV2.service.LendingApplicationServiceV2;
import com.bharatpe.lending.loanV3.config.SmfgConfig;
import com.bharatpe.lending.loanV3.dto.*;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.dto.request.smfg.SmfgAppPushRequest;
import com.bharatpe.lending.loanV3.dto.response.smfg.SmfgAppPushResponseDto;
import com.bharatpe.lending.loanV3.dto.request.smfg.SmfgCallbackRequest;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.bharatpe.lending.loanV3.utils.ConverterUtils;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.transaction.Transactional;
import java.util.*;

@Slf4j
@Service
public class SmfgBreService {

    @Autowired
    CommonService commonService;

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    LendingRiskVariablesSnapshotDao lendingRiskVariablesSnapshotDao;

    @Autowired
    KycUtils kycUtils;

    @Autowired
    KycHandler kycHandler;

    @Autowired
    ConverterUtils converterUtils;

    @Autowired
    @Lazy
    LendingApplicationServiceV2 lendingApplicationServiceV2;

    @Autowired
    SmfgConfig smfgConfig;

    @Value("${lender.change.enabled:false}")
    Boolean enableLenderChange;

    @Autowired
    MerchantService merchantService;

    @Transactional
    public Boolean invokeBre(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto) {
        try {
            lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStages.RISK_DECISION.name());
            if (ObjectUtils.isEmpty(lenderAssociationDetailsRequestDto.getCKycResponseDto())) {
                lenderAssociationDetailsRequestDto.setCKycResponseDto(kycUtils.getKycData(lenderAssociationDetailsRequestDto.getMerchantId()));
            }
            if (isBreChecksFailed(lenderAssociationDetailsRequestDto)) {
                lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_FAILED.name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequestDto, LenderAssociationStatus.RISK_FAILED);
                return false;
            }
            lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_PENDING.name());
            commonService.manageApplicationState(lenderAssociationDetailsRequestDto);
            NBFCRequestDTO breRequest = getPayload(lenderAssociationDetailsRequestDto);
            NBFCResponseDTO nbfcResponseDto = lenderAPIGateway.invokeStage(breRequest, LenderAssociationStages.BRE);
            log.info("Bre response of SMFG from nbfc: {} with applicationId: {}", nbfcResponseDto, lenderAssociationDetailsRequestDto.getApplicationId());
            if (!ObjectUtils.isEmpty(nbfcResponseDto) && nbfcResponseDto.getSuccess() && !ObjectUtils.isEmpty(nbfcResponseDto.getData())) {
                SmfgAppPushResponseDto breResponseDTO = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDto.getData()), SmfgAppPushResponseDto.class);
                if ("SUCCESS".equalsIgnoreCase(breResponseDTO.getStatus())) {
                    lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setLeadId(breResponseDTO.getData().getApplicationid());
                    lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_IN_PROGRESS.name());
                    commonService.manageApplicationState(lenderAssociationDetailsRequestDto);
                    return true;
                }
            }
            lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.ValidationStatus.API_RESPONSE_FAILED.name());
        } catch (Exception e) {
            log.error("error while invoking Bre of SMFG for  {} {} {}", lenderAssociationDetailsRequestDto.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_FAILED.name());
        commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequestDto, LenderAssociationStatus.RISK_FAILED);
        return false;
    }

    private NBFCRequestDTO getPayload(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest) {
        LendingApplication lendingApplication = lenderAssociationDetailsRequest.getLendingApplication();
        CKycResponseDto cKycResponseDto = lenderAssociationDetailsRequest.getCKycResponseDto();
        NameAndDobDetailsDto nameAndDobDetailsDto = kycUtils.getNameAndDobValues(cKycResponseDto, lendingApplication.getMerchantId());
        LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = lendingRiskVariablesSnapshotDao.findByApplicationId(lendingApplication.getId());
        final Optional<BankDetailsDto> bankDetailsDtoOptional = merchantService.fetchMerchantBankDetails(lenderAssociationDetailsRequest.getMerchantId());
        if (!bankDetailsDtoOptional.isPresent()) {
            log.info("bank details not found for merchantId : {}", lenderAssociationDetailsRequest.getMerchantId());
            throw new RuntimeException("bank details not found for SMFG application merchant id:" + lenderAssociationDetailsRequest.getMerchantId());
        }
        BankDetailsDto merchantBankDetail = bankDetailsDtoOptional.get();
        lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setPennyDropAccountNumber(merchantBankDetail.getAccountNumber()); // to later check if account is changed
        if (ObjectUtils.isEmpty(lendingRiskVariablesSnapshot)) {
            log.info("lending risk variable snapshot not found for applicationId : {}", lendingApplication.getId());
            throw new RuntimeException("lending risk variable snapshot not found for SMFG application " + lendingApplication.getId());
        }
        Map<String, String> businessCategoryAndSubCategoryMap = kycUtils.getBusinessCategoryAndSubCategory(lendingApplication.getMerchantId());
        PriorityQueue<BusinessDocsDTO> businessDocs = kycUtils.getBusinessDocData(lendingApplication.getMerchantId(), "SMFG", KycDocType.UDYAM_CERTIFICATE.name());
        try {
            SmfgAppPushRequest smfgAppPushRequest = SmfgAppPushRequest.builder()
                    .partnerapplicationid(lendingApplication.getExternalLoanId())
                    .partnerid(smfgConfig.getPartnerId())
                    .programtype(smfgConfig.getProgramType())
                    .apiaction(smfgConfig.getAppPushApiAction())
                    .leaddetails(SmfgAppPushRequest.LeadDetails.builder()
                            .firstname(nameAndDobDetailsDto.getFirstName())
                            .middlename(nameAndDobDetailsDto.getMiddleName())
                            .lastname(nameAndDobDetailsDto.getLastName())
                            .mobilenumber(kycUtils.getMobileFromKycData(cKycResponseDto))
                            .producttype(smfgConfig.getProductType())
                            .currentpincode(lendingApplication.getPincode())
                            .pep(smfgConfig.getPep())
                            .employmenttype(smfgConfig.getEmploymentType())
                            .callbackurl(smfgConfig.getCallbackUrl()).build())
                    .basicdetails(SmfgAppPushRequest.BasicDetails.builder()
                            .nationality(smfgConfig.getNationality())
                            .dob(DateTimeUtil.formatDate(nameAndDobDetailsDto.getDob(), "dd/MM/yyyy", "dd-MM-yyyy"))
                            .pannumber(cKycResponseDto.getPanNumber())
                            .monthlyincome(Optional.ofNullable(lendingRiskVariablesSnapshot.getMonthlyIncome()).map(Double::intValue).orElse(null))
                            .consentmode(smfgConfig.getConsentMode())
                            .consentdate(DateTimeUtil.getDateInFormat(lendingApplication.getCreatedAt(), "dd-MM-yyyy"))
                            .kycmode(smfgConfig.getKycMode())
                            .famhouseholdinc(smfgConfig.getFamHouseHoldingInclusive())
                            .gender(("M".equalsIgnoreCase(cKycResponseDto.getGender()) ? smfgConfig.getMaleGender() : smfgConfig.getFemaleGender())).build())
                    .loandetails(SmfgAppPushRequest.LoanDetails.builder()
                            .roi(lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().getAnnualRoi())
                            .tenure(lendingApplication.getPayableDays())
                            .loantype(getLoanTypeMapping(lendingRiskVariablesSnapshot.getRiskSegment()))
                            .loanamount(Optional.ofNullable(lendingApplication.getLoanAmount()).map(Double::intValue).orElse(null))
                            .processingfeewithgst(Optional.ofNullable(lendingApplication.getProcessingFee()).map(Double::intValue).orElse(null))
                            .partnerscorecardscore(lendingRiskVariablesSnapshot.getRiskGroup())
                            .stampdutywithgst(smfgConfig.getStampDutyWithGst()).build())
                    .repaymentdisbbankdetails(SmfgAppPushRequest.RepaymentDisbBankDetails.builder()
                            .accountholdername(merchantBankDetail.getBeneficiaryName())
                            .accountno(merchantBankDetail.getAccountNumber())
                            .accounttype("CURRENT".equalsIgnoreCase(merchantBankDetail.getAccountType()) ? smfgConfig.getCurrentAccountType() : smfgConfig.getSavingAccountType())
                            .bankname(merchantBankDetail.getBankName())
                            .ifsccode(merchantBankDetail.getIfsc()).build())
                    .mandatedetails(SmfgAppPushRequest.MandateDetails.builder()
                            .mandateregflag(smfgConfig.getNegativeMandateFlag())
                            .emifrequency(smfgConfig.getDailyInstallmentFrequency())
                            .build())
                    .additionaldetails(SmfgAppPushRequest.AdditionalDetails.builder()
                            .purposeofloan(smfgConfig.getPurposeOfLoan())
                            .ispanaadharlinked("YES")
                            .ispanverified("YES")
                            .isaadharmatched("YES")
                            .livelinessscore(cKycResponseDto.getSelfieLivelinessScore())
                            .selfiematchscore(!ObjectUtils.isEmpty(cKycResponseDto.getSelfieAadhaarFaceMatchPer()) ? cKycResponseDto.getSelfieAadhaarFaceMatchPer() / 100 : null)
                            .pennydropnamematchper(cKycResponseDto.getBankBenePanNameMatchPer())
                            .nfi(Optional.ofNullable(lendingRiskVariablesSnapshot.getMonthlyNfi()).map(Double::intValue).orElse(null))
                            .riskgroup(lendingRiskVariablesSnapshot.getRiskGroup())
                            .monthlyadjtpv(!ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getMonthlyTpv()) ? lendingRiskVariablesSnapshot.getMonthlyTpv().intValue() : null)
                            .appvintage(lendingRiskVariablesSnapshot.getVintage())
                            .last3monthtpv(Optional.ofNullable(lendingRiskVariablesSnapshot.getSummaryTpv()).map(tpv -> (int) (tpv * 90)).orElse(null))
                            .merchantcategory(businessCategoryAndSubCategoryMap.getOrDefault("businessCategory", ""))
                            .merchantsubcategory(businessCategoryAndSubCategoryMap.getOrDefault("businessSubcategory", "")).build())
                    .currentaddressdetails(getKycAddress(cKycResponseDto, smfgConfig.getCurrentAddressType()))
                    .permanentaddressdetails(getKycAddress(cKycResponseDto, smfgConfig.getPermanentAddressType()))
                    .workdetails(getShopAddress(lendingApplication))
                    .additionalinfo(SmfgAppPushRequest.AdditionalInfo.builder().build()).build();

            if (!businessDocs.isEmpty()) {
                BusinessDocsDTO udyamRegistration = businessDocs.poll();
                SmfgAppPushRequest.UdyamDetails udyamDetails = SmfgAppPushRequest.UdyamDetails.builder()
                        .pslflag(smfgConfig.getPslFlag())
                        .urcn(udyamRegistration.getDocIdentifier())
                        .build();
                smfgAppPushRequest.setUdyamdetails(udyamDetails);
                lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setDataUploadStatus(smfgConfig.getPslFlagTrue());
            }

            return NBFCRequestDTO.builder()
                    .applicationId(lendingApplication.getId())
                    .lender(lendingApplication.getLender())
                    .productName("LENDING")
                    .payload(smfgAppPushRequest).build();
        } catch (Exception e) {
            log.info("Exception in creating BRE payload of SMFG for application {} {} {}", lendingApplication.getId(), e.getLocalizedMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    public Boolean processBreCallback(NBFCResponseDTO nbfcResponseDTO) {
        try {
            LendingApplication lendingApplication = lendingApplicationDao.findById(Long.valueOf(nbfcResponseDTO.getApplicationId())).orElse(null);
            if (ObjectUtils.isEmpty(lendingApplication)) {
                log.info("No application found for applicationId : {}", nbfcResponseDTO.getApplicationId());
                return false;
            }
            LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(lendingApplication.getId(), Status.ACTIVE.name(), Lender.SMFG.name());
            if (ObjectUtils.isEmpty(lendingApplicationLenderDetails) || !nbfcResponseDTO.getLender().equalsIgnoreCase(lendingApplicationLenderDetails.getLender())) {
                log.info("No active LendingApplicationLenderDetails found with lender {} for applicationId {}", lendingApplication.getLender(), lendingApplication.getId());
                return false;
            }
            if (!LenderAssociationStages.BRE.name().equalsIgnoreCase(lendingApplicationLenderDetails.getStage())
                    || !LenderAssociationStatus.RISK_IN_PROGRESS.name().equalsIgnoreCase(lendingApplicationLenderDetails.getBreStatus())) {
                log.info("application not in correct state for BRE callback for applicationId {}", lendingApplication.getId());
                return false;
            }
            LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest = LenderAssociationDetailsRequestDto.builder()
                    .applicationId(lendingApplication.getId())
                    .lendingApplication(lendingApplication)
                    .lendingApplicationLenderDetails(lendingApplicationLenderDetails)
                    .modifyLender(enableLenderChange)
                    .manageState(true)
                    .build();
            if (nbfcResponseDTO.getSuccess() && Objects.nonNull(nbfcResponseDTO.getData())) {
                SmfgCallbackRequest callbackResponse = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDTO.getData()), SmfgCallbackRequest.class);
                log.info("Bre callback Response of SMFG for {} {}", nbfcResponseDTO.getApplicationId(), callbackResponse);
                if (!ObjectUtils.isEmpty(callbackResponse) && !ObjectUtils.isEmpty(callbackResponse.getData()) && !ObjectUtils.isEmpty(callbackResponse.getData().getOutput())) {
                    if ("Eligibility approve".equalsIgnoreCase(callbackResponse.getData().getCallbackStage()) && "APPROVE".equalsIgnoreCase(callbackResponse.getData().getOutput().getResult())) {
                        lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_COMPLETED.name());
                        commonService.manageApplicationStateAndPushToNextStage(lenderAssociationDetailsRequest);
                        return true;
                    }
                }
            }
            lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.ValidationStatus.CALLBACK_FAILED.name());
            lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_FAILED.name());
            commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequest, LenderAssociationStatus.RISK_FAILED);
        } catch (Exception e) {
            log.error("exception while processing bre callback of SMFG for {} {} {}", nbfcResponseDTO.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return false;
    }

    private boolean isBreChecksFailed(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest) {
        CKycResponseDto cKycResponseDto = lenderAssociationDetailsRequest.getCKycResponseDto();
        PanFetchKYCResponseDto panFetchKYCResponse = kycHandler.panFetch(cKycResponseDto.getPanNumber(), lenderAssociationDetailsRequest.getMerchantId());
        if (ObjectUtils.isEmpty(cKycResponseDto) || ObjectUtils.isEmpty(panFetchKYCResponse) || ObjectUtils.isEmpty(panFetchKYCResponse.getData())) {
            log.info("bre check failed application id {}, pan or kyc data missing for merchant id {}, {}, {}", lenderAssociationDetailsRequest.getApplicationId(), lenderAssociationDetailsRequest.getMerchantId(), cKycResponseDto, panFetchKYCResponse);
            lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.ValidationStatus.PAN_OR_KYC_DATA_MISSING.name());
            return true;
        }
        if (!panFetchKYCResponse.getData().getIsPanNsdlVerified()) {
            log.info("bre check failed application id {}, pan nsdl not verified {}", lenderAssociationDetailsRequest.getApplicationId(), panFetchKYCResponse.getData().getIsPanNsdlVerified());
            lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.ValidationStatus.PAN_NSDL_NOT_VERIFIED.name());
            return true;
        }
        if (!cKycResponseDto.getAadharNumber().equalsIgnoreCase(panFetchKYCResponse.getData().getMaskedAadhaar())) {
            log.info("bre check failed application id {}, pan aadhaar {} not equal kyc aadhaar {}", lenderAssociationDetailsRequest.getApplicationId(), panFetchKYCResponse.getData().getMaskedAadhaar(), cKycResponseDto.getAadharNumber());
            lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.ValidationStatus.PAN_AADHAAR_MISMATCH.name());
            return true;
        }
        Date cKycDob = KycUtils.getFormattedDate(cKycResponseDto.getDob());
        Date panDob = KycUtils.getFormattedDate(panFetchKYCResponse.getData().getVerifiedDob());
        if (ObjectUtils.isEmpty(cKycDob) || ObjectUtils.isEmpty(panDob) || !cKycDob.equals(panDob)) {
            log.info("bre check failed application id {}, pan dob {} not equal kyc dob {}", lenderAssociationDetailsRequest.getApplicationId(), panDob, cKycDob);
            lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.ValidationStatus.PAN_DOB_MISMATCH.name());
            return true;
        }
        if (ObjectUtils.isEmpty(cKycResponseDto.getSelfieAadhaarFaceMatchPer()) || cKycResponseDto.getSelfieAadhaarFaceMatchPer() < smfgConfig.getSelfieMatchPerThreshold()) {
            log.info("bre check failed application id {}, selfie aadhaar face match {} is empty or less than threshold {}", lenderAssociationDetailsRequest.getApplicationId(), cKycResponseDto.getSelfieAadhaarFaceMatchPer(), smfgConfig.getSelfieMatchPerThreshold());
            lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.ValidationStatus.SELFIE_MATCH_FAILED.name());
            return true;
        }
        if (ObjectUtils.isEmpty(cKycResponseDto.getSelfieLivelinessScore()) || cKycResponseDto.getSelfieLivelinessScore() < smfgConfig.getFaceLivelinessPerThreshold()) {
            log.info("bre check failed application id {}, selfie liveliness {} is empty or less than threshold {}", lenderAssociationDetailsRequest.getApplicationId(), cKycResponseDto.getSelfieLivelinessScore(), smfgConfig.getFaceLivelinessPerThreshold());
            lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.ValidationStatus.LIVELINESS_CHECK_FAILED.name());
            return true;
        }
        if (ObjectUtils.isEmpty(cKycResponseDto.getBankBenePanNameMatchPer()) || cKycResponseDto.getBankBenePanNameMatchPer() < smfgConfig.getBenePanNameMatchPerThreshold()) {
            log.info("bre check failed application id {}, bank and pan name match {} is empty or less than threshold {}", lenderAssociationDetailsRequest.getApplicationId(),  cKycResponseDto.getBankBenePanNameMatchPer(), smfgConfig.getBenePanNameMatchPerThreshold());
            lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.ValidationStatus.PAN_BANK_NAME_MISMATCH.name());
            return true;
        }
        return false;
    }

    private String getLoanTypeMapping(RiskSegment riskSegment) {
        switch (riskSegment) {
            case REPEAT:
                return smfgConfig.getRepeatLoanType();
            case REGULAR_ETC:
                return smfgConfig.getRegularEtcLoanType();
            default:
                return smfgConfig.getFreshLoanType();
        }
    }

    private SmfgAppPushRequest.AddressDetails getKycAddress(CKycResponseDto cKycResponseDto, String addressType) {
        String kycAddress = converterUtils.parseData(cKycResponseDto.getAddress());
        List<String> addresses = getAddresses(kycAddress);
        SmfgAppPushRequest.AddressDetails address = SmfgAppPushRequest.AddressDetails.builder()
                .address1(addresses.get(0))
                .address2(addresses.get(1))
                .address3(addresses.get(2))
                .addresstype(addressType)
                .pincode(cKycResponseDto.getPincode()).build();
        if (smfgConfig.getPermanentAddressType().equalsIgnoreCase(addressType)) {
            address.setPermanentaddressameas(smfgConfig.getCurrentAddressType());
        }
        return address;
    }

    private SmfgAppPushRequest.WorkDetails getShopAddress(LendingApplication lendingApplication) {
        String shopAddress = lendingApplicationServiceV2.constructShopAddress(lendingApplication);
        List<String> addresses = getAddresses(shopAddress);
        return SmfgAppPushRequest.WorkDetails.builder()
                .officeaddress1(addresses.get(0))
                .officeaddress2(addresses.get(1))
                .officeaddress3(addresses.get(2))
                .company(lendingApplication.getBusinessName())
                .officepincode(lendingApplication.getPincode()).build();
    }

    private List<String> getAddresses(String address) {
        List<String> addressList = new ArrayList<>();
        int addressSize = address.length();
        String address1 = "", address2 = "", address3 = null;
        if (addressSize <= 60) {
            address1 = address.substring(0, addressSize / 2);
            address2 = address.substring(addressSize / 2);
        } else if (addressSize <= 120) {
            address1 = address.substring(0, 60);
            address2 = address.substring(60, addressSize);
        } else if (addressSize <= 180) {
            address1 = address.substring(0, 60);
            address2 = address.substring(60, 120);
            address3 = address.substring(120, addressSize);
        } else {
            address1 = address.substring(0, 60);
            address2 = address.substring(60, 120);
            address3 = address.substring(120, 180);
        }
        addressList.add(address1);
        addressList.add(address2);
        addressList.add(address3);
        return addressList;
    }

}
