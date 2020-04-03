package com.bharatpe.lending.service;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.bharatpe.common.dao.*;
import com.bharatpe.common.entities.*;
import com.bharatpe.lending.constant.ExperianConstants;
import com.bharatpe.lending.dao.*;
import com.bharatpe.lending.entity.OglLoans;
import com.bharatpe.lending.util.LoanCalculationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.bharatpe.common.enums.NotificationProvider;
import com.bharatpe.common.handlers.PushNotificationHandler;
import com.bharatpe.common.handlers.SmsServiceHandler;
import com.bharatpe.common.objects.CommonAPIRequest;
import com.bharatpe.common.objects.Meta;
import com.bharatpe.common.service.WhatsappNotificationService;
import com.bharatpe.lending.handlers.GupShupOTPHandler;


@Service
public class VerifyOTPService {
	private Logger logger = LoggerFactory.getLogger(VerifyOTPService.class);
	
	@Autowired
	LendingApplicationDao lendingApplicationDao;
	
	@Autowired
	LendingAuditTrialDao lendingAuditTrialDao;

	@Autowired
	MerchantBankDetailDao merchantBankDetailDao;
	
	@Autowired
	MerchantFcmTokenDao merchantFcmTokenDao;
	
	@Autowired
	PushNotificationHandler pushNotificationHandler;
	
	@Autowired
	SmsServiceHandler smsServiceHandler;
	
	@Autowired
	GupShupOTPHandler gupShupOTPHandler;
	
	@Autowired
	WhatsappNotificationService whatsappNotificationService;

	@Autowired
	BankListDao bankListDao;

	@Autowired
	OglLoansDao oglLoansDao;

	@Autowired
	MerchantSummaryLendingDao merchantSummaryLendingDao;

	@Autowired
	MerchantSummaryDao merchantSummaryDao;

	@Autowired
	LendingCategoryDao lendingCategoryDao;

	@Autowired
	PaymentTransactionNewDao paymentTransactionNewDao;

	@Autowired
	LendingPrebookLoansDao lendingPrebookLoansDao;
	
	ExecutorService notificationExecutor = Executors.newFixedThreadPool(5);

	ExecutorService preBookExecutor = Executors.newFixedThreadPool(5);

	public Map<String, Boolean> verifyOTP(Merchant merchant, CommonAPIRequest commonAPIRequest) {
		Map<String, Boolean> finalResponse = new LinkedHashMap<>();
		finalResponse.put("success",false);
		finalResponse.put("agreement_verified",false);
		
		Long applicationId =  commonAPIRequest.getPayload().get("application_id") != null ? Long.parseLong(commonAPIRequest.getPayload().get("application_id").toString()) : null;
		String otp =  commonAPIRequest.getPayload().get("otp") != null ? commonAPIRequest.getPayload().get("otp").toString() : null;

		if(applicationId == null || applicationId <= 0 || StringUtils.isEmpty(otp)) {
			logger.info("No application found in draft status for given application id {}", applicationId);
			return finalResponse;
		}
		LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchantAndStatus(applicationId, merchant, "draft");
		if(lendingApplication == null) {
			logger.info("No application found in draft status for given application id {}", applicationId);
			return finalResponse;
		}

		return verifyOTP(otp, merchant, lendingApplication, commonAPIRequest.getMeta());
	}
	
	private Map<String, Boolean> verifyOTP(String otp, Merchant merchant, LendingApplication lendingApplication, Meta meta) {
		Map<String, Boolean> finalResponse = new LinkedHashMap<>();
		finalResponse.put("success",false);
		finalResponse.put("agreement_verified",false);
		
		if(merchant.getMobile().length() == 12) {
			Boolean isOTPVerified = gupShupOTPHandler.verifyOTP(merchant.getMobile(), otp);
			if(isOTPVerified) {
				finalResponse = updateApplicationStatusAndSuccessSms(merchant, lendingApplication, meta);
			}
		}
		return finalResponse;
	}
	
	private Map<String, Boolean> updateApplicationStatusAndSuccessSms(Merchant merchant, LendingApplication lendingApplication, Meta meta) {
		OglLoans oglLoans = oglLoansDao.findByMerchantIdAndExternalLoanId(merchant.getId(), lendingApplication.getExternalLoanId());
		Map<String, Boolean> finalResponse = new LinkedHashMap<>();
		DateFormat df = new SimpleDateFormat("ddMMyy");
		Date dateobj = new Date();
		String loanId = "BPL" + df.format(dateobj) + lendingApplication.getId();
		lendingApplication.setAgreementAt(new Date());
		lendingApplication.setAgreement(1);
		lendingApplication.setLatitude(meta.getLatitude());
		lendingApplication.setLongitude(meta.getLongitude());
		lendingApplication.setIp(meta.getIp());
		lendingApplication.setExternalLoanId(loanId);
		if (oglLoans != null) {
			logger.info("Found OGL merchant: {}", merchant.getId());
			lendingApplication.setStatus("approved");
			lendingApplication.setManualKyc("APPROVED");
			lendingApplication.setManualCibil("APPROVED");
			lendingApplication.setPhysicalVerificationStatus("APPROVED");
			lendingApplication.setLender("LIQUILOANS");
		} else if("TOPUP".equalsIgnoreCase(lendingApplication.getLoanType())){
			logger.info("TOPUP loan submitted for merchant {}", merchant.getId());
			if(lendingApplication.getLoanAmount() > 100000) {
				lendingApplication.setStatus("pending_verification");
			} else {
				lendingApplication.setPhysicalVerificationStatus("APPROVED");
				lendingApplication.setStatus("approved");
			}
			lendingApplication.setManualKyc("APPROVED");
			lendingApplication.setManualCibil("APPROVED");
		} else if (lendingApplication.getNachStatus() != null && (lendingApplication.getNachStatus().equalsIgnoreCase("initiated") || lendingApplication.getNachStatus().equalsIgnoreCase("approved"))) {
			logger.info("Physical nach submitted by merchant: {}", merchant.getId());
			lendingApplication.setStatus("approved");
			lendingApplication.setManualKyc("APPROVED");
			lendingApplication.setManualCibil("APPROVED");
			lendingApplication.setPhysicalVerificationStatus("APPROVED");
		} else {
			lendingApplication.setStatus("pending_verification");
		}
		
		finalResponse.put("success",false);
		finalResponse.put("agreement_verified",false);
		lendingApplicationDao.save(lendingApplication);


		LendingAuditTrial lendingAuditTrial = new LendingAuditTrial();
		lendingAuditTrial.setApplicationId(lendingApplication.getId());
		lendingAuditTrial.setMerchantId(merchant.getId());
		lendingAuditTrial.setLoanId(loanId);
		lendingAuditTrial.setUserId(Long.parseLong("0"));
		lendingAuditTrial.setOldStatus("draft");
		if (oglLoans != null || (lendingApplication.getNachStatus() != null && (lendingApplication.getNachStatus().equalsIgnoreCase("initiated") || lendingApplication.getNachStatus().equalsIgnoreCase("approved")))) {
			lendingAuditTrial.setNewStatus("approved");
		} else {
			lendingAuditTrial.setNewStatus("pending_verification");
		}
		lendingAuditTrial.setType("APP_STATUS");

		lendingAuditTrialDao.save(lendingAuditTrial);

		notificationExecutor.submit(() -> sendNotification(merchant, lendingApplication));
		if (ExperianConstants.LOCKDOWN && merchant.getBusinessCategory() != null && lendingApplication.getLoanAmount() > 100000D && lendingApplication.getLoanType() != null && lendingApplication.getLoanType().equalsIgnoreCase("PREBOOK")) {
			preBookExecutor.submit(() -> checkPreBook(merchant, lendingApplication));
		}
		
		finalResponse.put("success",true);
		finalResponse.put("agreement_verified",true);
		return finalResponse;
	}

	private void checkPreBook(Merchant merchant, LendingApplication lendingApplication) {
		LendingPrebookLoans lendingPrebookLoans = lendingPrebookLoansDao.findByMerchantId(merchant.getId());
		if (lendingPrebookLoans != null) {
			logger.info("Prebook loan already exists for merchant: {}", merchant.getId());
			return;
		}
		MerchantSummaryLending merchantSummaryLending = merchantSummaryLendingDao.findByMerchantId(merchant.getId());
		MerchantSummary merchantSummary = merchantSummaryDao.getByMerchantId(merchant.getId());
		LendingCategories lendingCategories = lendingCategoryDao.getByCategory(lendingApplication.getCategory());
		List<String> preBookCategories = Arrays.asList("Grocery","Medical","Dairy");
		List<String> etcCategories = Arrays.asList("S1LG","S1DG","S2LG","S2DG");
		List<String> cities = Arrays.asList("Bangalore", "Hyderabad", "Pune", "Delhi");
		if (preBookCategories.contains(merchant.getBusinessCategory()) && merchantSummaryLending != null && merchantSummaryLending.getSegment().equalsIgnoreCase("2") && merchantSummary.getBpScore() > 10 && lendingCategories.getMasterCategory() != null && etcCategories.contains(lendingCategories.getMasterCategory()) && cities.contains(lendingApplication.getCity())) {
			Calendar c = Calendar.getInstance();
			c.setTime(lendingApplication.getAgreementAt());
			c.add(Calendar.DATE, -9);
			Date startDate = c.getTime();
			List<Object[]> transactions = paymentTransactionNewDao.getCountForPreBook(merchant.getId(), startDate, lendingApplication.getAgreementAt());
			if (transactions != null && transactions.size() >= 8) {
				Double previousLoanAmount = lendingApplication.getLoanAmount();
				AvailableLoan availableLoan = new AvailableLoan();
				availableLoan.setAmount(100000D);
				LoanCalculationUtil.LoanBreakupDetail breakup = LoanCalculationUtil.getLoanBreakup(availableLoan, lendingCategories);
				lendingApplication.setEdi(Double.valueOf(breakup.getEdi()));
				lendingApplication.setIoEdi(Double.valueOf(breakup.getIoEdi()));
				lendingApplication.setRepayment(Double.valueOf(breakup.getRepayment()));
				lendingApplication.setDisbursalAmount((double)breakup.getLoanAmount());
				lendingApplication.setLoanAmount((double)breakup.getLoanAmount());
				lendingApplicationDao.save(lendingApplication);
				lendingPrebookLoansDao.save(new LendingPrebookLoans(merchant.getId(), lendingApplication.getId(), previousLoanAmount));
				logger.info("Updated loan amount to 100000 for merchant: {} with applicationId: {}", merchant.getId(), lendingApplication.getId());
			}
		}
	}

	private void sendNotification(Merchant merchant, LendingApplication lendingApplication) {
		
		MerchantBankDetail merchantBankDetail = merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchant.getId(),"ACTIVE");
		if(merchantBankDetail == null) {
			return;
		}

		List<String> mobiles = new ArrayList<> ();
		mobiles.add(merchant.getMobile());
		Double loanAmount = lendingApplication.getLoanAmount();
		
		String smsContent = "Hi "+merchantBankDetail.getBeneficiaryName()+",\n\nYour loan application for INR "+loanAmount.intValue()+" has been received successfully.\n\nYour Application ID is "+lendingApplication.getExternalLoanId()+".\n\nNote: Due to necessary precautions for Coronavirus, there may be some delay in processing your application. We'll keep you updated.";
		smsServiceHandler.sendSMS(mobiles, smsContent, NotificationProvider.SMS.GUPSHUP);

//		String whatsappContent = "Hi  " + merchantBankDetail.getBeneficiaryName() + ",\n" +
//				"\n" +
//				"Your loan application for INR " + loanAmount.intValue() + " has been received successfully.\n" +
//				"Your Application ID is " + lendingApplication.getExternalLoanId() + ".";
		
		whatsappNotificationService.send(merchant, null, smsContent, mobiles, null);

		MerchantFcmToken merchantFcmToken = merchantFcmTokenDao.findByMerchantId(merchant.getId());
		
		if(merchantFcmToken != null) {
			String pushContent = "Dear "+merchantBankDetail.getBeneficiaryName()+", Your loan application for INR "+loanAmount.intValue()+" has been received successfully.";
			pushNotificationHandler.sendPushNotification(merchantFcmToken.getFcmToken(), merchantFcmToken.getPlatform(), pushContent, "homepage.html");
			if (isPaymentBank(merchant, merchantBankDetail)) {
				String pushNotification = "Hi  " + merchantBankDetail.getBeneficiaryName() + ",\n" +
						"\n" +
						"We have received your Loan Application of Rs." + loanAmount.intValue() + ".Our lending partners do not support disbursal in Payment Banks. Please change your registered account with us to a non-payment bank to get Rs." + loanAmount.intValue() + " NOW!";
				pushNotificationHandler.sendPushNotification(merchantFcmToken.getFcmToken(), merchantFcmToken.getPlatform(), pushNotification, "bharatpe://dynamic?key=change-acc");
			}
		}
	}

	private boolean isPaymentBank(Merchant merchant, MerchantBankDetail merchantBankDetail) {
		try {
			if(merchantBankDetail == null) {
				logger.error("No merchnat bank detail found for merchant id {}", merchant.getId());
				return true;
			}

			if(StringUtils.isEmpty(merchantBankDetail.getIfscCode())) {
				logger.error("IFSC is empty for merchant bank detail id {} and merchant ID {}", merchantBankDetail.getId(), merchant.getId());
				return true;
			}

			List<BankList> nonPaymentBankList = bankListDao.fetchNonPaymentBankList(merchantBankDetail.getIfscCode().substring(0,4));

			if (nonPaymentBankList == null || nonPaymentBankList.size() == 0) {
				return false;
			} else {
				logger.info("IFSC {} is of Payment bank, returning true", merchantBankDetail.getIfscCode());
				return true;
			}
		} catch(Exception ex) {
			logger.error("Exception while checking if merchant's bank is payment bank with merchant id {}, Exception is {}", merchant.getId(), ex);
		}
		return true;
	}
}
