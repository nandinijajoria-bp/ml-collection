package com.bharatpe.lending.loanV2.service;

import com.bharatpe.cache.service.LendingCache;
import com.bharatpe.common.dao.LendingPancardDao;
import com.bharatpe.common.entities.LendingPancard;
import com.bharatpe.lending.common.dao.Gst3bSessionDetailsDao;
import com.bharatpe.lending.common.dao.LendingRiskVariablesDao;
import com.bharatpe.lending.common.entity.Gst3bSessionDetails;
import com.bharatpe.lending.common.entity.LendingRiskVariables;
import com.bharatpe.lending.common.enums.FunnelEnums;
import com.bharatpe.lending.common.enums.Gst3bSessionStatus;


import com.bharatpe.lending.common.service.FunnelService;
import com.bharatpe.lending.dto.GlobalLimitResponse;
import com.bharatpe.lending.enums.Gst3bRejectReason;
import com.bharatpe.lending.loanV2.dto.ApiResponse;
import com.bharatpe.lending.loanV2.dto.Gst3bSessionCallbackDto;
import com.bharatpe.lending.loanV2.dto.Gst3bSessionRequestDTO;
import com.bharatpe.lending.loanV2.dto.Gst3bSessionResponseDTO;
import com.bharatpe.lending.loanV2.handlers.FinanceUtilsHandler;
import com.bharatpe.lending.loanV3.revamp.constants.LoanDetailsConstant;
import com.bharatpe.lending.loanV3.revamp.response.LoanDashboardApiVersion;
import com.bharatpe.lending.loanV3.revamp.services.LoanDashboardService;
import com.bharatpe.lending.service.APIGatewayService;
import com.bharatpe.lending.util.LoanUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.*;

@Service
@Slf4j
public class Gst3bService {

    @Autowired
    Gst3bSessionDetailsDao gst3bSessionDetailsDao;

    @Autowired
    FinanceUtilsHandler financeUtilsHandler;

    @Autowired
    APIGatewayService apiGatewayService;

    @Autowired
    FunnelService funnelService;

    @Autowired
    LendingCache lendingCache;

    @Autowired
    LoanDetailsServiceV2 loanDetailsServiceV2;

    @Autowired
    LendingPancardDao lendingPancardDao;

    @Autowired
    LoanUtil loanUtil;

    @Autowired
    LendingRiskVariablesDao lendingRiskVariablesDao;

    @Autowired
    private LoanDashboardService loanDashboardService;

    public static final String CUSTOMISED_MESSAGE_FOR_OTP_FAILURE = "An internal error occured. Please try after sometime";

    public ApiResponse<?> sendOtpGst3b(Gst3bSessionRequestDTO gst3bSessionRequestDTO, Long merchantId) {
        try {
            if (ObjectUtils.isEmpty(gst3bSessionRequestDTO.getGstin())) {
                return new ApiResponse<>(false, "Gstin not provided");
            }
            if (ObjectUtils.isEmpty(gst3bSessionRequestDTO.getUsername())) {
                return new ApiResponse<>(false, "userName not provided");
            }
            if (!verifyPanWithGst(merchantId, gst3bSessionRequestDTO.getGstin()) && !loanUtil.isInternalMerchant(merchantId)) {
                return new ApiResponse<>(false, "Gstin and Pan number mismatched");
            }
            LoanDashboardApiVersion loanDashboardApiVersion = loanDashboardService.getLoanDashboardApiVersion(merchantId);

            sendFunnelEvent(merchantId, FunnelEnums.StageEvent.INITIATED, "gst3b_page_with_otp_initiated", loanDashboardApiVersion);
            Gst3bSessionDetails previousGst3bSessionDetails = gst3bSessionDetailsDao.findFirstByMerchantIdAndMethodOrderByIdDesc(merchantId, "OTP");
            String orderId = UUID.randomUUID().toString();
            Gst3bSessionDetails gst3bSessionDetails = Gst3bSessionDetails.builder()
                    .orderId(orderId)
                    .merchantId(merchantId)
                    .status(Gst3bSessionStatus.PENDING)
                    .method("OTP")
                    .build();
            gst3bSessionDetailsDao.save(gst3bSessionDetails);
            Gst3bSessionResponseDTO apiResponse = financeUtilsHandler.sendGst3bOtp(gst3bSessionRequestDTO.getGstin(), gst3bSessionRequestDTO.getUsername(), orderId, merchantId);
            if (ObjectUtils.isEmpty(apiResponse)) {
                gst3bSessionDetails.setStatus(Gst3bSessionStatus.FAILED);
                gst3bSessionDetails.setRejectReason(Gst3bRejectReason.SENT_OTP_API_FAILED.name());
                gst3bSessionDetailsDao.save(gst3bSessionDetails);
                sendFunnelEvent(merchantId, FunnelEnums.StageEvent.OTP_SENT, "gst3b_page_with_otp_sent_otp_failed", loanDashboardApiVersion);
                return new ApiResponse<>(false, "Unable to send otp");
            }
            if (!apiResponse.getSuccess()) {
                if(!ObjectUtils.isEmpty(apiResponse.getErrors())) {
                    gst3bSessionDetails.setStatus(Gst3bSessionStatus.FAILED);
                    gst3bSessionDetails.setRequestId(apiResponse.getRequestId());
                    gst3bSessionDetails.setRejectReason(Gst3bRejectReason.SENT_OTP_API_FAILED.name());
                    gst3bSessionDetailsDao.save(gst3bSessionDetails);
                    sendFunnelEvent(merchantId, FunnelEnums.StageEvent.OTP_SENT, "gst3b_page_with_otp_sent_otp_failed", loanDashboardApiVersion);
                    return new ApiResponse<>(false, apiResponse.getErrors().get(0).getError());
                }
                if(apiResponse.getMessage().equalsIgnoreCase(CUSTOMISED_MESSAGE_FOR_OTP_FAILURE)) {
                    gst3bSessionDetails.setStatus(Gst3bSessionStatus.FAILED);
                    gst3bSessionDetails.setRequestId(apiResponse.getRequestId());
                    gst3bSessionDetails.setRejectReason(Gst3bRejectReason.SENT_OTP_API_FAILED.name());
                    gst3bSessionDetailsDao.save(gst3bSessionDetails);
                    sendFunnelEvent(merchantId, FunnelEnums.StageEvent.OTP_SENT, "gst3b_page_with_otp_sent_otp_failed", loanDashboardApiVersion);
                    return new ApiResponse<>(false, "Unable to send otp");
                } else {
                    gst3bSessionDetails.setStatus(Gst3bSessionStatus.FAILED);
                    gst3bSessionDetails.setRequestId(apiResponse.getRequestId());
                    gst3bSessionDetails.setRejectReason(apiResponse.getMessage());
                    gst3bSessionDetailsDao.save(gst3bSessionDetails);
                    sendFunnelEvent(merchantId, FunnelEnums.StageEvent.OTP_SENT, "gst3b_page_with_otp_sent_otp_failed", loanDashboardApiVersion);
                    return new ApiResponse<>(false, apiResponse.getMessage());
                }
            }
            gst3bSessionDetails.setStatus(Gst3bSessionStatus.OTP_INIT);
            gst3bSessionDetails.setRequestId(apiResponse.getRequestId());
            gst3bSessionDetailsDao.save(gst3bSessionDetails);
            if (ObjectUtils.isEmpty(previousGst3bSessionDetails)) {
                sendFunnelEvent(merchantId, FunnelEnums.StageEvent.OTP_SENT, "gst3b_page_with_otp_sent_otp", loanDashboardApiVersion);
            } else {
                sendFunnelEvent(merchantId, FunnelEnums.StageEvent.OTP_RESEND, "gst3b_page_with_otp_resend_otp", loanDashboardApiVersion);
            }
            Map<String, String> data = new HashMap<>();
            data.put("message", "Successfully sent otp for gst3b");
            data.put("requestId", apiResponse.getRequestId());
            return new ApiResponse<>(data);
        } catch (Exception e) {
            log.error("Exception in sending otp ", e);
            return new ApiResponse<>(false, e.getMessage());
        }
    }

    public ApiResponse<?> verifyOtpGst3b(Gst3bSessionRequestDTO gst3bSessionRequestDTO) {
        try {
            if (ObjectUtils.isEmpty(gst3bSessionRequestDTO.getOtp())) {
                return new ApiResponse<>(false, "Otp not provided");
            }
            Gst3bSessionDetails gst3bSessionDetails = gst3bSessionDetailsDao.findByRequestIdAndStatus(gst3bSessionRequestDTO.getRequestId(), Gst3bSessionStatus.OTP_INIT);
            if (ObjectUtils.isEmpty(gst3bSessionDetails)) {
                return new ApiResponse<>(false, "No session details found for given requestId");
            }
            LoanDashboardApiVersion loanDashboardApiVersion = loanDashboardService.getLoanDashboardApiVersion(gst3bSessionDetails.getMerchantId());

            Gst3bSessionResponseDTO apiResponse = financeUtilsHandler.verifyGst3bOtp(gst3bSessionRequestDTO.getRequestId(), gst3bSessionRequestDTO.getOtp());
            if (ObjectUtils.isEmpty(apiResponse)) {
                gst3bSessionDetails.setStatus(Gst3bSessionStatus.FAILED);
                gst3bSessionDetails.setRejectReason(Gst3bRejectReason.VERIFY_OTP_API_FAILED.name());
                gst3bSessionDetailsDao.save(gst3bSessionDetails);
                sendFunnelEvent(gst3bSessionDetails.getMerchantId(), FunnelEnums.StageEvent.WRONG_OTP, "gst3b_with_otp_verify_failed", loanDashboardApiVersion);
                return new ApiResponse<>(false, "Unable to verify otp");
            }
            if (!apiResponse.getSuccess()) {
                if(!ObjectUtils.isEmpty(apiResponse.getErrors())) {
                    gst3bSessionDetails.setStatus(Gst3bSessionStatus.FAILED);
                    gst3bSessionDetails.setRequestId(apiResponse.getRequestId());
                    gst3bSessionDetails.setRejectReason(Gst3bRejectReason.VERIFY_OTP_API_FAILED.name());
                    gst3bSessionDetailsDao.save(gst3bSessionDetails);
                    sendFunnelEvent(gst3bSessionDetails.getMerchantId(), FunnelEnums.StageEvent.WRONG_OTP, "gst3b_with_otp_verify_failed", loanDashboardApiVersion);
                    return new ApiResponse<>(false, apiResponse.getErrors().get(0).getError());
                }
                if(apiResponse.getMessage().equalsIgnoreCase(CUSTOMISED_MESSAGE_FOR_OTP_FAILURE)) {
                    gst3bSessionDetails.setStatus(Gst3bSessionStatus.FAILED);
                    gst3bSessionDetails.setRequestId(apiResponse.getRequestId());
                    gst3bSessionDetails.setRejectReason(Gst3bRejectReason.VERIFY_OTP_API_FAILED.name());
                    gst3bSessionDetailsDao.save(gst3bSessionDetails);
                    sendFunnelEvent(gst3bSessionDetails.getMerchantId(), FunnelEnums.StageEvent.WRONG_OTP, "gst3b_page_with_otp_wrong_otp", loanDashboardApiVersion);
                    return new ApiResponse<>(false, "Unable to verify otp");
                } else {
                    gst3bSessionDetails.setStatus(Gst3bSessionStatus.FAILED);
                    gst3bSessionDetails.setRequestId(apiResponse.getRequestId());
                    gst3bSessionDetails.setRejectReason(apiResponse.getMessage());
                    gst3bSessionDetailsDao.save(gst3bSessionDetails);
                    sendFunnelEvent(gst3bSessionDetails.getMerchantId(), FunnelEnums.StageEvent.WRONG_OTP, "gst3b_page_with_otp_wrong_otp", loanDashboardApiVersion);
                    return new ApiResponse<>(false, apiResponse.getMessage());
                }
            }
            gst3bSessionDetails.setStatus(Gst3bSessionStatus.SUBMITTED);
            gst3bSessionDetails.setRequestId(apiResponse.getRequestId());
            gst3bSessionDetailsDao.save(gst3bSessionDetails);
            sendFunnelEvent(gst3bSessionDetails.getMerchantId(), FunnelEnums.StageEvent.OTP_VERIFIED, "gst3b_page_with_otp_verified_otp", loanDashboardApiVersion);
            return new ApiResponse<>("Successfully verified otp for gst3b");
        } catch (Exception e) {
            log.error("Exception in verifying otp ", e);
            return new ApiResponse<>(false, e.getMessage());
        }
    }

    public ApiResponse<?> uploadGst3bFile(Gst3bSessionRequestDTO gst3bSessionRequestDTO, Long merchantId) {
        try {
            if (ObjectUtils.isEmpty(gst3bSessionRequestDTO.getGstin())) {
                return new ApiResponse<>(false, "Gstin not provided");
            }
            if (ObjectUtils.isEmpty(gst3bSessionRequestDTO.getBase64())) {
                return new ApiResponse<>(false, "base64Image of file not provided");
            }
            if (ObjectUtils.isEmpty(gst3bSessionRequestDTO.getFileName())) {
                return new ApiResponse<>(false, "File name is not provided");
            }
            if (gst3bSessionRequestDTO.getFileName().length() >= 255) {
                return new ApiResponse<>(false, "File name should be less than 255 characters");
            }
            if (!verifyPanWithGst(merchantId, gst3bSessionRequestDTO.getGstin()) && !loanUtil.isInternalMerchant(merchantId)) {
                return new ApiResponse<>(false, "Gstin and Pan number mismatched");
            }
            LoanDashboardApiVersion loanDashboardApiVersion = loanDashboardService.getLoanDashboardApiVersion(merchantId);

            sendFunnelEvent(merchantId, FunnelEnums.StageEvent.INITIATED, "gst3b_page_with_doc_initiated", loanDashboardApiVersion);
            Gst3bSessionDetails previousGst3bSessionDetails = gst3bSessionDetailsDao.findFirstByMerchantIdAndMethodOrderByIdDesc(merchantId, "UPLOAD");
            String orderId = UUID.randomUUID().toString();
            Gst3bSessionDetails gst3bSessionDetails = Gst3bSessionDetails.builder()
                    .orderId(orderId)
                    .merchantId(merchantId)
                    .fileName(gst3bSessionRequestDTO.getFileName())
                    .status(Gst3bSessionStatus.PENDING)
                    .method("UPLOAD")
                    .build();
            gst3bSessionDetailsDao.save(gst3bSessionDetails);
            Gst3bSessionResponseDTO apiResponse = financeUtilsHandler.gst3bUploadFile(gst3bSessionRequestDTO.getBase64(), gst3bSessionRequestDTO.getGstin(), gst3bSessionRequestDTO.getFileName(), orderId, merchantId);
            if (ObjectUtils.isEmpty(apiResponse)) {
                gst3bSessionDetails.setStatus(Gst3bSessionStatus.FAILED);
                gst3bSessionDetails.setRejectReason(Gst3bRejectReason.UPLOAD_API_FAILED.name());
                gst3bSessionDetailsDao.save(gst3bSessionDetails);
                sendFunnelEvent(merchantId, FunnelEnums.StageEvent.UPLOAD_FAILED, "gst3b_page_with_doc_upload_failed", loanDashboardApiVersion);
                return new ApiResponse<>(false, "Error in uploading gst3b file");
            }
            if (!apiResponse.getSuccess()) {
                gst3bSessionDetails.setStatus(Gst3bSessionStatus.FAILED);
                gst3bSessionDetails.setRejectReason(Gst3bRejectReason.UPLOAD_API_FAILED.name());
                gst3bSessionDetails.setRequestId(apiResponse.getRequestId());
                gst3bSessionDetailsDao.save(gst3bSessionDetails);
                sendFunnelEvent(merchantId, FunnelEnums.StageEvent.UPLOAD_FAILED, "gst3b_page_with_doc_upload_failed", loanDashboardApiVersion);
                return new ApiResponse<>(false, "Error in uploading gst3b file");
            }
            gst3bSessionDetails.setStatus(Gst3bSessionStatus.SUBMITTED);
            gst3bSessionDetails.setRequestId(apiResponse.getRequestId());
            gst3bSessionDetailsDao.save(gst3bSessionDetails);
            if (ObjectUtils.isEmpty(previousGst3bSessionDetails)) {
                sendFunnelEvent(merchantId, FunnelEnums.StageEvent.UPLOAD, "gst3b_page_with_doc_upload", loanDashboardApiVersion);
            } else {
                sendFunnelEvent(merchantId, FunnelEnums.StageEvent.UPLOADED_AGAIN, "gst3b_page_with_doc_uploaded_again", loanDashboardApiVersion);
            }
            return new ApiResponse<>("Successfully submitted gst3b file");
        } catch (Exception e) {
            log.error("Exception in uploading gst3b file ", e);
            return new ApiResponse<>(false, e.getMessage());
        }
    }

    public ApiResponse<?> getGst3bSessionList(Long merchantId) {
        try {
            if (ObjectUtils.isEmpty(merchantId)) {
                return new ApiResponse<>(false, "merchantId is not provided");
            }
            List<Gst3bSessionDetails> gst3bSessionDetailsList = gst3bSessionDetailsDao.findAllByMerchantIdOrderByIdDesc(merchantId);
            if (ObjectUtils.isEmpty(gst3bSessionDetailsList)) {
                return new ApiResponse<>(new ArrayList<>());
            }
            return new ApiResponse<>(gst3bSessionDetailsList);
        } catch (Exception e) {
            log.error("Exception getting gst3b session list ", e);
            return new ApiResponse<>(false, e.getMessage());
        }
    }

    public void gst3bSessionCallback(Gst3bSessionCallbackDto sessionCallbackDto) {
        Gst3bSessionDetails gst3bSessionDetails = gst3bSessionDetailsDao.findByOrderIdAndStatusOrderByIdDesc(sessionCallbackDto.getSessionId(), Gst3bSessionStatus.SUBMITTED);
        if(ObjectUtils.isEmpty(gst3bSessionDetails)) {
            log.error("No gst3b session found for given session id : {}", sessionCallbackDto.getSessionId());
            return;
        }
        if(!gst3bSessionDetails.getStatus().equals(Gst3bSessionStatus.SUBMITTED)) {
            log.error("Gst3b session for given sessionId is already processed");
            return;
        }
        LoanDashboardApiVersion loanDashboardApiVersion = loanDashboardService.getLoanDashboardApiVersion(gst3bSessionDetails.getMerchantId());
        try {
            if (sessionCallbackDto.getStatus().equals(Gst3bSessionStatus.FAILED)) {
                gst3bSessionDetails.setStatus(sessionCallbackDto.getStatus());
                gst3bSessionDetails.setRejectReason(Gst3bRejectReason.FAILED_CALLBACK_STATUS.name());
                if("OTP".equalsIgnoreCase(gst3bSessionDetails.getMethod())) {
                    sendFunnelEvent(gst3bSessionDetails.getMerchantId(), FunnelEnums.StageEvent.FAILED, "gst3b_page_with_otp_failed", loanDashboardApiVersion);
                } else {
                    sendFunnelEvent(gst3bSessionDetails.getMerchantId(), FunnelEnums.StageEvent.FAILED, "gst3b_page_with_upload_failed", loanDashboardApiVersion);
                }
            } else if (sessionCallbackDto.getStatus().equals(Gst3bSessionStatus.SUCCESS)) {
                if (!ObjectUtils.isEmpty(sessionCallbackDto.getCorrectTenure()) && !sessionCallbackDto.getCorrectTenure() && !loanUtil.isInternalMerchant(gst3bSessionDetails.getMerchantId())) {
                    gst3bSessionDetails.setStatus(Gst3bSessionStatus.FAILED);
                    gst3bSessionDetails.setRejectReason(Gst3bRejectReason.GST3B_NOT_IN_12_MONTH_PERIOD.name());
                    if("OTP".equalsIgnoreCase(gst3bSessionDetails.getMethod())) {
                        sendFunnelEvent(gst3bSessionDetails.getMerchantId(), FunnelEnums.StageEvent.FAILED, "gst3b_page_with_otp_failed", loanDashboardApiVersion);
                    } else {
                        sendFunnelEvent(gst3bSessionDetails.getMerchantId(), FunnelEnums.StageEvent.FAILED, "gst3b_page_with_upload_failed", loanDashboardApiVersion);
                    }
                } else if (!verifyPanWithGst(gst3bSessionDetails.getMerchantId(), sessionCallbackDto.getGstin()) && !loanUtil.isInternalMerchant(gst3bSessionDetails.getMerchantId())) {
                    gst3bSessionDetails.setStatus(Gst3bSessionStatus.FAILED);
                    gst3bSessionDetails.setRejectReason(Gst3bRejectReason.PAN_GST_MISMATCHED.name());
                    if("OTP".equalsIgnoreCase(gst3bSessionDetails.getMethod())) {
                        sendFunnelEvent(gst3bSessionDetails.getMerchantId(), FunnelEnums.StageEvent.FAILED, "gst3b_page_with_otp_failed", loanDashboardApiVersion);
                    } else {
                        sendFunnelEvent(gst3bSessionDetails.getMerchantId(), FunnelEnums.StageEvent.FAILED, "gst3b_page_with_upload_failed", loanDashboardApiVersion);
                    }
                } else {
                    gst3bSessionDetails.setStatus(Gst3bSessionStatus.INPROCESS);
                    gst3bSessionDetailsDao.save(gst3bSessionDetails);
                    if (gst3bSessionDetails.getMethod().equalsIgnoreCase("OTP")) {
                        sendFunnelEvent(gst3bSessionDetails.getMerchantId(), FunnelEnums.StageEvent.SUBMITTED, "gst3b_page_with_otp_submitted", loanDashboardApiVersion);
                    } else {
                        sendFunnelEvent(gst3bSessionDetails.getMerchantId(), FunnelEnums.StageEvent.SUBMITTED, "gst3b_page_with_doc_upload_submitted", loanDashboardApiVersion);
                    }
                    gst3bSessionDetails = gst3bUnderwritingAnalysis(gst3bSessionDetails, loanDashboardApiVersion);
                }
            }
            gst3bSessionDetailsDao.save(gst3bSessionDetails);
            log.info("Successfully update gst3b session : {}", gst3bSessionDetails);
        } catch (Exception e) {
            log.error("Exception in updating gst3b session for sessionId : {}", sessionCallbackDto.getSessionId(), e);
            gst3bSessionDetails.setRejectReason("INTERNAL_ERROR");
            gst3bSessionDetails.setStatus(Gst3bSessionStatus.FAILED);
            gst3bSessionDetailsDao.save(gst3bSessionDetails);
            if("OTP".equalsIgnoreCase(gst3bSessionDetails.getMethod())) {
                sendFunnelEvent(gst3bSessionDetails.getMerchantId(), FunnelEnums.StageEvent.FAILED, "gst3b_page_with_otp_failed", loanDashboardApiVersion);
            } else {
                sendFunnelEvent(gst3bSessionDetails.getMerchantId(), FunnelEnums.StageEvent.FAILED, "gst3b_page_with_upload_failed", loanDashboardApiVersion);
            }
        }
    }

    private Gst3bSessionDetails gst3bUnderwritingAnalysis(Gst3bSessionDetails gst3bSessionDetails, LoanDashboardApiVersion loanDashboardApiVersion) {
        try {
            Double currentLimit = 0D;
            LendingRiskVariables lendingRiskVariables = lendingRiskVariablesDao.findByMerchantId(gst3bSessionDetails.getMerchantId());
            if(!ObjectUtils.isEmpty(lendingRiskVariables)) {
                currentLimit = lendingRiskVariables.getFinalOffer();
            }
            GlobalLimitResponse globalLimitResponse = apiGatewayService.getGlobalLimit(gst3bSessionDetails.getMerchantId(), gst3bSessionDetails.getOrderId(), "GST");
            String eventValue = gst3bSessionDetails.getMethod().equals("OTP") ? "gst3b_page_with_otp" : "gst3b_page_with_doc";
            if (globalLimitResponse != null && globalLimitResponse.getData() != null && globalLimitResponse.getData().getGst3bAffectedOffer() != null) {
                if (globalLimitResponse.getData().getGst3bAffectedOffer()) {
                    if(globalLimitResponse.getData().getGlobalLimit() > currentLimit) {
                        Double eligibleAmount = 0D;
                        log.info("Global limit for merchant:{} is {}", gst3bSessionDetails.getMerchantId(), globalLimitResponse.getData().getGlobalLimit());
                        eligibleAmount = globalLimitResponse.getData().getGlobalLimit();
                        if (eligibleAmount > 0D) {
                            log.info("Eligibility found for merchant:{}", gst3bSessionDetails.getMerchantId());
                            loanDetailsServiceV2.recomputeEligibleLoan(globalLimitResponse, null, gst3bSessionDetails.getMerchantId());
                            evictLoanDetailV2Cache(gst3bSessionDetails.getMerchantId());
                        }
                        gst3bSessionDetails.setStatus(Gst3bSessionStatus.SUCCESS);
                        sendFunnelEvent(gst3bSessionDetails.getMerchantId(), FunnelEnums.StageEvent.SUCCESS, eventValue + "_success", loanDashboardApiVersion);
                    } else {
                        gst3bSessionDetails.setStatus(Gst3bSessionStatus.FAILED);
                        gst3bSessionDetails.setRejectReason(Gst3bRejectReason.OFFER_SAME.name());
                        sendFunnelEvent(gst3bSessionDetails.getMerchantId(), FunnelEnums.StageEvent.FAILED, eventValue + "_failed", loanDashboardApiVersion);
                    }
                } else {
                    gst3bSessionDetails.setStatus(Gst3bSessionStatus.FAILED);
                    gst3bSessionDetails.setRejectReason(Gst3bRejectReason.OFFER_SAME.name());
                    sendFunnelEvent(gst3bSessionDetails.getMerchantId(), FunnelEnums.StageEvent.FAILED, eventValue + "_failed", loanDashboardApiVersion);
                }
            } else {
                gst3bSessionDetails.setRejectReason(Gst3bRejectReason.GLOBAL_LIMIT_FAILED.name());
                gst3bSessionDetails.setStatus(Gst3bSessionStatus.FAILED);
                sendFunnelEvent(gst3bSessionDetails.getMerchantId(), FunnelEnums.StageEvent.FAILED, eventValue + "_failed", loanDashboardApiVersion);
            }
        } catch (Exception e) {
            log.error("Exception in getting global limit for merchantId : {}", gst3bSessionDetails.getMerchantId());
            String eventValue = gst3bSessionDetails.getMethod().equals("OTP") ? "gst3b_page_with_otp" : "gst3b_page_with_doc";
            gst3bSessionDetails.setStatus(Gst3bSessionStatus.FAILED);
            gst3bSessionDetails.setRejectReason(Gst3bRejectReason.GLOBAL_LIMIT_EXCEPTION.name());
            sendFunnelEvent(gst3bSessionDetails.getMerchantId(), FunnelEnums.StageEvent.FAILED, eventValue + "_failed", loanDashboardApiVersion);
        }
        gst3bSessionDetailsDao.save(gst3bSessionDetails);
        return gst3bSessionDetails;
    }

    private void evictLoanDetailV2Cache( Long merchantId) {
        if(Objects.nonNull(merchantId)) {
            String loanDetailsCacheKey = "LENDING_LOAN_DETAILS_" + merchantId;
            log.info("deleting cached key of loan details in create application for merchant: {}",merchantId);
            lendingCache.delete(loanDetailsCacheKey);
        } else {
            log.info("no key exists!");
        }
        loanDashboardService.deleteLoanDashboardCache(merchantId);
    }

    private Boolean verifyPanWithGst(Long merchantId, String gstin) {
        LendingPancard lendingPancard = lendingPancardDao.findByMerchantId(merchantId);
        if(!ObjectUtils.isEmpty(lendingPancard)) {
           if(gstin.contains(lendingPancard.getPancardNumber())) {
               return true;
           } else {
               return false;
           }
        }
        return false;
    }

    private void sendFunnelEvent(Long merchantId, FunnelEnums.StageEvent stageEvent, String eventValue, LoanDashboardApiVersion loanDashboardApiVersion){
        if(LoanDetailsConstant.VERSION_V2.equalsIgnoreCase(loanDashboardApiVersion.getApiVersion())){
            funnelService.submitEventV3(merchantId, null, null,
                    FunnelEnums.StageId.GST3B, stageEvent, eventValue, LoanDetailsConstant.FUNNEL_VERSION_TAG);
        }
        else{
            funnelService.submitEvent(merchantId, null, null,
                    FunnelEnums.StageId.GST3B, stageEvent, eventValue);
        }
    }
}
