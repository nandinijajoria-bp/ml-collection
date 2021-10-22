package com.bharatpe.lending.service;

import com.bharatpe.common.dao.*;
import com.bharatpe.common.entities.*;
import com.bharatpe.common.enums.Status.LendingStatus;
import com.bharatpe.lending.constant.ExperianConstants;
import com.bharatpe.lending.constant.LendingConstants;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingCategoryDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dto.LoanEligibilityDTO;
import com.bharatpe.lending.util.LoanUtil;
import com.bharatpe.lending.util.creditresponse.ResponseUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.util.*;

@Component
public class TopupLoanEligibleService {

    private Logger logger = LoggerFactory.getLogger(TopupLoanEligibleService.class);

    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;

    @Autowired
    ExperianDao experianDao;

    @Autowired
    LendingCategoryDao lendingCategoryDao;

    @Autowired
    EligibleLoanDao eligibleLoanDao;

    @Autowired
    LendingApplicationDao lendingApplicationDao;
    
	@Autowired
	MerchantSummaryDao merchantSummaryDao;
	
	@Autowired
	MerchantBankDetailDao merchantBankDetailDao;
	
	@Autowired
	MerchantDao merchantDao;

    @Autowired
    DocKycDetailsDao docKycDetailsDao;

    @Autowired
    LoanEligibleService loanEligibleService;

    @Autowired
    LendingEnachDao lendingEnachDao;

    @Autowired
    ExperianAuditTrailDao experianAuditTrailDao;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    LoanUtil loanUtil;

    List<Long> exemptMerchant = Arrays.asList(3692069L);
    
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
    		 List<LendingPaymentSchedule> lendingPaymentScheduleList = lendingPaymentScheduleDao.findByMerchantIdAndCreditLoanOrderByIdDesc(merchant.get().getId(),false);
             fetchTopupLoans(merchant.get(), experian, merchantSummary, merchantBankDetail, lendingPaymentScheduleList, null);
    	 } catch(Exception ex) {
    		 logger.error("Exception while generating new loans for merchant with ID {}, Exception is {}", merchantId, ex);
    	 }
    }

    public List<LoanEligibilityDTO> fetchTopupLoans(Merchant merchant, Experian experian, MerchantSummary merchantSummary, MerchantBankDetail merchantBankDetail, List<LendingPaymentSchedule> lendingPaymentScheduleList, String bankCode) throws ParseException {
        logger.info("fetching topup loan for merchant:{}", merchant.getId());
        if (true) {
            logger.info("topup loan closed");
            return new ArrayList<>();
        }
        double bpScore = (merchantSummary != null && merchantSummary.getBpScore() != null) ? merchantSummary.getBpScore() : 0d;
        LendingPaymentSchedule activeLoan = getActiveLoan(lendingPaymentScheduleList);
        if(lendingPaymentScheduleList == null || lendingPaymentScheduleList.isEmpty() || activeLoan == null || activeLoan.getLoanAmount() <= 5000) {
            logger.info("No previous loan/active loan for merchant ID {}", merchant.getId());
            return new ArrayList<>();
        }
        LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchant(activeLoan.getApplicationId(), merchant);
        if (lendingApplication == null || "TOPUP".equalsIgnoreCase(lendingApplication.getLoanType())) {
            logger.info("Lending Application not found/topup loan for merchant:{}", merchant.getId());
            return new ArrayList<>();
        }
        if (!"LIQUILOANS".equalsIgnoreCase(lendingApplication.getLender())) {
            logger.info("Active loan lender is not LIQUILOANS for merchant:{}", merchant.getId());
            return new ArrayList<>();
        }
        double paidRatio = 0d;
        if (activeLoan.getPaidPrinciple() != null && activeLoan.getLoanAmount() != null) {
            paidRatio = activeLoan.getPaidPrinciple() / activeLoan.getLoanAmount();
        }
        double dpd = activeLoan.getDueAmount() / activeLoan.getEdiAmount();
        if(dpd > 5D) {
            logger.info("DPD is greater than 5 for merchant ID {}", merchant.getId());
            return new ArrayList<>();
        }
        if (loanEligibleService.checkFraud(merchantSummary)) {
            logger.info("Fraud Merchant, so rejecting merchant: {}", merchant.getId());
            return new ArrayList<>();
        }
        if (experian == null) {
            DocKycDetails docKycDetails = docKycDetailsDao.fetchLatestPanCardDetails(merchant.getId(), activeLoan.getApplicationId());
            if (docKycDetails != null && docKycDetails.getDocNo() != null) {
                logger.info("fetching experian for merchant:{} and pancard:{}", merchant.getId(), docKycDetails.getDocNo());
                try {
                    JsonNode experianResponse;
                    ExperianAuditTrail experianAuditTrail = experianAuditTrailDao.findLatestByMerchantId(merchant.getId());
                    if (experianAuditTrail != null && experianAuditTrail.getResponse() != null && experianAuditTrail.getPancardNumber().equalsIgnoreCase(docKycDetails.getDocNo()) && LoanUtil.getDateDiffInDays(experianAuditTrail.getCreatedAt(), new Date()) <= 45) {//get experian data from db if less than 45 days old
                        experianResponse = objectMapper.readTree(experianAuditTrail.getResponse());
                    } else {
                        experianResponse = loanEligibleService.fetchExperianDetails(merchant.getMobile(), null, merchant.getId(), bpScore, merchantBankDetail, true);
                    }
                    if (experianResponse != null) {
                        experian = updateExperian(experianResponse, merchant, bpScore, docKycDetails.getDocNo(), lendingApplication.getPincode());
                        loanUtil.auditExperian(experian);
                    } else {
                        logger.info("Experian not found for merchant:{}", merchant.getId());
                    }
                } catch (Exception e) {
                    logger.error("Exception while fetching experian---", e);
                }
            } else {
                logger.info("pancard not found for merchant:{} for application:{}", merchant.getId(), activeLoan.getApplicationId());
            }
        } else {
            try {
                JsonNode experianResponse;
                ExperianAuditTrail experianAuditTrail = experianAuditTrailDao.findLatestByMerchantId(merchant.getId());
                if (experianAuditTrail != null && experianAuditTrail.getResponse() != null && experianAuditTrail.getPancardNumber().equalsIgnoreCase(experian.getPancardNumber()) && LoanUtil.getDateDiffInDays(experianAuditTrail.getCreatedAt(), new Date()) <= 45) {//get experian data from db if less than 45 days old
                    experianResponse = objectMapper.readTree(experianAuditTrail.getResponse());
                } else {
                    experianResponse = loanEligibleService.fetchExperianDetails(merchant.getMobile(), experian, merchant.getId(), bpScore, merchantBankDetail, true);
                }
                if (experianResponse != null) {
                    experian.setResponse(experianResponse.toString());
                    experian.setBureau(LendingConstants.BUREAU_TYPES.EXPERIAN.name());
                    experianDao.save(experian);
                    loanUtil.auditExperian(experian);
                }
            } catch (Exception e) {
                logger.error("Exception while fetching experian---", e);
            }
        }
        ResponseUtil responseUtil = loanEligibleService.getCreditBureauResponse(experian);
        if (!exemptMerchant.contains(merchant.getId()) && responseUtil.isValid(experian.getPancardNumber(), merchant.getMobile()) && responseUtil.isDerog(merchant, true, experian)) {
            logger.info("Derog Merchant, so rejecting merchant: {}", merchant.getId());
            return new ArrayList<>();
        }
        double repaidRatio = 0.6d;
        double prevLoanAmount = 0d;
        String color = (experian != null && !experian.getRejected() && experian.getColor() != null && !loanEligibleService.isNTC(experian)) ? experian.getColor() : "AMBER";
        switch (color){
            case "AMBER": prevLoanAmount = lendingApplication.getLoanAmount() * 1.1;break;
            case "LIGHT_GREEN": prevLoanAmount = lendingApplication.getLoanAmount() * 1.25;repaidRatio=0.5;break;
            case "DARK_GREEN": prevLoanAmount = lendingApplication.getLoanAmount() * 1.5;repaidRatio=0.4;break;
        }
        prevLoanAmount = Math.min(prevLoanAmount, 1000000);
        if(paidRatio < repaidRatio || paidRatio > 0.98D) {
            logger.info("Insufficient paid ratio for merchant ID {}", merchant.getId());
            return new ArrayList<>();
        }
        LendingCategories lendingCategories = lendingCategoryDao.getByCategory(lendingApplication.getCategory());
        if (lendingCategories != null) {
            //lendingCategories.setInterestRate(1.75D);//fixed for topup loan
            Long experianId = experian != null ? experian.getId() : 0L;
            eligibleLoanDao.deleteByMerchantId(merchant.getId());
            LoanEligibilityDTO loanEligibilityDTO = loanEligibleService.calculateLoanBreakup(lendingCategories, 0, null, merchant.getId(), experianId, prevLoanAmount, color, "2", "TOPUP", false, false);
            double prevLoanUnpaidAmount = (activeLoan.getLoanAmount() - activeLoan.getPaidPrinciple()) + activeLoan.getDueInterest();
            if (loanEligibilityDTO != null) {
                loanEligibilityDTO.setPrevLoanUnpaidAmount((int) prevLoanUnpaidAmount);
                loanEligibilityDTO.setDisbursementAmount(loanEligibilityDTO.getDisbursementAmount() - (int) prevLoanUnpaidAmount);
                return new ArrayList<LoanEligibilityDTO>(){{add(loanEligibilityDTO);}};
            }
        }
        logger.info("No topup loan for merchant:{}", merchant.getId());
        return new ArrayList<>();
    }

    private Experian updateExperian(JsonNode experianResponse, Merchant merchant, double bpScore, String pancard, Long pincode) throws ParseException {
        Experian experian = new Experian();
        if(experianResponse != null){
            experian.setResponse(experianResponse.toString());
            experian.setBureau(LendingConstants.BUREAU_TYPES.EXPERIAN.name());
        }
        ResponseUtil creditBureauResponseUtil = loanEligibleService.getCreditBureauResponse(experian);
        if(creditBureauResponseUtil.isValid(experian.getPancardNumber(), merchant.getMobile())){
            experian.setMerchantId(merchant.getId());
            experian.setResponse(creditBureauResponseUtil.getResponse());
            experian.setBureau(creditBureauResponseUtil.getType());
            experian.setPancardNumber(pancard);
            experian.setPincode(pincode.intValue());
            experian.setRequestedLoanAmount(0);
            String email = creditBureauResponseUtil.getEmail();
            if(email != null) experian.setEmail(email);
            Double bureauScore = creditBureauResponseUtil.getBureauScore();
            if(bureauScore != null) experian.setExperianScore(bureauScore);
        }
        creditBureauResponseUtil.isDerog(merchant, true, experian);
        int bureauVintage = creditBureauResponseUtil.fetchBureauVintage();//months
        String accountCategory = creditBureauResponseUtil.fetchAccountCategory();// A,B,C or NTC
        String segment;
        String color;
        if (accountCategory.equals("NTC")){
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
        } else {
            segment = loanEligibleService.calculateSegment(bureauVintage, accountCategory, bpScore);
            if (ExperianConstants.RED.contains(segment)) {
                color = ExperianConstants.COLOR.RED.name();
            } else if (ExperianConstants.AMBER.contains(segment)) {
                color = ExperianConstants.COLOR.AMBER.name();
            } else if (ExperianConstants.LIGHT_GREEN.contains(segment)) {
                color = ExperianConstants.COLOR.LIGHT_GREEN.name();
            } else {
                color = ExperianConstants.COLOR.DARK_GREEN.name();
            }
        }
        experian.setColor(color);
        experian.setCategory(segment);
        return experianDao.save(experian);
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