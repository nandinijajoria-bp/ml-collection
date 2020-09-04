package com.bharatpe.lending.controller;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.bharatpe.common.dao.MerchantBankDetailDao;
import com.bharatpe.common.entities.Experian;
import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.MerchantBankDetail;
import com.bharatpe.lending.constant.ExperianConstants;
import com.bharatpe.lending.dao.ExperianDummyDao;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.entity.ExperianDummy;
import com.bharatpe.lending.service.LoanEligibleService;
import com.bharatpe.lending.util.LoanUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.bharatpe.common.dao.ExperianDao;
import com.bharatpe.common.dao.MerchantDao;
import com.bharatpe.common.entities.Merchant;
import com.bharatpe.lending.dto.IneligibleRequestDTO;
import com.bharatpe.lending.dto.RequestDTO;
import com.bharatpe.lending.service.LoanDetailsService;

@Service
public class CallLoanDetailService {

	private final Logger logger = LoggerFactory.getLogger(CallLoanDetailService.class);

	List<Integer> derogAccountStatus = Arrays.asList(93,89,93,97,97,97,97,30,31,32,33,35,37,38,39,41,42,43,44,45,47,49,50,51,53,54,55,56,57,58,59,60,61,62,63,64,65,66,67,68,69,70,72,73,74,75,76,77,79,81,85,86,87,88,94,90,91);
	List<Integer> derogUnsecuredProducts = Arrays.asList(5,10,36,37,38,39,43,51,52,53,54,55,56,57,58,60,61);
	
	@Autowired
	ExperianDao experianDao;
	
	@Autowired
	MerchantDao merchantDao;
	
	@Autowired
	LoanDetailsService loanDetailsService;

	@Autowired
	LendingApplicationDao lendingApplicationDao;

	@Autowired
	ExperianDummyDao experianDummyDao;

	SimpleDateFormat experianFormat = new SimpleDateFormat("yyyyMMdd");

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	LoanEligibleService loanEligibleService;

	@Autowired
	MerchantBankDetailDao merchantBankDetailDao;
	
	public void callLoanDetail() {
		long offset = 0;
		boolean lastBatchProcessed = false;
		logger.info("Loan Details Script Started");
		ExecutorService executorService = Executors.newFixedThreadPool(100);
		while (!lastBatchProcessed) {
			try {
				List<LendingApplication> lendingApplications = lendingApplicationDao.getApplications(offset);//query returns integer in merchant_id
				logger.info("Processing loan details batch starting at offset: {}", offset);
				offset += 1000;
				if (lendingApplications.size() < 1000) {
					lastBatchProcessed = true;
				}
				for (LendingApplication lendingApplication : lendingApplications) {
					executorService.submit(() -> test(lendingApplication.getMerchant()));
				}
			} catch (Exception e) {
				logger.error("Exception---", e);
			}
		}
		logger.info("Loan Details Script Ended");
	}

	private void test(Merchant merchant) {
		try {
			MerchantBankDetail merchantBankDetail = merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchant.getId(), "ACTIVE");
			Experian experian = experianDao.getByMerchantId(merchant.getId());
			ExperianDummy experianDummy = experianDummyDao.getByMerchantId(merchant.getId());
			if (experianDummy == null) {
				experianDummy = ExperianDummy.createObject(experian);
			}
			//Date reportDate = experianFormat.parse(objectMapper.readTree(experian.getResponse()).get("INProfileResponse").get("CreditProfileHeader").get("ReportDate").asText());
			JsonNode experianResponse = objectMapper.readTree(experian.getResponse());
//			if (experian.getResponse() != null && reportDate != null && LoanUtil.getDateDiffInDays(reportDate, new Date()) <= 45) {//get experian data from db if less than 45 days old
//				experianResponse = objectMapper.readTree(experian.getResponse());
//			} else {
//				int retry=0;
//				while (retry < 3) {
//					try {
//						experianResponse = loanEligibleService.fetchExperianDetails(merchant.getMobile(), experian.getPancardNumber(), merchant.getId(), experian.getBpScore(), merchantBankDetail);
//						break;
//					} catch (Exception e) {
//						retry++;
//					}
//				}
//			}
//			if (experianResponse == null) {
//				logger.info("Experian not found for merchant:{}", merchant.getId());
//				return;
//			}
			isDerog(experianResponse, merchant, experianDummy, false);
			experianDummyDao.save(experianDummy);
		} catch (Exception e) {
			logger.error("Exception---", e);
		}
	}

	public boolean isDerog(JsonNode experianResponse, Merchant merchant, ExperianDummy experian, boolean isRepeatLoanNoDerog) throws ParseException {
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
				experianDummyDao.save(experian);
				return true;
			}
		}
		//Not more than 4 unsecured loan enquiries in the last 6 months --- Derog check
		if (!isRepeatLoanNoDerog && checkUnsecuredLoanEnquiriesInLast6Months(experianResponse, reportDate)) {
			logger.info("Derog more than 4 unsecured loan enquiries in the last 6 months, rejecting merchant: {}", merchant.getId());
			experian.setRejected(true);
			experian.setRejectedDate(new Date());
			experian.setReason(ExperianConstants.DEROG_UNSECURED_LOAN_ENQUIRY);
			experianDummyDao.save(experian);
			return true;
		}
		//Not more than 6 enquiries in the last 3 months ( across all product types) --- Derog check
		if (!isRepeatLoanNoDerog && checkLoanEnquiriesInLast3Months(experianResponse)) {
			logger.info("Derog more than 6 enquiries in the last 3 months, rejecting merchant: {}", merchant.getId());
			experian.setRejected(true);
			experian.setRejectedDate(new Date());
			experian.setReason(ExperianConstants.DEROG_MORE_THAN_6_LOAN_ENQUIRY);
			experianDummyDao.save(experian);
			return true;
		}
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
		if (dateReported != null) {
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

	private boolean derogChecks(JsonNode jsonNode, Long merchantId, ExperianDummy experian, boolean isRepeatLoanNoDerog, Date reportDate) {
		//Check for Derog Account Status
		if (jsonNode.get("Account_Status") != null && derogAccountStatus.contains(jsonNode.get("Account_Status").asInt())){
			logger.info("Derog Account Status check failed, rejecting merchant: {}", merchantId);
			experian.setRejected(true);
			experian.setRejectedDate(new Date());
			experian.setReason(ExperianConstants.DEROG_ACCOUNT_STATUS);
			experianDummyDao.save(experian);
			return true;
		}
		//Check for Derog DPD Last 3 months
		if (!isRepeatLoanNoDerog && jsonNode.get("AccountHoldertypeCode").asInt() != 7 && checkDPDLastXmonths(jsonNode, 3, reportDate)){
			logger.info("Derog DPD Last 3 months check failed, rejecting merchant: {}", merchantId);
			experian.setRejected(true);
			experian.setRejectedDate(new Date());
			experian.setReason(ExperianConstants.DEROG_DPD_LAST_3_MONTHS);
			experianDummyDao.save(experian);
			return true;
		}
		//Check for Derog DPD Last 6 months
		if (jsonNode.get("AccountHoldertypeCode").asInt() != 7 && checkDPDLastXmonths(jsonNode, 6, reportDate)){
			logger.info("Derog DPD Last 6 months check failed, rejecting merchant: {}", merchantId);
			experian.setRejected(true);
			experian.setRejectedDate(new Date());
			experian.setReason(ExperianConstants.DEROG_DPD_LAST_6_MONTHS);
			experianDummyDao.save(experian);
			return true;
		}
		//Check for Derog DPD Last 12 months
		if (!isRepeatLoanNoDerog && jsonNode.get("AccountHoldertypeCode").asInt() != 7 && checkDPDLastXmonths(jsonNode, 12, reportDate)){
			logger.info("Derog DPD Last 12 months check failed, rejecting merchant: {}", merchantId);
			experian.setRejected(true);
			experian.setRejectedDate(new Date());
			experian.setReason(ExperianConstants.DEROG_DPD_LAST_12_MONTHS);
			experianDummyDao.save(experian);
			return true;
		}
		//Check for Derog DPD Last 24 months
		if (!isRepeatLoanNoDerog && jsonNode.get("AccountHoldertypeCode").asInt() != 7 && checkDPDLastXmonths(jsonNode, 24, reportDate)){
			logger.info("Derog DPD Last 24 months check failed, rejecting merchant: {}", merchantId);
			experian.setRejected(true);
			experian.setRejectedDate(new Date());
			experian.setReason(ExperianConstants.DEROG_DPD_LAST_24_MONTHS);
			experianDummyDao.save(experian);
			return true;
		}
		return false;
	}
	
	public void callLoanDetailFunction(Merchant merchant) {
		try {
			RequestDTO<IneligibleRequestDTO> requestDTO = new RequestDTO<>();
			requestDTO.setPayload(new IneligibleRequestDTO());
			requestDTO.getPayload().setSkip(false);
			logger.info("Calling loan details for merchant:{}", merchant.getId());
			loanDetailsService.fetchLoanDetails(merchant, requestDTO, null);
		} catch (Exception e) {
			logger.error("Exception---", e);
		}
	}
}


