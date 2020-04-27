package com.bharatpe.lending.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Optional;

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
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.bharatpe.common.dao.EligibleLoanDao;
import com.bharatpe.common.dao.ExperianAuditTrailDao;
import com.bharatpe.common.dao.ExperianDao;
import com.bharatpe.common.dao.ExperianDetailsDao;
import com.bharatpe.common.dao.ExternalGatewayDao;
import com.bharatpe.common.dao.LendingPancardDao;
import com.bharatpe.common.dao.MerchantBankDetailDao;
import com.bharatpe.common.dao.MerchantDao;
import com.bharatpe.common.dao.MerchantSummaryDao;
import com.bharatpe.common.dao.MerchantSummarySnapshotDao;
import com.bharatpe.common.entities.AvailableLoan;
import com.bharatpe.common.entities.EligibleLoan;
import com.bharatpe.common.entities.Experian;
import com.bharatpe.common.entities.ExperianAuditTrail;
import com.bharatpe.common.entities.ExperianDetails;
import com.bharatpe.common.entities.LendingCategories;
import com.bharatpe.common.entities.LendingPancard;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.common.entities.Merchant;
import com.bharatpe.common.entities.MerchantBankDetail;
import com.bharatpe.common.entities.MerchantSummary;
import com.bharatpe.common.entities.MerchantSummarySnapshot;
import com.bharatpe.common.enums.Status.LendingStatus;
import com.bharatpe.common.handlers.EmailHandler;
import com.bharatpe.common.utils.AesEncryption;
import com.bharatpe.common.utils.HmacCalculator;
import com.bharatpe.lending.constant.ExperianConstants;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingCategoryDao;
import com.bharatpe.lending.dao.LendingLedgerDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dto.LoanEligibilityDTO;
import com.bharatpe.lending.util.LoanCalculationUtil;
import com.bharatpe.lending.util.LoanUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class TopupLoanEligibleService {

    List<Integer> derogAccountStatus = Arrays.asList(93,89,93,97,97,97,97,30,31,32,33,35,37,38,39,41,42,43,44,45,47,49,50,51,53,54,55,56,57,58,59,60,61,62,63,64,65,66,67,68,69,70,72,73,74,75,76,77,79,81,85,86,87,88,94,90,91);
    List<Integer> derogUnsecuredProducts = Arrays.asList(5,10,36,37,38,39,43,51,52,53,54,55,56,57,58,60,61);
    List<String> emails = Arrays.asList("rajat.jain@bharatpe.com", "khushal.virmani@bharatpe.com", "puneet.arora@bharatpe.com");

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
    LiquiloansService liquiloansService;
    
    @Autowired
    MerchantSummarySnapshotDao merchantSummarySnapshotDao;
    
	@Autowired
	MerchantSummaryDao merchantSummaryDao;
	
	@Autowired
	MerchantBankDetailDao merchantBankDetailDao;
	
	@Autowired
	MerchantDao merchantDao;
    
    public void generateTopupLoan(Long merchantId) {
    	 try {
    		 Optional<Merchant> merchant = merchantDao.findById(merchantId);
    		 if(!merchant.isPresent()) {
    			 logger.error("No merchant found with merchant id {}", merchantId);
    			 return;
    		 }
    		 
    		 MerchantSummary merchantSummary = merchantSummaryDao.getByMerchantId(merchant.get().getId());
    		 Experian experian = experianDao.getByMerchantId(merchant.get().getId());
    		 MerchantBankDetail merchantBankDetail = merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchant.get().getId(), "ACTIVE");
    		 List<LendingPaymentSchedule> lendingPaymentScheduleList = lendingPaymentScheduleDao.findByMerchantIdOrderByIdDesc(merchant.get().getId());
    		 getTopupLoanDetails(merchant.get(), experian, merchantSummary, merchantBankDetail, lendingPaymentScheduleList);
    	 } catch(Exception ex) {
    		 logger.error("Exception while generating new loans for merchant with ID {}, Exception is {}", merchantId, ex);
    	 }
    }
    
    public List<LoanEligibilityDTO> getTopupLoanDetails(Merchant merchant, Experian experian, MerchantSummary merchantSummary, MerchantBankDetail merchantBankDetail, List<LendingPaymentSchedule> lendingPaymentScheduleList){
        Double bpScore = (merchantSummary != null && merchantSummary.getBpScore() != null) ? merchantSummary.getBpScore() : 0D;
        double tpvLast30Days = (merchantSummary != null && merchantSummary.getTpv1Mon() != null) ? merchantSummary.getTpv1Mon() : 0D;
        int txnLast30Days = 30;
        double avgTpv = tpvLast30Days/txnLast30Days;
        
        if(lendingPaymentScheduleList == null || lendingPaymentScheduleList.size() < 1) {
        	logger.info("No previous loan for merchant ID {}", merchant.getId());
        	return new ArrayList<>();
        }
        
        int loanCount = lendingPaymentScheduleList.size();
        LendingPaymentSchedule activeLoan = getActiveLoan(lendingPaymentScheduleList);
        
        if(activeLoan == null) {
        	logger.info("No active loan for merchant ID {}", merchant.getId());
        	return new ArrayList<>();
        }
        
        double paidRatio = activeLoan.getPaidAmount() / activeLoan.getTotalPayableAmount();
        
        if(paidRatio < 0.40D) {
        	logger.info("Amount paid is less than 40% for merchant ID {}", merchant.getId());
        	return new ArrayList<>();
        }
        
        double dpd = activeLoan.getDueAmount() / activeLoan.getEdiAmount();
        
        if(dpd > 5D) {
        	logger.info("DPD is greater than 5 for merchant ID {}", merchant.getId());
        	return new ArrayList<>();
        }
        
        if(activeLoan.getEdiCount() < 77) {
        	logger.info("Active loan tenure is less than 3 months for merchant ID {}", merchant.getId());
        	return new ArrayList<>();
        }
        
        MerchantSummarySnapshot merchantSummarySnapshot = merchantSummarySnapshotDao.findByApplication(activeLoan.getLoanApplication());
        if(merchantSummarySnapshot != null && merchantSummarySnapshot.getBpScore() != null && merchantSummarySnapshot.getBpScore() < merchantSummary.getBpScore()) {
        	logger.info("BP Score reduced for merchant ID {} from the BP score at the last loan application.", merchant.getId());
        	return new ArrayList<>();
        }
        
        LendingPancard lendingPancard = lendingPancardDao.findByMerchantId(merchant.getId());
        if (lendingPancard == null && bpScore > 10D) { // get data from liquiloans
            try {
                lendingPancard = liquiloansService.fetchNameOnPancard(experian.getPancardNumber(), merchant.getId());
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
        
        JsonNode experianResponse;
        experian.setReason(null);
        if (checkFraud(merchantSummary)) {
            logger.info("Fraud Merchant, so rejecting merchant: {}", merchant.getId());
            experian.setCategory("1N");
            experian.setColor(ExperianConstants.COLOR.RED.name());
            experian.setReason(ExperianConstants.FRAUD);
            experianDao.save(experian);
            return new ArrayList<>();
        }
        
        if (bpScore <= 10D) {
            logger.info("BP Score less than 10, so rejecting merchant: {}", merchant.getId());
            experian.setCategory("1N");
            experian.setColor(ExperianConstants.COLOR.RED.name());
            experian.setReason(ExperianConstants.LOW_BP_SCORE);
            experianDao.save(experian);
            return new ArrayList<>();
        }
        
        if (avgTpv < 35d) {
            logger.info("Last 30 days tpv less than minimum required, so rejecting merchant: {}", merchant.getId());
            experian.setReason(ExperianConstants.LOW_TPV);
            experianDao.save(experian);
            return new ArrayList<>();
        }
        
        try {
            ExperianAuditTrail experianAuditTrail = experianAuditTrailDao.findLatestByMerchantId(merchant.getId());
            if (experianAuditTrail != null && experianAuditTrail.getResponse() != null && LoanUtil.getDateDiffInDays(experianAuditTrail.getCreatedAt(), new Date()) <= 45) {//get experian data from db if less than 45 days old
                experianResponse = objectMapper.readTree(experianAuditTrail.getResponse());
            } else {
                try {
                    experianResponse = fetchExperianDetails(firstName, lastName, merchant.getMobile(), experian.getPancardNumber(), merchant.getId());
                } catch (ResourceAccessException e) {
                    experianResponse = null;
                    logger.error("Experian not responding---", e);
                    if (experian.getRetryCount() != null && experian.getRetryCount() == 0) {
                        logger.error("Experian timeout for merchant: {}, firstname: {}, lastname:{}, pancard: {}", merchant.getId(), firstName,lastName,experian.getPancardNumber());
                        experian.setRetryCount(experian.getRetryCount() + 1);
                        experianDao.save(experian);
                        //emailHandler.sendEmail(emails, "Experian APIs failing on PROD", "");
                        return new ArrayList<>();
                    } else if (experian.getRetryCount() != null && experian.getRetryCount() == 1) {
                        experianResponse = fetchExperianDetails(firstName, lastName, merchant.getMobile(), experian.getPancardNumber(), merchant.getId());
                    }
                }
            }
            experian.setRetryCount(0);
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
            } else if (experianDetails == null) {
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
                    if (experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS") != null && experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS").isObject()) {
                        JsonNode caisAccountDetails = experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS");
                        if (derogChecks(caisAccountDetails, merchant.getId(), experian)) {
                            logger.info("Derog check failed, rejecting merchant: {}", merchant.getId());
                            return new ArrayList<>();
                        }
                    } else if (experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS") != null && experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS").isArray()) {
                        int unsecuredLoanCount = 0;
                        for (JsonNode caisAccountDetails : experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS")) {
                            if (derogChecks(caisAccountDetails, merchant.getId(), experian)) {
                                logger.info("Derog check failed, rejecting merchant: {}", merchant.getId());
                                return new ArrayList<>();
                            }
                            if (checkUnsecuredLiveLoans(caisAccountDetails)) {
                                unsecuredLoanCount++;
                            }
                        }
                        //Not more than 3 live unsecured loans running
                        if (unsecuredLoanCount > 3) {
                            logger.info("Derog more than 3 live unsecured loans running, rejecting merchant: {}", merchant.getId());
                            experian.setRejected(true);
                            experian.setReason(ExperianConstants.DEROG_UNSECURED_LOANS);
                            experianDao.save(experian);
                            return new ArrayList<>();
                        }
                    }
                    //Not more than 4 unsecured loan enquiries in the last 6 months --- Derog check
                    if (checkUnsecuredLoanEnquiriesInLast6Months(experianResponse)) {
                        logger.info("Derog more than 4 unsecured loan enquiries in the last 6 months, rejecting merchant: {}", merchant.getId());
                        experian.setRejected(true);
                        experian.setReason(ExperianConstants.DEROG_UNSECURED_LOAN_ENQUIRY);
                        experianDao.save(experian);
                        return new ArrayList<>();
                    }
                    //Not more than 6 enquiries in the last 3 months ( across all product types) --- Derog check
                    if (checkLoanEnquiriesInLast3Months(experianResponse)) {
                        logger.info("Derog more than 6 enquiries in the last 3 months, rejecting merchant: {}", merchant.getId());
                        experian.setRejected(true);
                        experian.setReason(ExperianConstants.DEROG_MORE_THAN_6_LOAN_ENQUIRY);
                        experianDao.save(experian);
                        return new ArrayList<>();
                    }
                } catch (Exception e) {
                    logger.info("Exception while checking derog for merchant: {}", merchant.getId());
                    logger.error("Exception---", e);
                }
                return fetchBureauEligibleLoan(experianResponse, merchant.getId(), bpScore, experian, avgTpv, activeLoan, loanCount);
            }
        } catch (ResourceAccessException e) {
            logger.error("Experian not responding---", e);
            logger.error("Experian timeout for merchant: {}, firstname: {}, lastname:{}, pancard: {}", merchant.getId(), firstName,lastName,experian.getPancardNumber());
            experian.setRetryCount(experian.getRetryCount() + 1);
            experianDao.save(experian);
            emailHandler.sendEmail(emails, "Experian APIs failing on PROD", "Failed for merchant: "+merchant.getId());
        } catch (Exception e) {
            logger.error("Exception while fetching experian details---", e);
        }
        logger.info("Experian Report not found for merchant: {}, Calculate NTC...", merchant.getId());
        //calculate NTC....
        return calculateNTC(bpScore, merchant.getId(), avgTpv, experian, activeLoan, loanCount);
    }

    private boolean checkFraud(MerchantSummary merchantSummary) {
        return (merchantSummary != null && merchantSummary.getUniqueCustomer1mon() != null && merchantSummary.getUniqueCustomer1mon() < 15)
                || (merchantSummary != null && merchantSummary.getFraudCustomer() != null);
    }

    private List<LoanEligibilityDTO> fetchBureauEligibleLoan(JsonNode experianResponse, Long merchantId, Double bpScore, Experian experian, double avgTpv, LendingPaymentSchedule activeLoan, int loanCount) {
        int bureauVintage = fetchBureauVintage(experianResponse);//months
        String accountCategory = fetchAccountCategory(experianResponse);// A,B,C or NTC
        if (accountCategory.equals("NTC")){
            logger.info("Loan category is NTC for merchant: {}, Calculate NTC...", merchantId);
            return calculateNTC(bpScore, merchantId, avgTpv, experian, activeLoan, loanCount);
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
        return calculateEligibleLoans(avgTpv, true, color, false, merchantId, experian.getId(), activeLoan);
    }

    private List<LoanEligibilityDTO> calculateNTC(Double bpScore, Long merchantId, double avgTpv, Experian experian, LendingPaymentSchedule activeLoan, int loanCount) {
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
        return loanCount > 2 ? calculateEligibleLoans(avgTpv, true, color, false, merchantId, experian.getId(), activeLoan) : calculateEligibleLoans(avgTpv, true, color, true, merchantId, experian.getId(), activeLoan);
    }

    private List<LoanEligibilityDTO> calculateEligibleLoans(double avgTpv, boolean repeatedLoan, String color, boolean isNTC, Long merchantId, Long experianId, LendingPaymentSchedule activeLoan) {
        logger.info("Calculating offers for merchant: {}", merchantId);
        String masterCategory = getMasterCategory(color, isNTC, repeatedLoan);
        logger.info("Master Category for merchant: {} is {}", merchantId, masterCategory);
        List<LendingCategories> lendingCategories;
        String type = null;
        double prevLoanAmount = 0d;
        if (activeLoan.getLoanApplication() != null) {
            switch (color){
                case "AMBER": prevLoanAmount = activeLoan.getLoanApplication().getLoanAmount() * 1.1;break;
                case "LIGHT_GREEN": prevLoanAmount = activeLoan.getLoanApplication().getLoanAmount() * 1.25;break;
                case "DARK_GREEN": prevLoanAmount = activeLoan.getLoanApplication().getLoanAmount() * 1.5;break;
            }
        }
        
        double paidRatio = activeLoan.getPaidAmount() / activeLoan.getTotalPayableAmount();
        logger.info("Paid Ration : {}", paidRatio);
        if("AMBER".equals(color)) {
        	logger.info("Color AMBER for merchant ID {}, Not eligible for topup loan.", merchantId);
        	return new ArrayList<>();
        } else if ("LIGHT_GREEN".equals(color)) {
        	if(paidRatio < 0.50D || activeLoan.getEdiCount() < 77) {
        		logger.info("Color LIGHT_GREEN : paid amount less than 50% or edi count < 77  for merchant ID {}", merchantId);
        		return new ArrayList<>();
        	}
        } else if ("DARK_GREEN".equals(color)) {
        	if(paidRatio < 0.40D || activeLoan.getEdiCount() < 77) {
        		logger.info("Color DARK_GREEN : paid amount less than 40% or edi count < 77  for merchant ID {}", merchantId);
        		return new ArrayList<>();
        	}
        } else if ("RED".equals(color)){
        	logger.info("Color RED for merchant ID {}, not eligible", merchantId);
        	return new ArrayList<>();
        }
        
        
        lendingCategories = lendingCategoryDao.getByMasterCategoryForConstruct1(masterCategory);
        if (lendingCategories.isEmpty()) {
            logger.error("No active lending category found for merchant: {}", merchantId);
            return new ArrayList<>();
        } else {
            logger.info("Deleting eligible loans for merchant: {}", merchantId);
            eligibleLoanDao.deleteByMerchantId(merchantId);
            List<LoanEligibilityDTO> loanEligibilityDTOList = new ArrayList<>();
            Double prevLoanUnpaidAmount = (activeLoan.getLoanAmount() - activeLoan.getPaidPrinciple()) + activeLoan.getDueAmount();
            for (LendingCategories lendingCategory : lendingCategories) {
                LoanEligibilityDTO loanEligibilityDTO = calculateLoanBreakup(lendingCategory, avgTpv, type, merchantId, experianId, prevLoanAmount, color);
                if (loanEligibilityDTO != null) {
                	loanEligibilityDTO.setPrevLoanUnpaidAmount(prevLoanUnpaidAmount.intValue());
                	loanEligibilityDTO.setDisbursementAmount(loanEligibilityDTO.getDisbursementAmount() - prevLoanUnpaidAmount.intValue());
                    loanEligibilityDTOList.add(loanEligibilityDTO);
                } else {
                    logger.info("loan offer is null for merchant: {}", merchantId);
                }
            }
            loanEligibilityDTOList.sort(Comparator.comparing(LoanEligibilityDTO::getAmount).reversed());
            if (activeLoan.getLoanApplication() != null && activeLoan.getLoanApplication().getCategory() != null && (loanEligibilityDTOList.isEmpty() || (loanEligibilityDTOList.get(0).getAmount() < prevLoanAmount))) {
                List<LendingCategories> lendingCategoriesList = lendingCategoryDao.findByCategory(activeLoan.getLoanApplication().getCategory());
                if (lendingCategoriesList != null && !lendingCategoriesList.isEmpty()) {
                    LoanEligibilityDTO loanEligibilityDTO = calculateLoanBreakup(lendingCategoriesList.get(0), 0, type, merchantId, experianId, prevLoanAmount, color);
                    if (loanEligibilityDTO != null) {
                        logger.info("loan offer calculated using previous category for merchant: {}", merchantId);
                        loanEligibilityDTO.setPrevLoanUnpaidAmount(prevLoanUnpaidAmount.intValue());
                        loanEligibilityDTOList.add(loanEligibilityDTO);
                        loanEligibilityDTOList.sort(Comparator.comparing(LoanEligibilityDTO::getAmount).reversed());
                    } else {
                        logger.info("loan offer is null for merchant: {}", merchantId);
                    }
                }
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

    private LoanEligibilityDTO calculateLoanBreakup(LendingCategories lendingCategories, double avgTpv, String type, Long merchantId, Long experianId, double prevLoanAmount, String color) {
        double percentage = lendingCategories.getMultiplier();
        double interest = lendingCategories.getInterestRate();
        int tenure = Math.round(lendingCategories.getTenureMonths());
        int ioTenure = Math.round(lendingCategories.getIoTenureMonths());
        Integer maxAmount = lendingCategories.getMaxTpvAmount();
        int ioPayableDays = lendingCategories.getIoPayableDays();
        String construct = lendingCategories.getLoanConstruct();
        String category = lendingCategories.getCategory();
        String payableConverter = lendingCategories.getPayableConverter();
        int ioEdiDays = construct.equalsIgnoreCase("CONSTRUCT_3") ? 30 : 0;
        LoanCalculationUtil.LoanBreakupDetail breakup;
        if (avgTpv == 0 && prevLoanAmount > 0) {
            AvailableLoan availableLoan = new AvailableLoan();
            availableLoan.setAmount(prevLoanAmount);
            breakup = LoanCalculationUtil.getLoanBreakup(availableLoan, lendingCategories);
        } else {
            breakup = getBreakup(tenure, construct, type, avgTpv, percentage, interest, maxAmount, ioTenure, ioPayableDays);
        }
        if (color.equalsIgnoreCase("AMBER") && breakup.getLoanAmount() < 20000) {
            logger.info("loan amount is less than 20000 for merchant: {}", merchantId);
            return null;
        } else if (breakup.getLoanAmount() < 10000) {
            logger.info("loan amount is less than 10000 for merchant: {}", merchantId);
            return null;
        }
        logger.info("saving eligible loan for merchant: {}", merchantId);
        EligibleLoan eligibleLoan = eligibleLoanDao.save(new EligibleLoan(merchantId, experianId, (double)breakup.getLoanAmount(), payableConverter, "ACTIVE", category, ioEdiDays, 0, avgTpv, breakup.getEdi(), breakup.getIoEdi(), breakup.getRepayment(), construct, "TOPUP"));
        logger.info("eligible loan for merchant: {} is-- {}", merchantId, eligibleLoan.toString());
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

    private int fetchBureauVintage(JsonNode experianResponse) {
        DateTimeFormatter formatter = DateTimeFormat.forPattern("yyyyMMdd");
        DateTime min = new DateTime();
        if (experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS") != null && experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS").isArray()){
            for (JsonNode jsonNode : experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS")) {
                try {
                    min = formatter.parseDateTime(jsonNode.get("Open_Date").toString()).isBefore(min) ? formatter.parseDateTime(jsonNode.get("Open_Date").toString()) : min;
                } catch (Exception e) {
                    logger.error("Invalid Open_Date");
                }
            }
            return Months.monthsBetween(min, DateTime.now()).getMonths();
        } else if (experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS") != null && experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS").isObject()){
            JsonNode jsonNode = experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS");
            try {
                min = formatter.parseDateTime(jsonNode.get("Open_Date").toString()).isBefore(min) ? formatter.parseDateTime(jsonNode.get("Open_Date").toString()) : min;
            } catch (Exception e) {
                logger.error("Invalid Open_Date");
            }
            return Months.monthsBetween(min, DateTime.now()).getMonths();
        }
        return 0;
    }

    private boolean derogChecks(JsonNode jsonNode, Long merchantId, Experian experian) {
        //Check for Derog Account Status
        if (jsonNode.get("Account_Status") != null && derogAccountStatus.contains(jsonNode.get("Account_Status").asInt())){
            logger.info("Derog Account Status check failed, rejecting merchant: {}", merchantId);
            experian.setRejected(true);
            experian.setReason(ExperianConstants.DEROG_ACCOUNT_STATUS);
            experianDao.save(experian);
            return true;
        }
        //Check for Derog DPD Last 3 months
        if (jsonNode.get("AccountHoldertypeCode").asInt() != 7 && checkDPDLastXmonths(jsonNode, 3)){
            logger.info("Derog DPD Last 3 months check failed, rejecting merchant: {}", merchantId);
            experian.setRejected(true);
            experian.setReason(ExperianConstants.DEROG_DPD_LAST_3_MONTHS);
            experianDao.save(experian);
            return true;
        }
        //Check for Derog DPD Last 6 months
        if (jsonNode.get("AccountHoldertypeCode").asInt() != 7 && checkDPDLastXmonths(jsonNode, 6)){
            logger.info("Derog DPD Last 6 months check failed, rejecting merchant: {}", merchantId);
            experian.setRejected(true);
            experian.setReason(ExperianConstants.DEROG_DPD_LAST_6_MONTHS);
            experianDao.save(experian);
            return true;
        }
        //Check for Derog DPD Last 12 months
        if (jsonNode.get("AccountHoldertypeCode").asInt() != 7 && checkDPDLastXmonths(jsonNode, 12)){
            logger.info("Derog DPD Last 12 months check failed, rejecting merchant: {}", merchantId);
            experian.setRejected(true);
            experian.setReason(ExperianConstants.DEROG_DPD_LAST_12_MONTHS);
            experianDao.save(experian);
            return true;
        }
        //Check for Derog DPD Last 24 months
        if (jsonNode.get("AccountHoldertypeCode").asInt() != 7 && checkDPDLastXmonths(jsonNode, 24)){
            logger.info("Derog DPD Last 24 months check failed, rejecting merchant: {}", merchantId);
            experian.setRejected(true);
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

    private boolean checkUnsecuredLoanEnquiriesInLast6Months(JsonNode experianResponse) {
        if (experianResponse.get("INProfileResponse").get("TotalCAPS_Summary").get("TotalCAPSLast180Days").asInt() <= 4){
            return false;
        }
        Calendar c = Calendar.getInstance();
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
                if (monthYear.contains(cais_account_history.get("Month") + "$" + cais_account_history.get("Year")) && !cais_account_history.get("Days_Past_Due").isNull() && cais_account_history.get("Days_Past_Due").asInt() >= dpd) {
                    return true;
                }
            }
        } else if (jsonNode.get("CAIS_Account_History") != null && jsonNode.get("CAIS_Account_History").isObject()){
            JsonNode cais_account_history = jsonNode.get("CAIS_Account_History");
            return monthYear.contains(cais_account_history.get("Month") + "$" + cais_account_history.get("Year")) && !cais_account_history.get("Days_Past_Due").isNull() && cais_account_history.get("Days_Past_Due").asInt() >= dpd;
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


    private JsonNode fetchExperianDetails(String firstName, String lastName, String contact, String panCard, Long merchantId) {
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
                return null;
            }
            String xmlResponse = jsonNode.get("showHtmlReportForCreditReport").textValue().replaceAll("&amp;", "&").replaceAll("&gt;", ">").replaceAll("&lt;", "<").replaceAll("&quot;", "\"");
            //String xmlResponse = new String(Files.readAllBytes(Paths.get("/Users/admin/codebase/Lending/src/main/resources/experian_sample.txt")));
            JSONObject jsonObject = XML.toJSONObject(xmlResponse);
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
    
	private LendingPaymentSchedule getActiveLoan(List<LendingPaymentSchedule> lendingPaymentScheduleList) {
		if(lendingPaymentScheduleList == null || lendingPaymentScheduleList.size() == 0) {
			return null;
		}
		
		for(LendingPaymentSchedule schedule : lendingPaymentScheduleList) {
			if(LendingStatus.ACTIVE.toString().equals(schedule.getStatus())) {
				return schedule;
			}
		}
		return null;
	}	
}