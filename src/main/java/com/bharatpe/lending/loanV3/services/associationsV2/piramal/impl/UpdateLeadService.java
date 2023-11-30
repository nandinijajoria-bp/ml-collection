package com.bharatpe.lending.loanV3.services.associationsV2.piramal.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingGstDetail;
import com.bharatpe.lending.common.dao.LendingBBSDao;
import com.bharatpe.lending.common.dao.LendingMerchantDetailsDao;
import com.bharatpe.lending.common.dao.LendingRiskVariablesSnapshotDao;
import com.bharatpe.lending.common.entity.LendingBBS;
import com.bharatpe.lending.common.entity.LendingMerchantDetails;
import com.bharatpe.lending.common.entity.LendingRiskVariablesSnapshot;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.common.dao.LendingApplicationKycDetailsDao;
import com.bharatpe.lending.dao.LendingGstDao;
import com.bharatpe.lending.common.entity.LendingApplicationKycDetails;
import com.bharatpe.lending.loanV3.dto.piramal.*;
import com.bharatpe.lending.loanV3.enums.StateMapping;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.associationsV2.piramal.validations.UpdateLeadValidationLayer;
import com.bharatpe.lending.loanV3.services.gateway.piramal.ILenderGateway;
import com.bharatpe.lending.loanV3.utils.ConverterUtils;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import com.bharatpe.lending.util.LoanUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.transaction.Transactional;
import java.util.*;

@Service
@Slf4j
public class UpdateLeadService {
    @Autowired
    private LendingRiskVariablesSnapshotDao lendingRiskVariablesSnapshotDao;
    @Autowired
    private LendingApplicationKycDetailsDao lendingApplicationKycDetailsDao;

    @Autowired
    CommonService commonService;

    @Autowired
    ILenderGateway iLenderGateway;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LendingGstDao lendingGstDao;

    @Autowired
    LendingBBSDao lendingBBSDao;

    @Autowired
    KycUtils kycUtils;

    @Autowired
    CreateLeadService createLeadService;

    @Autowired
    ConverterUtils converterUtils;

    @Autowired
    LendingMerchantDetailsDao lendingMerchantDetailsDao;

    @Autowired
    UpdateLeadValidationLayer updateLeadValidationLayer;

    @Autowired
    LoanUtil loanUtil;

    @Value("${vintage.internal.merchant:6}")
    int internalMerchantVinatge;

    @Value("${default.vintage:false}")
    Boolean defaultVintageAssignment;

    @Autowired
    MerchantService merchantService;

    @Transactional
    public Boolean updateLead(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto) {
        try {
            lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStages.PiramalAssociationStages.UPDATE_LEAD.name());
            lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.UPDATE_LEAD_PENDING.name());
            commonService.manageApplicationState(lenderAssociationDetailsRequestDto);
            // check this once and add validation layer
            lenderAssociationDetailsRequestDto.setCKycResponseDto(kycUtils.getKycData(lenderAssociationDetailsRequestDto.getMerchantId()));
            NbfcRequestDto updateLeadRequestDto = getPayload(lenderAssociationDetailsRequestDto);
            if (Objects.isNull(updateLeadRequestDto) || updateLeadValidationLayer.isInvalidPayload(updateLeadRequestDto, lenderAssociationDetailsRequestDto.getApplicationId())) {
                log.info("error in update lead payload for applicationId: {}", lenderAssociationDetailsRequestDto.getApplicationId());
                lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.UPDATE_LEAD_FAILED.name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequestDto, LenderAssociationStatus.UPDATE_LEAD_FAILED);
                return false;
            }
            NbfcResponseDto updateLeadResponseDTO = iLenderGateway.invokeStage(updateLeadRequestDto, LenderAssociationStages.PiramalAssociationStages.UPDATE_LEAD);
            log.info("update lead response from nbfc: {} with applicationId: {}", updateLeadResponseDTO, lenderAssociationDetailsRequestDto.getApplicationId());
            if (Objects.nonNull(updateLeadResponseDTO) && updateLeadResponseDTO.getSuccess() && Objects.nonNull(updateLeadResponseDTO.getData())) {
                lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.UPDATE_LEAD_COMPLETED.name());
                commonService.manageApplicationState(lenderAssociationDetailsRequestDto);
                return true;
            }
        } catch (Exception e) {
            log.error("error while pushing update lead for  {} {} {}", lenderAssociationDetailsRequestDto.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.UPDATE_LEAD_FAILED.name());
        commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequestDto, LenderAssociationStatus.UPDATE_LEAD_FAILED);
        return Boolean.FALSE;
    }

    private NbfcRequestDto getPayload(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto) {

        LendingApplication lendingApplication = lenderAssociationDetailsRequestDto.getLendingApplication();
        LendingGstDetail lendingGstDetail = lendingGstDao.findByApplicationId(lenderAssociationDetailsRequestDto.getApplicationId());
        LendingBBS lendingBBS = lendingBBSDao.findByMerchantId(lendingApplication.getMerchantId());
        LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = lendingRiskVariablesSnapshotDao.findByApplicationId(lenderAssociationDetailsRequestDto.getApplicationId());
        List<CreateLeadRequestDTO.ApplicantsDetail> applicant = new ArrayList<>();
        applicant.add(getApplicantDetails(lendingApplication, lendingBBS, lendingGstDetail, lenderAssociationDetailsRequestDto));
        int vintage = (int)Math.ceil(Optional.ofNullable(lendingRiskVariablesSnapshot.getVintage()).orElse(0L)/30.0);
        if (defaultVintageAssignment && loanUtil.isInternalMerchant(lenderAssociationDetailsRequestDto.getMerchantId())){
            vintage = internalMerchantVinatge;
        }
        CreateLeadRequestDTO createLeadRequestDTO = CreateLeadRequestDTO.builder()
                .leadId(lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().getLeadId())
                .partnerApplicationId((lendingApplication.getExternalLoanId()))
                .applicantsDetail(applicant)
                .additionalInformation(CreateLeadRequestDTO.AdditionalInformation.builder()
                        // TODO: 27/03/23 change this later as we dont have this data
                        .applicantProfile("VENDOR")
                        .loanSegment(lendingRiskVariablesSnapshot.getRiskSegment().name())
                        .pincodeColor(lendingRiskVariablesSnapshot.getPincodeColor().name())
                        .riskGroup(lendingRiskVariablesSnapshot.getRiskGroup())
                        .total60DaysTPV(lendingRiskVariablesSnapshot.getMonthlyTpv() * 2)
                        .monthlyNFI(lendingRiskVariablesSnapshot.getMonthlyNfi().intValue())
                        .bharatpeVintage(vintage)
                        .build())
                .auditTrailInformation(createAuditTrailList(lendingApplication, lenderAssociationDetailsRequestDto))
                .build();
        return NbfcRequestDto.builder()
                .applicationId(lenderAssociationDetailsRequestDto.getApplicationId())
                .payload(createLeadRequestDTO)
                .lender("PIRAMAL")
                .productName("LENDING")
                .build();
    }

    private CreateLeadRequestDTO.ApplicantsDetail getApplicantDetails(LendingApplication lendingApplication, LendingBBS lendingBBS,
                                                                      LendingGstDetail lendingGstDetail, LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto) {

        Optional<BasicDetailsDto> basicDetailsDto = merchantService.fetchMerchantBasicDetails(lendingApplication.getMerchantId());
//        LendingMerchantDetails lendingMerchantDetails = lendingMerchantDetailsDao.findTop1ByMerchantIdOrderByIdDesc(lendingApplication.getMerchantId());
        CreateLeadRequestDTO.ApplicantsDetail applicantsDetail = CreateLeadRequestDTO.ApplicantsDetail.builder()
                .customerId(lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().getCccId())
                .businessInformation(CreateLeadRequestDTO.ApplicantsDetail.BusinessInformation.builder()
                        .businessName(lendingApplication.getBusinessName())
                        .businessType("PROPRIETORSHIP")
                        .monthlyIncome(Objects.nonNull(lendingBBS) ? lendingBBS.getIncome() : 0d)
                        .businessAddress(getAddress(lenderAssociationDetailsRequestDto.getLendingApplication(), "OFFICE"))
                        .industry((Objects.nonNull(basicDetailsDto) && Objects.nonNull(basicDetailsDto.get().getBussinessCategory()) ?
                                basicDetailsDto.get().getBussinessCategory() : "BUSINESS"))
                        .subIndustry((Objects.nonNull(basicDetailsDto) && Objects.nonNull(basicDetailsDto.get().getSubCategory()))
                                ? basicDetailsDto.get().getSubCategory() : "BUSINESS")
                        .businessAddressType("SELF_OWNED")
                        .isGstEligible(lendingGstDetail.getGst())
                        .gstNumber(ObjectUtils.isEmpty(lendingGstDetail.getGstNumber()) ? null : lendingGstDetail.getGstNumber())
                        .monthlyNFI(Objects.nonNull(lendingBBS) ? lendingBBS.getNetFreeIncome() : 0d)
                        .build())
                .build();
        return applicantsDetail;
    }

    private CreateLeadRequestDTO.AuditTrailInformation createAuditTrailList(LendingApplication lendingApplication, LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto) {
        List<CreateLeadRequestDTO.AuditTrailInformation.AuditTrailList> auditTrailLists = new ArrayList<>();
        CreateLeadRequestDTO.AuditTrailInformation.AuditTrailList cibilAuditTrail = CreateLeadRequestDTO.AuditTrailInformation.AuditTrailList.builder()
                .auditCode("BRTPE_BUREAU_CONSENT")
                .auditName("I agree to avail the loan facilitation services offered by Resilient Digi Services Private Limited (RDSPL) through RBI registered NBFC/Banks and further authorize and appoint RDSPL as my authorized agent to receive my credit information from credit information companies such as Experian, CRIF High Mark etc. subject to the terms and conditions. I hereby also agree to the lending partner of RDSPL to obtain my credit information from the credit bureaus")
                .ipAddress(lendingApplication.getIp())
                .timeStamp(DateTimeUtil.getDateInFormat(lendingApplication.getCreatedAt(), "yyyy-MM-dd'T'HH:mm:ss.000'Z'"))
                .build();
        auditTrailLists.add(cibilAuditTrail);
        LendingApplicationKycDetails lendingApplicationKycDetails = lendingApplicationKycDetailsDao.findTop1ByApplicationIdOrderByIdDesc(lendingApplication.getId());
        CreateLeadRequestDTO.AuditTrailInformation.AuditTrailList kycAuditTrail = CreateLeadRequestDTO.AuditTrailInformation.AuditTrailList.builder()
                .auditCode("BRTPE_OKYC_CONSENT")
                .auditName(" I agree to sharing my KYC information with Bharatpe’s lending and other financial services partners")
                .ipAddress(lendingApplication.getIp())
                .timeStamp(DateTimeUtil.getDateInFormat(
                        (ObjectUtils.isEmpty(lendingApplicationKycDetails) ? lendingApplication.getCreatedAt() : lendingApplicationKycDetails.getConsentDate()),
                        "yyyy-MM-dd'T'HH:mm:ss.000'Z'"))
                .build();
        auditTrailLists.add(kycAuditTrail);

        CreateLeadRequestDTO.AuditTrailInformation.AuditTrailList addressAuditTrail = CreateLeadRequestDTO.AuditTrailInformation.AuditTrailList.builder()
                .auditCode("BRTPE_PERMADDR_CONSENT")
                .auditName("I undertake that my current address is same as mentioned in the submitted KYC documents")
                .ipAddress(lendingApplication.getIp())
                .timeStamp(DateTimeUtil.getDateInFormat(
                        (ObjectUtils.isEmpty(lendingApplicationKycDetails) ? lendingApplication.getCreatedAt() : lendingApplicationKycDetails.getConsentDate()),
                        "yyyy-MM-dd'T'HH:mm:ss.000'Z'"))
                .build();
        auditTrailLists.add(addressAuditTrail);


        CreateLeadRequestDTO.AuditTrailInformation auditTrailInformation = CreateLeadRequestDTO.AuditTrailInformation.builder()
                .auditTrailList(auditTrailLists)
                .build();
        return auditTrailInformation;
    }

    public CreateLeadRequestDTO.ApplicantsDetail.Applicant.Address getAddress(LendingApplication lendingApplication, String addressType) {
        String address = Optional.ofNullable(lendingApplication.getArea()).orElse("") + " " +
                Optional.ofNullable(lendingApplication.getLandmark()).orElse("");
        int addressSize = address.length();
        String address1 = "", address2 = "", address3 = "";
        if (addressSize <= 40) {
            address1 = address;
        } else if (addressSize <= 80) {
            address1 = address.substring(0, 40);
            address2 = address.substring(40, addressSize);
        } else {
            address1 = address.substring(0, 40);
            address2 = address.substring(40, 80);
            address3 = address.substring(80, addressSize);
        }
        CreateLeadRequestDTO.ApplicantsDetail.Applicant.Address currentAddress = CreateLeadRequestDTO.ApplicantsDetail.Applicant.Address
                .builder()
                .addressType(addressType)
                .addressLine3(address3)
                .addressLine1(address1)
                .addressLine2(address2)
                .city(lendingApplication.getCity())
                .street(lendingApplication.getStreetAddress())
                .buildingNumber(lendingApplication.getShopNumber())
                .stateCode(Objects.nonNull(StateMapping.getStateEnum(lendingApplication.getState())) ? StateMapping.getStateEnum(lendingApplication.getState()).name() : null)
                .country("INDIA")
                .postalCode(String.valueOf(lendingApplication.getPincode()))
                .build();
        return currentAddress;
    }

}
