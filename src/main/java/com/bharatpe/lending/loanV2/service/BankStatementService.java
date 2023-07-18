package com.bharatpe.lending.loanV2.service;

import com.bharatpe.cache.service.LendingCache;
import com.bharatpe.lending.common.dao.BankStatementSessionDetailsDao;
import com.bharatpe.lending.common.dao.LendingRiskVariablesDao;
import com.bharatpe.lending.common.entity.BankStatementSessionDetails;
import com.bharatpe.lending.common.entity.LendingRiskVariables;
import com.bharatpe.lending.common.enums.BankStatementSessionStatus;
import com.bharatpe.lending.common.enums.FunnelEnums;
import com.bharatpe.lending.common.service.FunnelService;
import com.bharatpe.lending.common.service.merchant.dto.BankDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.dto.GlobalLimitResponse;
import com.bharatpe.lending.enums.BankStatementRejectReason;
import com.bharatpe.lending.loanV2.dto.ApiResponse;
import com.bharatpe.lending.loanV2.dto.BankStatementSessionCallbackDto;
import com.bharatpe.lending.loanV2.dto.BankStatementUploadResponseDto;
import com.bharatpe.lending.loanV2.handlers.FinanceUtilsHandler;
import com.bharatpe.lending.service.APIGatewayService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.*;

@Service
@Slf4j
public class BankStatementService {

    @Autowired
    BankStatementSessionDetailsDao bankStatementSessionDetailsDao;

    @Autowired
    FinanceUtilsHandler financeUtilsHandler;

    @Autowired
    FunnelService funnelService;

    @Autowired
    MerchantService merchantService;

    @Autowired
    APIGatewayService apiGatewayService;

    @Autowired
    LoanDetailsServiceV2 loanDetailsServiceV2;

    @Autowired
    LendingCache lendingCache;

    @Autowired
    LendingRiskVariablesDao lendingRiskVariablesDao;


    public ApiResponse<?> uploadBankStatementFile(Long merchantId, String fileName, String password, String bankName, String base64) {
        try {
            if (fileName.length() >= 255) {
                return new ApiResponse<>(false, "File name should be less than 255 characters");
            }
            BankStatementSessionDetails prevBankStatementSessionDetails = bankStatementSessionDetailsDao.findFirstByMerchantIdOrderByIdDesc(merchantId);
            String orderId = UUID.randomUUID().toString();
            BankStatementSessionDetails bankStatementSessionDetails = BankStatementSessionDetails.builder()
                    .orderId(orderId)
                    .merchantId(merchantId)
                    .type("BANK_STATEMENT")
                    .fileName(fileName)
                    .status(BankStatementSessionStatus.PENDING)
                    .method("UPLOAD")
                    .build();
            bankStatementSessionDetailsDao.save(bankStatementSessionDetails);
            if (ObjectUtils.isEmpty(prevBankStatementSessionDetails)) {
                funnelService.submitEvent(merchantId, null, null, FunnelEnums.StageId.BANK_STATEMENT, FunnelEnums.StageEvent.UPLOAD, "bank_statement_upload_offer_page");
            } else if (prevBankStatementSessionDetails.getStatus().equals(BankStatementSessionStatus.FAILED)) {
                funnelService.submitEvent(merchantId, null, null, FunnelEnums.StageId.BANK_STATEMENT, FunnelEnums.StageEvent.RE_UPLOAD, "bank_statement_reupload_home");
            }
            BankStatementUploadResponseDto apiResponse = financeUtilsHandler.uploadFile(fileName, password, base64, bankName, orderId, merchantId);
            if (ObjectUtils.isEmpty(apiResponse)) {
                bankStatementSessionDetails.setStatus(BankStatementSessionStatus.FAILED);
                bankStatementSessionDetails.setRejectReason(BankStatementRejectReason.UPLOAD_API_FAILED.name());
                bankStatementSessionDetailsDao.save(bankStatementSessionDetails);
                funnelService.submitEvent(merchantId, null, null, FunnelEnums.StageId.BANK_STATEMENT, FunnelEnums.StageEvent.REJECT, "upload_bank_statement_reject");
                return new ApiResponse<>(false, "Error in uploading bank statement file");
            }
            if (!apiResponse.getSuccess()) {
                if(apiResponse.getMessage().equalsIgnoreCase("REJECTED")) {
                    bankStatementSessionDetails.setStatus(BankStatementSessionStatus.FAILED);
                    bankStatementSessionDetails.setRejectReason(BankStatementRejectReason.REJECT_AT_PARTNER.name());
                    bankStatementSessionDetails.setRequestId(apiResponse.getRequestId());
                    bankStatementSessionDetailsDao.save(bankStatementSessionDetails);
                    funnelService.submitEvent(merchantId, null, null, FunnelEnums.StageId.BANK_STATEMENT, FunnelEnums.StageEvent.REJECT, "upload_bank_statement_reject");
                    return new ApiResponse<>(false, "Error in uploading bank statement file");
                } else {
                    bankStatementSessionDetails.setStatus(BankStatementSessionStatus.FAILED);
                    bankStatementSessionDetails.setRejectReason(BankStatementRejectReason.UPLOAD_API_FAILED.name());
                    bankStatementSessionDetails.setRequestId(apiResponse.getRequestId());
                    bankStatementSessionDetailsDao.save(bankStatementSessionDetails);
                    funnelService.submitEvent(merchantId, null, null, FunnelEnums.StageId.BANK_STATEMENT, FunnelEnums.StageEvent.REJECT, "upload_bank_statement_reject");
                    return new ApiResponse<>(false, "Error in uploading bank statement file");
                }
            }
            bankStatementSessionDetails.setRequestId(apiResponse.getRequestId());
            bankStatementSessionDetails.setStatus(BankStatementSessionStatus.SUBMITTED);
            bankStatementSessionDetailsDao.save(bankStatementSessionDetails);
            if (ObjectUtils.isEmpty(prevBankStatementSessionDetails)) {
                funnelService.submitEvent(merchantId, null, null, FunnelEnums.StageId.BANK_STATEMENT, FunnelEnums.StageEvent.SUBMITTED, "statement_success_response_offer_page");
            } else if (prevBankStatementSessionDetails.getStatus().equals(BankStatementSessionStatus.FAILED)) {
                funnelService.submitEvent(merchantId, null, null, FunnelEnums.StageId.BANK_STATEMENT, FunnelEnums.StageEvent.SUBMITTED, "bank_statement_reupload_success_home");
            }
            return new ApiResponse<>("Successfully submitted bank statement file");
        } catch (Exception e) {
            log.error("Exception uploading bankStatement file ", e);
            return new ApiResponse<>(false, e.getMessage());
        }
    }

    public ApiResponse<?> bankStatementSessionsList(Long merchantId) {
        try {
            if (ObjectUtils.isEmpty(merchantId)) {
                return new ApiResponse<>(false, "merchantId is not provided");
            }
            List<BankStatementSessionDetails> bankStatementSessionDetails = bankStatementSessionDetailsDao.findAllByMerchantIdOrderByIdDesc(merchantId);
            if (ObjectUtils.isEmpty(bankStatementSessionDetails)) {
                return new ApiResponse<>(new ArrayList<>());
            }
            return new ApiResponse<>(bankStatementSessionDetails);
        } catch (Exception e) {
            log.error("Exception in getting bankStatement session list ", e);
            return new ApiResponse<>(false, e.getMessage());
        }
    }

    public ApiResponse<?> fetchBankList(String bankName) {
        try {
            ApiResponse apiResponse = financeUtilsHandler.getBankList(bankName);
            if (ObjectUtils.isEmpty(apiResponse)) {
                return new ApiResponse<>(false, "Unable to get bank list");
            }
            return apiResponse;
        } catch (Exception e) {
            log.error("Exception in getting bankList for bankStatement ", e);
            return new ApiResponse<>(false, e.getMessage());
        }
    }

    public void updateBankStatementSession(BankStatementSessionCallbackDto sessionCallbackDto) {
        BankStatementSessionDetails bankStatementSessionDetails = bankStatementSessionDetailsDao.findByOrderId(sessionCallbackDto.getSessionId());
        if(ObjectUtils.isEmpty(bankStatementSessionDetails)) {
            log.error("No bank statement session found for given session id : {}", sessionCallbackDto.getSessionId());
            return;
        }
        if(!bankStatementSessionDetails.getStatus().equals(BankStatementSessionStatus.SUBMITTED)) {
            log.error("Bank statement session for given sessionId is already processed");
            return;
        }
        try {
            if (sessionCallbackDto.getStatus().equals(BankStatementSessionStatus.FAILED)) {
                bankStatementSessionDetails.setStatus(BankStatementSessionStatus.FAILED);
                if (!ObjectUtils.isEmpty(sessionCallbackDto.getMessage())) {
                    bankStatementSessionDetails.setRejectReason(sessionCallbackDto.getMessage());
                } else {
                    bankStatementSessionDetails.setRejectReason(BankStatementRejectReason.FAILED_CALLBACK_STATUS.name());
                }
                funnelService.submitEvent(bankStatementSessionDetails.getMerchantId(), null, null, FunnelEnums.StageId.BANK_STATEMENT, FunnelEnums.StageEvent.FAILED, "statement_failed_response_home");
            } else if (sessionCallbackDto.getStatus().equals(BankStatementSessionStatus.SUCCESS)) {
                final BankDetailsDto bankDetailsDtoOptional = merchantService.fetchMerchantBankDetails(bankStatementSessionDetails.getMerchantId()).orElse(null);
                if (!ObjectUtils.isEmpty(bankDetailsDtoOptional)) {
                    String accountNo = null;
                    if (!ObjectUtils.isEmpty(bankDetailsDtoOptional.getAccountNumber())) {
                        accountNo = bankDetailsDtoOptional.getAccountNumber().replaceAll("^0+(?!$)", "");
                    }
                    log.info("AccountNo : {}", accountNo);
                    if (!sessionCallbackDto.getAccountNo().equals(accountNo)) {
                        bankStatementSessionDetails.setRejectReason(BankStatementRejectReason.ACCOUNT_NO_MISMATCH.name());
                        bankStatementSessionDetails.setStatus(BankStatementSessionStatus.FAILED);
                        funnelService.submitEvent(bankStatementSessionDetails.getMerchantId(), null, null, FunnelEnums.StageId.BANK_STATEMENT, FunnelEnums.StageEvent.FAILED, "statement_failed_response_home");
                    } else if (sessionCallbackDto.getPeriod() < 6) {
                        bankStatementSessionDetails.setRejectReason(BankStatementRejectReason.BANK_STATEMENT_NOT_IN_6_MONTH_PERIOD.name());
                        bankStatementSessionDetails.setStatus(BankStatementSessionStatus.FAILED);
                        funnelService.submitEvent(bankStatementSessionDetails.getMerchantId(), null, null, FunnelEnums.StageId.BANK_STATEMENT, FunnelEnums.StageEvent.FAILED, "statement_failed_response_home");
                    } else {
                        bankStatementSessionDetails.setStatus(BankStatementSessionStatus.INPROCESS);
                        bankStatementSessionDetails = underWritingAnalysis(bankStatementSessionDetails);
                    }
                } else {
                    bankStatementSessionDetails.setStatus(BankStatementSessionStatus.FAILED);
                    bankStatementSessionDetails.setRejectReason(BankStatementRejectReason.BANK_DETAILS_MISSING.name());
                    funnelService.submitEvent(bankStatementSessionDetails.getMerchantId(), null, null, FunnelEnums.StageId.BANK_STATEMENT, FunnelEnums.StageEvent.FAILED, "statement_failed_response_home");
                }
            }
            bankStatementSessionDetailsDao.save(bankStatementSessionDetails);
            log.info("Successfully update bankStatement session : {}", bankStatementSessionDetails);
        } catch (Exception e) {
            log.error("Exception in updating bankStatement session for sessionId : {}", sessionCallbackDto.getSessionId(), e);
            bankStatementSessionDetails.setRejectReason("INTERNAL_ERROR");
            bankStatementSessionDetails.setStatus(BankStatementSessionStatus.FAILED);
            bankStatementSessionDetailsDao.save(bankStatementSessionDetails);
            funnelService.submitEvent(bankStatementSessionDetails.getMerchantId(), null, null, FunnelEnums.StageId.BANK_STATEMENT, FunnelEnums.StageEvent.FAILED, "statement_failed_response_home");
        }
    }

    private BankStatementSessionDetails underWritingAnalysis(BankStatementSessionDetails bankStatementSessionDetails) {
        try {
            Double currentLimit = 0D;
            LendingRiskVariables lendingRiskVariables = lendingRiskVariablesDao.findByMerchantId(bankStatementSessionDetails.getMerchantId());
            if(!ObjectUtils.isEmpty(lendingRiskVariables)) {
                currentLimit = lendingRiskVariables.getFinalOffer();
            }
            GlobalLimitResponse globalLimitResponse = apiGatewayService.getGlobalLimit(bankStatementSessionDetails.getMerchantId(), bankStatementSessionDetails.getOrderId(), "BANK_STATEMENT");
            if (globalLimitResponse != null && globalLimitResponse.getData() != null && globalLimitResponse.getData().getBankAffectedOffer() != null) {
                if (globalLimitResponse.getData().getBankAffectedOffer()) {
                    if(globalLimitResponse.getData().getGlobalLimit() > currentLimit) {
                        bankStatementSessionDetails.setStatus(BankStatementSessionStatus.SUCCESS);
                        Double eligibleAmount = 0D;
                        log.info("Global limit for merchant:{} is {}", bankStatementSessionDetails.getMerchantId(), globalLimitResponse.getData().getGlobalLimit());
                        eligibleAmount = globalLimitResponse.getData().getGlobalLimit();
                        if (eligibleAmount > 0D) {
                            log.info("Eligibility found for merchant:{}", bankStatementSessionDetails.getMerchantId());
                            loanDetailsServiceV2.recomputeEligibleLoan(globalLimitResponse, null, bankStatementSessionDetails.getMerchantId());
                            evictLoanDetailV2Cache(bankStatementSessionDetails.getMerchantId());
                        }
                        funnelService.submitEvent(bankStatementSessionDetails.getMerchantId(), null, null, FunnelEnums.StageId.BANK_STATEMENT, FunnelEnums.StageEvent.SUCCESS, "statement_success_response_home");
                    } else {
                        bankStatementSessionDetails.setStatus(BankStatementSessionStatus.FAILED);
                        bankStatementSessionDetails.setRejectReason(BankStatementRejectReason.OFFER_SAME.name());
                        funnelService.submitEvent(bankStatementSessionDetails.getMerchantId(), null, null, FunnelEnums.StageId.BANK_STATEMENT, FunnelEnums.StageEvent.FAILED, "statement_failed_response_home");
                    }
                } else {
                    bankStatementSessionDetails.setStatus(BankStatementSessionStatus.FAILED);
                    bankStatementSessionDetails.setRejectReason(BankStatementRejectReason.OFFER_SAME.name());
                    funnelService.submitEvent(bankStatementSessionDetails.getMerchantId(), null, null, FunnelEnums.StageId.BANK_STATEMENT, FunnelEnums.StageEvent.FAILED, "statement_failed_response_home");
                }
            } else {
                bankStatementSessionDetails.setRejectReason(BankStatementRejectReason.GLOBAL_LIMIT_FAILED.name());
                bankStatementSessionDetails.setStatus(BankStatementSessionStatus.FAILED);
                funnelService.submitEvent(bankStatementSessionDetails.getMerchantId(), null, null, FunnelEnums.StageId.BANK_STATEMENT, FunnelEnums.StageEvent.FAILED, "statement_failed_response_home");
            }
        } catch (Exception e) {
            log.error("Exception in getting global limit for merchantId : {}", bankStatementSessionDetails.getMerchantId());
            bankStatementSessionDetails.setStatus(BankStatementSessionStatus.FAILED);
            bankStatementSessionDetails.setRejectReason(BankStatementRejectReason.GLOBAL_LIMIT_EXCEPTION.name());
            funnelService.submitEvent(bankStatementSessionDetails.getMerchantId(), null, null, FunnelEnums.StageId.BANK_STATEMENT, FunnelEnums.StageEvent.FAILED, "statement_failed_response_home");
        }
        bankStatementSessionDetailsDao.save(bankStatementSessionDetails);
        return bankStatementSessionDetails;
    }

    private void evictLoanDetailV2Cache( Long merchantId) {
        if(Objects.nonNull(merchantId)) {
            String loanDetailsCacheKey = "LENDING_LOAN_DETAILS_" + merchantId;
            log.info("deleting cached key of loan details in create application for merchant: {}",merchantId);
            lendingCache.delete(loanDetailsCacheKey);
        } else {
            log.info("no key exists!");
        }
    }
}
