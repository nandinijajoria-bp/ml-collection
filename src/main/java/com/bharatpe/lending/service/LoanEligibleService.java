package com.bharatpe.lending.service;

import com.bharatpe.common.dao.*;
import com.bharatpe.common.entities.*;
import com.bharatpe.common.enums.Loan;
import com.bharatpe.common.handlers.EmailHandler;
import com.bharatpe.common.utils.AesEncryption;
import com.bharatpe.common.utils.HmacCalculator;
import com.bharatpe.lending.common.dao.ExperianRawResponseDao;
import com.bharatpe.lending.common.entity.ExperianRawResponse;
import com.bharatpe.lending.constant.ExperianConstants;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingCategoryDao;
import com.bharatpe.lending.dao.LendingLedgerDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dto.LoanEligibilityDTO;
import com.bharatpe.lending.util.LoanCalculationUtil;
import com.bharatpe.lending.util.LoanUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.joda.time.DateTime;
import org.joda.time.Months;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.json.JSONObject;
import org.json.XML;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class LoanEligibleService {

    List<Integer> derogAccountStatus = Arrays.asList(93,89,93,97,97,97,97,30,31,32,33,35,37,38,39,41,42,43,44,45,47,49,50,51,53,54,55,56,57,58,59,60,61,62,63,64,65,66,67,68,69,70,72,73,74,75,76,77,79,81,85,86,87,88,94,90,91);
    List<Integer> derogUnsecuredProducts = Arrays.asList(5,10,36,37,38,39,43,51,52,53,54,55,56,57,58,60,61);
    List<String> emails = Arrays.asList("rajat.jain@bharatpe.com", "khushal.virmani@bharatpe.com", "puneet.arora@bharatpe.com");
    List<Long> exemptMerchant = Arrays.asList(2368388L);

    private Logger logger = LoggerFactory.getLogger(LoanEligibleService.class);

    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;

    @Autowired
    RestTemplate restTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    ExperianDao experianDao;

    @Autowired
    ExperianAuditTrailDao experianAuditTrailDao;

    @Autowired
    LendingLedgerDao lendingLedgerDao;

    @Autowired
    EmailHandler emailHandler;

    @Autowired
    LendingCategoryDao lendingCategoryDao;

    @Autowired
    EligibleLoanDao eligibleLoanDao;

    @Autowired
    LendingPancardDao lendingPancardDao;

    @Autowired
    ExternalGatewayDao externalGatewayDao;

    @Autowired
    AesEncryption aesEncryption;

    @Autowired
    HmacCalculator hmacCalculator;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    ExperianDetailsDao experianDetailsDao;

    @Autowired
    MerchantSummaryLendingDao merchantSummaryLendingDao;

    @Autowired
    EligibleLoanAuditDao eligibleLoanAuditDao;

    @Autowired
    PaymentTransactionNewDao paymentTransactionNewDao;
    
    @Autowired
    ExperianService experianService;

    @Autowired
    ExperianRawResponseDao experianRawResponseDao;

    @Autowired
    APIGatewayService apiGatewayService;

    SimpleDateFormat experianFormat = new SimpleDateFormat("yyyyMMdd");

    public List<LoanEligibilityDTO> getNewLoanDetails(Merchant merchant, Experian experian, MerchantSummary merchantSummary, MerchantBankDetail merchantBankDetail, boolean skip, String pancard, MerchantSummaryLending merchantSummaryLending, boolean isZomato, String lendingType, boolean yellowPincode){
        Double bpScore = (merchantSummary != null && merchantSummary.getBpScore() != null) ? merchantSummary.getBpScore() : 0D;
        double selfTpv = (merchantSummary != null && merchantSummary.getSelfTxnValue1Mon() != null) ? merchantSummary.getSelfTxnValue1Mon() : 0d;
        double tpvLast30Days = (merchantSummary != null && merchantSummary.getTpv1Mon() != null) ? merchantSummary.getTpv1Mon() - selfTpv : 0D;
        int txnLast30Days = 30;
        double avgTpv = tpvLast30Days/txnLast30Days;
        List<LendingPaymentSchedule> prevLoans;
        if(lendingType.equalsIgnoreCase("CREDITLINE")) {
        	prevLoans = lendingPaymentScheduleDao.findPreviousLoansByMerchantAndCreditLoan(merchant.getId(),true);
        }
        else {
        	prevLoans = lendingPaymentScheduleDao.findPreviousLoansByMerchantAndCreditLoan(merchant.getId(),false);
        }
        int loanCount = (prevLoans == null || prevLoans.isEmpty()) ? 0 : prevLoans.size();
        boolean repeatedLoan = loanCount > 0;
        if (skip) {
            experian.setSkip(true);
            experianDao.save(experian);
        }
        boolean isEligibleForConstruct2And3 = false;
//        boolean isEligibleForConstruct2And3 = isEligibleForConstruct2And3(merchantSummary, prevLoans);
        boolean isRepeatLoanNoDerog = isRepeatLoanNoDerog(prevLoans);
        LendingApplication lendingApplication = null;
        if (isRepeatLoanNoDerog && prevLoans != null) {
            lendingApplication = lendingApplicationDao.findByIdAndMerchant(prevLoans.get(0).getApplicationId(), merchant);
        }
        int previousLoanDays = (prevLoans != null && !prevLoans.isEmpty()) ? prevLoans.get(prevLoans.size() - 1).getEdiCount() : 0;
        if (experian.getReason() == null || !experian.getReason().equalsIgnoreCase("ZOMATO_ETC")) {
            experian.setReason(null);
        }
        JsonNode experianResponse = null;
        try {
            ExperianRawResponse experianRawResponse = experianRawResponseDao.getLatest(merchant.getId());
            Date reportDate = null;
            if (experian.getResponse() != null) {
                reportDate = experianFormat.parse(objectMapper.readTree(experian.getResponse()).get("INProfileResponse").get("CreditProfileHeader").get("ReportDate").asText());
            }
            if (experian.getResponse() != null && reportDate != null && LoanUtil.getDateDiffInDays(reportDate, new Date()) <= 45) {//get experian data from db if less than 45 days old
                experianResponse = objectMapper.readTree(experian.getResponse());
            } else if ((reportDate != null && LoanUtil.getDateDiffInDays(reportDate, new Date()) > 45) || (experian.getRetryCount() != null && experian.getRetryCount() > 0) || experianRawResponse == null || LoanUtil.getDateDiffInDays(experianRawResponse.getCreatedAt(), new Date()) > 45) {
                try {
                    experianResponse = fetchExperianDetails(merchant.getMobile(), experian.getPancardNumber(), merchant.getId(), bpScore, merchantBankDetail);
                    experian.setRetryCount(0);
                } catch (ResourceAccessException e) {
                    logger.info("Experian not responding---", e);
                    experian.setReason(ExperianConstants.TIMEOUT);
                    experianDao.save(experian);
                    if (experian.getRetryCount() != null && experian.getRetryCount() == 0) {
                        logger.info("Experian timeout for merchant: {}, pancard: {}", merchant.getId(), experian.getPancardNumber());
                        experian.setRetryCount(experian.getRetryCount() + 1);
                        experianDao.save(experian);
                        //emailHandler.sendEmail(emails, "Experian APIs failing on PROD", "");
                        return new ArrayList<>();
                    } else if (experian.getRetryCount() != null && experian.getRetryCount() == 1) {
                        experianResponse = fetchExperianDetails(merchant.getMobile(), experian.getPancardNumber(), merchant.getId(), bpScore, merchantBankDetail);
                    }
                }
            }
            ExperianDetails experianDetails = experianDetailsDao.findByMerchantId(merchant.getId());
            if (experianResponse != null){
                if (experianResponse.get("INProfileResponse").get("Current_Application").get("Current_Application_Details") != null && experianResponse.get("INProfileResponse").get("Current_Application").get("Current_Application_Details").get("Current_Applicant_Details") != null) {
                    String email = experianResponse.get("INProfileResponse").get("Current_Application").get("Current_Application_Details").get("Current_Applicant_Details").get("EMailId").textValue();
                    experian.setEmail(email);
                }
                if (experianResponse.get("INProfileResponse").get("SCORE").get("BureauScore") != null) {
                    experian.setExperianScore(experianResponse.get("INProfileResponse").get("SCORE").get("BureauScore").doubleValue());
                }
                experian.setResponse(experianResponse.toString());
                experianDao.save(experian);//updating response
            } else if ((!experian.isSkip() && experianDetails == null) || pancard != null) {
                logger.info("Experian not found for merchant: {}, going to ExperianV2", merchant.getId());
                experian.setNoExperian(true);
                return new ArrayList<>();
            } else if (!experian.isSkip() && experianDetails.getMaskedMobile() != null && !experianDetails.getOtpVerified()) {
                logger.info("Experian not found for merchant: {}, going to ExperianV2", merchant.getId());
                experian.setNoExperian(true);
                String[] mobiles = experianDetails.getMaskedMobile().replaceAll("\\[","").replaceAll("\\]","").split(",");
                List<String> maskedMobiles = new ArrayList<>();
                Collections.addAll(maskedMobiles, mobiles);
                experian.setMaskedMobiles(maskedMobiles);
                return new ArrayList<>();
            }
            if (experianResponse != null){
                try {
                    if (!exemptMerchant.contains(merchant.getId()) && isDerog(experianResponse, merchant, experian, isRepeatLoanNoDerog)) {
                        return new ArrayList<>();
                    }
                } catch (Exception e) {
                    logger.info("Exception while checking derog for merchant: {}", merchant.getId());
                    logger.error("Exception---", e);
                }
                //base checks
                if (!baseChecks(isZomato, merchant, merchantSummary, experian, lendingType, prevLoans, bpScore, yellowPincode)) {
                    logger.info("Base Checks Failed, so rejecting merchant: {}", merchant.getId());
                    return new ArrayList<>();
                }
                return fetchBureauEligibleLoan(experianResponse, merchant.getId(), bpScore, experian, repeatedLoan, avgTpv, isEligibleForConstruct2And3, loanCount, previousLoanDays, lendingApplication);
            }
        } catch (ResourceAccessException e) {
            logger.info("Experian not responding---", e);
            logger.info("Experian timeout for merchant: {}, pancard: {}", merchant.getId(), experian.getPancardNumber());
            experian.setReason(ExperianConstants.TIMEOUT);
            experian.setRetryCount(experian.getRetryCount() + 1);
            experianDao.save(experian);
            emailHandler.sendEmail(emails, "Experian APIs failing on PROD", "Failed for merchant: "+merchant.getId());
        } catch (Exception e) {
            experian.setRetryCount(experian.getRetryCount() + 1);
            experianDao.save(experian);
            logger.error("Exception while fetching experian details---", e);
        }
        logger.info("Experian Report not found for merchant: {}, Calculate NTC...", merchant.getId());
        //calculate NTC....
        //base checks
        if (!baseChecks(isZomato, merchant, merchantSummary, experian, lendingType, prevLoans, bpScore, yellowPincode)) {
            logger.info("Base Checks Failed, so rejecting merchant: {}", merchant.getId());
            return new ArrayList<>();
        }
        return calculateNTC(bpScore, merchant.getId(), repeatedLoan, avgTpv, isEligibleForConstruct2And3, experian, loanCount, previousLoanDays, lendingApplication);
    }

    public boolean isDerog(JsonNode experianResponse, Merchant merchant, Experian experian, boolean isRepeatLoanNoDerog) throws ParseException {
        Date reportDate = new SimpleDateFormat("yyyyMMdd").parse(experianResponse.get("INProfileResponse").get("CreditProfileHeader").get("ReportDate").asText());
        if (experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS") != null && experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS").isObject()) {
            JsonNode caisAccountDetails = experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS");
            if (derogChecks(caisAccountDetails, merchant.getId(), experian, isRepeatLoanNoDerog, reportDate)) {
                logger.info("Derog check failed, rejecting merchant: {}", merchant.getId());
                return true;
            }
        } else if (experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS") != null && experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS").isArray()) {
            int unsecuredLoanCount = 0;
            for (JsonNode caisAccountDetails : experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS")) {
                if (derogChecks(caisAccountDetails, merchant.getId(), experian, isRepeatLoanNoDerog, reportDate)) {
                    logger.info("Derog check failed, rejecting merchant: {}", merchant.getId());
                    return true;
                }
                if (checkUnsecuredLiveLoans(caisAccountDetails)) {
                    unsecuredLoanCount++;
                }
            }
            //Not more than 3 live unsecured loans running
            if (!isRepeatLoanNoDerog && unsecuredLoanCount > 3) {
                logger.info("Derog more than 3 live unsecured loans running, rejecting merchant: {}", merchant.getId());
                experian.setRejected(true);
                experian.setRejectedDate(new Date());
                experian.setReason(ExperianConstants.DEROG_UNSECURED_LOANS);
                experianDao.save(experian);
                return true;
            }
        }
        //Not more than 4 unsecured loan enquiries in the last 6 months --- Derog check
        if (!isRepeatLoanNoDerog && checkUnsecuredLoanEnquiriesInLast6Months(experianResponse, reportDate)) {
            logger.info("Derog more than 4 unsecured loan enquiries in the last 6 months, rejecting merchant: {}", merchant.getId());
            experian.setRejected(true);
            experian.setRejectedDate(new Date());
            experian.setReason(ExperianConstants.DEROG_UNSECURED_LOAN_ENQUIRY);
            experianDao.save(experian);
            return true;
        }
        //Not more than 6 enquiries in the last 3 months ( across all product types) --- Derog check
        if (!isRepeatLoanNoDerog && checkLoanEnquiriesInLast3Months(experianResponse)) {
            logger.info("Derog more than 6 enquiries in the last 3 months, rejecting merchant: {}", merchant.getId());
            experian.setRejected(true);
            experian.setRejectedDate(new Date());
            experian.setReason(ExperianConstants.DEROG_MORE_THAN_6_LOAN_ENQUIRY);
            experianDao.save(experian);
            return true;
        }
        return false;
    }

    private boolean checkOverdue(List<LendingPaymentSchedule> prevLoans) {
        try {
            if(prevLoans == null || prevLoans.isEmpty()) {
                return false;
            }
            if(prevLoans.get(0) != null && prevLoans.get(0).getLoanAmount() <= 5000D) {
                return false;
            }
            return getOvershootPeriod(prevLoans.get(0)) > 15;
        } catch(Exception ex){
            logger.error("Error while fetching eligibility for repeat loan no derog", ex);
            return false;
        }
    }

    public boolean checkFraud(MerchantSummary merchantSummary) {
        int selfTxnCount = (merchantSummary != null && merchantSummary.getSelfTxnCount1Mon() != null) ? merchantSummary.getSelfTxnCount1Mon() : 0;
        return (merchantSummary != null && merchantSummary.getUniqueCustomer1mon() != null && (merchantSummary.getUniqueCustomer1mon() - selfTxnCount) < 15)
                || (merchantSummary != null && merchantSummary.getFraudCustomer() != null);
    }

    private LendingPancard fetchNameFromLiquiloans(String pancardNumber, Long merchantId) {
        logger.info("Calling Liquiloan Name Fetch Api for merchant:{}", merchantId);
        String name = null;
        String apiResponse = null;
        try {
            ExternalGateway externalGateway = externalGatewayDao.findByGatewayNameAndTypeAndStatus("LIQUILOANS", null, "ACTIVE");
            if (externalGateway != null) {
                Map<String, String> requestParams = new HashMap<>();
                Date currentTime = new Date();
                String payload = pancardNumber + "||" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(currentTime);
                String checksum = hmacCalculator.calculateHMACHexEncoded(payload, aesEncryption.decrypt(externalGateway.getSecret()));
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
                    int retry=0;
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
                        apiResponse= response.toString();
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

    private LendingPancard fetchNameFromSignzy(String pancardNumber, Long merchantId) {
        logger.info("Calling Pan Fetch Api for merchant:{}", merchantId);
        try {
            Map<String, String> identityDetail = apiGatewayService.signzyIdentityDetails("individualPan", merchantId);
            if (identityDetail != null) {
                String response = apiGatewayService.signzyPanFetch(identityDetail.get("itemId"), identityDetail.get("accessToken"), pancardNumber, merchantId);
                if (response != null) {
                    JsonNode responseNode = objectMapper.readTree(response);
                    if(responseNode != null && responseNode.has("response") && !responseNode.get("response").isNull() && responseNode.get("response").has("result") && !responseNode.get("response").get("result").isNull() && responseNode.get("response").get("result").get("name") != null) {
                        String name = responseNode.get("response").get("result").get("name").asText();
                        logger.info("Name:{} found in pancard:{}", name, pancardNumber);
                        lendingPancardDao.deleteByMerchantId(merchantId);
                        return lendingPancardDao.save(new LendingPancard(merchantId, pancardNumber, name, response));
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Exception in Signzy Pan Fetch Api", e);
        }
        return null;
    }

    private List<LoanEligibilityDTO> fetchBureauEligibleLoan(JsonNode experianResponse, Long merchantId, Double bpScore, Experian experian, boolean repeatedLoan, double avgTpv, boolean isEligibleForConstruct2And3, int loanCount, int previousLoanDays, LendingApplication lendingApplication) {
        int bureauVintage = fetchBureauVintage(experianResponse);//months
        String accountCategory = fetchAccountCategory(experianResponse);// A,B,C or NTC
        if (accountCategory.equals("NTC")){
            logger.info("Loan category is NTC for merchant: {}, Calculate NTC...", merchantId);
            return calculateNTC(bpScore, merchantId, repeatedLoan, avgTpv, isEligibleForConstruct2And3, experian, loanCount, previousLoanDays, lendingApplication);
            //calculate NTC....
        }
        String segment = calculateSegment(bureauVintage, accountCategory, bpScore);
        String color;
        if (ExperianConstants.RED.contains(segment)) {
            color = ExperianConstants.COLOR.RED.name();
            experian.setCategory(segment);
            experian.setColor(color);
            experian.setReason(ExperianConstants.CATEGORY_RED);
            experianDao.save(experian);
            logger.info("Category color RED, so rejecting merchant: {}", merchantId);
            return new ArrayList<>();
        } else if (ExperianConstants.AMBER.contains(segment)) {
            color = ExperianConstants.COLOR.AMBER.name();
        } else if (ExperianConstants.LIGHT_GREEN.contains(segment)) {
            color = ExperianConstants.COLOR.LIGHT_GREEN.name();
        } else {
            color = ExperianConstants.COLOR.DARK_GREEN.name();
        }
        logger.info("Bureau Segment: {}, Color: {} for merchant: {}", segment, color, merchantId);
        //update segment and color
        experian.setCategory(segment);
        experian.setColor(color);
        experianDao.save(experian);
        logger.info("Calculating bureau eligible loans for merchant: {}", merchantId);
        return calculateEligibleLoans(avgTpv, repeatedLoan, color, isEligibleForConstruct2And3, false, previousLoanDays, merchantId, experian.getId(), lendingApplication);
    }

    private List<LoanEligibilityDTO> calculateNTC(Double bpScore, Long merchantId, boolean repeatedLoan, double avgTpv, boolean isEligibleForConstruct2And3, Experian experian, int loanCount, int previousLoanDays, LendingApplication lendingApplication) {
        logger.info("Calculating NTC for merchant: {}", merchantId);
        String segment;
        String color;
        if (bpScore <= 15){
            segment = "2N";
            color = ExperianConstants.COLOR.AMBER.name();
        } else if (bpScore <= 25){
            segment = "3N";
            color = ExperianConstants.COLOR.LIGHT_GREEN.name();
        }else {
            segment = "4N";
            color = ExperianConstants.COLOR.DARK_GREEN.name();
        }
        logger.info("NTC Segment: {}, Color: {} for merchant: {}", segment, color, merchantId);
        //update segment and color
        experian.setCategory(segment);
        experian.setColor(color);
        experianDao.save(experian);
        return loanCount > 2 ? calculateEligibleLoans(avgTpv, repeatedLoan, color, isEligibleForConstruct2And3, false, previousLoanDays, merchantId, experian.getId(), lendingApplication) : calculateEligibleLoans(avgTpv, repeatedLoan, color, isEligibleForConstruct2And3, true, previousLoanDays, merchantId, experian.getId(), lendingApplication);
    }

    private List<LoanEligibilityDTO> calculateEligibleLoans(double avgTpv, boolean repeatedLoan, String color, boolean isEligibleForConstruct2And3, boolean isNTC, int previousLoanDays, Long merchantId, Long experianId, LendingApplication lendingApplication) {
        logger.info("Calculating offers for merchant: {}", merchantId);
        String masterCategory = getMasterCategory(color, isNTC, repeatedLoan);
        logger.info("Master Category for merchant: {} is {}", merchantId, masterCategory);
        List<LendingCategories> lendingCategories;
        String type;
        MerchantSummaryLending merchantSummaryLending = merchantSummaryLendingDao.findByMerchantId(merchantId);
        String set = (merchantSummaryLending != null && merchantSummaryLending.getSegment() != null) ? merchantSummaryLending.getSegment() : "2";
        double prevLoanAmount = 0d;
        if (lendingApplication != null) {
            switch (color){
                case "AMBER": prevLoanAmount = lendingApplication.getLoanAmount() * 1.1;break;
                case "LIGHT_GREEN": prevLoanAmount = lendingApplication.getLoanAmount() * 1.25;break;
                case "DARK_GREEN": prevLoanAmount = lendingApplication.getLoanAmount() * 1.5;break;
            }
        }
        if (isEligibleForConstruct2And3) {
            List<String> payableConverters = new ArrayList<>();
            switch (previousLoanDays){
                case 26: payableConverters.add("1+3 Months");break;
                case 77: payableConverters.addAll(Arrays.asList("1+3 Months", "1+6 Months"));break;
                default: payableConverters.addAll(Arrays.asList("1+3 Months", "1+6 Months", "1+12 Months"));
            }
            lendingCategories = lendingCategoryDao.getByMasterCategoryForConstruct3(masterCategory, payableConverters);
            type = "Only Interest";
        } else {
            lendingCategories = lendingCategoryDao.getByMasterCategoryForConstruct1(masterCategory);
            type = null;
        }
        if (lendingCategories.isEmpty()) {
            logger.error("No active lending category found for merchant: {}", merchantId);
            return new ArrayList<>();
        } else {
            String loanType = "REGULAR";
            logger.info("Deleting eligible loans for merchant: {}", merchantId);
            eligibleLoanDao.deleteByMerchantId(merchantId);
            List<LoanEligibilityDTO> loanEligibilityDTOList = new ArrayList<>();
            for (LendingCategories lendingCategory : lendingCategories) {
                if (lendingCategory.getLoanConstruct() != null && lendingCategory.getLoanConstruct().equalsIgnoreCase("CONSTRUCT_1")) {
                    LoanEligibilityDTO loanEligibilityDTO = calculateLoanBreakup(lendingCategory, avgTpv, type, merchantId, experianId, prevLoanAmount, color, set, loanType, false, false);
                    if (loanEligibilityDTO != null) {
                        loanEligibilityDTOList.add(loanEligibilityDTO);
                    } else {
                        logger.info("loan offer is null for merchant: {}", merchantId);
                    }
                }
            }
            loanEligibilityDTOList.sort(Comparator.comparing(LoanEligibilityDTO::getAmount, Comparator.reverseOrder()).thenComparing(LoanEligibilityDTO::getEdi));
            if (lendingApplication != null && lendingApplication.getCategory() != null && (loanEligibilityDTOList.isEmpty() || (loanEligibilityDTOList.get(0).getAmount() < prevLoanAmount))) {
                List<LendingCategories> lendingCategoriesList = lendingCategoryDao.findByCategory(lendingApplication.getCategory());
                if (lendingCategoriesList != null && !lendingCategoriesList.isEmpty() && lendingCategoriesList.get(0).getLoanConstruct() != null && lendingCategoriesList.get(0).getLoanConstruct().equalsIgnoreCase("CONSTRUCT_1")) {
                    LoanEligibilityDTO loanEligibilityDTO = calculateLoanBreakup(lendingCategoriesList.get(0), 0, type, merchantId, experianId, prevLoanAmount, color, set, loanType, false, false);
                    if (loanEligibilityDTO != null) {
                        logger.info("loan offer calculated using previous category for merchant: {}", merchantId);
                        loanEligibilityDTOList.add(loanEligibilityDTO);
                        loanEligibilityDTOList.sort(Comparator.comparing(LoanEligibilityDTO::getAmount, Comparator.reverseOrder()).thenComparing(LoanEligibilityDTO::getEdi));
                    } else {
                        logger.info("loan offer is null for merchant: {}", merchantId);
                    }
                }
            }
            if (!loanEligibilityDTOList.isEmpty()) {
                experianDao.updateEligibleAmount(experianId, loanEligibilityDTOList.get(0).getAmount().doubleValue(), loanEligibilityDTOList.get(0).getPrincipleEdiTenure().toString(), "REGULAR");
            }
            return loanEligibilityDTOList;
        }
    }

    private String getMasterCategory(String color, boolean isNTC, boolean repeatedLoan) {
        switch (color){
            case "AMBER":
                if (isNTC){
                    if (repeatedLoan){
                        return "S4A";
                    } else {
                        return "S3A";
                    }
                } else {
                    if (repeatedLoan){
                        return "S2A";
                    } else {
                        return "S1A";
                    }
                }
            case "LIGHT_GREEN":
                if (isNTC){
                    if (repeatedLoan){
                        return "S4LG";
                    } else {
                        return "S3LG";
                    }
                } else {
                    if (repeatedLoan){
                        return "S2LG";
                    } else {
                        return "S1LG";
                    }
                }
            case "DARK_GREEN":
                if (isNTC){
                    if (repeatedLoan){
                        return "S4DG";
                    } else {
                        return "S3DG";
                    }
                } else {
                    if (repeatedLoan){
                        return "S2DG";
                    } else {
                        return "S1DG";
                    }
                }
        }
        return "S4A";
    }

    public LoanEligibilityDTO calculateLoanBreakup(LendingCategories lendingCategories, double avgTpv, String type, Long merchantId, Long experianId, double prevLoanAmount, String color, String set, String loanType, boolean isZomato, boolean yellowPincode) {
        Double percentage = lendingCategories.getMultiplier();
        double interest = "TOPUP".equalsIgnoreCase(loanType) ? 1.75 : lendingCategories.getInterestRate();
        int tenure = Math.round(lendingCategories.getTenureMonths());
        int ioTenure = Math.round(lendingCategories.getIoTenureMonths());
        Integer maxAmount = lendingCategories.getMaxTpvAmount();
        int ioPayableDays = lendingCategories.getIoPayableDays();
        String construct = lendingCategories.getLoanConstruct();
        String category = lendingCategories.getCategory();
        String payableConverter = lendingCategories.getPayableConverter();
        int ioEdiDays = lendingCategories.getIoEdiDays();
        LoanCalculationUtil.LoanBreakupDetail breakup;
        if (avgTpv == 0 && prevLoanAmount > 0) {
            if ("NTB".equalsIgnoreCase(loanType)) {
                prevLoanAmount = Math.min(roundUp(prevLoanAmount), 100000);
            } else {
                prevLoanAmount = Math.min(roundUp(prevLoanAmount), 700000);
            }
            AvailableLoan availableLoan = new AvailableLoan();
            availableLoan.setAmount(prevLoanAmount);
            breakup = LoanCalculationUtil.getLoanBreakup(availableLoan, lendingCategories, loanType);
        } else {
            breakup = getBreakup(tenure, construct, type, avgTpv, percentage, interest, maxAmount, ioTenure, ioPayableDays);
        }
        if (!isZomato && !"OGL".equalsIgnoreCase(loanType)) {
            if (color.equalsIgnoreCase("AMBER") && breakup.getLoanAmount() < 20000 && !"NTB".equalsIgnoreCase(loanType)) {
                logger.info("loan amount is less than 20000 for merchant: {}", merchantId);
                return null;
            } else if (breakup.getLoanAmount() < 10000) {
                logger.info("loan amount is less than 10000 for merchant: {}", merchantId);
                return null;
            }
        }
        logger.info("saving eligible loan for merchant: {}", merchantId);
        EligibleLoan eligibleLoan = eligibleLoanDao.save(new EligibleLoan(merchantId, experianId, (double)breakup.getLoanAmount(), payableConverter, "ACTIVE", category, ioEdiDays, 0, avgTpv, breakup.getEdi(), breakup.getIoEdi(), breakup.getRepayment(), construct, loanType));
        logger.info("eligible loan for merchant: {} is-- {}", merchantId, eligibleLoan.toString());
        eligibleLoanAuditDao.save(EligibleLoanAudit.createObject(eligibleLoan));
        return createLoanEligibilityDTO(breakup, payableConverter, category);
    }

    private LoanCalculationUtil.LoanBreakupDetail getBreakup(int tenureMonth, String construct, String type, double avgTpv, double percentage, double interest, int maxAmount, int ioTenure, int ioPayableDays){
        int processingFee = 0;
        int tenure = tenureMonth - ioTenure;
        int ediDays, disbursementAmount, ioInterestAmount, principleEdiTenure, repayment;
        double loanAmount, edi, totalInterestAmount, ioEdi;
        ediDays = getEdiDays(tenure);
        edi = (avgTpv * percentage);
        repayment = (int)Math.round(ediDays * edi);
        loanAmount = Math.min(roundUp(repayment / (1 + (interest/100)*tenure)), maxAmount);// round down
        edi = Math.ceil((loanAmount * (1 + (interest/100)*tenure)) / ediDays);
        disbursementAmount = (int)loanAmount - processingFee;
        ioEdi = ioPayableDays > 0 ? Math.ceil((loanAmount * (interest / 100)) / ioPayableDays) : 0;
        ioInterestAmount = (int) (ioEdi * ioPayableDays);
        repayment =  (int) Math.round((edi * ediDays) + ioInterestAmount);
        totalInterestAmount = repayment - loanAmount;
        principleEdiTenure = tenure;
        return new LoanCalculationUtil.LoanBreakupDetail(construct, (int)edi, (int)ioEdi, processingFee, ioInterestAmount, (int)totalInterestAmount,(int) totalInterestAmount,
                ioTenure, principleEdiTenure, repayment, disbursementAmount, type, (int)loanAmount, interest);
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

    private LoanEligibilityDTO createLoanEligibilityDTO(LoanCalculationUtil.LoanBreakupDetail breakup, String tenure, String category){
        LoanEligibilityDTO loanEligibilityDTO = new LoanEligibilityDTO();
        loanEligibilityDTO.setProcessingFee(breakup.getProcessingFee());
        loanEligibilityDTO.setInterestRate(breakup.getEffectiveInterestRate());
        loanEligibilityDTO.setAmount(breakup.getLoanAmount());
        loanEligibilityDTO.setCategory(category);
        loanEligibilityDTO.setInterestAmount(breakup.getTotalInterestAmount());
        loanEligibilityDTO.setEdi(breakup.getEdi());
        loanEligibilityDTO.setRepayment(breakup.getRepayment());
        loanEligibilityDTO.setDisbursementAmount(breakup.getDisbursementAmount());
        loanEligibilityDTO.setTenure(tenure);
        loanEligibilityDTO.setConstruct(breakup.getConstruct());
        loanEligibilityDTO.setList(LoanCalculationUtil.prepareLabels(breakup, breakup.getIoOrFreeEdiTenure()));
        loanEligibilityDTO.setType(breakup.getType());
        loanEligibilityDTO.setOptionEnable(true);
        loanEligibilityDTO.setPrincipleEdiTenure(breakup.getPrincipleEdiTenure());
        return loanEligibilityDTO;
    }

    private int getEdiDays(int tenure){
        switch (tenure){
            case 1: return 26;
            case 3: return 77;
            case 6: return 155;
            case 9: return 234;
            case 12: return 311;
            default: return 388;//15 months
        }
    }


    public boolean isEligibleForConstruct2And3(MerchantSummary summary, List<LendingPaymentSchedule> prevLoans) {
        try {
            if(prevLoans == null || prevLoans.isEmpty()) {
                return false;
            }
            if(isAnyHighTPVLoan(prevLoans)) {
                return false;
            }
            if(summary == null || summary.getTxnDayCount1Mon() == null || summary.getTxnDayCount1Mon() < 15) {
                return false;
            }
            return getOvershootPeriod(prevLoans.get(0)) <= 5;
        } catch(Exception ex){
            logger.error("Error while fetching eligiblity for construct 2 and 3", ex);
            return false;
        }
    }

    public boolean isRepeatLoanNoDerog(List<LendingPaymentSchedule> prevLoans) {
        try {
            if(prevLoans == null || prevLoans.isEmpty()) {
                return false;
            }
            if(prevLoans.get(0) != null && prevLoans.get(0).getLoanAmount() < 5000D) {
                return false;
            }
            return getOvershootPeriod(prevLoans.get(0)) <= 15;
        } catch(Exception ex){
            logger.error("Error while fetching eligibility for repeat loan no derog", ex);
            return false;
        }
    }

    private boolean isAnyHighTPVLoan(List<LendingPaymentSchedule> lendingPaymentScheduleList) {
        if(lendingPaymentScheduleList == null || lendingPaymentScheduleList.isEmpty()) {
            return false;
        }
        for (LendingPaymentSchedule lendingPaymentSchedule : lendingPaymentScheduleList) {
            if(Loan.Category.HIGHTPV.toString().equals(lendingPaymentSchedule.getLoanType())) {
                return true;
            }
        }
        return false;
    }

    private long getOvershootPeriod(LendingPaymentSchedule lendingPaymentSchedule) {
        if(lendingPaymentSchedule == null) {
            return 0;
        }
        LendingLedger lastEDI = lendingLedgerDao.findLastEDIDueEntryByMerchantAndLoan(lendingPaymentSchedule.getMerchant().getId(), lendingPaymentSchedule.getId());
        LendingLedger lastPayment = lendingLedgerDao.findLastPaymentEntryByMerchantAndLoan(lendingPaymentSchedule.getMerchant().getId(), lendingPaymentSchedule.getId());
        if (lastEDI == null || lastPayment == null) {
            return 0;
        }
        return LoanUtil.getDateDiffInDays(lastEDI.getDate(), lastPayment.getDate());
    }

    public String calculateSegment(int bureauVintage, String accountCategory, Double bpScore) {
        int col;
        int row;
        String[][] m1 = {{"1","5","9"}, {"2","6","10"}, {"3","7","11"}, {"4","8","12"}};
        String[][] m2 = {{"13","17","21"}, {"14","18","22"}, {"15","19","23"}, {"16","20","24"}};
        String[][] m3 = {{"25","29","33"}, {"26","30","34"}, {"27","31","35"}, {"28","32","36"}};
        String[][] m4 = {{"37","41","45"}, {"38","42","46"}, {"39","43","47"}, {"40","44","48"}};
        switch (accountCategory){
            case "B": col = 1;break;
            case "C": col = 2;break;
            default: col = 0;// "A"
        }
        if (bpScore <= 15) { row = 0;}
        else if (bpScore < 20) { row = 1;}
        else if (bpScore <= 25) { row = 2;}
        else { row = 3;}
        if (bureauVintage <= 3){
            return m1[row][col];
        } else if (bureauVintage <= 6) {
            return m2[row][col];
        } else if (bureauVintage <= 12) {
            return m3[row][col];
        } else {
            return m4[row][col];
        }
    }

    public String fetchAccountCategory(JsonNode experianResponse) {
        List<Integer> categoryA = Arrays.asList(6,7,13,38,39,43);
        List<Integer> categoryB = Arrays.asList(1,5,8,9,10,11,12,17,32,33,34,36,37,51,52,53,54,55,56,57,58,59,60,61);
        List<Integer> categoryC = Arrays.asList(2,3);
        boolean a=false, b=false, c=false;
        if (experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS") != null && experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS").isArray()){
            for (JsonNode jsonNode : experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS")) {
                if (categoryA.contains(jsonNode.get("Account_Type").asInt())) { a = true;}
                if (categoryB.contains(jsonNode.get("Account_Type").asInt())) { b = true;}
                if (categoryC.contains(jsonNode.get("Account_Type").asInt())) { c = true;}
            }
        } else if (experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS") != null && experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS").isObject()){
            JsonNode jsonNode = experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS");
            if (categoryA.contains(jsonNode.get("Account_Type").asInt())) { a = true;}
            if (categoryB.contains(jsonNode.get("Account_Type").asInt())) { b = true;}
            if (categoryC.contains(jsonNode.get("Account_Type").asInt())) { c = true;}
        }
        return c ? "C" : b ? "B" : a ? "A" : "NTC";
    }

    public int fetchBureauVintage(JsonNode experianResponse) {
        DateTimeFormatter formatter = DateTimeFormat.forPattern("yyyyMMdd");
        DateTime min = new DateTime();
        if (experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS") != null && experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS").isArray()){
            for (JsonNode jsonNode : experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS")) {
                try {
                    min = formatter.parseDateTime(jsonNode.get("Open_Date").toString()).isBefore(min) ? formatter.parseDateTime(jsonNode.get("Open_Date").toString()) : min;
                } catch (Exception e) {
                    logger.info("Invalid Open_Date");
                }
            }
            return Months.monthsBetween(min, DateTime.now()).getMonths();
        } else if (experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS") != null && experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS").isObject()){
            JsonNode jsonNode = experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS");
            try {
                min = formatter.parseDateTime(jsonNode.get("Open_Date").toString()).isBefore(min) ? formatter.parseDateTime(jsonNode.get("Open_Date").toString()) : min;
            } catch (Exception e) {
                logger.info("Invalid Open_Date");
            }
            return Months.monthsBetween(min, DateTime.now()).getMonths();
        }
        return 0;
    }

    private boolean derogChecks(JsonNode jsonNode, Long merchantId, Experian experian, boolean isRepeatLoanNoDerog, Date reportDate) {
        //Check for Derog Account Status
        if (jsonNode.get("Account_Status") != null && derogAccountStatus.contains(jsonNode.get("Account_Status").asInt())){
            logger.info("Derog Account Status check failed, rejecting merchant: {}", merchantId);
            experian.setRejected(true);
            experian.setRejectedDate(new Date());
            experian.setReason(ExperianConstants.DEROG_ACCOUNT_STATUS);
            experianDao.save(experian);
            return true;
        }
        //Check for Derog DPD Last 3 months
        if (!isRepeatLoanNoDerog && jsonNode.get("AccountHoldertypeCode").asInt() != 7 && checkDPDLastXmonths(jsonNode, 3, reportDate)){
            logger.info("Derog DPD Last 3 months check failed, rejecting merchant: {}", merchantId);
            experian.setRejected(true);
            experian.setRejectedDate(new Date());
            experian.setReason(ExperianConstants.DEROG_DPD_LAST_3_MONTHS);
            experianDao.save(experian);
            return true;
        }
        //Check for Derog DPD Last 6 months
        if (jsonNode.get("AccountHoldertypeCode").asInt() != 7 && checkDPDLastXmonths(jsonNode, 6, reportDate)){
            logger.info("Derog DPD Last 6 months check failed, rejecting merchant: {}", merchantId);
            experian.setRejected(true);
            experian.setRejectedDate(new Date());
            experian.setReason(ExperianConstants.DEROG_DPD_LAST_6_MONTHS);
            experianDao.save(experian);
            return true;
        }
        //Check for Derog DPD Last 12 months
        if (!isRepeatLoanNoDerog && jsonNode.get("AccountHoldertypeCode").asInt() != 7 && checkDPDLastXmonths(jsonNode, 12, reportDate)){
            logger.info("Derog DPD Last 12 months check failed, rejecting merchant: {}", merchantId);
            experian.setRejected(true);
            experian.setRejectedDate(new Date());
            experian.setReason(ExperianConstants.DEROG_DPD_LAST_12_MONTHS);
            experianDao.save(experian);
            return true;
        }
        //Check for Derog DPD Last 24 months
        if (!isRepeatLoanNoDerog && jsonNode.get("AccountHoldertypeCode").asInt() != 7 && checkDPDLastXmonths(jsonNode, 24, reportDate)){
            logger.info("Derog DPD Last 24 months check failed, rejecting merchant: {}", merchantId);
            experian.setRejected(true);
            experian.setRejectedDate(new Date());
            experian.setReason(ExperianConstants.DEROG_DPD_LAST_24_MONTHS);
            experianDao.save(experian);
            return true;
        }
        //Check for Derog DPD Older than 24 months
//        if (jsonNode.get("AccountHoldertypeCode").asInt() != 7 && checkDPDOlderThan24months(jsonNode)){
//            logger.info("Derog DPD Older than 24 months check failed, rejecting merchant: {}", merchantId);
//            experian.setRejected(true);
//            experian.setReason(ExperianConstants.DEROG_DPD_OLDER_THAN_24_MONTHS);
//            experianDao.save(experian);
//            return true;
//        }
        return false;
    }

    private boolean checkUnsecuredLiveLoans(JsonNode jsonNode) {
        return jsonNode.get("Date_Closed").toString().equals("\"\"") && jsonNode.get("Account_Type").asInt() != 10 && derogUnsecuredProducts.contains(jsonNode.get("Account_Type").asInt());
    }

    private boolean checkLoanEnquiriesInLast3Months(JsonNode experianResponse) {
        return experianResponse.get("INProfileResponse").get("TotalCAPS_Summary").get("TotalCAPSLast90Days").asInt() > 6;
    }

    private boolean checkUnsecuredLoanEnquiriesInLast6Months(JsonNode experianResponse, Date reportDate) {
        if (experianResponse.get("INProfileResponse").get("TotalCAPS_Summary").get("TotalCAPSLast180Days").asInt() <= 4){
            return false;
        }
        Calendar c = Calendar.getInstance();
        c.setTime(reportDate);
        c.add(Calendar.MONTH, -6);
        String month = (c.get(Calendar.MONTH) + 1) < 10 ? "0" + (c.get(Calendar.MONTH) + 1) : (c.get(Calendar.MONTH) + 1) + "";
        String day = (c.get(Calendar.DAY_OF_MONTH) + 1) < 10 ? "0" + (c.get(Calendar.DAY_OF_MONTH) + 1) : (c.get(Calendar.DAY_OF_MONTH) + 1) + "";
        long previous6MonthDate = Long.parseLong(c.get(Calendar.YEAR) + month + day);
        if (experianResponse.get("INProfileResponse").get("CAPS").get("CAPS_Application_Details") != null && experianResponse.get("INProfileResponse").get("CAPS").get("CAPS_Application_Details").isObject()) {
            JsonNode jsonNode = experianResponse.get("INProfileResponse").get("CAPS").get("CAPS_Application_Details");
            return derogUnsecuredProducts.contains(jsonNode.get("Product").asInt()) && jsonNode.get("Date_of_Request").longValue() >= previous6MonthDate;
        } else if (experianResponse.get("INProfileResponse").get("CAPS").get("CAPS_Application_Details") != null && experianResponse.get("INProfileResponse").get("CAPS").get("CAPS_Application_Details").isArray()) {
            for (JsonNode jsonNode : experianResponse.get("INProfileResponse").get("CAPS").get("CAPS_Application_Details")) {
                if (jsonNode.get("Product") != null && derogUnsecuredProducts.contains(jsonNode.get("Product").asInt()) && jsonNode.get("Date_of_Request") != null && jsonNode.get("Date_of_Request").longValue() >= previous6MonthDate) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean checkDPDLastXmonths(JsonNode jsonNode, int months, Date reportDate){
        Date dateReported = null;
        try {
            if (jsonNode.get("Date_Reported") != null && !jsonNode.get("Date_Reported").asText().equalsIgnoreCase("")) {
                dateReported = new SimpleDateFormat("yyyyMMdd").parse(jsonNode.get("Date_Reported").asText());
            }
        } catch (Exception e) {
            logger.error("Exception:", e);
        }
        List<String> monthYear = new ArrayList<>();
        Calendar c = Calendar.getInstance();
        if (dateReported != null && LoanUtil.getDateDiffInDays(dateReported, reportDate) > months * 30) {
            return false;
        }
        if (dateReported != null && LoanUtil.getDateDiffInDays(dateReported, reportDate) <= months * 30) {
            c.setTime(dateReported);
        } else {
            c.setTime(reportDate);
        }
        String month;
        int dpd = 5;//3 months
        switch (months){
            case 6: dpd = 30;break;
            case 12: dpd = 60;break;
            case 24: dpd = 90;break;
        }
        for (int i = 0; i < months; i++) {
            month = (c.get(Calendar.MONTH) + 1) < 10 ? "0" + (c.get(Calendar.MONTH) + 1) : (c.get(Calendar.MONTH) + 1) + "";
            monthYear.add(month + "$" + c.get(Calendar.YEAR));//01$2020
            c.add(Calendar.MONTH, -1);
        }
        if (jsonNode.get("CAIS_Account_History") != null && jsonNode.get("CAIS_Account_History").isArray()) {
            for (JsonNode cais_account_history : jsonNode.get("CAIS_Account_History")) {
                if (monthYear.contains(cais_account_history.get("Month").asText() + "$" + cais_account_history.get("Year").asText()) && !cais_account_history.get("Days_Past_Due").isNull() && !cais_account_history.get("Days_Past_Due").asText().equalsIgnoreCase("") && cais_account_history.get("Days_Past_Due").asInt() >= dpd) {
                    return true;
                }
            }
        } else if (jsonNode.get("CAIS_Account_History") != null && jsonNode.get("CAIS_Account_History").isObject()){
            JsonNode cais_account_history = jsonNode.get("CAIS_Account_History");
            return monthYear.contains(cais_account_history.get("Month").asText() + "$" + cais_account_history.get("Year").asText()) && !cais_account_history.get("Days_Past_Due").isNull() && !cais_account_history.get("Days_Past_Due").asText().equalsIgnoreCase("") && cais_account_history.get("Days_Past_Due").asInt() >= dpd;
        }
        return false;
    }

    //No 60DPD in any month older than 24 months, for cases where no recent loan is there
    private boolean checkDPDOlderThan24months(JsonNode jsonNode, Date reportDate){
        if (!checkDPDLastXmonths(jsonNode, 24, reportDate)){
            List<String> monthYear = new ArrayList<>();
            Calendar c = Calendar.getInstance();
            String month;
            for (int i = 0; i < 24; i++) {
                month = (c.get(Calendar.MONTH) + 1) < 10 ? "0" + (c.get(Calendar.MONTH) + 1) : (c.get(Calendar.MONTH) + 1) + "";
                monthYear.add(month + "$" + c.get(Calendar.YEAR));//01$2020
                c.add(Calendar.MONTH, -1);
            }
            if (jsonNode.get("CAIS_Account_History") != null && jsonNode.get("CAIS_Account_History").isArray()) {
                for (JsonNode cais_account_history : jsonNode.get("CAIS_Account_History")) {
                    if (monthYear.contains(cais_account_history.get("Month") + "$" + cais_account_history.get("Year"))) {
                        return false;//active loan found in last 24 months without any DPD then return false
                    }
                }
                for (JsonNode cais_account_history : jsonNode.get("CAIS_Account_History")) {
                    if (!cais_account_history.get("Days_Past_Due").isNull() && cais_account_history.get("Days_Past_Due").asInt() >= 60) {
                        return true;
                    }
                }
            } else if (jsonNode.get("CAIS_Account_History") != null && jsonNode.get("CAIS_Account_History").isObject()){
                JsonNode cais_account_history = jsonNode.get("CAIS_Account_History");
                if (monthYear.contains(cais_account_history.get("Month") + "$" + cais_account_history.get("Year"))) {
                    return false;//active loan found in last 24 months without any DPD then return false
                }
                return !cais_account_history.get("Days_Past_Due").isNull() && cais_account_history.get("Days_Past_Due").asInt() >= 60;
            }
        }
        return false;
    }

    private boolean validatePancard(JsonNode experianResponse, String panCard, Long merchantId, Experian experian){
        if (experianResponse.get("INProfileResponse").get("Current_Application").get("Current_Application_Details") != null && experianResponse.get("INProfileResponse").get("Current_Application").get("Current_Application_Details").get("Current_Applicant_Details") != null) {
            String email = experianResponse.get("INProfileResponse").get("Current_Application").get("Current_Application_Details").get("Current_Applicant_Details").get("EMailId").textValue();
            experian.setEmail(email);
        }
        if (experianResponse.get("INProfileResponse").get("SCORE").get("BureauScore") != null) {
            experian.setExperianScore(experianResponse.get("INProfileResponse").get("SCORE").get("BureauScore").doubleValue());
        }
        if (experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS") != null && experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS").isArray()){
            for (JsonNode jsonNode : experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS")) {
                if (jsonNode.get("CAIS_Holder_Details") != null && jsonNode.get("CAIS_Holder_Details").isObject()) {
                    if (jsonNode.get("CAIS_Holder_Details").get("Income_TAX_PAN") != null && jsonNode.get("CAIS_Holder_Details").get("Income_TAX_PAN").textValue().equalsIgnoreCase(panCard)) {
                        String merchantName = jsonNode.get("CAIS_Holder_Details").get("First_Name_Non_Normalized").textValue() + " " + jsonNode.get("CAIS_Holder_Details").get("Middle_Name_1_Non_Normalized").textValue() + " " + jsonNode.get("CAIS_Holder_Details").get("Surname_Non_Normalized").textValue();
                        experian.setMerchantName(merchantName);
                        experianDao.save(experian);
                        return true;
                    }
                } else if (jsonNode.get("CAIS_Holder_Details") != null && jsonNode.get("CAIS_Holder_Details").isArray()) {
                    for (JsonNode node : jsonNode.get("CAIS_Holder_Details")) {
                        if (node.get("Income_TAX_PAN") != null && node.get("Income_TAX_PAN").textValue().equalsIgnoreCase(panCard)) {
                            String merchantName = node.get("First_Name_Non_Normalized").textValue() + " " + node.get("Middle_Name_1_Non_Normalized").textValue() + " " + node.get("Surname_Non_Normalized").textValue();
                            experian.setMerchantName(merchantName);
                            experianDao.save(experian);
                            return true;
                        }
                    }
                }
            }
        } else if (experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS") != null && experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS").isObject()){
            JsonNode jsonNode = experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS");
            if (jsonNode.get("CAIS_Holder_Details") != null && jsonNode.get("CAIS_Holder_Details").isObject()) {
                if (jsonNode.get("CAIS_Holder_Details").get("Income_TAX_PAN") != null && jsonNode.get("CAIS_Holder_Details").get("Income_TAX_PAN").textValue().equalsIgnoreCase(panCard)) {
                    String merchantName = jsonNode.get("CAIS_Holder_Details").get("First_Name_Non_Normalized").textValue() + " " + jsonNode.get("CAIS_Holder_Details").get("Middle_Name_1_Non_Normalized").textValue() + " " + jsonNode.get("CAIS_Holder_Details").get("Surname_Non_Normalized").textValue();
                    experian.setMerchantName(merchantName);
                    experianDao.save(experian);
                    return true;
                }
            } else if (jsonNode.get("CAIS_Holder_Details") != null && jsonNode.get("CAIS_Holder_Details").isArray()) {
                for (JsonNode node : jsonNode.get("CAIS_Holder_Details")) {
                    if (node.get("Income_TAX_PAN") != null && node.get("Income_TAX_PAN").textValue().equalsIgnoreCase(panCard)) {
                        String merchantName = node.get("First_Name_Non_Normalized").textValue() + " " + node.get("Middle_Name_1_Non_Normalized").textValue() + " " + node.get("Surname_Non_Normalized").textValue();
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


    public JsonNode fetchExperianDetails(String contact, String panCard, Long merchantId, Double bpScore, MerchantBankDetail merchantBankDetail) {
        LendingPancard lendingPancard = lendingPancardDao.findByMerchantId(merchantId);
        if (lendingPancard == null || lendingPancard.getName() == null) {// get data from signzy
            try {
                lendingPancard = fetchNameFromSignzy(panCard, merchantId);
            } catch (Exception e) {
                logger.error("Exception in Signzy pan fetch API---", e);
            }
        }
        if (lendingPancard == null || lendingPancard.getName() == null) {// get data from liquiloans
            try {
                lendingPancard = fetchNameFromLiquiloans(panCard, merchantId);
            } catch (Exception e) {
                logger.error("Exception in Liquiloans API---", e);
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
        setExperianApiParams(body, firstName, lastName, contact, panCard);
        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body,headers);
        Long a = DateTime.now().getMillis();
        logger.info("Experian request for merchant: {} is {}", merchantId, body.toString());
        String response = restTemplate.postForObject(ExperianConstants.SHORT_API_URL, request, String.class);
        Long b = DateTime.now().getMillis();
        logger.info("Experian API response time---" + (b-a) + "ms");
        try {
            JsonNode jsonNode = objectMapper.readTree(response);
            if (jsonNode == null || jsonNode.get("showHtmlReportForCreditReport").isNull()) {
                try {
                    experianService.insertExperianCallRecord(null, "SHORT_API_URL", objectMapper.writeValueAsString(request), merchantId, bpScore, panCard, contact);
                } catch (Exception e) {
                    logger.error("Error occured while inserting experian call record",e);
                }
                return null;
            }
            String xmlResponse = jsonNode.get("showHtmlReportForCreditReport").textValue().replaceAll("&amp;", "&").replaceAll("&gt;", ">").replaceAll("&lt;", "<").replaceAll("&quot;", "\"");
            //String xmlResponse = new String(Files.readAllBytes(Paths.get("/Users/admin/codebase/Lending/src/main/resources/experian_sample.txt")));
            JSONObject jsonObject = XML.toJSONObject(xmlResponse);
            try {
                experianService.insertExperianCallRecord(objectMapper.readTree(jsonObject.toString()).toString(), "SHORT_API_URL", objectMapper.writeValueAsString(request), merchantId, bpScore, panCard, contact);
            } catch (Exception e) {
                logger.error("Error occured while inserting experian call record",e);
            }
            return objectMapper.readTree(jsonObject.toString());
        } catch (Exception e) {
            emailHandler.sendEmail(new ArrayList<String>(){{add("khushal.virmani@bharatpe.com");}}, "Experian Short API Exception", "");
            logger.error("Exception while parsing experian response", e);
            logger.info("Experian response is---" + response);
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

    public String getFirstName(String name){
        if (name == null) {
            return "";
        }
        int lastIndexOfSpace = name.lastIndexOf(" ");
        if (lastIndexOfSpace != -1){
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

    public String getLastName(String name){
        if (name == null) {
            return "";
        }
        int lastIndexOfSpace = name.lastIndexOf(" ");
        if (lastIndexOfSpace != -1){
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
        List<String> ntcCategories = Arrays.asList("1N","2N","3N","4N");
        return ntcCategories.contains(experian.getCategory());
    }

    private boolean baseChecks(boolean isZomato, Merchant merchant, MerchantSummary merchantSummary, Experian experian, String lendingType, List<LendingPaymentSchedule> prevLoans, double bpScore, boolean yellowPincode) {
        if (!isZomato && checkFraud(merchantSummary)) {
            logger.info("Fraud Merchant, so rejecting merchant: {}", merchant.getId());
            experian.setCategory("1N");
            experian.setColor(ExperianConstants.COLOR.RED.name());
            experian.setReason(ExperianConstants.FRAUD);
            experianDao.save(experian);
            return false;
        }
        if (!isZomato && !exemptMerchant.contains(merchant.getId()) && checkOverdue(prevLoans)) {
            logger.info("Overdue Merchant, so rejecting merchant: {}", merchant.getId());
            experian.setCategory("1N");
            experian.setColor(ExperianConstants.COLOR.RED.name());
            experian.setReason(ExperianConstants.OVERDUE);
            experianDao.save(experian);
            return false;
        }
        if(lendingType.equalsIgnoreCase("CREDITLINE")) {
            if (bpScore <= 12D) {
                logger.info("BP Score less than 12, so rejecting merchant: {}", merchant.getId());
                experian.setCategory("1N");
                experian.setColor(ExperianConstants.COLOR.RED.name());
                experian.setReason(ExperianConstants.LOW_BP_SCORE);
                experianDao.save(experian);
                return false;
            }
        } else {
            if (!isZomato && !yellowPincode && bpScore <= 10D) {
                logger.info("BP Score less than 10, so rejecting merchant: {}", merchant.getId());
                experian.setCategory("1N");
                experian.setColor(ExperianConstants.COLOR.RED.name());
                experian.setReason(ExperianConstants.LOW_BP_SCORE);
                experianDao.save(experian);
                return false;
            }
            if (yellowPincode && bpScore < 12) {
                logger.info("BP Score less than 10, so rejecting merchant: {}", merchant.getId());
                experian.setCategory("1N");
                experian.setColor(ExperianConstants.COLOR.RED.name());
                experian.setReason(ExperianConstants.LOW_BP_SCORE);
                experianDao.save(experian);
                return false;
            }
        }
        if (!isZomato) {
            PaymentTransactionNew firstTransaction = paymentTransactionNewDao.getFirstTransaction(merchant.getId());
            if (firstTransaction == null || LoanUtil.getDateDiffInDays(firstTransaction.getCreatedAt(), new Date()) < 90) {
                logger.info("Vintage less than 3 months, so rejecting merchant: {}", merchant.getId());
                experian.setCategory("1N");
                experian.setColor(ExperianConstants.COLOR.RED.name());
                experian.setReason(ExperianConstants.VINTAGE);
                experianDao.save(experian);
                return false;
            }
        }
        return true;
    }
}
