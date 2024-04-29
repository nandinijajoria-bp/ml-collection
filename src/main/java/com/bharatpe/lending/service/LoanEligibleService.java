package com.bharatpe.lending.service;

import com.bharatpe.common.dao.*;
import com.bharatpe.common.entities.*;
import com.bharatpe.common.enums.Loan;
import com.bharatpe.lending.common.Handler.MerchantSummaryHandler;
import com.bharatpe.lending.common.dao.ExperianRawResponseDao;
import com.bharatpe.lending.common.dao.LendingMerchantDropoffDao;
import com.bharatpe.lending.common.dto.MerchantResponseDTO;
import com.bharatpe.lending.common.query.dao.LendingLedgerSlaveDao;
import com.bharatpe.lending.common.query.entity.LendingLedgerSlave;
import com.bharatpe.lending.common.service.merchant.dto.BankDetailsDto;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.dto.PincodeCityStateMappingDTO;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.common.query.dao.ExternalGatewayDaoSlave;
import com.bharatpe.lending.common.query.entity.ExternalGatewaySlave;
import com.bharatpe.lending.common.util.AesEncryptionUtil;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.common.util.LendingHmacCalculator;
import com.bharatpe.lending.constant.ExperianConstants;
import com.bharatpe.lending.constant.LendingConstants;
import com.bharatpe.lending.dao.*;
import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.enums.EligibilityRequestSource;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.exception.BureauCallMaskedApiException;
import com.bharatpe.lending.handlers.KycHandler;
import com.bharatpe.lending.handlers.MerchantSummaryExceptionHandler;
import com.bharatpe.lending.loanV2.service.LoanDetailsServiceV2;
import com.bharatpe.lending.util.LoanCalculationUtil;
import com.bharatpe.lending.util.LoanUtil;
import com.bharatpe.lending.util.creditresponse.CrifResponseUtil;
import com.bharatpe.lending.util.creditresponse.ExperianResponseUtil;
import com.bharatpe.lending.util.creditresponse.ResponseUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.joda.time.DateTime;
import org.json.JSONObject;
import org.json.XML;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.ObjectUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class LoanEligibleService {

    List<String> emails = Arrays.asList("rajat.jain@bharatpe.com", "khushal.virmani@bharatpe.com");

    List<Long> exemptMerchant = Arrays.asList(2411647L, 1210933L, 4340760L, 2097359L, 7090157L, 6518986L, 1141505L, 3L, 3543643L, 9319451L, 8891247L, 2078363L);

    private final Logger logger = LoggerFactory.getLogger(LoanEligibleService.class);

    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;

    @Autowired
    RestTemplate restTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    ExperianDao experianDao;

    @Autowired
    LoanUtil loanUtil;

    @Autowired
    LendingLedgerDao lendingLedgerDao;

    @Autowired
    LendingLedgerSlaveDao lendingLedgerSlaveDao;

//    @Autowired
//    EmailHandler emailHandler;

    @Autowired
    LendingCategoryDao lendingCategoryDao;

    @Autowired
    EligibleLoanDao eligibleLoanDao;

    @Autowired
    LendingPancardDao lendingPancardDao;

    @Autowired
    ExternalGatewayDaoSlave externalGatewayDaoSlave;

    @Autowired
    AesEncryptionUtil aesEncryptionUtil;

    @Autowired
    LendingHmacCalculator lendingHmacCalculator;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    ExperianDetailsDao experianDetailsDao;

    @Autowired
    EligibleLoanAuditDao eligibleLoanAuditDao;

    @Autowired
    ExperianService experianService;

    @Autowired
    ExperianRawResponseDao experianRawResponseDao;

    @Autowired
    APIGatewayService apiGatewayService;

//    @Autowired
//    MerchantDao merchantDao;

//    @Autowired
//    MerchantSummaryDao merchantSummaryDao;

//    @Autowired
//    CrifRequestResponseDao crifRequestResponseDao;

    @Autowired
    LendingMerchantDropoffDao lendingMerchantDropoffDao;

    @Autowired
    LoanDetailsServiceV2 loanDetailsServiceV2;

    @Autowired
    DateTimeUtil dateTimeUtil;

    @Autowired
    MerchantSummaryHandler merchantSummaryHandler;

    @Value("${eligibility.refresh.window:1}")
    int eligibilityRefreshWindow;

    @Value("${new.eligibility.refresh.window:1}")
    int newEligibilityRefreshWindow;

    @Value("${new.eligibility.refresh.window.rollout.percent:0}")
    Integer newEligibilityRefreshWindowRolloutPercent;

    ExecutorService executorService = Executors.newFixedThreadPool(10);
    @Autowired
    MerchantService merchantService;

    @Autowired
    KycHandler kycHandler;

    @Autowired
    EasyLoanUtil easyLoanUtil;

    static List<String> topupLoans = Arrays.asList(LoanType.TOPUP.name(), LoanType.HALF_TOPUP.name(),
            LoanType.IO_TOPUP.name());

    public EligibleLendingOffersResponseDTO getEligibilityDetails(Long merchantId, Double queryAmount, Integer ediModel) throws BureauCallMaskedApiException {

        EligibleLendingOffersResponseDTO responseDTO = new EligibleLendingOffersResponseDTO();

        int updatedEligibilityRefreshWindow = easyLoanUtil.percentScaleUp(merchantId, newEligibilityRefreshWindowRolloutPercent) ? newEligibilityRefreshWindow : eligibilityRefreshWindow;
        Date dateWindow = dateTimeUtil.getDatePlusMinutes(dateTimeUtil.getCurrentDate(), -1);

//        List<EligibleLoan> eligibleLoans = eligibleLoanDao.findByMerchantIdAndAmountAndCreatedAtIsGreaterThanEqualAndLoanTypeNotIn(merchantId, queryAmount, dateWindow, topupLoans,
//          Sort.by(Sort.Direction.DESC, "id"));

        List<EligibleLoan> eligibleLoans = new ArrayList<>();

        if (ObjectUtils.isEmpty(eligibleLoans)) {
            GlobalLimitResponse globalLimitResponse = apiGatewayService.getGlobalLimit(merchantId, EligibilityRequestSource.EASY_LOANS);
            //Todo if global response is null: return
            if ((queryAmount < 10000 && Objects.nonNull(globalLimitResponse) && Objects.nonNull(globalLimitResponse.getData()) &&
                    !globalLimitResponse.getData().getLoanType().equalsIgnoreCase(LoanType.SMALL_TICKET.name())) || (Objects.nonNull(globalLimitResponse)
              && Objects.nonNull(globalLimitResponse.getData()) && ObjectUtils.isEmpty(globalLimitResponse.getData().getOfferDetails()))) {
                responseDTO.setSuccess(false);
                responseDTO.setMessage("Invalid Loan Amount");
                return responseDTO;
            }
            //        eligibleLoanDao.deleteCustomOffers(merchantId);
            if (Objects.nonNull(globalLimitResponse) && Objects.nonNull(globalLimitResponse.getData()) && Objects.nonNull(globalLimitResponse.getData().getGlobalLimit())) {
                logger.info("query amount: {}, global calculated offer: {}", queryAmount, globalLimitResponse.getData().getGlobalLimit());
                queryAmount = queryAmount > globalLimitResponse.getData().getGlobalLimit() ? globalLimitResponse.getData().getGlobalLimit() : queryAmount;
            }
            loanDetailsServiceV2.recomputeEligibleLoan(globalLimitResponse, queryAmount, merchantId, false);
            eligibleLoans = eligibleLoanDao.findByMerchantIdAndAmountAndCreatedAtIsGreaterThanEqualAndLoanTypeNotIn(merchantId, queryAmount, dateWindow, topupLoans,
                    Sort.by(Sort.Direction.DESC, "id"));
            logger.info("Eligible loan offers : {} , {}", merchantId, eligibleLoans);
        }

        List<EligibleLendingOffersResponseDTO.TenureDetails> tenures = new ArrayList<>();
        for (EligibleLoan el : eligibleLoans) {
            if (ediModel == 6 && el.getEdiCount() % 30 !=0){
                tenures.add(convertLoanToTenureDetails(el, responseDTO));
            } else if (ediModel == 7 && el.getEdiCount() % 30 ==0) {
                tenures.add(convertLoanToTenureDetails(el, responseDTO));
            }
        }
        responseDTO.setEligibleOfferDetails(responseDTO.new EligibleOfferDetails(queryAmount, tenures));
        responseDTO.setMessage("Available tenures for given amount");
        responseDTO.setSuccess(true);
        logger.info("Eligibility Details response for merchant: {} is: {}", merchantId, responseDTO.toString());
        return responseDTO;
    }

    private EligibleLendingOffersResponseDTO.TenureDetails convertLoanToTenureDetails(
            EligibleLoan eligibleLoan, EligibleLendingOffersResponseDTO responseDTO) {
        EligibleLendingOffersResponseDTO.TenureDetails tenureDetails = responseDTO.new TenureDetails();
        tenureDetails.setTenure(eligibleLoan.getTenure());
        tenureDetails.setCategory(eligibleLoan.getCategory());
        tenureDetails.setEdi(eligibleLoan.getEdi());
        tenureDetails.setIoEdi(eligibleLoan.getIoEdi());
        tenureDetails.setFinanceCharge(eligibleLoan.getProcessingFee());
        tenureDetails.setRateOfInterest(eligibleLoan.getRateOfInterest());
        tenureDetails.setRepaymentAmount(eligibleLoan.getRepayment());
        tenureDetails.setEdiCount(eligibleLoan.getEdiCount());
        tenureDetails.setTenureInMonths(eligibleLoan.getTenureInMonths());
        return tenureDetails;
    }

    public ResponseDTO updateEligibleLoan(Long merchantId, EligibleLoanUpdateRequestDTO body) {
        ResponseDTO responseDTO = new ResponseDTO();
        // todo final fix this later
        Date dateWindow = dateTimeUtil.getDatePlusDays(dateTimeUtil.getCurrentDate(), -24 );
        if (loanUtil.isInternalMerchant(merchantId)) {
            body.setEdiDays( body.getEdiDays() == null ? body.getTenure() * 30 : body.getEdiDays());
//            dateWindow = dateTimeUtil.getDatePlusMinutes(dateTimeUtil.getCurrentDate(), -5);
        }
        try {
        logger.info("EligibleLoan Query values merchantId : {}, amount : {}, tenure : {}, dateWindow : {}", merchantId, body.getAmount(), body.getTenure(), dateWindow);
        EligibleLoan eligibleLoan = null;
        if (body.getEdiDays() == null) {
            eligibleLoan = eligibleLoanDao.findTopByMerchantIdAndAmountAndTenureInMonthsAndCreatedAtIsGreaterThanEqualAndLoanTypeNotInOrderByIdDesc(merchantId, body.getAmount(), body.getTenure(), dateWindow, topupLoans);
        } else {
            eligibleLoan = eligibleLoanDao.findTopByMerchantIdAndAmountAndEdiCountAndCreatedAtIsGreaterThanEqualAndLoanTypeNotInOrderByIdDesc(merchantId, body.getAmount(), body.getEdiDays(),dateWindow, topupLoans);
        }
            logger.info("eligibleLoan merchant_id : {}", eligibleLoan);
//        EligibleLoan eligibleLoan = eligibleLoanDao.findTopByMerchantIdAndAmountAndTenureInMonthsOrderByIdDesc(merchantId, body.getAmount(), body.getTenure());
        if (Objects.nonNull(eligibleLoan)) {
            EligibleLoan customLoan = new EligibleLoan(eligibleLoan);
            customLoan.setOfferType("CUSTOM");
            eligibleLoanDao.save(customLoan);
            logger.info("Eligible loan custom offer for merchantId : {} {}", merchantId, customLoan);
            eligibleLoanAuditDao.save(EligibleLoanAudit.createObject(customLoan));
            responseDTO.setMessage("Created eligible loan entry successfully");
            responseDTO.setSuccess(true);
            return responseDTO;
        }
        } catch (Exception e) {
            logger.error("Error occurred while creating custom offer for merchantId : {} {}", merchantId, Arrays.asList(e.getStackTrace()));
        }

        logger.info("Could not create custom offer for the merchant : {}", merchantId);
        responseDTO.setMessage("No eligible loan entry found");
        responseDTO.setSuccess(false);
        return responseDTO;
    }

//    public List<LoanEligibilityDTO> getNewLoanDetails(BasicDetailsDto merchantBasicDetails, Experian experian, MerchantResponseDTO merchantResponseDTO, BankDetailsDto merchantBankDetail, boolean skip, String pancard, boolean isZomato, String lendingType, boolean yellowPincode, String bankCode) {
//        Double bpScore = (merchantResponseDTO != null && merchantResponseDTO.getBpScore() != null) ? merchantResponseDTO.getBpScore() : 0D;
//        double selfTpv = (merchantResponseDTO != null && merchantResponseDTO.getSelfTxnValue1Mon() != null) ? merchantResponseDTO.getSelfTxnValue1Mon() : 0d;
//        double tpvLast30Days = (merchantResponseDTO != null && merchantResponseDTO.getTpv1Mon() != null) ? merchantResponseDTO.getTpv1Mon() - selfTpv : 0D;
//        int txnLast30Days = 30;
//        double avgTpv = tpvLast30Days / txnLast30Days;
//        if (exemptMerchant.contains(merchantBasicDetails.getId())) {
//            avgTpv = 100000;
//            bpScore = 20D;
//            merchantResponseDTO.setUniqueCustomer1mon(20);
//        }
//        List<LendingPaymentSchedule> prevLoans;
//        if (lendingType.equalsIgnoreCase("CREDITLINE")) {
//            prevLoans = lendingPaymentScheduleDao.findPreviousLoansByMerchantAndCreditLoan(merchantBasicDetails.getId(), true);
//        } else {
//            prevLoans = lendingPaymentScheduleDao.findPreviousLoansByMerchantAndCreditLoan(merchantBasicDetails.getId(), false);
//        }
//
////        Merchant merchant = merchantDao.getById(merchantBasicDetails.getId());
//
//        int loanCount = (prevLoans == null || prevLoans.isEmpty()) ? 0 : prevLoans.size();
//        boolean repeatedLoan = loanCount > 0;
//        if (skip) {
//            experian.setSkip(true);
//            experianDao.save(experian);
//        }
//        boolean isEligibleForConstruct2And3 = false;
////        boolean isEligibleForConstruct2And3 = isEligibleForConstruct2And3(merchantSummary, prevLoans);
//        boolean isRepeatLoanNoDerog = isRepeatLoanNoDerog(prevLoans, merchantBasicDetails.getId());
//        LendingApplication lendingApplication = null;
//        if (isRepeatLoanNoDerog && prevLoans != null) {
//            lendingApplication = lendingApplicationDao.findByIdAndMerchantId(prevLoans.get(0).getApplicationId(),
//                    merchantBasicDetails.getId());
//        }
//        int previousLoanDays = (prevLoans != null && !prevLoans.isEmpty()) ? prevLoans.get(prevLoans.size() - 1).getEdiCount() : 0;
//        if (experian.getReason() == null || !experian.getReason().equalsIgnoreCase("ZOMATO_ETC")) {
//            experian.setReason(null);
//        }
//        JsonNode creditBureauResponse = null;
//        ResponseUtil responseUtil;
//        String bureau = null;
//        try {
//            ExperianRawResponse experianRawResponse = experianRawResponseDao.getLatest(merchantBasicDetails.getId());
//            Date reportDate = null;
//            if (experian.getResponse() != null) {
//                responseUtil = getCreditBureauResponse(experian);
//                reportDate = responseUtil.getReportDate();
//                experian.setReportDate(reportDate);
//                bureau = responseUtil.getType();
//            }
//            if (pancard == null && experian.getResponse() != null && reportDate != null && LoanUtil.getDateDiffInDays(reportDate, new Date()) <= 45) {//get experian data from db if less than 45 days old
//                responseUtil = getCreditBureauResponse(experian);
//                creditBureauResponse = objectMapper.readTree(experian.getResponse());
//                bureau = responseUtil.getType();
//            } else if (pancard != null || (reportDate != null && LoanUtil.getDateDiffInDays(reportDate, new Date()) > 45) || (experian.getRetryCount() != null && experian.getRetryCount() > 0) || experianRawResponse == null || LoanUtil.getDateDiffInDays(experianRawResponse.getCreatedAt(), new Date()) > 45) {
//                try {
//                    creditBureauResponse = fetchExperianDetails(merchantBasicDetails.getMobile(), experian, merchantBasicDetails.getId(), bpScore, merchantBankDetail, pancard == null);
//                    experian.setRetryCount(0);
//                } catch (Exception e) {
//                    logger.info("Experian not responding---", e);
//                    experian.setReason(ExperianConstants.TIMEOUT);
//                    experianDao.save(experian);
//                    if (experian.getRetryCount() != null && experian.getRetryCount() == 0) {
//                        logger.info("Experian timeout for merchant: {}, pancard: {}", merchantBasicDetails.getId(), experian.getPancardNumber());
//                        experian.setRetryCount(experian.getRetryCount() + 1);
//                        experianDao.save(experian);
//                        //emailHandler.sendEmail(emails, "Experian APIs failing on PROD", "");
//                        return new ArrayList<>();
//                    } else if (experian.getRetryCount() != null && experian.getRetryCount() == 1) {
//                        creditBureauResponse = fetchExperianDetails(merchantBasicDetails.getMobile(), experian, merchantBasicDetails.getId(), bpScore, merchantBankDetail, pancard == null);
//                    }
//                }
//                if (creditBureauResponse != null) {
//                    logger.info("Found Experian for merchant:{}", merchantBasicDetails.getId());
//                    bureau = "EXPERIAN";
//                } else {
//                    logger.info("Experian not found for merchant:{}, calling crif", merchantBasicDetails.getId());
//                    creditBureauResponse = apiGatewayService.fetchCrifResponse(merchantBasicDetails, experian);
//                    if (creditBureauResponse != null) {
//                        logger.info("Found Crif for merchant:{}", merchantBasicDetails.getId());
//                        bureau = "CRIF";
//                    } else {
//                        logger.info("Bureau not found for merchant:{}", merchantBasicDetails.getId());
//                    }
//                }
//            }
//            if (creditBureauResponse != null) {
//                experian.setResponse(creditBureauResponse.toString());
//                experian.setBureau(bureau);
//            } else {
//                logger.info("bureau response not found for merchant:{}", merchantBasicDetails.getId());
//                experian.setResponse(null);
//                experian.setBureau(null);
//                experian.setReportDate(null);
//                experian.setExperianScore(null);
//                experianDao.save(experian);
//            }
//            responseUtil = getCreditBureauResponse(experian);
//            if (responseUtil.isValid(experian.getPancardNumber(), merchantBasicDetails.getMobile())) {
//                String email = responseUtil.getEmail();
//                Double bureauScore = responseUtil.getBureauScore();
//                if (email != null) experian.setEmail(email);
//                if (bureauScore != null) experian.setExperianScore(bureauScore);
//                experian.setResponse(responseUtil.getResponse());
//                experian.setBureau(responseUtil.getType());
//                experian.setReportDate(responseUtil.getReportDate());
//                experianDao.save(experian);
//            }
//            if (responseUtil.isValid(experian.getPancardNumber(), merchantBasicDetails.getMobile())) {
//                try {
//                    if (!exemptMerchant.contains(merchantBasicDetails.getId()) && responseUtil.isDerog(merchantBasicDetails, isRepeatLoanNoDerog, experian)) {
//                        return new ArrayList<>();
//                    }
//                } catch (Exception e) {
//                    logger.info("Exception while checking derog for merchant: {}", merchantBasicDetails.getId());
//                    logger.error("Exception---", e);
//                }
//                //base checks
//                if (!exemptMerchant.contains(merchantBasicDetails.getId()) && !baseChecks(isZomato, merchantBasicDetails, merchantResponseDTO, experian, lendingType, prevLoans, bpScore, yellowPincode, false, bankCode)) {
//                    logger.info("Base Checks Failed, so rejecting merchant: {}", merchantBasicDetails.getId());
//                    return new ArrayList<>();
//                }
//                return fetchBureauEligibleLoan(responseUtil, merchantBasicDetails.getId(), bpScore, experian, repeatedLoan, avgTpv, isEligibleForConstruct2And3, loanCount, previousLoanDays, lendingApplication, yellowPincode);
//            }
//        } catch (ResourceAccessException e) {
//            logger.info("Experian not responding---", e);
//            logger.info("Experian timeout for merchant: {}, pancard: {}", merchantBasicDetails.getId(), experian.getPancardNumber());
//            experian.setReason(ExperianConstants.TIMEOUT);
//            experian.setRetryCount(experian.getRetryCount() + 1);
//            experianDao.save(experian);
//            emailHandler.sendEmail(emails, "Experian APIs failing on PROD", "Failed for merchant: " + merchantBasicDetails.getId());
//        } catch (Exception e) {
//            experian.setRetryCount(experian.getRetryCount() + 1);
//            experianDao.save(experian);
//            logger.error("Exception while fetching experian details---", e);
//        }
//        logger.info("Experian Report not found for merchant: {}, Calculate NTC...", merchantBasicDetails.getId());
//        //calculate NTC....
//        //base checks
//        if (!exemptMerchant.contains(merchantBasicDetails.getId()) && !baseChecks(isZomato, merchantBasicDetails, merchantResponseDTO, experian, lendingType, prevLoans, bpScore, yellowPincode, true, bankCode)) {
//            logger.info("Base Checks Failed, so rejecting merchant: {}", merchantBasicDetails.getId());
//            return new ArrayList<>();
//        }
//        return calculateNTC(bpScore, merchantBasicDetails.getId(), repeatedLoan, avgTpv, isEligibleForConstruct2And3, experian, loanCount, previousLoanDays, lendingApplication, yellowPincode);
//    }

//    private boolean goToExperianV2(Experian experian, Merchant merchant, String pancard, boolean skip) {
//        if (true) return false;
//        ExperianDetails experianDetails = experianDetailsDao.findByMerchantId(merchant.getId());
//        CrifRequestResponse crifRequestResponse = crifRequestResponseDao.findTop1ByMerchantIdOrderByIdDesc(merchant.getId());
//        if (experianDetails == null || pancard != null || (LoanUtil.getDateDiffInDays(experianDetails.getCreatedAt(), new Date()) > 45)) {
//            logger.info("Experian not found for merchant: {}, going to ExperianV2", merchant.getId());
//            experian.setNoExperian(true);
//            return true;
//        } else if (!skip && experianDetails.getMaskedMobile() != null && !experianDetails.getOtpVerified()) {
//            logger.info("Experian not found for merchant: {}, going to ExperianV2 masked mobile", merchant.getId());
//            experian.setNoExperian(true);
//            String[] mobiles = experianDetails.getMaskedMobile().replaceAll("\\[", "").replaceAll("\\]", "").split(",");
//            List<String> maskedMobiles = new ArrayList<>();
//            Collections.addAll(maskedMobiles, mobiles);
//            experian.setMaskedMobiles(maskedMobiles);
//            return true;
//        } else if (crifRequestResponse != null && crifRequestResponse.getApiName().equalsIgnoreCase("STAGE2") && crifRequestResponse.getResponse() != null) {
//            try {
//                JsonNode crifResponse = objectMapper.readTree(crifRequestResponse.getResponse());
//                if (crifResponse != null && crifResponse.get("status") != null && crifResponse.get("status").asText().equals("S11")) {
//                    logger.info("Crif not found for merchant: {}, going to Crif question", merchant.getId());
//                    experian.setNoExperian(true);
//                    return true;
//                }
//            } catch (Exception e) {
//                logger.error("Exception while parsing crif response", e);
//            }
//        }
//        return false;
//    }

    public ApplicationDerogResponseDTO processDerogSince(Long merchantId, Long applicationId, int daysDiffToCheck) {
        ApplicationDerogResponseDTO responseDTO = new ApplicationDerogResponseDTO();
        Date reportDate = null;
//        Optional<Merchant> merchantOptional = merchantDao.findById(merchantId);

        Optional<BasicDetailsDto> merchantOptional = merchantService.fetchMerchantBasicDetails(merchantId);
        if (!merchantOptional.isPresent()) {
            logger.info("Merchant not found for merchantId: {}", merchantId);
            responseDTO.setMessage("Merchant not found");
            responseDTO.setIsRejected(false);
            responseDTO.setSuccess(false);
            return responseDTO;
        }
        BasicDetailsDto merchant = merchantOptional.get();
        LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchantId(applicationId, merchant.getId());
        if (lendingApplication == null) {
            logger.info("Application not found for applicationId: {}", applicationId);
            responseDTO.setMessage("Application not found");
            responseDTO.setIsRejected(false);
            responseDTO.setSuccess(false);
            return responseDTO;
        }
        Experian experian = experianDao.getByMerchantId(merchantId);
        if (experian == null) {
            logger.info("Experian not found for merchantId: {}", merchantId);
            responseDTO.setMessage("Experian not found");
            responseDTO.setIsRejected(false);
            responseDTO.setSuccess(false);
            return responseDTO;
        }
        JsonNode bureauResponse = null;
        ResponseUtil creditBureauResponseUtil = getCreditBureauResponse(experian);
        if (creditBureauResponseUtil.isValid(experian.getPancardNumber(), merchant.getMobile())) {
            reportDate = creditBureauResponseUtil.getReportDate();
        }
        if (reportDate == null || LoanUtil.getDateDiffInDays(reportDate, new Date()) >= daysDiffToCheck) {
            final Optional<BankDetailsDto> bankDetailsDtoOptional = merchantService.fetchMerchantBankDetails(merchant.getId());
            BankDetailsDto merchantBankDetail = null;
            if (bankDetailsDtoOptional.isPresent())
                merchantBankDetail = bankDetailsDtoOptional.get();
            if (merchantBankDetail == null) {
                logger.info("MerchantBankDetail not found for merchantId: {}", merchantId);
                responseDTO.setMessage("Experian not found");
                responseDTO.setIsRejected(false);
                responseDTO.setSuccess(false);
                return responseDTO;
            }
//            MerchantSummary merchantSummary = merchantSummaryDao.findByMerchantId(merchantId);
            MerchantResponseDTO merchantResponseDTO = merchantSummaryHandler.getMerchantSummary(merchantId);
            if (ObjectUtils.isEmpty(merchantResponseDTO)) {
                throw new MerchantSummaryExceptionHandler(merchantId.toString());
            }
            Double bpScore = (merchantResponseDTO != null && merchantResponseDTO.getBpScore() != null) ? merchantResponseDTO.getBpScore() : 0D;
            bureauResponse = getLatestExperianDetails(merchant.getMobile(), experian, merchant.getId(), bpScore, merchantBankDetail, 3);
            if (bureauResponse != null) {
                experian.setResponse(bureauResponse.toString());
                experian.setBureau(LendingConstants.BUREAU_TYPES.EXPERIAN.name());
            }
            List<LendingPaymentSchedule> prevLoans = lendingPaymentScheduleDao.findPreviousLoansByMerchantAndCreditLoan(merchantId, false);
            boolean isRepeatLoanNoDerog = isRepeatLoanNoDerog(prevLoans,merchantId);
            creditBureauResponseUtil = getCreditBureauResponse(experian);
            if (!creditBureauResponseUtil.isValid(experian.getPancardNumber(), merchant.getMobile())) {
                responseDTO.setMessage("Unable to fetch experian data, please retry!");
                responseDTO.setIsRejected(false);
                responseDTO.setSuccess(true);
                return responseDTO;
            }
            experianDao.save(experian);
            if (isDerogApplication(creditBureauResponseUtil, merchant, experian, isRepeatLoanNoDerog)) {
                loanUtil.auditExperian(experian);
                lendingApplication.setStatus("rejected");
                lendingApplication.setManualCibil("REJECTED");
                lendingApplication.setManualCibilReason("EXPERIAN DEROG FAILED");
                lendingApplication.setCibilApprovedDate(new Date());
                lendingApplicationDao.save(lendingApplication);
                executorService.execute(() -> apiGatewayService.globalLimitTxn(merchantId, "CREDIT", lendingApplication.getLoanAmount()));
                responseDTO.setManualCibil("REJECTED");
                responseDTO.setManualCibilReason("EXPERIAN DEROG FAILED");
                responseDTO.setMessage("Application Derof Failed");
                responseDTO.setIsRejected(true);
                responseDTO.setSuccess(true);
                return responseDTO;
            }
            loanUtil.auditExperian(experian);
        }
        responseDTO.setMessage("Application Derog Passed");
        responseDTO.setIsRejected(false);
        responseDTO.setSuccess(true);
        return responseDTO;
    }

    private JsonNode getLatestExperianDetails(String contact, Experian experian, Long merchantId, Double bpScore, BankDetailsDto merchantBankDetail, int maxExperianRetryCount) {
        int retryCount = 0;
        JsonNode experianResponse = null;
        while (retryCount < maxExperianRetryCount) {
            try {
                experianResponse = fetchExperianDetails(contact, experian, merchantId, bpScore, merchantBankDetail, true);
                break;
            } catch (Exception e) {
                logger.info("Exception occured, sending for retry --- ", e);
                retryCount += 1;
            }
        }
        return experianResponse;
    }

    private boolean isDerogApplication(ResponseUtil responseUtil, BasicDetailsDto merchant, Experian experian, boolean isRepeatLoanNoDerog) {
        try {
            if (!exemptMerchant.contains(merchant.getId()) && responseUtil.isDerog(merchant, isRepeatLoanNoDerog, experian)) {
                return true;
            }
        } catch (Exception e) {
            logger.info("Exception while checking derog for merchant: {}", merchant.getId());
            logger.error("Exception---", e);
        }
        return false;
    }

    private JsonNode parseStringResponse(String response) {
        if (response == null || response.isEmpty()) return null;
        try {
            return objectMapper.readTree(response);
        } catch (Exception e) {
            logger.info("Exception while parsing string response ", e);
            return null;
        }
    }

    private boolean checkOverdue(List<LendingPaymentSchedule> prevLoans, Long merchantId) {
        try {
            if (prevLoans == null || prevLoans.isEmpty()) {
                return false;
            }
            if (prevLoans.get(0) != null && prevLoans.get(0).getLoanAmount() <= 5000D) {
                return false;
            }
            return getOvershootPeriod(prevLoans.get(0)) > 15;
        } catch (Exception ex) {
            logger.error("Error while fetching eligibility for repeat loan no derog", ex);
            return false;
        }
    }

    public boolean checkFraud(MerchantResponseDTO merchantResponseDTO) {
        int selfTxnCount = (merchantResponseDTO != null && merchantResponseDTO.getSelfTxnCount1Mon() != null) ? merchantResponseDTO.getSelfTxnCount1Mon() : 0;
        return (merchantResponseDTO != null && merchantResponseDTO.getUniqueCustomer1mon() != null && (merchantResponseDTO.getUniqueCustomer1mon() - selfTxnCount) < 15)
                || (merchantResponseDTO != null && merchantResponseDTO.getFraudCustomer() != null);
    }

    private LendingPancard fetchNameFromLiquiloans(String pancardNumber, Long merchantId) {
        logger.info("Calling Liquiloan Name Fetch Api for merchant:{}", merchantId);
        String name = null;
        String apiResponse = null;
        try {
            ExternalGatewaySlave externalGateway = externalGatewayDaoSlave.findByGatewayNameAndTypeAndStatus("LIQUILOANS", null, "ACTIVE");
            if (externalGateway != null) {
                Map<String, String> requestParams = new HashMap<>();
                Date currentTime = new Date();
                String payload = pancardNumber + "||" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(currentTime);
                String checksum = lendingHmacCalculator.calculateHMACHexEncoded(payload, aesEncryptionUtil.decrypt(externalGateway.getSecret()));
                logger.info("Liquiloans Checksum:{} for payload: {} for merchant:{}, PAN: {}", checksum, payload, merchantId, pancardNumber);
                requestParams.put("MID", externalGateway.getMbid());
                requestParams.put("Pan", pancardNumber);
                requestParams.put("Timestamp", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(currentTime));
                requestParams.put("Checksum", checksum);
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setCacheControl(CacheControl.noCache());
                HttpEntity<Map<String, String>> request = new HttpEntity<>(requestParams, headers);
                try {
                    long startTime = System.currentTimeMillis();
                    int retry = 0;
                    Map response = null;
                    while (retry < 3) {
                        try {
                            response = restTemplate.postForObject("https://api.liquiloans.com/api/apiintegration/v3/VerifyPanNumber", request, Map.class);
                            if (response != null) {
                                break;
                            }
                        } catch (Exception e) {
                            logger.info("Exception in liquiloans pancard api---", e);
                        }
                        retry++;
                    }
                    logger.info("Liquloans PAN validation API response: {}, response time: {}ms", response, (System.currentTimeMillis() - startTime));
                    if (response != null && response.containsKey("status")) {
                        apiResponse = response.toString();
                        boolean status = (boolean) response.get("status");
                        Map responseDataMap = (Map) response.get("data");
                        String statusCode = (String) responseDataMap.get("status-code");
                        if (status && statusCode.equals("101")) {
                            Map responseResultMap = (Map) responseDataMap.get("result");
                            name = (String) responseResultMap.get("name");
                            logger.info("Liquiloans Set status success for merchant: {}", merchantId);
                        } else {
                            logger.info("Liquiloans Set status failed Response params status : {}, status code: {} for merchant: {}", status, statusCode.equals("101"), merchantId);
                        }
                    } else {
                        logger.info("Liquiloans Set status failed response not contain status for merchant: {}", merchantId);
                    }
                } catch (RestClientException e) {
                    logger.error("RestClient Exception accrue in Liquiloans API calling", e);
                }
            }
        } catch (Exception e) {
            logger.error("Exception while fetching name from liquiloans for merchant: {}", merchantId);
            logger.error("Exception---", e);
        }
        LendingPancard lendingPancard = lendingPancardDao.findByMerchantId(merchantId);
        if (lendingPancard != null) {
            return lendingPancard;
        }
        if (name == null) {
            return null;
        }
        lendingPancardDao.deleteByMerchantId(merchantId);
        return lendingPancardDao.save(new LendingPancard(merchantId, pancardNumber, name, apiResponse));
    }

    public LendingPancard fetchPanName(String pancardNumber, Long merchantId) {
        logger.info("Calling Pan Fetch Api for merchant:{}", merchantId);
        try {
            String name = kycHandler.getPanName(pancardNumber, merchantId);
            if (!ObjectUtils.isEmpty(name)) {
                logger.info("Name:{} found in pancard:{} for merchant:{}", name, pancardNumber, merchantId);
                LendingPancard lendingPancard = lendingPancardDao.findByMerchantId(merchantId);
                if (lendingPancard != null) {
                    lendingPancard.setName(name);
                    lendingPancard.setPancardNumber(pancardNumber);
                    return lendingPancardDao.save(lendingPancard);
                }
                return lendingPancardDao.save(new LendingPancard(merchantId, pancardNumber, name));
            }
        } catch (Exception e) {
            logger.error("Exception in fetchNameFromSignzy for merchant:{}", merchantId, e);
        }
        return null;
    }


    public VerifyPanCardResponseDto verifyPanDetails(VerifyPanCardRequestDto verifyPanCardRequestDto, String token, Long merchantId, VerifyPanCardResponseDto verifyPanCardResponseDto) {
        logger.info("Calling Pan Verify Api for merchant:{}", merchantId);
        try {
            PanVerifyKYCResponseDto responseDto = kycHandler.verifyPanDetails(token, verifyPanCardRequestDto.getPanNumber(), verifyPanCardRequestDto.getFullName(), verifyPanCardRequestDto.getDob(), merchantId);
            if(!ObjectUtils.isEmpty(responseDto))  {
                if (responseDto.getStatus()) {
                    verifyPanCardResponseDto.setIsPanVerified(!ObjectUtils.isEmpty(responseDto.getData().getPanValid()) ? responseDto.getData().getPanValid() : false);
                    verifyPanCardResponseDto.setIsDobVerified(!ObjectUtils.isEmpty(responseDto.getData().getDobMatch()) ? responseDto.getData().getDobMatch() : false);
                    verifyPanCardResponseDto.setIsNameVerified(!ObjectUtils.isEmpty(responseDto.getData().getNameMatch()) ? responseDto.getData().getNameMatch() : false);
                } else {
                    verifyPanCardResponseDto.setStatus(false);
                    verifyPanCardResponseDto.setMessage(responseDto.getData().getMessage());
                }

                if (verifyPanCardResponseDto.getIsPanVerified() && verifyPanCardResponseDto.getIsDobVerified() && verifyPanCardResponseDto.getIsNameVerified()) {
                    LendingPancard lendingPancard = lendingPancardDao.findByMerchantId(merchantId);
                    if (lendingPancard != null) {
                        lendingPancard.setName(responseDto.getData().getPanHolderName());
                        lendingPancard.setPancardNumber(verifyPanCardRequestDto.getPanNumber());
                        lendingPancardDao.save(lendingPancard);
                    } else {
                        lendingPancardDao.save(new LendingPancard(merchantId, verifyPanCardRequestDto.getPanNumber(), responseDto.getData().getPanHolderName()));
                    }
                }
                return verifyPanCardResponseDto;
            }
        }catch (HttpClientErrorException.TooManyRequests e) {
            throw e;
        }catch (Exception e) {
            logger.error("Exception in verifyPanDetails for merchant:{}", merchantId, e);
        }
        return null;
    }



//    private List<LoanEligibilityDTO> fetchBureauEligibleLoan(ResponseUtil responseUtil, Long merchantId, Double bpScore, Experian experian, boolean repeatedLoan, double avgTpv, boolean isEligibleForConstruct2And3, int loanCount, int previousLoanDays, LendingApplication lendingApplication, boolean yellowPincode) {
//        int bureauVintage = responseUtil.fetchBureauVintage();//months
//        String accountCategory = responseUtil.fetchAccountCategory();// A,B,C or NTC
//        if (accountCategory.equals("NTC")) {
//            logger.info("Loan category is NTC for merchant: {}, Calculate NTC...", merchantId);
//            return calculateNTC(bpScore, merchantId, repeatedLoan, avgTpv, isEligibleForConstruct2And3, experian, loanCount, previousLoanDays, lendingApplication, yellowPincode);
//            //calculate NTC....
//        }
//        String segment = calculateSegment(bureauVintage, accountCategory, bpScore);
//        String color;
//        if (ExperianConstants.RED.contains(segment)) {
//            color = ExperianConstants.COLOR.RED.name();
//            experian.setCategory(segment);
//            experian.setColor(color);
//            experian.setReason(ExperianConstants.CATEGORY_RED);
//            experianDao.save(experian);
//            logger.info("Category color RED, so rejecting merchant: {}", merchantId);
//            return new ArrayList<>();
//        } else if (ExperianConstants.AMBER.contains(segment)) {
//            color = ExperianConstants.COLOR.AMBER.name();
//        } else if (ExperianConstants.LIGHT_GREEN.contains(segment)) {
//            color = ExperianConstants.COLOR.LIGHT_GREEN.name();
//        } else {
//            color = ExperianConstants.COLOR.DARK_GREEN.name();
//        }
//        logger.info("Bureau Segment: {}, Color: {} for merchant: {}", segment, color, merchantId);
//        //update segment and color
//        experian.setCategory(segment);
//        experian.setColor(color);
//        experianDao.save(experian);
//        logger.info("Calculating bureau eligible loans for merchant: {}", merchantId);
//        return calculateEligibleLoans(avgTpv, repeatedLoan, color, isEligibleForConstruct2And3, false, previousLoanDays, merchantId, experian.getId(), lendingApplication, yellowPincode);
//    }

//    private List<LoanEligibilityDTO> calculateNTC(Double bpScore, Long merchantId, boolean repeatedLoan, double avgTpv, boolean isEligibleForConstruct2And3, Experian experian, int loanCount, int previousLoanDays, LendingApplication lendingApplication, boolean yellowPincode) {
//        logger.info("Calculating NTC for merchant: {}", merchantId);
//        String segment;
//        String color;
//        if (bpScore <= 15) {
//            segment = "2N";
//            color = ExperianConstants.COLOR.AMBER.name();
//        } else if (bpScore <= 25) {
//            segment = "3N";
//            color = ExperianConstants.COLOR.LIGHT_GREEN.name();
//        } else {
//            segment = "4N";
//            color = ExperianConstants.COLOR.DARK_GREEN.name();
//        }
//        logger.info("NTC Segment: {}, Color: {} for merchant: {}", segment, color, merchantId);
//        //update segment and color
//        experian.setCategory(segment);
//        experian.setColor(color);
//        experianDao.save(experian);
//        return loanCount > 2 ? calculateEligibleLoans(avgTpv, repeatedLoan, color, isEligibleForConstruct2And3, false, previousLoanDays, merchantId, experian.getId(), lendingApplication, yellowPincode) : calculateEligibleLoans(avgTpv, repeatedLoan, color, isEligibleForConstruct2And3, true, previousLoanDays, merchantId, experian.getId(), lendingApplication, yellowPincode);
//    }

//    private List<LoanEligibilityDTO> calculateEligibleLoans(double avgTpv, boolean repeatedLoan, String color, boolean isEligibleForConstruct2And3, boolean isNTC, int previousLoanDays, Long merchantId, Long experianId, LendingApplication lendingApplication, boolean yellowPincode) {
//        logger.info("Calculating offers for merchant: {}", merchantId);
//        String masterCategory = getMasterCategory(color, isNTC, repeatedLoan);
//        logger.info("Master Category for merchant: {} is {}", merchantId, masterCategory);
//        List<LendingCategories> lendingCategories;
//        String type;
//        MerchantSummaryLending merchantSummaryLending = merchantSummaryLendingDao.findByMerchantId(merchantId);
//        String set = (merchantSummaryLending != null && merchantSummaryLending.getSegment() != null) ? merchantSummaryLending.getSegment() : "2";
//        double prevLoanAmount = 0d;
//        if (lendingApplication != null) {
//            switch (color) {
//                case "AMBER":
//                    prevLoanAmount = lendingApplication.getLoanAmount() * 1.1;
//                    break;
//                case "LIGHT_GREEN":
//                    prevLoanAmount = lendingApplication.getLoanAmount() * 1.25;
//                    break;
//                case "DARK_GREEN":
//                    prevLoanAmount = lendingApplication.getLoanAmount() * 1.5;
//                    break;
//            }
//        }
//        if (yellowPincode) {
//            lendingCategories = lendingCategoryDao.findByBureau("OGL");
//            type = null;
//        } else if (isEligibleForConstruct2And3) {
//            List<String> payableConverters = new ArrayList<>();
//            switch (previousLoanDays) {
//                case 26:
//                    payableConverters.add("1+3 Months");
//                    break;
//                case 77:
//                    payableConverters.addAll(Arrays.asList("1+3 Months", "1+6 Months"));
//                    break;
//                default:
//                    payableConverters.addAll(Arrays.asList("1+3 Months", "1+6 Months", "1+12 Months"));
//            }
//            lendingCategories = lendingCategoryDao.getByMasterCategoryForConstruct3(masterCategory, payableConverters);
//            type = "Only Interest";
//        } else {
//            lendingCategories = lendingCategoryDao.getByMasterCategoryForConstruct1(masterCategory);
//            type = null;
//        }
//        if (lendingCategories.isEmpty()) {
//            logger.error("No active lending category found for merchant: {}", merchantId);
//            return new ArrayList<>();
//        } else {
//            String loanType = yellowPincode ? "OGL" : "REGULAR";
//            logger.info("Deleting eligible loans for merchant: {}", merchantId);
//            eligibleLoanDao.deleteByMerchantId(merchantId);
//            List<LoanEligibilityDTO> loanEligibilityDTOList = new ArrayList<>();
//            for (LendingCategories lendingCategory : lendingCategories) {
//                if (lendingCategory.getLoanConstruct() != null && lendingCategory.getLoanConstruct().equalsIgnoreCase("CONSTRUCT_1")) {
//                    if (yellowPincode && ((isNTC && !lendingCategory.getCategory().contains("NTC")) || (!isNTC && !lendingCategory.getCategory().contains("ETC")))) {
//                        continue;
//                    }
//                    LoanEligibilityDTO loanEligibilityDTO = calculateLoanBreakup(lendingCategory, avgTpv, type, merchantId, experianId, prevLoanAmount, color, set, loanType, false, yellowPincode);
//                    if (loanEligibilityDTO != null) {
//                        loanEligibilityDTOList.add(loanEligibilityDTO);
//                    } else {
//                        logger.info("loan offer is null for merchant: {}", merchantId);
//                    }
//                }
//            }
//            if (yellowPincode && loanEligibilityDTOList.isEmpty()) {
//                logger.info("No OGL loan for merchant:{}, fetching 10k loans", merchantId);
//                for (LendingCategories category : lendingCategories) {
//                    if ((isNTC && category.getCategory().contains("NTC")) || (!isNTC && category.getCategory().contains("ETC")) && category.getLoanConstruct().equalsIgnoreCase("CONSTRUCT_1")) {
//                        loanEligibilityDTOList.add(calculateLoanBreakup(category, 0, null, merchantId, experianId, 10000D, color, "2", loanType, false, true));
//                    }
//                }
//            }
//            loanEligibilityDTOList.sort(Comparator.comparing(LoanEligibilityDTO::getAmount, Comparator.reverseOrder()).thenComparing(LoanEligibilityDTO::getEdi));
//            if (!yellowPincode && lendingApplication != null && lendingApplication.getCategory() != null && (loanEligibilityDTOList.isEmpty() || (loanEligibilityDTOList.get(0).getAmount() < prevLoanAmount))) {
//                LendingCategories category = lendingCategoryDao.getByCategory(lendingApplication.getCategory());
//                if (category != null && category.getLoanConstruct() != null && category.getLoanConstruct().equalsIgnoreCase("CONSTRUCT_1")) {
//                    LoanEligibilityDTO loanEligibilityDTO = calculateLoanBreakup(category, 0, type, merchantId, experianId, prevLoanAmount, color, set, loanType, false, yellowPincode);
//                    if (loanEligibilityDTO != null) {
//                        logger.info("loan offer calculated using previous category for merchant: {}", merchantId);
//                        loanEligibilityDTOList.add(loanEligibilityDTO);
//                        loanEligibilityDTOList.sort(Comparator.comparing(LoanEligibilityDTO::getAmount, Comparator.reverseOrder()).thenComparing(LoanEligibilityDTO::getEdi));
//                    } else {
//                        logger.info("loan offer is null for merchant: {}", merchantId);
//                    }
//                }
//            }
//            if (!yellowPincode && !loanEligibilityDTOList.isEmpty()) {
//                try {
//                    LendingApplication ntbLoan = lendingApplicationDao.getPreviousNTBLoan(merchantId);
//                    if (ntbLoan != null && ntbLoan.getLoanAmount() * 1.25 > loanEligibilityDTOList.get(0).getAmount()) {
//                        logger.info("Calculating regular loan using previous NTB loan amount for merchant:{}", merchantId);
//                        LendingCategories categories = lendingCategoryDao.getByCategory(ntbLoan.getCategory());
//                        if (categories != null) {
//                            LoanEligibilityDTO loanEligibilityDTO = calculateLoanBreakup(categories, 0, type, merchantId, experianId, ntbLoan.getLoanAmount() * 1.25, color, set, "NTB", false, false);
//                            if (loanEligibilityDTO != null) {
//                                logger.info("loan offer calculated using previous ntb loan for merchant: {}", merchantId);
//                                loanEligibilityDTOList.add(loanEligibilityDTO);
//                                loanEligibilityDTOList.sort(Comparator.comparing(LoanEligibilityDTO::getAmount, Comparator.reverseOrder()).thenComparing(LoanEligibilityDTO::getEdi));
//                            } else {
//                                logger.info("loan offer is null for merchant: {}", merchantId);
//                            }
//                        }
//                    }
//                } catch (Exception e) {
//                    logger.error("Exception in regular ntb loan", e);
//                }
//            }
//            if (!loanEligibilityDTOList.isEmpty()) {
//                experianDao.updateEligibleAmount(experianId, loanEligibilityDTOList.get(0).getAmount().doubleValue(), loanEligibilityDTOList.get(0).getPrincipleEdiTenure().toString(), loanType);
//            }
//            return loanEligibilityDTOList;
//        }
//    }

//    private String getMasterCategory(String color, boolean isNTC, boolean repeatedLoan) {
//        switch (color) {
//            case "AMBER":
//                if (isNTC) {
//                    if (repeatedLoan) {
//                        return "S4A";
//                    } else {
//                        return "S3A";
//                    }
//                } else {
//                    if (repeatedLoan) {
//                        return "S2A";
//                    } else {
//                        return "S1A";
//                    }
//                }
//            case "LIGHT_GREEN":
//                if (isNTC) {
//                    if (repeatedLoan) {
//                        return "S4LG";
//                    } else {
//                        return "S3LG";
//                    }
//                } else {
//                    if (repeatedLoan) {
//                        return "S2LG";
//                    } else {
//                        return "S1LG";
//                    }
//                }
//            case "DARK_GREEN":
//                if (isNTC) {
//                    if (repeatedLoan) {
//                        return "S4DG";
//                    } else {
//                        return "S3DG";
//                    }
//                } else {
//                    if (repeatedLoan) {
//                        return "S2DG";
//                    } else {
//                        return "S1DG";
//                    }
//                }
//        }
//        return "S4A";
//    }

    public LoanEligibilityDTO calculateLoanBreakup(LendingCategories lendingCategories, double avgTpv, String type, Long merchantId, Long experianId, double prevLoanAmount, String color, String set, String loanType, boolean isZomato, boolean yellowPincode) {
        Optional<BasicDetailsDto> basicDetailsDto = merchantService.fetchMerchantBasicDetails(merchantId);
        if (ObjectUtils.isEmpty(basicDetailsDto)) {
            return null;
        }

        LendingPaymentSchedule previousLoan = lendingPaymentScheduleDao.findByMerchantIdAndStatus(merchantId, "CLOSED");
        Experian experian = experianDao.getByMerchantId(merchantId);
        boolean cpvCity = false;
        if (experian != null && experian.getPincode() != null) {
            PincodeCityStateMappingDTO pincodeCityStateMapping = merchantService.findByPincode(experian.getPincode());
            cpvCity = (pincodeCityStateMapping != null && LendingConstants.CPV_CITIES.contains(pincodeCityStateMapping.getCity()));
        }
        boolean isNTC = isNTC(experian);
//        Merchant merchant = merchantDao.findById(merchantId).get();

        double bureauScore = experian != null && experian.getExperianScore() != null ? experian.getExperianScore() : 0;
        Double percentage = lendingCategories.getMultiplier();
        double interest = "TOPUP".equalsIgnoreCase(loanType) ? 1.75 : lendingCategories.getInterestRate();
//        if ("S4A".equalsIgnoreCase(lendingCategories.getMasterCategory()) || "S4LG".equalsIgnoreCase(lendingCategories.getMasterCategory())) {
//            long dpd = getDPDInLastLoan(merchantId);
//            if (dpd > 5) {
//                interest = 2.75d;
//            }
//        } else if ("S4DG".equalsIgnoreCase(lendingCategories.getMasterCategory())) {
//            long dpd = getDPDInLastLoan(merchantId);
//            if (dpd > 10) {
//                interest = 2.25d;
//            }
//        }
        int tenure = Math.round(lendingCategories.getTenureMonths());
        int ioTenure = Math.round(lendingCategories.getIoTenureMonths());
        logger.info("score:{} for merchant:{}", bureauScore, merchantId);
        Integer maxAmount = bureauScore > 0 && bureauScore < 700 && !yellowPincode ? new Integer(300000) : lendingCategories.getMaxTpvAmount();
        int ioPayableDays = lendingCategories.getIoPayableDays();
        String construct = lendingCategories.getLoanConstruct();
        String category = lendingCategories.getCategory();
        String payableConverter = lendingCategories.getPayableConverter();
        int ioEdiDays = lendingCategories.getIoEdiDays();
        LoanCalculationUtil.LoanBreakupDetail breakup;
        // Capping first loan and Non CPV city
        if (previousLoan == null && !cpvCity) {
            maxAmount = 100000;
        }
        if (isNTC && !cpvCity) {
            maxAmount = 50000;
        }
        if (avgTpv == 0 && prevLoanAmount > 0) {
            if ("NTB".equalsIgnoreCase(loanType)) {
                maxAmount = 100000;
            } else if (!(previousLoan == null && !cpvCity)) {

                maxAmount = bureauScore > 0 && bureauScore < 700 && !yellowPincode ? 300000 : LendingConstants.MAX_LOAN_AMOUNT_INTEGER;
            }
            if (previousLoan != null && prevLoanAmount > previousLoan.getLoanAmount() && prevLoanAmount > 2.5 * previousLoan.getLoanAmount() && !yellowPincode && !"NTB".equalsIgnoreCase(loanType)) {
                maxAmount = Double.valueOf(2.5 * previousLoan.getLoanAmount()).intValue();
            }
            if (isNTC && !cpvCity) {
                maxAmount = 50000;
            }
            prevLoanAmount = Math.min(roundUp(prevLoanAmount), maxAmount);
            if (prevLoanAmount > 35000 && isNTC && basicDetailsDto.get().getBussinessCategory() != null && LendingConstants.FOOD_BEVERAGES.contains(basicDetailsDto.get().getBussinessCategory())) {
                prevLoanAmount = 35000 + ((prevLoanAmount - 35000) / 2);
            }
            AvailableLoan availableLoan = new AvailableLoan();
            availableLoan.setAmount(prevLoanAmount);
            breakup = LoanCalculationUtil.getLoanBreakup(availableLoan, lendingCategories, loanType);
        } else {
            breakup = getBreakup(tenure, construct, type, avgTpv, percentage, interest, maxAmount, ioTenure, ioPayableDays, lendingCategories, isNTC, basicDetailsDto.get(), previousLoan);
        }
        if (!isZomato) {
            if (color != null && color.equalsIgnoreCase("AMBER") && breakup.getLoanAmount() < 20000 && !"NTB".equalsIgnoreCase(loanType) && !"OGL".equalsIgnoreCase(loanType) && !"BHARAT_SWIPE".equalsIgnoreCase(loanType)) {
                logger.info("loan amount is less than 20000 for merchant: {}", merchantId);
                return null;
            } else if (breakup.getLoanAmount() < 10000) {
                logger.info("loan amount is less than 10000 for merchant: {}", merchantId);
                return null;
            }
        }
        logger.info("saving eligible loan for merchant: {}", merchantId);
        EligibleLoan eligibleLoan = eligibleLoanDao.save(new EligibleLoan(merchantId, experianId, (double) breakup.getLoanAmount(), payableConverter, "ACTIVE", category, ioEdiDays, 0, avgTpv, breakup.getEdi(), breakup.getIoEdi(), breakup.getRepayment(), construct, loanType, null));
        logger.info("eligible loan for merchant: {} is-- {}", merchantId, eligibleLoan.toString());
        eligibleLoanAuditDao.save(EligibleLoanAudit.createObject(eligibleLoan));
        return createLoanEligibilityDTO(breakup, payableConverter, lendingCategories, loanType);
    }

    private LoanCalculationUtil.LoanBreakupDetail getBreakup(int tenureMonth, String construct, String type, double avgTpv, double percentage, double interest, int maxAmount, int ioTenure, int ioPayableDays, LendingCategories categories, boolean isNTC, BasicDetailsDto merchant, LendingPaymentSchedule previousLoan) {
        int tenure = tenureMonth - ioTenure;
        int ediDays, disbursementAmount, ioInterestAmount, principleEdiTenure, repayment;
        double loanAmount, edi, totalInterestAmount, ioEdi;
        ediDays = getEdiDays(tenure);
        edi = (avgTpv * percentage);
        repayment = (int) Math.round(ediDays * edi);
        loanAmount = Math.min(roundUp(repayment / (1 + (interest / 100) * tenure)), maxAmount);// round down
        if (loanAmount > 35000 && isNTC && merchant.getBussinessCategory() != null && LendingConstants.FOOD_BEVERAGES.contains(merchant.getBussinessCategory())) {
            loanAmount = 35000 + ((loanAmount - 35000) / 2);
        }
        if (previousLoan != null && loanAmount > previousLoan.getLoanAmount() && loanAmount > 2.5 * previousLoan.getLoanAmount()) {
            loanAmount = 2.5 * previousLoan.getLoanAmount();
        }
        int processingFee = LoanCalculationUtil.getProcessingFee(loanAmount, categories);
        edi = Math.ceil((loanAmount * (1 + (interest / 100) * tenure)) / ediDays);
        disbursementAmount = (int) loanAmount - processingFee;
        ioEdi = ioPayableDays > 0 ? Math.ceil((loanAmount * (interest / 100)) / ioPayableDays) : 0;
        ioInterestAmount = (int) (ioEdi * ioPayableDays);
        repayment = (int) Math.round((edi * ediDays) + ioInterestAmount);
        totalInterestAmount = repayment - loanAmount;
        principleEdiTenure = tenure;

        return new LoanCalculationUtil.LoanBreakupDetail(construct, (int) edi, (int) ioEdi, processingFee, ioInterestAmount, (int) totalInterestAmount, (int) totalInterestAmount,
                ioTenure, principleEdiTenure, repayment, disbursementAmount, type, (int) loanAmount, interest);
    }

    private double roundUp(double loanAmount) {
        if (loanAmount < 20000) {
            return (Math.ceil(loanAmount / 1000.0) * 1000);
        } else if (loanAmount < 100000) {
            return (Math.ceil(loanAmount / 5000.0) * 5000);
        } else {
            return (Math.ceil(loanAmount / 10000.0) * 10000);
        }
    }

    private LoanEligibilityDTO createLoanEligibilityDTO(LoanCalculationUtil.LoanBreakupDetail breakup, String tenure, LendingCategories category, String loanType) {
        LoanEligibilityDTO loanEligibilityDTO = new LoanEligibilityDTO();
        loanEligibilityDTO.setProcessingFee(breakup.getProcessingFee());
        loanEligibilityDTO.setInterestRate(breakup.getEffectiveInterestRate());
        loanEligibilityDTO.setAmount(breakup.getLoanAmount());
        loanEligibilityDTO.setCategory(category.getCategory());
        loanEligibilityDTO.setInterestAmount(breakup.getTotalInterestAmount());
        loanEligibilityDTO.setEdi(breakup.getEdi());
        loanEligibilityDTO.setRepayment(breakup.getRepayment());
        loanEligibilityDTO.setDisbursementAmount(breakup.getDisbursementAmount());
        loanEligibilityDTO.setEdiCount(category.getPayableDays());
        loanEligibilityDTO.setConstruct(category.getLoanConstruct());
        loanEligibilityDTO.setTenure(tenure);
        loanEligibilityDTO.setConstruct(breakup.getConstruct());
        loanEligibilityDTO.setList(LoanCalculationUtil.prepareLabels(breakup, breakup.getIoOrFreeEdiTenure()));
        loanEligibilityDTO.setType(breakup.getType());
        loanEligibilityDTO.setOptionEnable(true);
        loanEligibilityDTO.setPrincipleEdiTenure(breakup.getPrincipleEdiTenure());
        loanEligibilityDTO.setLoanType(loanType);
        return loanEligibilityDTO;
    }

    private int getEdiDays(int tenure) {
        switch (tenure) {
            case 1:
                return 26;
            case 3:
                return 77;
            case 6:
                return 155;
            case 9:
                return 234;
            case 12:
                return 311;
            default:
                return 388;//15 months
        }
    }


    public boolean isEligibleForConstruct2And3(MerchantSummary summary, List<LendingPaymentSchedule> prevLoans) {
        try {
            if (prevLoans == null || prevLoans.isEmpty()) {
                return false;
            }
            if (isAnyHighTPVLoan(prevLoans)) {
                return false;
            }
            if (summary == null || summary.getTxnDayCount1Mon() == null || summary.getTxnDayCount1Mon() < 15) {
                return false;
            }
            return getOvershootPeriod(prevLoans.get(0)) <= 5;
        } catch (Exception ex) {
            logger.error("Error while fetching eligiblity for construct 2 and 3", ex);
            return false;
        }
    }

    public boolean isRepeatLoanNoDerog(List<LendingPaymentSchedule> prevLoans, Long merchantId) {
        try {
            if (prevLoans == null || prevLoans.isEmpty()) {
                return false;
            }
            if (prevLoans.get(0) != null && prevLoans.get(0).getLoanAmount() < 5000D) {
                return false;
            }
            return getOvershootPeriod(prevLoans.get(0)) <= 15;
        } catch (Exception ex) {
            logger.error("Error while fetching eligibility for repeat loan no derog", ex);
            return false;
        }
    }

    private boolean isAnyHighTPVLoan(List<LendingPaymentSchedule> lendingPaymentScheduleList) {
        if (lendingPaymentScheduleList == null || lendingPaymentScheduleList.isEmpty()) {
            return false;
        }
        for (LendingPaymentSchedule lendingPaymentSchedule : lendingPaymentScheduleList) {
            if (Loan.Category.HIGHTPV.toString().equals(lendingPaymentSchedule.getLoanType())) {
                return true;
            }
        }
        return false;
    }

    private long getOvershootPeriod(LendingPaymentSchedule lendingPaymentSchedule) {
        if (lendingPaymentSchedule == null) {
            return 0;
        }
        LendingLedger lastEDI = lendingLedgerDao.findLastEDIDueEntryByMerchantAndLoan(lendingPaymentSchedule.getMerchantId(), lendingPaymentSchedule.getId());
        LendingLedgerSlave lastPayment = lendingLedgerSlaveDao.findLastPaymentEntryByMerchantAndLoan(lendingPaymentSchedule.getMerchantId(), lendingPaymentSchedule.getId());
        if (lastEDI == null || lastPayment == null) {
            return 0;
        }
        return LoanUtil.getDateDiffInDays(lastEDI.getDate(), lastPayment.getDate());
    }

    private long getDPDInLastLoan(Long merchantId) {
        LendingPaymentSchedule lastLoan = lendingPaymentScheduleDao.findTop1ByMerchantIdAndStatusAndCreditLoanOrderByIdDesc(merchantId, "CLOSED", false);
        return getOvershootPeriod(lastLoan);
    }

    public String calculateSegment(int bureauVintage, String accountCategory, Double bpScore) {
        int col;
        int row;
        String[][] m1 = {{"1", "5", "9"}, {"2", "6", "10"}, {"3", "7", "11"}, {"4", "8", "12"}};
        String[][] m2 = {{"13", "17", "21"}, {"14", "18", "22"}, {"15", "19", "23"}, {"16", "20", "24"}};
        String[][] m3 = {{"25", "29", "33"}, {"26", "30", "34"}, {"27", "31", "35"}, {"28", "32", "36"}};
        String[][] m4 = {{"37", "41", "45"}, {"38", "42", "46"}, {"39", "43", "47"}, {"40", "44", "48"}};
        switch (accountCategory) {
            case "B":
                col = 1;
                break;
            case "C":
                col = 2;
                break;
            default:
                col = 0;// "A"
        }
        if (bpScore <= 15) {
            row = 0;
        } else if (bpScore < 20) {
            row = 1;
        } else if (bpScore <= 25) {
            row = 2;
        } else {
            row = 3;
        }
        if (bureauVintage < 6) {
            return m1[row][col];
        } else if (bureauVintage <= 12) {
            return m2[row][col];
        } else if (bureauVintage <= 24) {
            return m3[row][col];
        } else {
            return m4[row][col];
        }
    }

    private boolean validatePancard(JsonNode experianResponse, String panCard, Long merchantId, Experian experian) {
        if (experianResponse.get("INProfileResponse").get("Current_Application").get("Current_Application_Details") != null && experianResponse.get("INProfileResponse").get("Current_Application").get("Current_Application_Details").get("Current_Applicant_Details") != null) {
            String email = experianResponse.get("INProfileResponse").get("Current_Application").get("Current_Application_Details").get("Current_Applicant_Details").get("EMailId").asText();
            experian.setEmail(email);
        }
        if (experianResponse.get("INProfileResponse").get("SCORE").get("BureauScore") != null) {
            experian.setExperianScore(experianResponse.get("INProfileResponse").get("SCORE").get("BureauScore").doubleValue());
        }
        if (experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS") != null && experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS").isArray()) {
            for (JsonNode jsonNode : experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS")) {
                if (jsonNode.get("CAIS_Holder_Details") != null && jsonNode.get("CAIS_Holder_Details").isObject()) {
                    if (jsonNode.get("CAIS_Holder_Details").get("Income_TAX_PAN") != null && jsonNode.get("CAIS_Holder_Details").get("Income_TAX_PAN").asText().equalsIgnoreCase(panCard)) {
                        String merchantName = jsonNode.get("CAIS_Holder_Details").get("First_Name_Non_Normalized").asText() + " " + jsonNode.get("CAIS_Holder_Details").get("Middle_Name_1_Non_Normalized").asText() + " " + jsonNode.get("CAIS_Holder_Details").get("Surname_Non_Normalized").asText();
                        experian.setMerchantName(merchantName);
                        experianDao.save(experian);
                        return true;
                    }
                } else if (jsonNode.get("CAIS_Holder_Details") != null && jsonNode.get("CAIS_Holder_Details").isArray()) {
                    for (JsonNode node : jsonNode.get("CAIS_Holder_Details")) {
                        if (node.get("Income_TAX_PAN") != null && node.get("Income_TAX_PAN").asText().equalsIgnoreCase(panCard)) {
                            String merchantName = node.get("First_Name_Non_Normalized").asText() + " " + node.get("Middle_Name_1_Non_Normalized").asText() + " " + node.get("Surname_Non_Normalized").asText();
                            experian.setMerchantName(merchantName);
                            experianDao.save(experian);
                            return true;
                        }
                    }
                }
            }
        } else if (experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS") != null && experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS").isObject()) {
            JsonNode jsonNode = experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS");
            if (jsonNode.get("CAIS_Holder_Details") != null && jsonNode.get("CAIS_Holder_Details").isObject()) {
                if (jsonNode.get("CAIS_Holder_Details").get("Income_TAX_PAN") != null && jsonNode.get("CAIS_Holder_Details").get("Income_TAX_PAN").asText().equalsIgnoreCase(panCard)) {
                    String merchantName = jsonNode.get("CAIS_Holder_Details").get("First_Name_Non_Normalized").asText() + " " + jsonNode.get("CAIS_Holder_Details").get("Middle_Name_1_Non_Normalized").asText() + " " + jsonNode.get("CAIS_Holder_Details").get("Surname_Non_Normalized").asText();
                    experian.setMerchantName(merchantName);
                    experianDao.save(experian);
                    return true;
                }
            } else if (jsonNode.get("CAIS_Holder_Details") != null && jsonNode.get("CAIS_Holder_Details").isArray()) {
                for (JsonNode node : jsonNode.get("CAIS_Holder_Details")) {
                    if (node.get("Income_TAX_PAN") != null && node.get("Income_TAX_PAN").asText().equalsIgnoreCase(panCard)) {
                        String merchantName = node.get("First_Name_Non_Normalized").asText() + " " + node.get("Middle_Name_1_Non_Normalized").asText() + " " + node.get("Surname_Non_Normalized").asText();
                        experian.setMerchantName(merchantName);
                        experianDao.save(experian);
                        return true;
                    }
                }
            }
        }
        logger.info("Pancard not matched with experian for merchant: {}", merchantId);
        experian.setReason(ExperianConstants.INVALID_PANCARD);
        experianDao.save(experian);
        return false;
    }


    public JsonNode fetchExperianDetails(String contact, Experian experian, Long merchantId, Double bpScore, BankDetailsDto merchantBankDetail, boolean callRefreshApi) {
        JsonNode refreshResponse = null;
        if (experian.getHitId() != null && callRefreshApi) {
            refreshResponse = apiGatewayService.experianRefreshApi(merchantId, experian.getHitId());
        }
        if (refreshResponse != null) {
            return refreshResponse;
        }
        LendingPancard lendingPancard = lendingPancardDao.findByMerchantId(merchantId);
        if (lendingPancard == null || lendingPancard.getName() == null || !lendingPancard.getPancardNumber().equalsIgnoreCase(experian.getPancardNumber())) {// get data from signzy
            try {
                lendingPancard = fetchPanName(experian.getPancardNumber(), merchantId);
            } catch (Exception e) {
                logger.error("Exception in Signzy pan fetch API---", e);
            }
        }
        String firstName;
        String lastName;
        if (lendingPancard != null && lendingPancard.getName() != null && !lendingPancard.getName().trim().equalsIgnoreCase("")) {
            firstName = getFirstName(lendingPancard.getName());
            lastName = getLastName(lendingPancard.getName());
        } else {
            firstName = getFirstName(merchantBankDetail.getBeneficiaryName());
            lastName = getLastName(merchantBankDetail.getBeneficiaryName());
        }
        if (contact.length() > 10) {
            contact = contact.substring(2);//remove 91
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        setExperianApiParams(body, firstName, lastName, contact, experian.getPancardNumber());
        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
        Long a = DateTime.now().getMillis();
        logger.info("Experian request for merchant: {} is {}", merchantId, body);
        String response = restTemplate.postForObject(ExperianConstants.SHORT_API_URL, request, String.class);
        Long b = DateTime.now().getMillis();
        logger.info("Experian Short API response time---{}ms", (b - a));
        try {
            JsonNode jsonNode = objectMapper.readTree(response);
            if (jsonNode == null || jsonNode.get("showHtmlReportForCreditReport").isNull()) {
                experianService.insertExperianCallRecord(null, "SHORT_API_URL", objectMapper.writeValueAsString(request), merchantId, bpScore, experian.getPancardNumber(), contact);
                return null;
            }
            String xmlResponse = jsonNode.get("showHtmlReportForCreditReport").asText().replaceAll("&amp;", "&").replaceAll("&gt;", ">").replaceAll("&lt;", "<").replaceAll("&quot;", "\"");
            JSONObject jsonObject = XML.toJSONObject(xmlResponse);
            experian.setHitId(jsonNode.get("stageOneId_").asText());
            experianService.insertExperianCallRecord(objectMapper.readTree(jsonObject.toString()).toString(), "SHORT_API_URL", objectMapper.writeValueAsString(request), merchantId, bpScore, experian.getPancardNumber(), contact);
            return objectMapper.readTree(jsonObject.toString());
        } catch (Exception e) {
            logger.info("Exception while parsing experian response", e);
            return null;
        }
    }

    private void setExperianApiParams(MultiValueMap<String, Object> body, String firstName, String lastName, String contact, String panCard) {
        body.add("clientName", ExperianConstants.CLIENT_NAME);
        body.add("allowInput", "1");
        body.add("allowEdit", "1");
        body.add("allowCaptcha", "1");
        body.add("allowConsent", "1");
        body.add("allowEmailVerify", "1");
        body.add("allowVoucher", "1");
        body.add("voucherCode", ExperianConstants.VOUCHER_CODE);
        body.add("firstName", firstName);
        body.add("surName", lastName);
        body.add("mobileNo", contact);
        body.add("noValidationByPass", "0");
        body.add("emailConditionalByPass", "1");
        body.add("pan", panCard);
    }

    public String getFirstName(String name) {
        if (name == null) {
            return "";
        }
        int lastIndexOfSpace = name.lastIndexOf(" ");
        if (lastIndexOfSpace != -1) {
            return name.substring(0, lastIndexOfSpace);
        } else {
            lastIndexOfSpace = name.lastIndexOf(".");
            if (lastIndexOfSpace != -1) {
                return name.substring(0, lastIndexOfSpace);
            } else {
                return name;
            }
        }
    }

    public String getLastName(String name) {
        if (name == null) {
            return "";
        }
        int lastIndexOfSpace = name.lastIndexOf(" ");
        if (lastIndexOfSpace != -1) {
            return name.substring(lastIndexOfSpace + 1);
        } else {
            lastIndexOfSpace = name.lastIndexOf(".");
            if (lastIndexOfSpace != -1) {
                return name.substring(lastIndexOfSpace + 1);
            } else {
                return name;
            }
        }
    }

    public boolean isNTC(Experian experian) {
        if (experian == null || experian.getCategory() == null) {
            return true;
        }
        if (experian.getReason() != null && experian.getReason().equalsIgnoreCase("ZOMATO_ETC")) {
            return false;
        }
        List<String> ntcCategories = Arrays.asList("1N", "2N", "3N", "4N");
        return ntcCategories.contains(experian.getCategory());
    }

//    private boolean baseChecks(boolean isZomato, BasicDetailsDto merchant, MerchantResponseDTO merchantResponseDTO, Experian experian, String lendingType, List<LendingPaymentSchedule> prevLoans, double bpScore, boolean yellowPincode, boolean isNTC, String bankCode) {
//        if (yellowPincode) {
//            if (bankCode == null) {
//                logger.info("Non enachable bank code, so rejecting ogl loan for merchant: {}", experian.getMerchantId());
//                experian.setCategory("1N");
//                experian.setColor(ExperianConstants.COLOR.RED.name());
//                experian.setReason(ExperianConstants.ENACH);
//                experianDao.save(experian);
//                return false;
//            }
//            if ((isNTC && merchantResponseDTO != null && merchantResponseDTO.getBpScore() != null && merchantResponseDTO.getBpScore() < 15) || (!isNTC && merchantResponseDTO != null && merchantResponseDTO.getBpScore() != null && merchantResponseDTO.getBpScore() < 13)) {
//                logger.info("Low bp score, so rejecting ogl loan for merchant: {}", experian.getMerchantId());
//                experian.setCategory("1N");
//                experian.setColor(ExperianConstants.COLOR.RED.name());
//                experian.setReason(ExperianConstants.LOW_BP_SCORE);
//                experianDao.save(experian);
//                return false;
//            }
//            if (merchant.getBussinessCategory() == null || "Food_and_Drink".equalsIgnoreCase(merchant.getBussinessCategory())) {
//                logger.info("F&B category, so rejecting ogl loan for merchant: {}", experian.getMerchantId());
//                experian.setCategory("1N");
//                experian.setColor(ExperianConstants.COLOR.RED.name());
//                experian.setReason(ExperianConstants.BUSINESS_CATEGORY);
//                experianDao.save(experian);
//                return false;
//            }
//        }
//        if (!isZomato && checkFraud(merchantResponseDTO)) {
//            logger.info("Fraud Merchant, so rejecting merchant: {}", merchant.getId());
//            experian.setCategory("1N");
//            experian.setColor(ExperianConstants.COLOR.RED.name());
//            experian.setReason(ExperianConstants.FRAUD);
//            experianDao.save(experian);
//            return false;
//        }
//        if (!isZomato && !exemptMerchant.contains(merchant.getId()) && checkOverdue(prevLoans, merchantResponseDTO.getMerchantId())) {
//            logger.info("Overdue Merchant, so rejecting merchant: {}", merchant.getId());
//            experian.setCategory("1N");
//            experian.setColor(ExperianConstants.COLOR.RED.name());
//            experian.setReason(ExperianConstants.OVERDUE);
//            experian.setRejected(true);
//            experian.setRejectedDate(new Date());
//            experianDao.save(experian);
//            return false;
//        }
//        if (lendingType.equalsIgnoreCase("CREDITLINE")) {
//            if (bpScore <= 12D) {
//                logger.info("BP Score less than 12, so rejecting merchant: {}", merchant.getId());
//                experian.setCategory("1N");
//                experian.setColor(ExperianConstants.COLOR.RED.name());
//                experian.setReason(ExperianConstants.LOW_BP_SCORE);
//                experianDao.save(experian);
//                return false;
//            }
//        } else {
//            if (!isZomato && !yellowPincode && bpScore < 9D) {
//                logger.info("BP Score less than 9, so rejecting merchant: {}", merchant.getId());
//                experian.setCategory("1N");
//                experian.setColor(ExperianConstants.COLOR.RED.name());
//                experian.setReason(ExperianConstants.LOW_BP_SCORE);
//                experianDao.save(experian);
//                return false;
//            }
//        }
//        if (!isZomato) {
//            PaymentTransactionNewSlave firstTransaction = paymentTransactionNewDaoSlave.getFirstTransaction(merchant.getId());
//            BharatSwipeAccountSlave bharatSwipeAccount = bharatSwipeAccountDaoSlave.findByMerchantId(merchant.getId());
//            int vintageDays = bharatSwipeAccount != null ? 30 : 60;
//            if (firstTransaction == null || LoanUtil.getDateDiffInDays(firstTransaction.getCreatedAt(), new Date()) < vintageDays) {
//                logger.info("Vintage less than 60 days, so rejecting merchant: {}", merchant.getId());
//                experian.setCategory("1N");
//                experian.setColor(ExperianConstants.COLOR.RED.name());
//                experian.setReason(ExperianConstants.VINTAGE);
//                experianDao.save(experian);
//                return false;
//            }
//        }
//        return true;
//    }

    public ResponseUtil getCreditBureauResponse(Experian experian) {
        JsonNode bureauResponse = null;
        if (experian != null) {
            bureauResponse = parseStringResponse(experian.getResponse());
            if (experian.getBureau() != null && experian.getBureau().equalsIgnoreCase("crif")) {
                return new CrifResponseUtil(bureauResponse, experianDao, lendingMerchantDropoffDao);
            } else {
                return new ExperianResponseUtil(bureauResponse, experianDao, lendingMerchantDropoffDao);
            }
        }
        return new ExperianResponseUtil(bureauResponse, experianDao, lendingMerchantDropoffDao);
    }
}
