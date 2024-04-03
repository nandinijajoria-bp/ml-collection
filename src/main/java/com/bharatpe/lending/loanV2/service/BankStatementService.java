package com.bharatpe.lending.loanV2.service;

import com.bharatpe.cache.service.LendingCache;
import com.bharatpe.lending.common.dao.BankStatementSessionDetailsDao;
import com.bharatpe.lending.common.dao.LendingRiskVariablesDao;
import com.bharatpe.lending.common.entity.BankStatementSessionDetails;
import com.bharatpe.lending.common.entity.LendingRiskVariables;
import com.bharatpe.lending.common.enums.BankStatementSessionStatus;
import com.bharatpe.lending.common.enums.FunnelEnums;
import com.bharatpe.lending.common.enums.LendingEnum;
import com.bharatpe.lending.common.service.FunnelService;
import com.bharatpe.lending.common.service.merchant.dto.BankDetailsDto;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.dto.GlobalLimitResponse;
import com.bharatpe.lending.enums.BankStatementRejectReason;
import com.bharatpe.lending.enums.EligibilityRequestSource;
import com.bharatpe.lending.loanV2.dto.*;
import com.bharatpe.lending.loanV2.handlers.FinanceUtilsHandler;
import com.bharatpe.lending.loanV3.revamp.constants.LoanDetailsConstant;
import com.bharatpe.lending.loanV3.revamp.response.LoanDashboardApiVersion;
import com.bharatpe.lending.loanV3.revamp.services.LoanDashboardService;
import com.bharatpe.lending.service.APIGatewayService;
import com.bharatpe.lending.util.LoanUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.time.LocalDateTime;
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

    @Autowired
    LoanUtil loanUtil;

    @Autowired
    private LoanDashboardService loanDashboardService;

    @Value("${account-aggregator.lender.traffic.percent:10}")
    Integer[] accountAggregatorTrafficPercent;

    @Value("${default.AA.lender:LDC}")
    String defaultAALender;

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
            LoanDashboardApiVersion loanDashboardApiVersion = loanDashboardService.getLoanDashboardApiVersion(merchantId);

            if (ObjectUtils.isEmpty(prevBankStatementSessionDetails)) {
                sendFunnelEvent(merchantId, FunnelEnums.StageId.BANK_STATEMENT, FunnelEnums.StageEvent.UPLOAD, "bank_statement_upload_offer_page", loanDashboardApiVersion);
            } else if (prevBankStatementSessionDetails.getStatus().equals(BankStatementSessionStatus.FAILED)) {
                sendFunnelEvent(merchantId, FunnelEnums.StageId.BANK_STATEMENT, FunnelEnums.StageEvent.RE_UPLOAD, "bank_statement_reupload_home", loanDashboardApiVersion);
            }
            BankStatementUploadResponseDto apiResponse = financeUtilsHandler.uploadFile(fileName, password, base64, bankName, orderId, merchantId);
            if (ObjectUtils.isEmpty(apiResponse)) {
                bankStatementSessionDetails.setStatus(BankStatementSessionStatus.FAILED);
                bankStatementSessionDetails.setRejectReason(BankStatementRejectReason.UPLOAD_API_FAILED.name());
                bankStatementSessionDetailsDao.save(bankStatementSessionDetails);
                sendFunnelEvent(merchantId, FunnelEnums.StageId.BANK_STATEMENT, FunnelEnums.StageEvent.REJECT, "upload_bank_statement_reject", loanDashboardApiVersion);
                return new ApiResponse<>(false, "Error in uploading bank statement file");
            }
            if (!apiResponse.getSuccess()) {
                if(apiResponse.getMessage().equalsIgnoreCase("REJECTED")) {
                    bankStatementSessionDetails.setStatus(BankStatementSessionStatus.FAILED);
                    bankStatementSessionDetails.setRejectReason(BankStatementRejectReason.REJECT_AT_PARTNER.name());
                    bankStatementSessionDetails.setRequestId(apiResponse.getRequestId());
                    bankStatementSessionDetailsDao.save(bankStatementSessionDetails);
                    sendFunnelEvent(merchantId, FunnelEnums.StageId.BANK_STATEMENT, FunnelEnums.StageEvent.REJECT, "upload_bank_statement_reject", loanDashboardApiVersion);
                    return new ApiResponse<>(false, "Error in uploading bank statement file");
                } else {
                    bankStatementSessionDetails.setStatus(BankStatementSessionStatus.FAILED);
                    bankStatementSessionDetails.setRejectReason(BankStatementRejectReason.UPLOAD_API_FAILED.name());
                    bankStatementSessionDetails.setRequestId(apiResponse.getRequestId());
                    bankStatementSessionDetailsDao.save(bankStatementSessionDetails);
                    sendFunnelEvent(merchantId, FunnelEnums.StageId.BANK_STATEMENT, FunnelEnums.StageEvent.REJECT, "upload_bank_statement_reject", loanDashboardApiVersion);
                    return new ApiResponse<>(false, "Error in uploading bank statement file");
                }
            }
            bankStatementSessionDetails.setRequestId(apiResponse.getRequestId());
            bankStatementSessionDetails.setStatus(BankStatementSessionStatus.SUBMITTED);
            bankStatementSessionDetailsDao.save(bankStatementSessionDetails);
            if (ObjectUtils.isEmpty(prevBankStatementSessionDetails)) {
                sendFunnelEvent(merchantId, FunnelEnums.StageId.BANK_STATEMENT, FunnelEnums.StageEvent.SUBMITTED, "statement_success_response_offer_page", loanDashboardApiVersion);
            } else if (prevBankStatementSessionDetails.getStatus().equals(BankStatementSessionStatus.FAILED)) {
                sendFunnelEvent(merchantId, FunnelEnums.StageId.BANK_STATEMENT, FunnelEnums.StageEvent.SUBMITTED, "bank_statement_reupload_success_home", loanDashboardApiVersion);
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
        LoanDashboardApiVersion loanDashboardApiVersion = loanDashboardService.getLoanDashboardApiVersion(bankStatementSessionDetails.getMerchantId());
        try {
            if (sessionCallbackDto.getStatus().equals(BankStatementSessionStatus.FAILED)) {
                bankStatementSessionDetails.setStatus(BankStatementSessionStatus.FAILED);
                if (!ObjectUtils.isEmpty(sessionCallbackDto.getMessage())) {
                    bankStatementSessionDetails.setRejectReason(sessionCallbackDto.getMessage());
                } else {
                    bankStatementSessionDetails.setRejectReason(BankStatementRejectReason.FAILED_CALLBACK_STATUS.name());
                }
                if(("BANK_STATEMENT").equalsIgnoreCase(bankStatementSessionDetails.getType())) {
                    sendFunnelEvent(bankStatementSessionDetails.getMerchantId(), FunnelEnums.StageId.BANK_STATEMENT, FunnelEnums.StageEvent.FAILED, "statement_failed_response_home", loanDashboardApiVersion);
                } else {
                    sendFunnelEvent(bankStatementSessionDetails.getMerchantId(), FunnelEnums.StageId.BANK_STATEMENT, FunnelEnums.StageEvent.FAILED, "account_aggregator_failed_response_home", loanDashboardApiVersion);
                }
            } else if (sessionCallbackDto.getStatus().equals(BankStatementSessionStatus.SUCCESS)) {
                if (("BANK_STATEMENT").equalsIgnoreCase(bankStatementSessionDetails.getType())) {
                    final BankDetailsDto bankDetailsDtoOptional = merchantService.fetchMerchantBankDetails(bankStatementSessionDetails.getMerchantId()).orElse(null);
                    if (!ObjectUtils.isEmpty(bankDetailsDtoOptional)) {
                        if(isAccountMismatch(sessionCallbackDto, bankDetailsDtoOptional)) {
                            bankStatementSessionDetails.setRejectReason(BankStatementRejectReason.ACCOUNT_NO_MISMATCH.name());
                            bankStatementSessionDetails.setStatus(BankStatementSessionStatus.FAILED);
                            sendFunnelEvent(bankStatementSessionDetails.getMerchantId(), FunnelEnums.StageId.BANK_STATEMENT, FunnelEnums.StageEvent.FAILED, "statement_failed_response_home", loanDashboardApiVersion);
                        } else if (sessionCallbackDto.getPeriod() < 6) {
                            bankStatementSessionDetails.setRejectReason(BankStatementRejectReason.BANK_STATEMENT_NOT_IN_6_MONTH_PERIOD.name());
                            bankStatementSessionDetails.setStatus(BankStatementSessionStatus.FAILED);
                            sendFunnelEvent(bankStatementSessionDetails.getMerchantId(), FunnelEnums.StageId.BANK_STATEMENT, FunnelEnums.StageEvent.FAILED, "statement_failed_response_home", loanDashboardApiVersion);
                        } else {
                            bankStatementSessionDetails.setStatus(BankStatementSessionStatus.INPROCESS);
                            bankStatementSessionDetailsDao.save(bankStatementSessionDetails);
                            bankStatementSessionDetails = underWritingAnalysis(bankStatementSessionDetails, loanDashboardApiVersion);
                        }
                    } else {
                        bankStatementSessionDetails.setStatus(BankStatementSessionStatus.FAILED);
                        bankStatementSessionDetails.setRejectReason(BankStatementRejectReason.BANK_DETAILS_MISSING.name());
                        sendFunnelEvent(bankStatementSessionDetails.getMerchantId(), FunnelEnums.StageId.BANK_STATEMENT, FunnelEnums.StageEvent.FAILED, "statement_failed_response_home", loanDashboardApiVersion);
                    }
                } else if(("ACCOUNT_AGGREGATOR").equalsIgnoreCase(bankStatementSessionDetails.getType())) {
                    bankStatementSessionDetails.setStatus(BankStatementSessionStatus.INPROCESS);
                    bankStatementSessionDetailsDao.save(bankStatementSessionDetails);
                    bankStatementSessionDetails = underWritingAnalysis(bankStatementSessionDetails, loanDashboardApiVersion);
                }
            }
            bankStatementSessionDetailsDao.save(bankStatementSessionDetails);
            log.info("Successfully update bankStatement session : {}", bankStatementSessionDetails);
        } catch (Exception e) {
            log.error("Exception in updating bankStatement session for sessionId : {}", sessionCallbackDto.getSessionId(), e);
            bankStatementSessionDetails.setRejectReason("INTERNAL_ERROR");
            bankStatementSessionDetails.setStatus(BankStatementSessionStatus.FAILED);
            bankStatementSessionDetailsDao.save(bankStatementSessionDetails);
            sendFunnelEvent(bankStatementSessionDetails.getMerchantId(), FunnelEnums.StageId.BANK_STATEMENT, FunnelEnums.StageEvent.FAILED, "statement_failed_response_home", loanDashboardApiVersion);
        }
    }

    private BankStatementSessionDetails underWritingAnalysis(BankStatementSessionDetails bankStatementSessionDetails, LoanDashboardApiVersion loanDashboardApiVersion) {
        try {
            Double currentLimit = 0D;
            LendingRiskVariables lendingRiskVariables = lendingRiskVariablesDao.findByMerchantId(bankStatementSessionDetails.getMerchantId());
            if(!ObjectUtils.isEmpty(lendingRiskVariables)) {
                currentLimit = lendingRiskVariables.getFinalOffer();
            }
            String type = "BANK_STATEMENT".equalsIgnoreCase(bankStatementSessionDetails.getType()) ? bankStatementSessionDetails.getType() : "AA";
            GlobalLimitResponse globalLimitResponse = apiGatewayService.getGlobalLimit(bankStatementSessionDetails.getMerchantId(), bankStatementSessionDetails.getOrderId(),type, EligibilityRequestSource.EASY_LOANS);
            if (globalLimitResponse != null && globalLimitResponse.getData() != null && globalLimitResponse.getData().getBankAffectedOffer() != null) {
                if (globalLimitResponse.getData().getBankAffectedOffer()) {
                    if(globalLimitResponse.getData().getGlobalLimit() > currentLimit) {
                        bankStatementSessionDetails.setStatus(BankStatementSessionStatus.SUCCESS);
                        Double eligibleAmount = 0D;
                        log.info("Global limit for merchant:{} is {}", bankStatementSessionDetails.getMerchantId(), globalLimitResponse.getData().getGlobalLimit());
                        eligibleAmount = globalLimitResponse.getData().getGlobalLimit();
                        if (eligibleAmount > 0D) {
                            log.info("Eligibility found for merchant:{}", bankStatementSessionDetails.getMerchantId());
                            loanDetailsServiceV2.recomputeEligibleLoan(globalLimitResponse, null, bankStatementSessionDetails.getMerchantId(), false);
                            evictLoanDetailV2Cache(bankStatementSessionDetails.getMerchantId());
                        }
                        if(("BANK_STATEMENT").equalsIgnoreCase(bankStatementSessionDetails.getType())) {
                            sendFunnelEvent(bankStatementSessionDetails.getMerchantId(), FunnelEnums.StageId.BANK_STATEMENT, FunnelEnums.StageEvent.SUCCESS, "statement_success_response_home", loanDashboardApiVersion);
                        } else {
                            sendFunnelEvent(bankStatementSessionDetails.getMerchantId(), FunnelEnums.StageId.ACCOUNT_AGGREGATOR, FunnelEnums.StageEvent.SUCCESS, "account_aggregator_success_response_home", loanDashboardApiVersion);
                        }
                    } else {
                        bankStatementSessionDetails.setStatus(BankStatementSessionStatus.FAILED);
                        bankStatementSessionDetails.setRejectReason(BankStatementRejectReason.OFFER_SAME.name());
                        if(("BANK_STATEMENT").equalsIgnoreCase(bankStatementSessionDetails.getType())) {
                            sendFunnelEvent(bankStatementSessionDetails.getMerchantId(), FunnelEnums.StageId.BANK_STATEMENT, FunnelEnums.StageEvent.FAILED, "statement_failed_response_home", loanDashboardApiVersion);
                        } else {
                            sendFunnelEvent(bankStatementSessionDetails.getMerchantId(), FunnelEnums.StageId.ACCOUNT_AGGREGATOR, FunnelEnums.StageEvent.FAILED, "account_aggregator_failed_response_home", loanDashboardApiVersion);
                        }
                    }
                } else {
                    bankStatementSessionDetails.setStatus(BankStatementSessionStatus.FAILED);
                    bankStatementSessionDetails.setRejectReason(BankStatementRejectReason.OFFER_SAME.name());
                    if(("BANK_STATEMENT").equalsIgnoreCase(bankStatementSessionDetails.getType())) {
                        sendFunnelEvent(bankStatementSessionDetails.getMerchantId(), FunnelEnums.StageId.BANK_STATEMENT, FunnelEnums.StageEvent.FAILED, "statement_failed_response_home", loanDashboardApiVersion);
                    } else {
                        sendFunnelEvent(bankStatementSessionDetails.getMerchantId(), FunnelEnums.StageId.ACCOUNT_AGGREGATOR, FunnelEnums.StageEvent.FAILED, "account_aggregator_failed_response_home", loanDashboardApiVersion);
                    }
                }
            } else {
                bankStatementSessionDetails.setRejectReason(BankStatementRejectReason.GLOBAL_LIMIT_FAILED.name());
                bankStatementSessionDetails.setStatus(BankStatementSessionStatus.FAILED);
                if(("BANK_STATEMENT").equalsIgnoreCase(bankStatementSessionDetails.getType())) {
                    sendFunnelEvent(bankStatementSessionDetails.getMerchantId(), FunnelEnums.StageId.BANK_STATEMENT, FunnelEnums.StageEvent.FAILED, "statement_failed_response_home", loanDashboardApiVersion);
                } else {
                    sendFunnelEvent(bankStatementSessionDetails.getMerchantId(), FunnelEnums.StageId.ACCOUNT_AGGREGATOR, FunnelEnums.StageEvent.FAILED, "account_aggregator_failed_response_home", loanDashboardApiVersion);
                }
            }
        } catch (Exception e) {
            log.error("Exception in getting global limit for merchantId : {}", bankStatementSessionDetails.getMerchantId());
            bankStatementSessionDetails.setStatus(BankStatementSessionStatus.FAILED);
            bankStatementSessionDetails.setRejectReason(BankStatementRejectReason.GLOBAL_LIMIT_EXCEPTION.name());
            if(("BANK_STATEMENT").equalsIgnoreCase(bankStatementSessionDetails.getType())) {
                sendFunnelEvent(bankStatementSessionDetails.getMerchantId(), FunnelEnums.StageId.BANK_STATEMENT, FunnelEnums.StageEvent.FAILED, "statement_failed_response_home", loanDashboardApiVersion);
            } else {
                sendFunnelEvent(bankStatementSessionDetails.getMerchantId(), FunnelEnums.StageId.ACCOUNT_AGGREGATOR, FunnelEnums.StageEvent.FAILED, "account_aggregator_failed_response_home", loanDashboardApiVersion);
            }
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
        loanDashboardService.deleteLoanDashboardCache(merchantId);
    }

    public ApiResponse<?> initiateAccountAggregator(AAInitiateRequestDTO aaInitiateRequestDTO, BasicDetailsDto merchant) {
        try {
            BankStatementSessionDetails prevBankStatementSessionDetails = bankStatementSessionDetailsDao.findFirstByMerchantIdAndTypeOrderByIdDesc(merchant.getId(), "ACCOUNT_AGGREGATOR");
            String orderId = UUID.randomUUID().toString();
            final BankDetailsDto bankDetailsDtoOptional = merchantService.fetchMerchantBankDetails(merchant.getId()).orElse(null);
            if (ObjectUtils.isEmpty(bankDetailsDtoOptional)) {
                return new ApiResponse<>(false, "Merchant bankDetails not found");
            }
            LoanDashboardApiVersion loanDashboardApiVersion = loanDashboardService.getLoanDashboardApiVersion(merchant.getId());

            String bankAccount = bankDetailsDtoOptional.getBankCode();
            String accountNumber = bankDetailsDtoOptional.getAccountNumber().replaceAll("^0+(?!$)", "");
            String accountNoLast4Digit = accountNumber.substring(accountNumber.length() - 4);
            String mobile = merchant.getMobile().length() > 10 ? merchant.getMobile().substring(2) : merchant.getMobile();
            LendingEnum.LENDER lender = assignLender(merchant.getId());
            BankStatementSessionDetails bankStatementSessionDetails = BankStatementSessionDetails.builder()
                    .orderId(orderId)
                    .merchantId(merchant.getId())
                    .type("ACCOUNT_AGGREGATOR")
                    .status(BankStatementSessionStatus.PENDING)
                    .method("INITIATE")
                    .lender(lender)
                    .build();
            bankStatementSessionDetailsDao.save(bankStatementSessionDetails);
            AccountAggregatorInitiateResponseDTO apiResponse = financeUtilsHandler.AAInitiate(orderId, merchant.getId(), mobile, bankAccount, lender, aaInitiateRequestDTO.getRedirectUrl(), accountNoLast4Digit);
            if (ObjectUtils.isEmpty(prevBankStatementSessionDetails)) {
                sendFunnelEvent(bankStatementSessionDetails.getMerchantId(), FunnelEnums.StageId.ACCOUNT_AGGREGATOR, FunnelEnums.StageEvent.INITIATED, "account_aggregator_initiate_offer_page", loanDashboardApiVersion);
            } else if (prevBankStatementSessionDetails.getStatus().equals(BankStatementSessionStatus.FAILED)) {
                sendFunnelEvent(bankStatementSessionDetails.getMerchantId(), FunnelEnums.StageId.ACCOUNT_AGGREGATOR, FunnelEnums.StageEvent.RE_INITIATED, "account_aggregator_re-initiate_offer_page", loanDashboardApiVersion);
            }
            if (ObjectUtils.isEmpty(apiResponse)) {
                bankStatementSessionDetails.setStatus(BankStatementSessionStatus.FAILED);
                bankStatementSessionDetails.setRejectReason(BankStatementRejectReason.INITIATE_API_FAILED.name());
                bankStatementSessionDetailsDao.save(bankStatementSessionDetails);
                sendFunnelEvent(bankStatementSessionDetails.getMerchantId(), FunnelEnums.StageId.ACCOUNT_AGGREGATOR, FunnelEnums.StageEvent.REJECT, "account_aggregator_initiate_reject", loanDashboardApiVersion);
                return new ApiResponse<>(false, "Error in initiating account aggregator session");
            }
            if(!apiResponse.getSuccess() && "Bank Not Enabled".equalsIgnoreCase(apiResponse.getMessage())) {
                bankStatementSessionDetails.setStatus(BankStatementSessionStatus.FAILED);
                bankStatementSessionDetails.setRejectReason(BankStatementRejectReason.BANK_NOT_ENABLED_FOR_AA.name());
                bankStatementSessionDetailsDao.save(bankStatementSessionDetails);
                sendFunnelEvent(bankStatementSessionDetails.getMerchantId(), FunnelEnums.StageId.ACCOUNT_AGGREGATOR, FunnelEnums.StageEvent.REJECT, "account_aggregator_initiate_reject", loanDashboardApiVersion);
                return new ApiResponse<>(false, "Error in initiating account aggregator session");
            }
            if (!apiResponse.getSuccess() || ("FAILED").equalsIgnoreCase(apiResponse.getData().getStatus()) || ("REJECTED").equalsIgnoreCase(apiResponse.getData().getStatus())) {
                bankStatementSessionDetails.setStatus(BankStatementSessionStatus.FAILED);
                bankStatementSessionDetails.setRejectReason(BankStatementRejectReason.INITIATE_API_FAILED.name());
                bankStatementSessionDetails.setRequestId(apiResponse.getRequestId());
                bankStatementSessionDetailsDao.save(bankStatementSessionDetails);
                sendFunnelEvent(bankStatementSessionDetails.getMerchantId(), FunnelEnums.StageId.ACCOUNT_AGGREGATOR, FunnelEnums.StageEvent.REJECT, "account_aggregator_initiate_reject", loanDashboardApiVersion);
                return new ApiResponse<>(false, "Error in initiating account aggregator session");
            }
            bankStatementSessionDetails.setRequestId(apiResponse.getData().getTrackingId());
            bankStatementSessionDetails.setStatus(BankStatementSessionStatus.SUBMITTED);
            bankStatementSessionDetailsDao.save(bankStatementSessionDetails);
            if (ObjectUtils.isEmpty(prevBankStatementSessionDetails)) {
                sendFunnelEvent(bankStatementSessionDetails.getMerchantId(), FunnelEnums.StageId.ACCOUNT_AGGREGATOR, FunnelEnums.StageEvent.SUBMITTED, "account_aggregator_initiate_success_response_offer_page", loanDashboardApiVersion);
            } else if (prevBankStatementSessionDetails.getStatus().equals(BankStatementSessionStatus.FAILED)) {
                sendFunnelEvent(bankStatementSessionDetails.getMerchantId(), FunnelEnums.StageId.ACCOUNT_AGGREGATOR, FunnelEnums.StageEvent.SUBMITTED, "account_aggregator_re-initiate_success_home", loanDashboardApiVersion);
            }
            return new ApiResponse<>(apiResponse.getData());
        } catch (Exception e) {
            log.error("Exception initiating account aggregator session for merchantId : {},  ", merchant.getId(), e);
            return new ApiResponse<>(false, e.getMessage());
        }
    }

    public LendingEnum.LENDER assignLender(Long merchantId) {
        LendingEnum.LENDER defaultLender = LendingEnum.LENDER.valueOf(defaultAALender);
        Integer[] percentages = accountAggregatorTrafficPercent;
        if(4 != percentages.length || 100 != percentages[3]) {
            return defaultLender;
        }
        LendingEnum.LENDER lender =  loanUtil.percentLenderTrafficForAA(merchantId, percentages);
        if(ObjectUtils.isEmpty(lender)) {
            return defaultLender;
        }
        return lender;
    }


    private void sendFunnelEvent(Long merchantId, FunnelEnums.StageId stageId, FunnelEnums.StageEvent stageEvent, String eventValue, LoanDashboardApiVersion loanDashboardApiVersion){
        if(LoanDetailsConstant.VERSION_V2.equalsIgnoreCase(loanDashboardApiVersion.getApiVersion())){
            funnelService.submitEventV3(merchantId, null, null,
                    stageId, stageEvent, eventValue, LoanDetailsConstant.FUNNEL_VERSION_TAG);
        }
        else{
            funnelService.submitEvent(merchantId, null, null,
                    stageId, stageEvent, eventValue);
        }
    }


    public Boolean isAccountMismatch(BankStatementSessionCallbackDto sessionCallbackDto,  BankDetailsDto bankDetailsDto) {
        try {
            String accountNo = null;
            String ifscCode = null;
            if (!ObjectUtils.isEmpty(bankDetailsDto.getAccountNumber())) {
                accountNo = bankDetailsDto.getAccountNumber().replaceAll("^0+(?!$)", "");
            }
            if (!ObjectUtils.isEmpty(bankDetailsDto.getIfsc())) {
                ifscCode = bankDetailsDto.getIfsc();
            }
            log.info("accountNo : {}, ifscCode : {} for orderId : {} in linked bankDetails of merchant", accountNo, ifscCode, sessionCallbackDto.getSessionId());
            if (!sessionCallbackDto.getAccountNo().equals(accountNo)) {
                log.info("Exact match on accountNo failed for orderId {} with actual a/c no : {} and passed a/c no : {}", sessionCallbackDto.getSessionId(), accountNo, sessionCallbackDto.getAccountNo());
                if (!sessionCallbackDto.getAccountNo().substring(sessionCallbackDto.getAccountNo().length() - 4).equalsIgnoreCase(accountNo.substring(accountNo.length() - 4))
                        || !ifscCode.equalsIgnoreCase(sessionCallbackDto.getIfscCode())) {
                    log.info("accountNo and ifscCode mismatch for orderId : {}", sessionCallbackDto.getSessionId());
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            log.error("Exception in matching account no for sessionId : {}, {}", sessionCallbackDto.getSessionId(), e.getMessage());
            return true;
        }
    }


    public void checkAASessionStatus(String orderId, String eventValue) {
        try {
            BankStatementSessionDetails bankStatementSessionDetails = bankStatementSessionDetailsDao.findByOrderId(orderId);
            if(ObjectUtils.isEmpty(bankStatementSessionDetails) || !"ACCOUNT_AGGREGATOR".equalsIgnoreCase(bankStatementSessionDetails.getType())) {
                log.info("No Account-Aggregator session found for given orderId : {}", orderId);
                return;
            }
            LoanDashboardApiVersion loanDashboardApiVersion = loanDashboardService.getLoanDashboardApiVersion(bankStatementSessionDetails.getMerchantId());
            AccountAggregatorInitiateResponseDTO apiResponse = financeUtilsHandler.AAStatusCheck(orderId);
            if(ObjectUtils.isEmpty(apiResponse) || ObjectUtils.isEmpty(apiResponse.getData()) || !apiResponse.getSuccess()) {
                bankStatementSessionDetails.setStatus(BankStatementSessionStatus.FAILED);
                bankStatementSessionDetails.setRejectReason(eventValue);
                bankStatementSessionDetailsDao.save(bankStatementSessionDetails);
                sendFunnelEvent(bankStatementSessionDetails.getMerchantId(), FunnelEnums.StageId.ACCOUNT_AGGREGATOR, FunnelEnums.StageEvent.REJECT, "account_aggregator_initiate_reject", loanDashboardApiVersion);
                return;
            }
            if ("INITIATED".equalsIgnoreCase(apiResponse.getData().getStatus()) || ("FAILED").equalsIgnoreCase(apiResponse.getData().getStatus()) || ("PROCESSING").equalsIgnoreCase(apiResponse.getData().getStatus())) {
                bankStatementSessionDetails.setStatus(BankStatementSessionStatus.FAILED);
                bankStatementSessionDetails.setRejectReason(eventValue);
                bankStatementSessionDetails.setRequestId(apiResponse.getRequestId());
                bankStatementSessionDetailsDao.save(bankStatementSessionDetails);
                sendFunnelEvent(bankStatementSessionDetails.getMerchantId(), FunnelEnums.StageId.ACCOUNT_AGGREGATOR, FunnelEnums.StageEvent.REJECT, "account_aggregator_initiate_reject", loanDashboardApiVersion);
                return;
            }
            if("COMPLETED".equalsIgnoreCase(apiResponse.getData().getStatus()) && "ANALYTICS_COMPLETE".equalsIgnoreCase(apiResponse.getData().getNotificationType())) {
                bankStatementSessionDetails.setStatus(BankStatementSessionStatus.INPROCESS);
                bankStatementSessionDetailsDao.save(bankStatementSessionDetails);
                underWritingAnalysis(bankStatementSessionDetails,  loanDashboardService.getLoanDashboardApiVersion(bankStatementSessionDetails.getMerchantId()));
            }
        } catch (Exception e) {
            log.error("Exception in checking status of AA session with orderId : {}, {}", orderId, e.getMessage());
        }
    }
}
