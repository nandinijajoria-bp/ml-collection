package com.bharatpe.lending.loanV3.services;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationKycDetailsDao;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.dao.LendingApplicationVkycDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationKycDetails;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.entity.LendingApplicationVkycDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.VkycStatus;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.config.VkycConfig;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dto.vkyc.request.SkipVkycRequestDto;
import com.bharatpe.lending.dto.vkyc.request.VKycInitiateRequestDto;
import com.bharatpe.lending.dto.vkyc.request.VkycEligibilityRequestDto;
import com.bharatpe.lending.dto.vkyc.request.VkycStatusRequestDto;
import com.bharatpe.lending.dto.vkyc.response.VKycInitiateResponseDto;
import com.bharatpe.lending.dto.vkyc.response.VkycEligibilityResponseDto;
import com.bharatpe.lending.dto.vkyc.response.VkycStatusResponseDto;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.lendingplatform.lending.service.VkycServiceV2;
import com.bharatpe.lending.lendingplatform.lending.util.RolloutUtil;
import com.bharatpe.lending.loanV2.dto.ApiResponse;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import com.bharatpe.lending.loanV3.revamp.services.LoanDetailsV3Service;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;

import static com.bharatpe.lending.lendingplatform.nbfc.enums.Lender.CREDITSAISON;

@Slf4j
@Service
@RequiredArgsConstructor
public class VKycService {
    private final LendingApplicationDao lendingApplicationDao;
    private final LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;
    private final ILenderAPIGateway lenderAPIGateway;
    private final CommonService commonService;
    private final ObjectMapper objectMapper;
    private final VkycConfig vkycConfig;
    private final LendingApplicationVkycDetailsDao lendingApplicationVkycDetailsDao;
    private final EasyLoanUtil easyLoanUtil;
    private final LendingApplicationKycDetailsDao lendingApplicationKycDetailsDao;
    private final LoanDetailsV3Service loanDetailsV3Service;
    private final RolloutUtil rolloutUtil;

    @Autowired
    @Lazy
    private VkycServiceV2 vkycServiceV2;


    private static final String LENDING = "LENDING";

    public void setVkycEligibility(LendingApplicationVkycDetails vkycDetails, String leadId) {
        if (!vkycConfig.getEligibilityCheckLenders().contains(vkycDetails.getLender())) {
            vkycDetails.setVkycEligible(true);
            vkycDetails.setDkycEligible(vkycConfig.getDkycEligibleLenders().contains(vkycDetails.getLender()));
            return;
        }
        NBFCRequestDTO<VkycEligibilityRequestDto> requestDTO = NBFCRequestDTO.<VkycEligibilityRequestDto>builder()
                .applicationId(vkycDetails.getApplicationId())
                .lender(vkycDetails.getLender())
                .productName(LENDING)
                .payload(VkycEligibilityRequestDto.builder()
                        .leadId(leadId)
                        .build())
                .build();
        NBFCResponseDTO<?> responseDTO = lenderAPIGateway.invokeStage(requestDTO, LenderAssociationStages.CHECK_VKYC_ELIGIBILITY); //todo handle error reason if required.
        if (!ObjectUtils.isEmpty(responseDTO) && responseDTO.getSuccess() && !ObjectUtils.isEmpty(responseDTO.getData())) {
            VkycEligibilityResponseDto eligibilityResponse = objectMapper.convertValue(responseDTO.getData(), VkycEligibilityResponseDto.class);
            log.info("check vkyc eligibility response for lender {} for applicationId: {}, {}", vkycDetails.getLender(), vkycDetails.getApplicationId(), eligibilityResponse);
            if (!ObjectUtils.isEmpty(eligibilityResponse)) {
                vkycDetails.setVkycEligible(Boolean.TRUE.equals(eligibilityResponse.isVkycEligible()));
                vkycDetails.setDkycEligible(Boolean.TRUE.equals(eligibilityResponse.isDkycEligible()));
                return;
            }
        }
        vkycDetails.setVkycEligible(null);
        vkycDetails.setDkycEligible(null);
    }

    public ApiResponse<?> initiateVKyc(Long merchantId, Long applicationId, String lender, Boolean isRetry) {
        log.info("request received to initiate vkyc for merchantId: {}, applicationId: {}, lender: {}", merchantId, applicationId, lender);
        try {
            LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchantId(applicationId, merchantId);
            if (ObjectUtils.isEmpty(lendingApplication) || !Arrays.asList("pending_verification", "approved").contains(lendingApplication.getStatus())) {
                log.info("No application found for given merchantId {} and applicationId {} for initiate vkyc", merchantId, applicationId);
                return new ApiResponse<>(false, null, "No Application found for applicationId");
            }
            LendingApplicationLenderDetails lenderDetails = lendingApplicationLenderDetailsDao.findTop1ByApplicationIdAndLenderOrderByIdDesc(applicationId, lendingApplication.getLender());
            if (ObjectUtils.isEmpty(lenderDetails) || ObjectUtils.isEmpty(lenderDetails.getLeadId())) {
                log.info("No lender details or lead id found for given lender {} and applicationId {} for initiate vkyc", lendingApplication.getLender(), applicationId);
                return new ApiResponse<>(false, null, "No Lender details found for given applicationId and lender");
            }
            LendingApplicationVkycDetails vkycDetails = lendingApplicationVkycDetailsDao.findByApplicationIdAndLender(applicationId, lendingApplication.getLender()).orElse(null);
            if (ObjectUtils.isEmpty(vkycDetails) || !vkycDetails.getVkycEligible()) {
                log.info("No vkyc details found or vkyc not eligible for given lender {} and applicationId {} for initiate vkyc", lendingApplication.getLender(), applicationId);
                return new ApiResponse<>(false, null, "No vkyc details found or vkyc not eligible for given lender");
            }

// Handles vKYC initiation specifically for Credit Saison when vKYC is enabled for the merchant
            if (CREDITSAISON.name().equalsIgnoreCase(lender)) {
                if (rolloutUtil.isEligibleForCreditSaisonVkyc(merchantId)) {
                    return vkycServiceV2.processVKycInitiation(lendingApplication, lenderDetails, vkycDetails, isRetry);
                }
                log.error("CreditSaison vkyc is not enabled after intiate vkyc Call for merchantId: {}, applicationId: {}, lender: {}", merchantId, applicationId, lender);
                return new ApiResponse<>(false, null, "CreditSaison vkyc is not enabled for this merchant");
            }

            if (isDisableInitiateVkycSession(vkycDetails)) {
                log.info("Initiate vkyc session is disabled for given lender {} and applicationId {}", lendingApplication.getLender(), applicationId);
                return new ApiResponse<>(false, null, "Initiate vkyc session is disabled for this application id");
            }
            NBFCRequestDTO<VKycInitiateRequestDto> requestDTO = NBFCRequestDTO.<VKycInitiateRequestDto>builder()
                    .applicationId(lendingApplication.getId())
                    .lender(lendingApplication.getLender())
                    .productName(LENDING)
                    .payload(VKycInitiateRequestDto.builder()
                            .redirectUrl(vkycConfig.getRedirectUrl())
                            .isRetry(isRetry)
                            .leadId(lenderDetails.getLeadId())
                            .build())
                    .build();
            NBFCResponseDTO<?> responseDTO = lenderAPIGateway.invokeStage(requestDTO, LenderAssociationStages.INITIATE_VKYC);
            if (!ObjectUtils.isEmpty(responseDTO) && responseDTO.getSuccess() && !ObjectUtils.isEmpty(responseDTO.getData())) {
                VKycInitiateResponseDto initiateResponse = objectMapper.convertValue(responseDTO.getData(), VKycInitiateResponseDto.class);
                log.info("vKyc initiate response of {} from nbfc with applicationId: {}, {}", lendingApplication.getLender(), lendingApplication.getId(), initiateResponse);
                if (!ObjectUtils.isEmpty(initiateResponse) && !ObjectUtils.isEmpty(initiateResponse.getSessionUrl())) {
                    vkycDetails.setSessionUrl(initiateResponse.getSessionUrl());
                    vkycDetails.setStatus(VkycStatus.VKYC_IN_PROGRESS);
                    lendingApplicationVkycDetailsDao.save(vkycDetails);
                    return new ApiResponse<>(initiateResponse);
                }
            }
            return new ApiResponse<>(false, null, "Could not generate vkyc session link, please try again later");
        } catch (Exception e) {
            log.info("Exception in initiating vKyc of {} for applicationId {} {} {}", lender, applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return new ApiResponse<>(false, null, "Something went wrong while initiating vKyc");
    }

    public ApiResponse<?> statusCheck(Long merchantId, Long applicationId, String lender) {
        try {
            LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchantId(applicationId, merchantId);
            if (ObjectUtils.isEmpty(lendingApplication)) {
                log.info("No application found for given merchantId {} and applicationId {}", merchantId, applicationId);
                return new ApiResponse<>(false, null, "No Application found for applicationId");
            }
            LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1ByApplicationIdAndLenderOrderByIdDesc(applicationId, lender);
            if (ObjectUtils.isEmpty(lendingApplicationLenderDetails) || ObjectUtils.isEmpty(lendingApplicationLenderDetails.getLeadId())) {
                log.info("No lender details or leadId found for given lender {} and applicationId {}", lender, applicationId);
                return new ApiResponse<>(false, null, "No Lender details found for given applicationId and lender");
            }
            LendingApplicationVkycDetails lendingApplicationVkycDetails = lendingApplicationVkycDetailsDao.findByApplicationIdAndLender(applicationId, lender).orElse(null);
            if (ObjectUtils.isEmpty(lendingApplicationVkycDetails)) {
                log.info("No vkyc details or leadId found for given lender {} and applicationId {}", lender, applicationId);
                return new ApiResponse<>(false, null, "No Vkyc details found for given applicationId and lender");
            }
            if (isDisableInitiateVkycSession(lendingApplicationVkycDetails)) {
                log.info("vkyc session disabled : {} for given lender {} and applicationId {}", lendingApplicationVkycDetails.getSessionStatus(), lender, applicationId);
                return new ApiResponse<>(LendingApplicationVkycDetails.getDto(lendingApplicationVkycDetails));
            }
            return statusCheck(lendingApplication, lendingApplicationVkycDetails, lendingApplicationLenderDetails);
        } catch (Exception e) {
            log.info("Exception in checking vKyc status of {} for applicationId {} {} {}", lender, applicationId, e.getLocalizedMessage(), Arrays.asList(e.getStackTrace()));
        }
        return new ApiResponse<>(false, null, "Something went wrong while vKyc status Check");
    }


    public ApiResponse<?> statusCheck(LendingApplication lendingApplication, LendingApplicationVkycDetails vkycDetails, LendingApplicationLenderDetails lenderDetails) {
        try {
            NBFCRequestDTO<VkycStatusRequestDto> requestDTO = NBFCRequestDTO.<VkycStatusRequestDto>builder()
                    .applicationId(lendingApplication.getId())
                    .productName(LENDING)
                    .lender(lendingApplication.getLender())
                    .payload(VkycStatusRequestDto.builder()
                            .leadId(lenderDetails.getLeadId())
                            .build())
                    .build();
            NBFCResponseDTO<?> responseDTO = lenderAPIGateway.invokeStage(requestDTO, LenderAssociationStages.VKYC_STATUS_CHECK); //todo handle error reason if required.
            if (!ObjectUtils.isEmpty(responseDTO) && responseDTO.getSuccess() && !ObjectUtils.isEmpty(responseDTO.getData())) {
                VkycStatusResponseDto statusCheckResponse = objectMapper.convertValue(responseDTO.getData(), VkycStatusResponseDto.class);
                log.info("vKyc status response of {} from nbfc with applicationId: {}, {}", lendingApplication.getLender(), lendingApplication.getId(), statusCheckResponse);
                if (!ObjectUtils.isEmpty(statusCheckResponse) && !ObjectUtils.isEmpty(statusCheckResponse.getSessionStatus())) {
                    persistVKycStatusResponse(statusCheckResponse, lendingApplication, vkycDetails, lenderDetails);
                    return new ApiResponse<>(LendingApplicationVkycDetails.getDto(vkycDetails));
                }
            }
            log.info("vKyc status response is empty or invalid for applicationId {}", lendingApplication.getId());
        } catch (Exception e) {
            log.info("Exception in checking vKyc status of {} for applicationId {} {} {}", lendingApplication.getLender(), lendingApplication.getId(), e.getLocalizedMessage(), Arrays.asList(e.getStackTrace()));
        }
        return new ApiResponse<>(false, null, "Something went wrong while vKyc status Check");
    }


    public void consumeVKycCallback(NBFCResponseDTO<VkycStatusResponseDto> callbackRequest) {
        log.info("Vkyc callback received {}", callbackRequest);
        try {
            if (ObjectUtils.isEmpty(callbackRequest) || !callbackRequest.getSuccess() || ObjectUtils.isEmpty(callbackRequest.getData())) {
                log.info("invalid vKyc callback request");
                return;
            }
            LendingApplication lendingApplication = lendingApplicationDao.findById(Long.valueOf(callbackRequest.getApplicationId())).orElse(null);
            if (ObjectUtils.isEmpty(lendingApplication)) {
                log.info("No application found for applicationId {}", callbackRequest.getApplicationId());
                return;
            }
            LendingApplicationLenderDetails lenderDetails = lendingApplicationLenderDetailsDao.findTop1ByApplicationIdAndLenderOrderByIdDesc(lendingApplication.getId(), lendingApplication.getLender());
            if (ObjectUtils.isEmpty(lenderDetails)) {
                log.info("No lender details found for given lender {} and applicationId {}", lendingApplication.getLender(), lendingApplication.getId());
                return;
            }
            LendingApplicationVkycDetails vkycDetails = lendingApplicationVkycDetailsDao.findByApplicationIdAndLender(lendingApplication.getId(), lendingApplication.getLender()).orElse(null);
            if (ObjectUtils.isEmpty(vkycDetails)) {
                log.info("No vkyc details found for given lender : {} and applicationId : {}", lendingApplication.getLender(), lendingApplication.getId());
                return;
            }
            if (!VkycStatus.VKYC_IN_PROGRESS.equals(vkycDetails.getStatus())) {
                log.info("Vkyc status is not in correct state {} for callback consumption for applicationId {}", vkycDetails.getStatus(), lendingApplication.getId());
                return;
            }
            persistVKycStatusResponse(callbackRequest.getData(), lendingApplication, vkycDetails, lenderDetails);
            if (VkycStatus.getTerminatedVkycStatusList().contains(vkycDetails.getStatus())) {
                loanDetailsV3Service.saveApplicationViewState(null, lendingApplication.getId(), LendingViewStates.APPLICATION_STATUS_PAGE);
            }
        } catch (Exception e) {
            log.info("Exception in vkyc callback consumption of {} for applicationId {} {} {}", callbackRequest.getLender(), callbackRequest.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
    }

    public void persistVKycStatusResponse(VkycStatusResponseDto vKycStatusCheckResponse, LendingApplication lendingApplication, LendingApplicationVkycDetails vkycDetails, LendingApplicationLenderDetails lenderDetails) {
        log.info("Updating vKyc status for applicationId {} with statusCheck response {}", lendingApplication.getId(), vKycStatusCheckResponse);
        try {
            vkycDetails.setStatus(Boolean.TRUE.equals(vKycStatusCheckResponse.getLeadRejected()) ? VkycStatus.VKYC_REJECTED
                    : getVkycStatusMapping(vKycStatusCheckResponse.getSessionStatus()));
            vkycDetails.setSessionStatus(vKycStatusCheckResponse.getSessionStatus().name()); // todo test this if this needs to be set when its not AADHAAR_EXPIRED
            vkycDetails.setRejectReason(vKycStatusCheckResponse.getReason());
            if (VkycStatus.VKYC_COMPLETED.equals(vkycDetails.getStatus())) {
                vkycDetails.setApprovedAt(new Date());
            }
            lendingApplicationVkycDetailsDao.save(vkycDetails);
            if (VkycStatus.VKYC_REJECTED.equals(vkycDetails.getStatus())) {
                lenderDetails.setLeadStatus(VkycStatus.VKYC_REJECTED.name());
                commonService.rejectApplication(lendingApplication, lenderDetails);
                return;
            }
            if (VkycStatus.VKYC_RETRY.equals(vkycDetails.getStatus())) {
                handleVkycRetryApplications(lendingApplication, vkycDetails);
            }
        } catch (Exception e) {
            log.info("Exception in handling vKyc status response for applicationId {} {} {}", lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
    }

    public ApiResponse<?> initiateDkyc(Long merchantId, Long applicationId, String lender) {
        log.info("request received to initiate dkyc for merchantId: {}, applicationId: {}, lender: {}", merchantId, applicationId, lender);
        try {
            LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchantId(applicationId, merchantId);
            if (ObjectUtils.isEmpty(lendingApplication) || !Arrays.asList("pending_verification","approved").contains(lendingApplication.getStatus())) {
                log.info("No application found for given merchantId {} and applicationId {} for initiate dkyc", merchantId, applicationId);
                return new ApiResponse<>(false, null, "No Application found for applicationId");
            }
            LendingApplicationLenderDetails lenderDetails = lendingApplicationLenderDetailsDao.findTop1ByApplicationIdAndLenderOrderByIdDesc(applicationId, lendingApplication.getLender());
            if (ObjectUtils.isEmpty(lenderDetails) || ObjectUtils.isEmpty(lenderDetails.getLeadId())) {
                log.info("No lender details or lead id found for given lender {} and applicationId {} for initiate dkyc", lendingApplication.getLender(), applicationId);
                return new ApiResponse<>(false, null, "No Lender details found for given applicationId and lender");
            }
            LendingApplicationVkycDetails vkycDetails = lendingApplicationVkycDetailsDao.findByApplicationIdAndLender(applicationId, lendingApplication.getLender()).orElse(null);
            if (ObjectUtils.isEmpty(vkycDetails) || !vkycDetails.getDkycEligible()) {
                log.info("No dkyc details found or dkyc not eligible for given lender {} and applicationId {} for initiate dkyc", lendingApplication.getLender(), applicationId);
                return new ApiResponse<>(false, null, "No dkyc details found or dkyc not eligible for given lender");
            }
            return initiateDkyc(lendingApplication, lenderDetails, vkycDetails);
        } catch (Exception e) {
            log.info("Exception in initiating dkyc of {} for applicationId {} {} {}", lender, applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return new ApiResponse<>(false, null, "Something went wrong while initiating dKyc");
    }

    public ApiResponse<?> initiateDkyc(LendingApplication lendingApplication, LendingApplicationLenderDetails lenderDetails, LendingApplicationVkycDetails vkycDetails) {
        try {
            vkycDetails.setStatus(VkycStatus.DKYC_PENDING);
            lendingApplicationVkycDetailsDao.save(vkycDetails);
            NBFCRequestDTO<SkipVkycRequestDto> requestDTO = NBFCRequestDTO.<SkipVkycRequestDto>builder()
                    .applicationId(lendingApplication.getId())
                    .lender(lendingApplication.getLender())
                    .productName(LENDING)
                    .payload(SkipVkycRequestDto.builder()
                            .leadId(lenderDetails.getLeadId())
                            .build())
                    .build();
            NBFCResponseDTO<?> responseDTO = lenderAPIGateway.invokeStage(requestDTO, LenderAssociationStages.SKIP_VKYC);
            log.info("vKyc skip vkyc response of {} from nbfc with applicationId: {}, {}", lendingApplication.getLender(), lendingApplication.getId(), responseDTO);
            if (!ObjectUtils.isEmpty(responseDTO) && responseDTO.getSuccess()) {
                vkycDetails.setStatus(VkycStatus.DKYC_COMPLETED);
                vkycDetails.setApprovedAt(new Date());
                lendingApplicationVkycDetailsDao.save(vkycDetails);
                loanDetailsV3Service.saveApplicationViewState(null, lendingApplication.getId(), LendingViewStates.APPLICATION_STATUS_PAGE);
                return new ApiResponse<>(responseDTO.getData());
            }
            return new ApiResponse<>(false, null, "Could not invoke dkyc api, please try again later");
        } catch (Exception e) {
            log.info("Exception in initiating vKyc of {} for applicationId {} {} {}", lendingApplication.getLender(), lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return new ApiResponse<>(false, null, "Something went wrong while initiating vKyc");
    }

    public void handleVkycRetryApplications(LendingApplication lendingApplication, LendingApplicationVkycDetails vkycDetails) {
        vkycDetails.setVkycRetryCount(vkycDetails.getVkycRetryCount() + 1);
        if (vkycDetails.getVkycRetryCount() <= vkycConfig.getMaxRetryCount()) {
            lendingApplicationVkycDetailsDao.save(vkycDetails);
            return;
        }
        if (vkycConfig.getRetryExhaustFlowEnabledLenders().contains(lendingApplication.getLender())) {
            log.info("skipping vkyc as retry count {} exceeds max retry count {} passed for applicationId {}", vkycDetails.getVkycRetryCount(), vkycConfig.getMaxRetryCount(), lendingApplication.getId());
            vkycDetails.setSessionStatus(VkycStatus.SessionStatus.VKYC_RETRY_EXHAUST.name());
            lendingApplicationVkycDetailsDao.save(vkycDetails);
        }
    }

    public LendingViewStates getLenderVkycPageOrDefault(LendingViewStates defaultViewStage, Long merchantId, String lender, Boolean topup) {
        if (isVkycEnabled(merchantId, lender, topup)) {
            LendingApplicationVkycDetails vkycDetails = lendingApplicationVkycDetailsDao.findByApplicationIdAndLender(merchantId, lender).orElse(null);
            if (ObjectUtils.isEmpty(vkycDetails) || !VkycStatus.getTerminatedVkycStatusList().contains(vkycDetails.getStatus())) {
                log.info("next page vkyc for merchantId {} and lender {}", merchantId, lender);
                return LendingViewStates.LENDER_VKYC_PAGE;
            }
        }
        return defaultViewStage;
    }

    public Boolean isVkycEnabled(Long merchantId, String lender, Boolean topup) {
        if (ObjectUtils.isEmpty(merchantId) || ObjectUtils.isEmpty(lender)) {
            log.info("Reason: merchantId or lender is empty | vkyc enabled: {} | merchantId: {} | lender: {}", false, merchantId, lender);
            return false;
        }

        // Special handling for CreditSaison
        if (CREDITSAISON.name().equalsIgnoreCase(lender)) {
            log.info("CreditSaison checking vkyc eligibility for merchantId {}", merchantId);
            boolean isEnabled = rolloutUtil.isEligibleForCreditSaisonVkyc(merchantId);
            log.info("CreditSaison vkyc enabled {} for merchantId {}", isEnabled, merchantId);
            return isEnabled;
        }

        boolean isEnabled = !topup && vkycConfig.getEnabledLenders().contains(lender)
                && easyLoanUtil.percentScaleUp(merchantId, vkycConfig.getRolloutPercentage());

        log.info("vkyc enabled {} for merchantId {} and lender {}", isEnabled, merchantId, lender);
        return isEnabled;
    }

    private VkycStatus getVkycStatusMapping(VkycStatusResponseDto.Status status) {
        switch (status) {
            case MANUALLY_APPROVED:
                return VkycStatus.VKYC_COMPLETED;
            case AUTO_DECLINED:
            case AGENT_CALL_ENDED:
                return VkycStatus.VKYC_RETRY;
            case NEEDS_REVIEW:
            case CALL_COMPLETED:
                return VkycStatus.VKYC_IN_PROGRESS;
            default:
                return VkycStatus.VKYC_PENDING;
        }
    }

    public Boolean isDisableInitiateVkycSession(LendingApplicationVkycDetails vkycDetails, Integer appVersion) {
        if (!ObjectUtils.isEmpty(appVersion) && appVersion < vkycConfig.getMinAppVersion()) {
            log.info("vkyc session is disabled for applicationId {} as app version {} is not supported, min app version req {}", vkycDetails.getApplicationId(), appVersion, vkycConfig.getMinAppVersion());
            vkycDetails.setSessionStatus(VkycStatus.SessionStatus.APP_VERSION_NOT_SUPPORTED.name());
            lendingApplicationVkycDetailsDao.save(vkycDetails);
            return true;
        }
        return isDisableInitiateVkycSession(vkycDetails);
    }

    public Boolean isDisableInitiateVkycSession(LendingApplicationVkycDetails vkycDetails) {
        if (VkycStatus.getVkycDisabledSessionStatuses().contains(vkycDetails.getSessionStatus())) {
            log.info("vkyc session is {} disabled for applicationId {}", vkycDetails.getSessionStatus(), vkycDetails.getApplicationId());
            return true;
        }
        LendingApplicationKycDetails kycDetails = lendingApplicationKycDetailsDao.findTop1ByApplicationIdOrderByIdDesc(vkycDetails.getApplicationId());
        Duration duration = Duration.between(kycDetails.getAadharApprovedAt().toInstant(), Instant.now());
        if (duration.toHours() > vkycConfig.getAadhaarExpiryTatInHours()) {
            log.info("vkyc aadhaar tat breached as days passed {} is more than {} for applicationId {}", duration.toHours(), vkycConfig.getAadhaarExpiryTatInHours(), vkycDetails.getApplicationId());
            vkycDetails.setSessionStatus(VkycStatus.SessionStatus.AADHAAR_XML_EXPIRED.name());
            lendingApplicationVkycDetailsDao.save(vkycDetails);
            return true;
        }
        return false;
    }

    public Boolean rejectApplicationIfRequired(LendingApplication lendingApplication, LendingApplicationVkycDetails vkycDetails, LendingApplicationLenderDetails lenderDetails) {
        // Need to add checks for CS and applied for if UID or App version is not permitted then we disbursed the loan .
        if (CREDITSAISON.name().equalsIgnoreCase(lendingApplication.getLender())) {
            return vkycServiceV2.rejectApplicationIfRequired(lendingApplication, vkycDetails, lenderDetails);
        }

        if (isDisableInitiateVkycSession(vkycDetails) && !ObjectUtils.isEmpty(vkycDetails.getDkycEligible()) && !vkycDetails.getDkycEligible()) {
            log.info("vkyc session status is {} for applicationId {} and no dkyc option available, rejecting application", vkycDetails.getSessionStatus(), lendingApplication.getId());
            vkycDetails.setStatus(VkycStatus.VKYC_HARD_FAILED);
            vkycDetails.setRejectReason(vkycDetails.getSessionStatus()); // todo check if required here
            lendingApplicationVkycDetailsDao.save(vkycDetails);
            lenderDetails.setLeadStatus(VkycStatus.VKYC_HARD_FAILED.name());
            commonService.rejectApplication(lendingApplication, lenderDetails);
            return true;
        }
        return false;
    }

    public Boolean skipVkycForInEligibleUsers(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto) {
        String lender = lenderAssociationDetailsRequestDto.getLendingApplication().getLender();
        if (!vkycConfig.getEnabledLenders().contains(lender)) {
            return true;
        }
        lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setSanctionStatus(LenderAssociationStages.SKIP_VKYC.name());
        commonService.manageApplicationState(lenderAssociationDetailsRequestDto);
        LendingApplicationVkycDetails vkycDetails = lendingApplicationVkycDetailsDao.findByApplicationIdAndLender(lenderAssociationDetailsRequestDto.getApplicationId(), lender)
                .orElseGet(() -> createPendingVkycDetailsRecord(lenderAssociationDetailsRequestDto.getLendingApplication()));
        if (!LoanType.TOPUP.name().equalsIgnoreCase(lenderAssociationDetailsRequestDto.getLendingApplication().getLoanType()) && vkycConfig.getDkycEligibleLenders().contains(vkycDetails.getLender())) {
            vkycDetails.setDkycEligible(true);
            lendingApplicationVkycDetailsDao.save(vkycDetails);
            ApiResponse<?> apiResponse = initiateDkyc(lenderAssociationDetailsRequestDto.getLendingApplication(), lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails(), vkycDetails);
            lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setLeadStatus(!ObjectUtils.isEmpty(vkycDetails.getStatus()) ? vkycDetails.getStatus().name() : VkycStatus.DKYC_PENDING.name());
            commonService.manageApplicationState(lenderAssociationDetailsRequestDto);
            return apiResponse.isSuccess();
        }
        vkycDetails.setStatus(VkycStatus.VKYC_SKIPPED);
        lendingApplicationVkycDetailsDao.save(vkycDetails);
        return true;
    }

    public LendingApplicationVkycDetails createPendingVkycDetailsRecord(LendingApplication openApplication) {
        LendingApplicationVkycDetails lendingApplicationVkycDetails = new LendingApplicationVkycDetails();
        lendingApplicationVkycDetails.setMerchantId(openApplication.getMerchantId());
        lendingApplicationVkycDetails.setApplicationId(openApplication.getId());
        lendingApplicationVkycDetails.setLender(openApplication.getLender());
        lendingApplicationVkycDetails.setStatus(VkycStatus.VKYC_PENDING);
        lendingApplicationVkycDetails.setLender(openApplication.getLender());
        lendingApplicationVkycDetails.setVkycRetryCount(1);
        return lendingApplicationVkycDetailsDao.save(lendingApplicationVkycDetails);
    }

}
