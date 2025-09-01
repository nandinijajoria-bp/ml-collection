package com.bharatpe.lending.loanV3.services.associationsV2.trillionloans.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.LendingEnum;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.loanV3.config.TrillionLoansConfig;
import com.bharatpe.lending.loanV3.dto.CKycResponseDto;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.dto.request.trillionloans.TLKycRequestDto;
import com.bharatpe.lending.loanV3.dto.request.trillionloans.TLKycValidityRequestDTO;
import com.bharatpe.lending.loanV3.dto.request.trillionloans.TLSkipKycRequestDTO;
import com.bharatpe.lending.loanV3.dto.response.trillionloans.TLKycCallbackResponseDto;
import com.bharatpe.lending.loanV3.dto.response.trillionloans.TLKycValidityResponseDTO;
import com.bharatpe.lending.loanV3.enums.KycMode;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
public class TLKycService {

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    CommonService commonService;

    @Value("${lender.change.enabled:false}")
    Boolean enableLenderChange;

    @Autowired
    private ILenderAPIGateway lenderAPIGateway;

    @Autowired
    private KycUtils kycUtils;

    @Autowired
    TrillionLoansConfig trillionLoansConfig;

    private final List<String> kycCallbackValidStatus = Arrays.asList(LenderAssociationStatus.KYC_IN_PROGRESS.name(), LenderAssociationStatus.AADHAR_UPLOAD_IN_PROGRESS.name());

    public Boolean processKycCallback(NBFCResponseDTO nbfcResponseDTO) {
        try {
            LendingApplication lendingApplication = lendingApplicationDao.findById(Long.valueOf(nbfcResponseDTO.getApplicationId())).orElse(null);
            if (ObjectUtils.isEmpty(lendingApplication)) {
                log.info("No application found for applicationId : {}", nbfcResponseDTO.getApplicationId());
                return false;
            }
            LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(lendingApplication.getId(), Status.ACTIVE.name(), lendingApplication.getLender());
            if (ObjectUtils.isEmpty(lendingApplicationLenderDetails) || !nbfcResponseDTO.getLender().equalsIgnoreCase(lendingApplicationLenderDetails.getLender())) {
                log.info("No LendingApplicationLenderDetails found with lender {} for applicationId {}", lendingApplication.getLender(), lendingApplication.getId());
                return false;
            }
            if(!kycCallbackValidStatus.contains(lendingApplicationLenderDetails.getKycStatus())) {
                log.info("Kyc status {} not correct for kyc callback for applicationId {}", lendingApplicationLenderDetails.getKycStatus(), lendingApplication.getId());
                return false;
            }
            LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest = LenderAssociationDetailsRequestDto.builder()
                    .applicationId(lendingApplication.getId())
                    .lendingApplication(lendingApplication)
                    .lendingApplicationLenderDetails(lendingApplicationLenderDetails)
                    .manageState(true)
                    .modifyLender(enableLenderChange)
                    .build();
            if (Objects.nonNull(nbfcResponseDTO.getData()) && nbfcResponseDTO.getSuccess()) {
                TLKycCallbackResponseDto kycCallbackResponseDto = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDTO.getData()), TLKycCallbackResponseDto.class);
                log.info("KYC callback Response of TrillionLoans for {} {}", nbfcResponseDTO.getApplicationId(), kycCallbackResponseDto);
                if (!ObjectUtils.isEmpty(kycCallbackResponseDto)) {
                    if (kycCallbackResponseDto.getKycStatus().equalsIgnoreCase("VERIFIED")) {
                        lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.KYC_COMPLETED.name());
                        commonService.manageApplicationStateAndPushToNextStage(lenderAssociationDetailsRequest);
                        return true;
                    }
                }
            }
            lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.KYC_FAILED.name());
            Boolean isEligibleForLenderKyc = kycUtils.isEligibleForLenderKyc(lendingApplication.getLender(), lendingApplication.getMerchantId(), LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.getLoanType()));
            Boolean skipKycMode = LenderAssociationStages.SKIP_KYC.name().equalsIgnoreCase(lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().getKycMode());
            if(skipKycMode) {
                commonService.manageApplicationStateAndReInitiateLenderAssociation(lenderAssociationDetailsRequest, isEligibleForLenderKyc);
                return true;
            }
            commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequest, LenderAssociationStatus.KYC_FAILED);
        } catch (Exception e) {
            log.error("exception while processing KYC callback of Trillionloans for  {} {} {}", nbfcResponseDTO.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return false;
    }

    public Boolean invokeKyc(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest) {
        try {
            boolean isEligibleForLenderKyc = kycUtils.isEligibleForLenderKyc(Lender.TRILLIONLOANS.name(), lenderAssociationDetailsRequest.getMerchantId(), LoanType.TOPUP.name().equalsIgnoreCase(lenderAssociationDetailsRequest.getLendingApplication().getLoanType()));
            boolean isSkipKycCase = (ObjectUtils.isEmpty(lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().getKycMode())) ?
                    Optional.ofNullable(lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().getMetaData()).map(id -> id.get("eligibleForSkipKyc")).filter(Boolean.class::isInstance).map(Boolean.class::cast).orElse(false)
                    : LenderAssociationStages.SKIP_KYC.name().equalsIgnoreCase(lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().getKycMode());
            LendingApplication lendingApplication = lenderAssociationDetailsRequest.getLendingApplication();
            if (ObjectUtils.isEmpty(lenderAssociationDetailsRequest.getApplicationId()) || ObjectUtils.isEmpty(lendingApplication)) {
                log.info("Application Id not found for merchant: {}", lenderAssociationDetailsRequest.getMerchantId());
                return false;
            }
            String kycMode = (isSkipKycCase || isEligibleForLenderKyc) ? lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().getKycMode() : LenderAssociationStages.KYC.name();
            lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setKycMode(kycMode);
            lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStages.KYC.name());
            lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.KYC_PENDING.name());
            commonService.manageApplicationState(lenderAssociationDetailsRequest);
            LinkedHashMap<String, Object> identifier = new LinkedHashMap<>();
            identifier.put("isSyncKycCall", (isEligibleForLenderKyc || isSkipKycCase));
            NBFCRequestDTO<?> kycRequestPayload = NBFCRequestDTO.builder()
                    .applicationId(lendingApplication.getId())
                    .productName("LENDING")
                    .lender(LendingEnum.LENDER.TRILLIONLOANS.name())
                    .payload(TLKycRequestDto.builder()
                            .leadId(lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().getLeadId())
                            .build())
                    .identifier(identifier)
                    .build();

            NBFCResponseDTO<?> nbfcResponseDto = lenderAPIGateway.invokeStage(kycRequestPayload, LenderAssociationStages.KYC, trillionLoansConfig.getKycTimeoutThreshold());
            log.info("KYC response of Trillionloans from nbfc {} with applicationId: {}", nbfcResponseDto, lenderAssociationDetailsRequest.getApplicationId());
            if (!ObjectUtils.isEmpty(nbfcResponseDto)) {
                if(nbfcResponseDto.getSuccess() && !ObjectUtils.isEmpty(nbfcResponseDto.getData())) {
                    LenderAssociationStatus kycStatus = isSkipKycCase ? (isEligibleForLenderKyc ? LenderAssociationStatus.SKIP_KYC_CONSENT_PENDING : LenderAssociationStatus.SKIP_KYC_PENDING) : (isEligibleForLenderKyc ? LenderAssociationStatus.EKYC_PENDING : LenderAssociationStatus.KYC_IN_PROGRESS);
                    lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setKycStatus(kycStatus.name());
                    commonService.manageApplicationState(lenderAssociationDetailsRequest);
                    return true;
                }
                if(nbfcResponseDto.getRetry()) {
                    log.info("Kyc request of trillionLoans pushed to retry for {}", lenderAssociationDetailsRequest.getApplicationId());
                    lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.KYC_RETRY.name());
                    commonService.manageApplicationState(lenderAssociationDetailsRequest);
                    return false;
                }
            }
        } catch (Exception e) {
            log.error("exception occurred while KYC of Trillionloans for {} {} {}", lenderAssociationDetailsRequest.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.KYC_FAILED.name());
        commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequest, LenderAssociationStatus.KYC_FAILED);
        return false;
    }

    public Boolean kycStatusCheck(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto) {
        try {
            if (ObjectUtils.isEmpty(lenderAssociationDetailsDto.getLendingApplication()) || ObjectUtils.isEmpty(lenderAssociationDetailsDto.getLendingApplicationLenderDetails())) {
                log.info("LendingApplication / LendingApplicationLenderDetails is empty for request {} {} ", lenderAssociationDetailsDto.getApplicationId(), lenderAssociationDetailsDto);
                return false;
            }
            NBFCRequestDTO<?> cKycInfoDto = NBFCRequestDTO.builder()
                    .applicationId(lenderAssociationDetailsDto.getLendingApplication().getId())
                    .productName("LENDING")
                    .lender(LendingEnum.LENDER.TRILLIONLOANS.name())
                    .payload(TLKycRequestDto.builder()
                            .leadId(lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getLeadId())
                            .build())
                    .build();
            NBFCResponseDTO<?> nbfcResponseDto = lenderAPIGateway.invokeStage(cKycInfoDto, LenderAssociationStages.KYC_STATUS_CHECK);
            return processKycCallback(nbfcResponseDto);
        } catch (Exception ex) {
            log.error("exception occurred while processing kyc status check request {} {}", ex.getMessage(), Arrays.asList(ex.getStackTrace()));
        }
        lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.EKYC_PENDING.name());
        lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycRetryCount(ObjectUtils.isEmpty(lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getKycRetryCount()) ? 0 : lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getKycRetryCount());
        commonService.manageApplicationState(lenderAssociationDetailsDto);
        return false;
    }

    public Boolean kycValidityCheck(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto) {
        try {
            LendingApplication lendingApplication = lenderAssociationDetailsDto.getLendingApplication();
            if (ObjectUtils.isEmpty(lenderAssociationDetailsDto.getApplicationId()) || ObjectUtils.isEmpty(lendingApplication)) {
                log.info("Application details not found for merchant: {}", lenderAssociationDetailsDto.getMerchantId());
                return false;
            }
            boolean isEligibleForLenderKyc = kycUtils.isEligibleForLenderKyc(lendingApplication.getLender(), lendingApplication.getMerchantId(), LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.getLoanType()));
            NBFCRequestDTO<?> payload = NBFCRequestDTO.builder()
                    .applicationId(lendingApplication.getId())
                    .lender(LendingEnum.LENDER.TRILLIONLOANS.name())
                    .productName("LENDING")
                    .payload(TLKycValidityRequestDTO.builder()
                            .leadId(lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getLeadId())
                            .build())
                    .build();
            int retryCount = 0;
            NBFCResponseDTO<?> nbfcResponseDto = null;
            do {
                nbfcResponseDto = lenderAPIGateway.invokeStage(payload, LenderAssociationStages.KYC_VALIDITY, trillionLoansConfig.getKycValidityTimeoutThreshold());
                retryCount++;
            } while (ObjectUtils.isEmpty(nbfcResponseDto) && retryCount < trillionLoansConfig.getKycValidityRetryCount());

            Map<String, Object> metaData = Optional.ofNullable(lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getMetaData()).orElse(new HashMap<>());
            metaData.put("eligibleForLenderKyc", isEligibleForLenderKyc);
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setMetaData(metaData);
            log.info("KYC validity response of TrillionLoans from nbfc {} with applicationId: {}", nbfcResponseDto, lenderAssociationDetailsDto.getApplicationId());
            if(!ObjectUtils.isEmpty(nbfcResponseDto) && nbfcResponseDto.getSuccess() && !ObjectUtils.isEmpty(nbfcResponseDto.getData())) {
                TLKycValidityResponseDTO response = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDto.getData()), TLKycValidityResponseDTO.class);
                log.info("KYC validity parsed response {}", response);
                if(!ObjectUtils.isEmpty(response) && trillionLoansConfig.getKycValiditySuccessMessage().equalsIgnoreCase(response.getMessage()) && !ObjectUtils.isEmpty(response.getData())) {
                    lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(isEligibleForLenderKyc ? LenderAssociationStatus.KYC_PENDING.name() : LenderAssociationStatus.SKIP_KYC_CONSENT_PENDING.name());
                    lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.KYC_VALIDITY_SUCCESS.name());
                    metaData = Optional.ofNullable(lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getMetaData()).orElse(new HashMap<>());
                    metaData.put("kycReuseLoanId", response.getData().getLastUsed().get(0).getLoanApplicationId());
                    metaData.put("eligibleForSkipKyc", true);
                    lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setMetaData(metaData);
                    kycUtils.savePoaDetailsForKyc(lendingApplication, KycMode.SKIP_KYC.name(), populateKycDetails(response.getData()));
                    commonService.manageApplicationState(lenderAssociationDetailsDto);
                    return true;
                }
            }
        } catch (Exception ex) {
            log.error("exception occurred while processing kyc validity check request for {} {}", lenderAssociationDetailsDto, Arrays.asList(ex.getStackTrace()));
        }
        lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.KYC_VALIDITY_FAILED.name());
        lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.KYC_PENDING.name());
        commonService.manageApplicationState(lenderAssociationDetailsDto);
        return true;
    }


    private CKycResponseDto populateKycDetails(TLKycValidityResponseDTO.ResponseData kycDetails) {
        if(ObjectUtils.isEmpty(kycDetails)) throw new RuntimeException("Kyc Details not found in kyc validity check for lender");
        if(ObjectUtils.isEmpty(kycDetails.getName())) throw new RuntimeException("Aadhaar name not found in kyc validity check for lender");
        TLKycValidityResponseDTO.AddressInfo addressInfo = kycDetails.getAddressInfo().get(0);
        if(ObjectUtils.isEmpty(addressInfo)) throw new RuntimeException("Address Details not found in kyc validity check for lender");
        CKycResponseDto cKycDto = new CKycResponseDto();
        cKycDto.setAddress(addressInfo.getAddressLine1() + " " + addressInfo.getAddressLine2() + " " + addressInfo.getCity() + " " + addressInfo.getPincode());
        cKycDto.setDob(kycDetails.getDob());
        cKycDto.setCity(addressInfo.getCity());
        cKycDto.setName(kycDetails.getName());
        cKycDto.setPincode(addressInfo.getPincode());
        cKycDto.setCareOf(kycDetails.getDependent());
        return cKycDto;
    }

    public Boolean skipKyc(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto) {
        Boolean isEligibleForLenderKyc = kycUtils.isEligibleForLenderKyc(lenderAssociationDetailsDto.getLendingApplication().getLender(), lenderAssociationDetailsDto.getLendingApplication().getMerchantId(), LoanType.TOPUP.name().equalsIgnoreCase(lenderAssociationDetailsDto.getLendingApplication().getLoanType()));
        try {
            LendingApplication lendingApplication = lenderAssociationDetailsDto.getLendingApplication();
            if (ObjectUtils.isEmpty(lenderAssociationDetailsDto.getApplicationId()) || ObjectUtils.isEmpty(lendingApplication)) {
                log.info("Application details not found for merchant : {}", lenderAssociationDetailsDto.getMerchantId());
                return false;
            }
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.SKIP_KYC_PENDING.name());
            commonService.manageApplicationState(lenderAssociationDetailsDto);
            NBFCRequestDTO<?> payload = getSkipKycPayload(lendingApplication, lenderAssociationDetailsDto.getLendingApplicationLenderDetails());
            if(ObjectUtils.isEmpty(payload)) {
                log.info("error in creating trillion skip kyc payload for application {}",lenderAssociationDetailsDto.getLendingApplication().getId());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.ValidationStatus.PAYLOAD_ERROR.name());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.SKIP_KYC_FAILED.name());
                commonService.manageApplicationStateAndReInitiateLenderAssociation(lenderAssociationDetailsDto,isEligibleForLenderKyc);
                return false;
            }
            int retryCount = 0;
            NBFCResponseDTO<?> nbfcResponseDto = null;
            do {
                nbfcResponseDto = lenderAPIGateway.invokeStage(payload, LenderAssociationStages.SKIP_KYC, trillionLoansConfig.getSkipKycTimeoutThreshold());
                retryCount++;
            } while (ObjectUtils.isEmpty(nbfcResponseDto) && retryCount < trillionLoansConfig.getKycValidityRetryCount());

            log.info("Skip Kyc response of TrillionLoans from nbfc {} with applicationId: {}", nbfcResponseDto, lenderAssociationDetailsDto.getApplicationId());
            if(!ObjectUtils.isEmpty(nbfcResponseDto) && nbfcResponseDto.getSuccess() && !ObjectUtils.isEmpty(nbfcResponseDto.getData())) {
                TLKycValidityResponseDTO response = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDto.getData()), TLKycValidityResponseDTO.class);
                log.info("Skip Kyc parsed response {}", response);
                if(!ObjectUtils.isEmpty(response) && trillionLoansConfig.getSkipKycSuccessMessage().equalsIgnoreCase(response.getMessage())) {
                    lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.SKIP_KYC_SUCCESS.name());
                    lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.KYC_IN_PROGRESS.name());
                    commonService.manageApplicationState(lenderAssociationDetailsDto);
                    return true;
                }
            }
        } catch (Exception ex) {
            log.error("Exception occurred while processing skip kyc request for {} {}", lenderAssociationDetailsDto, Arrays.asList(ex.getStackTrace()));
        }
        lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.SKIP_KYC_FAILED.name());
        lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.SKIP_KYC_FAILED.name());
        commonService.manageApplicationStateAndReInitiateLenderAssociation(lenderAssociationDetailsDto, isEligibleForLenderKyc);
        return false;
    }

    private NBFCRequestDTO<?> getSkipKycPayload(LendingApplication application, LendingApplicationLenderDetails lenderDetails) {
        try {
            Integer kycReuseLeadId = !ObjectUtils.isEmpty(lenderDetails.getMetaData()) ? (Integer) lenderDetails.getMetaData().getOrDefault("kycReuseLoanId", null) : null;
            LinkedHashMap<String, Object> identifier = new LinkedHashMap<>();
            identifier.put("leadId", lenderDetails.getLeadId());
            TLSkipKycRequestDTO skipKycRequest = TLSkipKycRequestDTO.builder()
                    .consentKey(trillionLoansConfig.getSkipKycConsentKey())
                    .isAccepted(Boolean.TRUE)
                    .dateTime(DateTimeUtil.getDateInFormat(new Date(), "dd-MM-yyyy"))
                    .ipAddress(application.getIp())
                    .reuseLoanId(String.valueOf(kycReuseLeadId))
                    .build();
            return NBFCRequestDTO.builder()
                    .productName("LENDING")
                    .lender(Lender.TRILLIONLOANS.name())
                    .applicationId(application.getId())
                    .payload(skipKycRequest)
                    .identifier(identifier)
                    .build();
        } catch (Exception e) {
            log.error("Exception in creating {} skip kyc payload for applicationId {} {}", application.getLender(), application.getId(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }
}
