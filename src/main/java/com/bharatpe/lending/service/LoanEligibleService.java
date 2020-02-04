package com.bharatpe.lending.service;

import com.bharatpe.common.dao.EligibleLoanDao;
import com.bharatpe.common.dao.ExperianAuditTrailDao;
import com.bharatpe.common.dao.ExperianDao;
import com.bharatpe.common.entities.*;
import com.bharatpe.common.enums.Loan;
import com.bharatpe.common.handlers.EmailHandler;
import com.bharatpe.lending.constant.ExperianConstants;
import com.bharatpe.lending.dao.LendingCategoryDao;
import com.bharatpe.lending.dao.LendingLedgerDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dto.LoanEligibilityDTO;
import com.bharatpe.lending.util.LoanCalculationUtil;
import com.bharatpe.lending.util.LoanUtil;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

@Service
public class LoanEligibleService {

    List<Integer> derogAccountStatus = Arrays.asList(93,89,93,97,97,97,97,30,31,32,33,35,37,38,39,41,42,43,44,45,47,49,50,51,53,54,55,56,57,58,59,60,61,62,63,64,65,66,67,68,69,70,72,73,74,75,76,77,79,81,85,86,87,88,94,90,91);
    List<Integer> derogUnsecuredProducts = Arrays.asList(5,10,36,37,38,39,43,51,52,53,54,55,56,57,58,60,61);
    List<String> emails = Arrays.asList("rajat.jain@bharatpe.com", "pawan@bharatpe.com", "puneet@bharatpe.com", "khushal.virmani@bharatpe.com", "nishit@bharatpe.com", "satyam@bharatpe.com");

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

    public List<LoanEligibilityDTO> getNewLoanDetails(Merchant merchant, Experian experian, MerchantSummary merchantSummary, MerchantBankDetail merchantBankDetail){
        Double bpScore = (merchantSummary != null && merchantSummary.getBpScore() != null) ? merchantSummary.getBpScore() : 0D;
        double tpvLast30Days = (merchantSummary != null && merchantSummary.getTpv1Mon() != null) ? merchantSummary.getTpv1Mon() : 0D;
        int txnLast30Days = (merchantSummary != null && merchantSummary.getTotalTxns1Month() != null) ? merchantSummary.getTotalTxns1Month() : 0;
        double avgTpv = (txnLast30Days > 0) ? tpvLast30Days/txnLast30Days : 0;
        List<LendingPaymentSchedule> prevLoans = lendingPaymentScheduleDao.findPreviousLoansByMerchant(merchant.getId());
        int loanCount = (prevLoans == null || prevLoans.isEmpty()) ? 0 : prevLoans.size();
        boolean repeatedLoan = loanCount > 0;
        String firstName = getFirstName(merchantBankDetail);
        String lastName = getLastName(merchantBankDetail);
        JsonNode experianResponse;
        boolean isEligibleForConstruct2And3 = isEligibleForConstruct2And3(merchantSummary, prevLoans);
        int previousLoanDays = (prevLoans != null && !prevLoans.isEmpty()) ? prevLoans.get(prevLoans.size() - 1).getEdiCount() : 0;
        experian.setReason(null);
        try {
            ExperianAuditTrail experianAuditTrail = experianAuditTrailDao.findLatestByMerchantId(merchant.getId());
            if (experianAuditTrail != null && experianAuditTrail.getResponse() != null && LoanUtil.getDateDiffInDays(experianAuditTrail.getCreatedAt(), new Date()) <= 45) {//get experian data from db if less than 45 days old
                experianResponse = objectMapper.readTree(experianAuditTrail.getResponse());
            } else {
                try {
                    experianResponse = fetchExperianDetails(firstName, lastName, merchant.getMobile(), experian.getPancardNumber());
                } catch (ResourceAccessException e) {
                    experianResponse = null;
                    if (experian.getRetryCount() != null && experian.getRetryCount() == 0) {
                        logger.error("Experian not responding---", e);
                        experian.setRetryCount(experian.getRetryCount() + 1);
                        experianDao.save(experian);
                        emailHandler.sendEmail(emails, "Experian APIs failing on PROD", "");
                        return new ArrayList<>();
                    } else if (experian.getRetryCount() != null && experian.getRetryCount() == 1) {
                        experianResponse = fetchExperianDetails(firstName, lastName, merchant.getMobile(), experian.getPancardNumber());
                    }
                }
            }
            if (experianResponse != null){
                experian.setResponse(experianResponse.toString());
                experian.setRetryCount(0);
                experianDao.save(experian);//updating response
            }
            if (bpScore <= 10D) {
                logger.info("BP Score less than 10, so rejecting merchant: {}", merchant.getId());
                experian.setCategory("1N");
                experian.setColor(ExperianConstants.COLOR.RED.name());
                experian.setReason(ExperianConstants.LOW_BP_SCORE);
                experianDao.save(experian);
                return new ArrayList<>();
            }
            if ((repeatedLoan && avgTpv < 35d) || (!repeatedLoan && avgTpv < 62d)){
                logger.info("Last 30 days tpv less than minimum required, so rejecting merchant: {}", merchant.getId());
                experian.setReason(ExperianConstants.LOW_TPV);
                experianDao.save(experian);
                return new ArrayList<>();
            }
            if (experianResponse != null && validatePancard(experianResponse, experian.getPancardNumber(), merchant.getId(), experian)){
                if (experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS") != null && experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS").isObject()){
                    JsonNode caisAccountDetails = experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS");
                    if (derogChecks(caisAccountDetails, merchant.getId(), experian)) {
                        logger.info("Derog check failed, rejecting merchant: {}", merchant.getId());
                        return new ArrayList<>();
                    }
                } else if (experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS") != null && experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS").isArray()){
                    for (JsonNode caisAccountDetails : experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS")) {
                        if (derogChecks(caisAccountDetails, merchant.getId(), experian)) {
                            logger.info("Derog check failed, rejecting merchant: {}", merchant.getId());
                            return new ArrayList<>();
                        }
                    }
                }
                //Not more than 4 unsecured loan enquiries in the last 6 months --- Derog check
                if (checkUnsecuredLoanEnquiriesInLast6Months(experianResponse)){
                    logger.info("Derog more than 4 unsecured loan enquiries in the last 6 months, rejecting merchant: {}", merchant.getId());
                    experian.setRejected(true);
                    experian.setReason(ExperianConstants.DEROG_UNSECURED_LOAN_ENQUIRY);
                    experianDao.save(experian);
                    return new ArrayList<>();
                }
                //Not more than 6 enquiries in the last 3 months ( across all product types) --- Derog check
                if (checkLoanEnquiriesInLast3Months(experianResponse)){
                    logger.info("Derog more than 6 enquiries in the last 3 months, rejecting merchant: {}", merchant.getId());
                    experian.setRejected(true);
                    experian.setReason(ExperianConstants.DEROG_MORE_THAN_6_LOAN_ENQUIRY);
                    experianDao.save(experian);
                    return new ArrayList<>();
                }
                return fetchBureauEligibleLoan(experianResponse, merchant.getId(), bpScore, experian, repeatedLoan, avgTpv, isEligibleForConstruct2And3, loanCount, previousLoanDays);
            }
        } catch (ResourceAccessException e) {
            logger.error("Experian not responding---", e);
            experian.setRetryCount(experian.getRetryCount() + 1);
            experianDao.save(experian);
            emailHandler.sendEmail(emails, "Experian APIs failing on PROD", "");
        } catch (Exception e) {
            logger.error("Exception while fetching experian details---", e);
        }
        logger.info("Experian Report not found for merchant: {}, Calculate NTC...", merchant.getId());
        //calculate NTC....
        return calculateNTC(bpScore, merchant.getId(), repeatedLoan, avgTpv, isEligibleForConstruct2And3, experian, loanCount, previousLoanDays);
    }

    private List<LoanEligibilityDTO> fetchBureauEligibleLoan(JsonNode experianResponse, Long merchantId, Double bpScore, Experian experian, boolean repeatedLoan, double avgTpv, boolean isEligibleForConstruct2And3, int loanCount, int previousLoanDays) {
        int bureauVintage = fetchBureauVintage(experianResponse);//months
        String accountCategory = fetchAccountCategory(experianResponse);// A,B,C or NTC
        if (accountCategory.equals("NTC")){
            logger.info("Loan category is NTC for merchant: {}, Calculate NTC...", merchantId);
            return calculateNTC(bpScore, merchantId, repeatedLoan, avgTpv, isEligibleForConstruct2And3, experian, loanCount, previousLoanDays);
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
        return calculateEligibleLoans(avgTpv, repeatedLoan, color, isEligibleForConstruct2And3, false, previousLoanDays, merchantId, experian.getId());
    }

    private List<LoanEligibilityDTO> calculateNTC(Double bpScore, Long merchantId, boolean repeatedLoan, double avgTpv, boolean isEligibleForConstruct2And3, Experian experian, int loanCount, int previousLoanDays) {
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
        return loanCount > 2 ? calculateEligibleLoans(avgTpv, repeatedLoan, color, isEligibleForConstruct2And3, false, previousLoanDays, merchantId, experian.getId()) : calculateEligibleLoans(avgTpv, repeatedLoan, color, isEligibleForConstruct2And3, true, previousLoanDays, merchantId, experian.getId());
    }

    private List<LoanEligibilityDTO> calculateEligibleLoans(double avgTpv, boolean repeatedLoan, String color, boolean isEligibleForConstruct2And3, boolean isNTC, int previousLoanDays, Long merchantId, Long experianId) {
        String masterCategory = getMasterCategory(color, isNTC, repeatedLoan);
        List<LendingCategories> lendingCategories;
        String type;
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
            logger.error("No active lending category found");
            return new ArrayList<>();
        } else {
            eligibleLoanDao.deleteByMerchantId(merchantId);
            List<LoanEligibilityDTO> loanEligibilityDTOList = new ArrayList<>();
            for (LendingCategories lendingCategory : lendingCategories) {
                loanEligibilityDTOList.add(calculateLoanBreakup(lendingCategory, avgTpv, type, merchantId, experianId));
            }
            loanEligibilityDTOList.sort(Comparator.comparing(LoanEligibilityDTO::getAmount).reversed());
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

    private LoanEligibilityDTO calculateLoanBreakup(LendingCategories lendingCategories, double avgTpv, String type, Long merchantId, Long experianId) {
        double percentage = lendingCategories.getMultiplier();
        double interest = lendingCategories.getInterestRate();
        int tenure = Math.round(lendingCategories.getTenureMonths());
        int ioTenure = Math.round(lendingCategories.getIoTenureMonths());
        int maxAmount = lendingCategories.getMaxTpvAmount();
        int ioPayableDays = lendingCategories.getIoPayableDays();
        String construct = lendingCategories.getLoanConstruct();
        String category = lendingCategories.getCategory();
        String payableConverter = lendingCategories.getPayableConverter();
        int ioEdiDays = construct.equalsIgnoreCase("CONSTRUCT_3") ? 30 : 0;
        LoanCalculationUtil.LoanBreakupDetail breakup = getBreakup(tenure, construct, type, avgTpv, percentage, interest, maxAmount, ioTenure, ioPayableDays);
        eligibleLoanDao.save(new EligibleLoan(merchantId, experianId, (double)breakup.getLoanAmount(), payableConverter, "ACTIVE", category, ioEdiDays, 0, avgTpv, breakup.getEdi(), breakup.getIoEdi(), breakup.getRepayment(), construct));
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
        loanAmount = roundDown(Math.min(repayment / (1 + (interest/100)*tenure), maxAmount));// round down
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

    private double roundDown(double loanAmount) {
        if (loanAmount < 20000) {
            return loanAmount - (loanAmount % 1000);
        } else if (loanAmount < 100000) {
            return loanAmount - (loanAmount % 5000);
        } else {
            return loanAmount - (loanAmount % 10000);
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
        loanEligibilityDTO.setList(LoanCalculationUtil.prepareLabels(breakup));
        loanEligibilityDTO.setType(breakup.getType());
        loanEligibilityDTO.setOptionEnable(true);
        return loanEligibilityDTO;
    }

    private int getEdiDays(int tenure){
        switch (tenure){
            case 1: return 26;
            case 3: return 77;
            case 6: return 155;
            case 9: return 234;
            default: return 311;//12 months
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
            return getOvershootPeriod(prevLoans.get(prevLoans.size() - 1)) <= 5;
        } catch(Exception ex){
            logger.error("Error while fetching eligiblity for construct 2 and 3", ex);
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

    private String calculateSegment(int bureauVintage, String accountCategory, Double bpScore) {
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

    private String fetchAccountCategory(JsonNode experianResponse) {
        List<Integer> categoryA = Arrays.asList(6,7,13,38,39,43);
        List<Integer> categoryB = Arrays.asList(1,5,8,9,10,11,12,17,32,33,34,36,37,51,52,53,54,55,56,57,58,59,60,61);
        List<Integer> categoryC = Arrays.asList(2,3);
        boolean a=false, b=false, c=false;
        if (experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS") != null && experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS").isArray()){
            for (JsonNode jsonNode : experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS")) {
                if (categoryA.contains(jsonNode.get("Account_Type").intValue())) { a = true;}
                if (categoryB.contains(jsonNode.get("Account_Type").intValue())) { b = true;}
                if (categoryC.contains(jsonNode.get("Account_Type").intValue())) { c = true;}
            }
        } else if (experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS") != null && experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS").isObject()){
            JsonNode jsonNode = experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS");
            if (categoryA.contains(jsonNode.get("Account_Type").intValue())) { a = true;}
            if (categoryB.contains(jsonNode.get("Account_Type").intValue())) { b = true;}
            if (categoryC.contains(jsonNode.get("Account_Type").intValue())) { c = true;}
        }
        return c ? "C" : b ? "B" : a ? "A" : "NTC";
    }

    private int fetchBureauVintage(JsonNode experianResponse) {
        DateTimeFormatter formatter = DateTimeFormat.forPattern("yyyyMMdd");
        DateTime min = new DateTime();
        if (experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS") != null && experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS").isArray()){
            for (JsonNode jsonNode : experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS")) {
                min = formatter.parseDateTime(jsonNode.get("Open_Date").toString()).isBefore(min) ? formatter.parseDateTime(jsonNode.get("Open_Date").toString()) : min;
            }
            return Months.monthsBetween(min, DateTime.now()).getMonths();
        } else if (experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS") != null && experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS").isObject()){
            JsonNode jsonNode = experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS");
            min = formatter.parseDateTime(jsonNode.get("Open_Date").toString()).isBefore(min) ? formatter.parseDateTime(jsonNode.get("Open_Date").toString()) : min;
            return Months.monthsBetween(min, DateTime.now()).getMonths();
        }
        return 0;
    }

    private boolean derogChecks(JsonNode jsonNode, Long merchantId, Experian experian) {
        //Check for Derog Account Status
        if (jsonNode.get("Account_Status") != null && derogAccountStatus.contains(jsonNode.get("Account_Status").intValue())){
            logger.info("Derog Account Status check failed, rejecting merchant: {}", merchantId);
            experian.setRejected(true);
            experian.setReason(ExperianConstants.DEROG_ACCOUNT_STATUS);
            experianDao.save(experian);
            return true;
        }
        //Check for Derog DPD Last 3 months
        if (jsonNode.get("AccountHoldertypeCode").intValue() != 7 && checkDPDLastXmonths(jsonNode, 3)){
            logger.info("Derog DPD Last 3 months check failed, rejecting merchant: {}", merchantId);
            experian.setRejected(true);
            experian.setReason(ExperianConstants.DEROG_DPD_LAST_3_MONTHS);
            experianDao.save(experian);
            return true;
        }
        //Check for Derog DPD Last 6 months
        if (jsonNode.get("AccountHoldertypeCode").intValue() != 7 && checkDPDLastXmonths(jsonNode, 6)){
            logger.info("Derog DPD Last 6 months check failed, rejecting merchant: {}", merchantId);
            experian.setRejected(true);
            experian.setReason(ExperianConstants.DEROG_DPD_LAST_6_MONTHS);
            experianDao.save(experian);
            return true;
        }
        //Check for Derog DPD Last 12 months
        if (jsonNode.get("AccountHoldertypeCode").intValue() != 7 && checkDPDLastXmonths(jsonNode, 12)){
            logger.info("Derog DPD Last 12 months check failed, rejecting merchant: {}", merchantId);
            experian.setRejected(true);
            experian.setReason(ExperianConstants.DEROG_DPD_LAST_12_MONTHS);
            experianDao.save(experian);
            return true;
        }
        //Check for Derog DPD Last 24 months
        if (jsonNode.get("AccountHoldertypeCode").intValue() != 7 && checkDPDLastXmonths(jsonNode, 24)){
            logger.info("Derog DPD Last 24 months check failed, rejecting merchant: {}", merchantId);
            experian.setRejected(true);
            experian.setReason(ExperianConstants.DEROG_DPD_LAST_24_MONTHS);
            experianDao.save(experian);
            return true;
        }
        //Check for Derog DPD Older than 24 months
        if (jsonNode.get("AccountHoldertypeCode").intValue() != 7 && checkDPDOlderThan24months(jsonNode)){
            logger.info("Derog DPD Older than 24 months check failed, rejecting merchant: {}", merchantId);
            experian.setRejected(true);
            experian.setReason(ExperianConstants.DEROG_DPD_OLDER_THAN_24_MONTHS);
            experianDao.save(experian);
            return true;
        }
        //Not more than 3 live unsecured loans running
        if (checkUnsecuredLiveLoans(jsonNode)) {
            logger.info("Derog more than 3 live unsecured loans running, rejecting merchant: {}", merchantId);
            experian.setRejected(true);
            experian.setReason(ExperianConstants.DEROG_UNSECURED_LOANS);
            experianDao.save(experian);
            return true;
        }
        return false;
    }

    private boolean checkUnsecuredLiveLoans(JsonNode jsonNode) {
        return jsonNode.get("Date_Closed").toString().equals("\"\"") && jsonNode.get("Account_Type").intValue() != 10 && derogUnsecuredProducts.contains(jsonNode.get("Account_Type").intValue());
    }

    private boolean checkLoanEnquiriesInLast3Months(JsonNode experianResponse) {
        return experianResponse.get("INProfileResponse").get("TotalCAPS_Summary").get("TotalCAPSLast90Days").intValue() > 6;
    }

    private boolean checkUnsecuredLoanEnquiriesInLast6Months(JsonNode experianResponse) {
        if (experianResponse.get("INProfileResponse").get("TotalCAPS_Summary").get("TotalCAPSLast180Days").intValue() <= 4){
            return false;
        }
        Calendar c = Calendar.getInstance();
        c.add(Calendar.MONTH, -6);
        String month = (c.get(Calendar.MONTH) + 1) < 10 ? "0" + (c.get(Calendar.MONTH) + 1) : (c.get(Calendar.MONTH) + 1) + "";
        String day = (c.get(Calendar.DAY_OF_MONTH) + 1) < 10 ? "0" + (c.get(Calendar.DAY_OF_MONTH) + 1) : (c.get(Calendar.DAY_OF_MONTH) + 1) + "";
        long previous6MonthDate = Long.parseLong(c.get(Calendar.YEAR) + month + day);
        if (experianResponse.get("INProfileResponse").get("CAPS").get("CAPS_Application_Details") != null && experianResponse.get("INProfileResponse").get("CAPS").get("CAPS_Application_Details").isObject()) {
            JsonNode jsonNode = experianResponse.get("INProfileResponse").get("CAPS").get("CAPS_Application_Details");
            return derogUnsecuredProducts.contains(jsonNode.get("Product").intValue()) && jsonNode.get("Date_of_Request").longValue() >= previous6MonthDate;
        } else if (experianResponse.get("INProfileResponse").get("CAPS").get("CAPS_Application_Details") != null && experianResponse.get("INProfileResponse").get("CAPS").get("CAPS_Application_Details").isArray()) {
            for (JsonNode jsonNode : experianResponse.get("INProfileResponse").get("CAPS").get("CAPS_Application_Details")) {
                if (derogUnsecuredProducts.contains(jsonNode.get("Product").intValue()) && jsonNode.get("Date_of_Request").longValue() >= previous6MonthDate) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean checkDPDLastXmonths(JsonNode jsonNode, int months){
        List<String> monthYear = new ArrayList<>();
        Calendar c = Calendar.getInstance();
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
                if (monthYear.contains(cais_account_history.get("Month") + "$" + cais_account_history.get("Year")) && !cais_account_history.get("Days_Past_Due").isNull() && cais_account_history.get("Days_Past_Due").intValue() >= dpd) {
                    return true;
                }
            }
        } else if (jsonNode.get("CAIS_Account_History") != null && jsonNode.get("CAIS_Account_History").isObject()){
            JsonNode cais_account_history = jsonNode.get("CAIS_Account_History");
            return monthYear.contains(cais_account_history.get("Month") + "$" + cais_account_history.get("Year")) && !cais_account_history.get("Days_Past_Due").isNull() && cais_account_history.get("Days_Past_Due").intValue() >= dpd;
        }
        return false;
    }

    //No 60DPD in any month older than 24 months, for cases where no recent loan is there
    private boolean checkDPDOlderThan24months(JsonNode jsonNode){
        if (!checkDPDLastXmonths(jsonNode, 24)){
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
                    if (!cais_account_history.get("Days_Past_Due").isNull() && cais_account_history.get("Days_Past_Due").intValue() >= 60) {
                        return true;
                    }
                }
            } else if (jsonNode.get("CAIS_Account_History") != null && jsonNode.get("CAIS_Account_History").isObject()){
                JsonNode cais_account_history = jsonNode.get("CAIS_Account_History");
                if (monthYear.contains(cais_account_history.get("Month") + "$" + cais_account_history.get("Year"))) {
                    return false;//active loan found in last 24 months without any DPD then return false
                }
                return !cais_account_history.get("Days_Past_Due").isNull() && cais_account_history.get("Days_Past_Due").intValue() >= 60;
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
                    if (jsonNode.get("CAIS_Holder_Details").get("Income_TAX_PAN").textValue().equalsIgnoreCase(panCard)) {
                        String merchantName = jsonNode.get("CAIS_Holder_Details").get("First_Name_Non_Normalized").textValue() + " " + jsonNode.get("CAIS_Holder_Details").get("Middle_Name_1_Non_Normalized").textValue() + " " + jsonNode.get("CAIS_Holder_Details").get("Surname_Non_Normalized").textValue();
                        experian.setMerchantName(merchantName);
                        experianDao.save(experian);
                        return true;
                    }
                } else if (jsonNode.get("CAIS_Holder_Details") != null && jsonNode.get("CAIS_Holder_Details").isArray()) {
                    for (JsonNode node : jsonNode.get("CAIS_Holder_Details")) {
                        if (node.get("Income_TAX_PAN").textValue().equalsIgnoreCase(panCard)) {
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
                if (jsonNode.get("CAIS_Holder_Details").get("Income_TAX_PAN").textValue().equalsIgnoreCase(panCard)) {
                    String merchantName = jsonNode.get("CAIS_Holder_Details").get("First_Name_Non_Normalized").textValue() + " " + jsonNode.get("CAIS_Holder_Details").get("Middle_Name_1_Non_Normalized").textValue() + " " + jsonNode.get("CAIS_Holder_Details").get("Surname_Non_Normalized").textValue();
                    experian.setMerchantName(merchantName);
                    experianDao.save(experian);
                    return true;
                }
            } else if (jsonNode.get("CAIS_Holder_Details") != null && jsonNode.get("CAIS_Holder_Details").isArray()) {
                for (JsonNode node : jsonNode.get("CAIS_Holder_Details")) {
                    if (node.get("Income_TAX_PAN").textValue().equalsIgnoreCase(panCard)) {
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


    private JsonNode fetchExperianDetails(String firstName, String lastName, String contact, String panCard) throws IOException {
        Long a = DateTime.now().getMillis();
        if (contact.length() > 10) {
            contact = contact.substring(2);//remove 91
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(headers);
        String response = restTemplate.postForObject("https://consumer.experian.in:8443/ECV-P2/content/enhancedMatch.action?clientName=BHARATPE_EM&allowInput=1&allowEdit=1&allowCaptcha=1&allowConsent=1&allowEmailVerify=1&allowVoucher=1&voucherCode=BharatPe214K2&firstName=" + firstName + "&surName=" + lastName + "&mobileNo=" + contact + "&noValidationByPass=0&emailConditionalByPass=1&pan=" + panCard + "", request, String.class);
        JsonNode jsonNode = objectMapper.readTree(response);
        if (jsonNode == null || jsonNode.get("showHtmlReportForCreditReport").isNull()) {
            return null;
        }
        String xmlResponse = jsonNode.get("showHtmlReportForCreditReport").textValue().replaceAll("&amp;", "&").replaceAll("&gt;",">").replaceAll("&lt;","<").replaceAll("&quot;","\"");
        //String xmlResponse = new String(Files.readAllBytes(Paths.get("/Users/admin/codebase/Lending/src/main/resources/experian_sample.txt")));
        JSONObject jsonObject = XML.toJSONObject(xmlResponse);
        Long b = DateTime.now().getMillis();
        logger.info("Experian API response time---" + (b-a) + "ms");
        return objectMapper.readTree(jsonObject.toString());
    }

    private String getFirstName(MerchantBankDetail merchantBankDetail){
        if (merchantBankDetail != null && merchantBankDetail.getBeneficiaryName() != null){
            int lastIndexOfSpace = merchantBankDetail.getBeneficiaryName().lastIndexOf(" ");
            if (lastIndexOfSpace != -1){
                return merchantBankDetail.getBeneficiaryName().substring(0, lastIndexOfSpace);
            } else {
                return merchantBankDetail.getBeneficiaryName();
            }
        }
        return "";
    }

    private String getLastName(MerchantBankDetail merchantBankDetail){
        if (merchantBankDetail != null && merchantBankDetail.getBeneficiaryName() != null){
            int lastIndexOfSpace = merchantBankDetail.getBeneficiaryName().lastIndexOf(" ");
            if (lastIndexOfSpace != -1){
                return merchantBankDetail.getBeneficiaryName().substring(lastIndexOfSpace + 1);
            } else {
                return  "";
            }
        }
        return "";
    }
}
