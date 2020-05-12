package com.bharatpe.lending.service;

import com.bharatpe.common.dao.*;
import com.bharatpe.common.entities.*;
import com.bharatpe.common.enums.Status.LendingStatus;
import com.bharatpe.lending.constant.ExperianConstants;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingCategoryDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dto.LoanEligibilityDTO;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

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
             fetchTopupLoans(merchant.get(), experian, merchantSummary, merchantBankDetail, lendingPaymentScheduleList, null);
    	 } catch(Exception ex) {
    		 logger.error("Exception while generating new loans for merchant with ID {}, Exception is {}", merchantId, ex);
    	 }
    }

    public List<LoanEligibilityDTO> fetchTopupLoans(Merchant merchant, Experian experian, MerchantSummary merchantSummary, MerchantBankDetail merchantBankDetail, List<LendingPaymentSchedule> lendingPaymentScheduleList, String bankCode) {
        logger.info("fetching topup loan for merchant:{}", merchant.getId());
        if (!Arrays.asList("918980994455","919899818499","918506057690","919971011197").contains(merchant.getMobile())) {//TODO remove this after testing
            return new ArrayList<>();
        }
        double bpScore = (merchantSummary != null && merchantSummary.getBpScore() != null) ? merchantSummary.getBpScore() : 0d;
        LendingPaymentSchedule activeLoan = getActiveLoan(lendingPaymentScheduleList);
        if(lendingPaymentScheduleList == null || lendingPaymentScheduleList.isEmpty() || activeLoan == null) {
            logger.info("No previous loan/active loan for merchant ID {}", merchant.getId());
            return new ArrayList<>();
        }
        LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchant(activeLoan.getApplicationId(), merchant);
        if (lendingApplication == null || "TOPUP".equalsIgnoreCase(lendingApplication.getLoanType())) {
            logger.info("Lending Application not found/topup loan for merchant:{}", merchant.getId());
            return new ArrayList<>();
        }
        double paidRatio = activeLoan.getPaidAmount() / activeLoan.getTotalPayableAmount();
        if(paidRatio < 0.10D || paidRatio > 0.98D) {
            logger.info("Amount paid is less than 10% or more than 98% for merchant ID {}", merchant.getId());
            return new ArrayList<>();
        }
        double dpd = activeLoan.getDueAmount() / activeLoan.getEdiAmount();
        if(dpd > 3D) {
            logger.info("DPD is greater than 3 for merchant ID {}", merchant.getId());
            return new ArrayList<>();
        }
        if (experian == null) {
            DocKycDetails docKycDetails = docKycDetailsDao.fetchLatestPanCardDetails(merchant.getId(), activeLoan.getApplicationId());
            if (docKycDetails != null && docKycDetails.getDocNo() != null) {
                logger.info("fetching experian for merchant:{} and pancard:{}", merchant.getId(), docKycDetails.getDocNo());
                try {
                    JsonNode experianResponse = loanEligibleService.fetchExperianDetails(merchant.getMobile(), docKycDetails.getDocNo(), merchant.getId(), bpScore, merchantBankDetail);
                    if (experianResponse != null) {
                        experian = updateExperian(experianResponse, merchant, bpScore, docKycDetails.getDocNo(), lendingApplication.getPincode());
                    } else {
                        logger.info("Experian not found for merchant:{}", merchant.getId());
                    }
                } catch (Exception e) {
                    logger.error("Exception while fetching experian---", e);
                }
            } else {
                logger.info("pancard not found for merchant:{} for application:{}", merchant.getId(), activeLoan.getApplicationId());
            }
        }
        double prevLoanAmount = 0d;
        String color = (experian != null && !experian.getRejected()) ? experian.getColor() : "AMBER";
        switch (color){
            case "AMBER": prevLoanAmount = lendingApplication.getLoanAmount() * 1.1;break;
            case "LIGHT_GREEN": prevLoanAmount = lendingApplication.getLoanAmount() * 1.25;break;
            case "DARK_GREEN": prevLoanAmount = lendingApplication.getLoanAmount() * 1.5;break;
        }
        LendingCategories lendingCategories = lendingCategoryDao.getByCategory(lendingApplication.getCategory());
        boolean enachSuccess = lendingEnachDao.findSuccessEnach(merchant.getId()) != null;
        if (lendingCategories != null) {
            lendingCategories.setInterestRate(1.75D);//fixed for topup loan
            Long experianId = experian != null ? experian.getId() : 0L;
            eligibleLoanDao.deleteByMerchantId(merchant.getId());
            LoanEligibilityDTO loanEligibilityDTO = loanEligibleService.calculateLoanBreakup(lendingCategories, 0, null, merchant.getId(), experianId, prevLoanAmount, color, "2", "TOPUP", false);
            double prevLoanUnpaidAmount = (activeLoan.getLoanAmount() - activeLoan.getPaidPrinciple()) + activeLoan.getDueInterest();
            if (loanEligibilityDTO != null) {
                if (loanEligibilityDTO.getAmount() > 100000 && !enachSuccess && bankCode == null) {
                    logger.info("Topup Loan amount is more than 1lac and enach not found for merchant:{}", merchant.getId());
                    return new ArrayList<>();
                }
                loanEligibilityDTO.setPrevLoanUnpaidAmount((int) prevLoanUnpaidAmount);
                loanEligibilityDTO.setDisbursementAmount(loanEligibilityDTO.getDisbursementAmount() - (int) prevLoanUnpaidAmount);
                return new ArrayList<LoanEligibilityDTO>(){{add(loanEligibilityDTO);}};
            }
        }
        logger.info("No topup loan for merchant:{}", merchant.getId());
        return new ArrayList<>();
    }

    private Experian updateExperian(JsonNode experianResponse, Merchant merchant, double bpScore, String pancard, Long pincode) {
        Experian experian = new Experian();
        experian.setMerchantId(merchant.getId());
        experian.setResponse(experianResponse.toString());
        experian.setPancardNumber(pancard);
        experian.setPincode(pincode.intValue());
        if (experianResponse.get("INProfileResponse").get("Current_Application").get("Current_Application_Details") != null && experianResponse.get("INProfileResponse").get("Current_Application").get("Current_Application_Details").get("Current_Applicant_Details") != null) {
            String email = experianResponse.get("INProfileResponse").get("Current_Application").get("Current_Application_Details").get("Current_Applicant_Details").get("EMailId").textValue();
            experian.setEmail(email);
        }
        if (experianResponse.get("INProfileResponse").get("SCORE").get("BureauScore") != null) {
            experian.setExperianScore(experianResponse.get("INProfileResponse").get("SCORE").get("BureauScore").doubleValue());
        }
        loanEligibleService.isDerog(experianResponse, merchant, experian, true);
        int bureauVintage = loanEligibleService.fetchBureauVintage(experianResponse);//months
        String accountCategory = loanEligibleService.fetchAccountCategory(experianResponse);// A,B,C or NTC
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