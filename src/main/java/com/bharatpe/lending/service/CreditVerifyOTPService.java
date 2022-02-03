package com.bharatpe.lending.service;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.bharatpe.lending.handlers.BharatPeOtpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.bharatpe.common.dao.LendingPrebookLoansDao;
import com.bharatpe.common.dao.MerchantBankDetailDao;
import com.bharatpe.common.dao.MerchantFcmTokenDao;
import com.bharatpe.common.dao.MerchantSummaryDao;
import com.bharatpe.common.dao.MerchantSummaryLendingDao;
import com.bharatpe.common.dao.PaymentTransactionNewDao;
import com.bharatpe.common.entities.AvailableLoan;
import com.bharatpe.common.entities.BankList;
import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingAuditTrial;
import com.bharatpe.common.entities.LendingCategories;
import com.bharatpe.common.entities.LendingPrebookLoans;
import com.bharatpe.common.entities.Merchant;
import com.bharatpe.common.entities.MerchantBankDetail;
import com.bharatpe.common.entities.MerchantFcmToken;
import com.bharatpe.common.entities.MerchantSummary;
import com.bharatpe.common.entities.MerchantSummaryLending;
import com.bharatpe.common.enums.LoyaltyTransactionType;
import com.bharatpe.common.enums.NotificationProvider;
import com.bharatpe.common.handlers.PushNotificationHandler;
import com.bharatpe.common.handlers.SmsServiceHandler;
import com.bharatpe.common.objects.CommonAPIRequest;
import com.bharatpe.common.objects.LoyaltyServiceRequest;
import com.bharatpe.common.objects.Meta;
import com.bharatpe.common.service.LoyaltyService;
import com.bharatpe.common.service.WhatsappNotificationService;
import com.bharatpe.lending.common.dao.CreditApplicationAddressDao;
import com.bharatpe.lending.common.dao.CreditApplicationDao;
import com.bharatpe.lending.common.dao.CreditApplicationNachDao;
import com.bharatpe.lending.common.entity.CreditApplication;
import com.bharatpe.lending.common.entity.CreditApplicationAddress;
import com.bharatpe.lending.common.entity.CreditApplicationNach;
import com.bharatpe.lending.constant.ExperianConstants;
import com.bharatpe.lending.dao.BankListDao;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingAuditTrialDao;
import com.bharatpe.lending.dao.LendingCategoryDao;
import com.bharatpe.lending.dao.OglLoansDao;
import com.bharatpe.lending.entity.OglLoans;
import com.bharatpe.lending.handlers.GupShupOTPHandler;
import com.bharatpe.lending.util.LoanCalculationUtil;

@Service
public class CreditVerifyOTPService {

private Logger logger = LoggerFactory.getLogger(VerifyOTPService.class);
	
	@Autowired
	CreditApplicationDao creditApplicationDao;
	@Autowired
	CreditApplicationAddressDao creditApplicationAddressDao;
	@Autowired
	CreditApplicationNachDao creditApplicationNachDao;
	
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
	BharatPeOtpHandler bharatPeOtpHandler;
	
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

	@Autowired
	ENachService eNachService;
	
	ExecutorService notificationExecutor = Executors.newFixedThreadPool(5);

	ExecutorService preBookExecutor = Executors.newFixedThreadPool(5);

	@Autowired
	LoyaltyService loyaltyService;

	public Map<String, Boolean> verifyOTP(Merchant merchant, CommonAPIRequest commonAPIRequest) {
		Map<String, Boolean> finalResponse = new LinkedHashMap<>();
		finalResponse.put("success",false);
		finalResponse.put("agreement_verified",false);
		
		Long applicationId =  commonAPIRequest.getPayload().get("application_id") != null ? Long.parseLong(commonAPIRequest.getPayload().get("application_id").toString()) : null;
		String otp =  commonAPIRequest.getPayload().get("otp") != null ? commonAPIRequest.getPayload().get("otp").toString() : null;
		String uuid =  commonAPIRequest.getPayload().get("uuid") != null ? commonAPIRequest.getPayload().get("otp").toString() : null;

		if(applicationId == null || applicationId <= 0 || StringUtils.isEmpty(otp)) {
			logger.info("No application found in draft status for given application id {}", applicationId);
			return finalResponse;
		}
		CreditApplication creditApplication = creditApplicationDao.findByIdAndMerchantIdAndStatus(applicationId, merchant.getId(), "draft");
		if(creditApplication == null) {
			logger.info("No application found in draft status for given application id {}", applicationId);
			return finalResponse;
		}

		return verifyOTP(otp, merchant, creditApplication, commonAPIRequest.getMeta(),uuid);
	}
	
	private Map<String, Boolean> verifyOTP(String otp, Merchant merchant, CreditApplication creditApplication, Meta meta, String uuid) {
		Map<String, Boolean> finalResponse = new LinkedHashMap<>();
		finalResponse.put("success",false);
		finalResponse.put("agreement_verified",false);
		
		if(merchant.getMobile().length() == 12) {
			Boolean isOTPVerified = bharatPeOtpHandler.verifyOtp(merchant, otp,uuid);
			if(isOTPVerified) {
				finalResponse = updateApplicationStatusAndSuccessSms(merchant, creditApplication, meta);
			}
		}
		return finalResponse;
	}
	
	private Map<String, Boolean> updateApplicationStatusAndSuccessSms(Merchant merchant, CreditApplication creditApplication, Meta meta) {
		//OglLoans oglLoans = oglLoansDao.findByMerchantIdAndExternalLoanId(merchant.getId(), lendingApplication.getExternalLoanId());
		CreditApplicationNach creditApplicationNach=creditApplicationNachDao.findByMerchantIdAndApplicationId(merchant.getId(),creditApplication.getId());
		Map<String, Boolean> finalResponse = new LinkedHashMap<>();
		DateFormat df = new SimpleDateFormat("ddMMyy");
		Date dateobj = new Date();
		String loanId = "BPL" + df.format(dateobj) + creditApplication.getId();
		creditApplication.setAgreementAt(new Date());
		 
		creditApplication.setLatitude(Double.valueOf(meta.getLatitude()));
		creditApplication.setLongitude(Double.valueOf(meta.getLongitude()));
		creditApplication.setIp(meta.getIp());
		 
		  if("TOPUP".equalsIgnoreCase(creditApplication.getLoanType())){
			logger.info("TOPUP loan submitted for merchant {}", merchant.getId());
			if(creditApplication.getAmount() > 100000) {
				creditApplication.setStatus("pending_verification");
			} else {
				  
				creditApplication.setStatus("approved");
			} 
		} else if (creditApplicationNach.getNachStatus() != null && (creditApplicationNach.getNachStatus().equalsIgnoreCase("initiated") || creditApplicationNach.getNachStatus().equalsIgnoreCase("approved"))) {
			logger.info("Physical nach submitted by merchant: {}", merchant.getId());
			creditApplication.setStatus("approved");
			  
		} else {
			creditApplication.setStatus("pending_verification");
		}
		
		finalResponse.put("success",false);
		finalResponse.put("agreement_verified",false);
		 creditApplicationDao.save(creditApplication);
		LoyaltyServiceRequest requestBean = new LoyaltyServiceRequest.LoyaltyServiceRequestBuilder(merchant.getId(), LoyaltyTransactionType.PRE_BOOK_LOAN)
				.amount(0D)
				.merchantStoreId(null)
				.transactionId(creditApplication.getId())
				.build();
		loyaltyService.pushAsync(requestBean);


		LendingAuditTrial lendingAuditTrial = new LendingAuditTrial();
		lendingAuditTrial.setApplicationId(creditApplication.getId());
		lendingAuditTrial.setMerchantId(merchant.getId());
		lendingAuditTrial.setLoanId(loanId);
		lendingAuditTrial.setUserId(Long.parseLong("0"));
		lendingAuditTrial.setOldStatus("draft");
		if ((creditApplicationNach.getNachStatus() != null && (creditApplicationNach.getNachStatus().equalsIgnoreCase("initiated") || creditApplicationNach.getNachStatus().equalsIgnoreCase("approved")))) {
			lendingAuditTrial.setNewStatus("approved");
		} else {
			lendingAuditTrial.setNewStatus("pending_verification");
		}
		lendingAuditTrial.setType("APP_STATUS");

		lendingAuditTrialDao.save(lendingAuditTrial);

		String bankCode = null;
		MerchantBankDetail merchantBankDetail = merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchant.getId(),"ACTIVE");
		if (merchantBankDetail != null && meta.getAppVersion() != null && Integer.parseInt(meta.getAppVersion()) >= 238) {
			bankCode = eNachService.fetchBankCode(merchantBankDetail.getIfscCode().substring(0,4), "BOTH");
		} else if (merchantBankDetail != null){
			bankCode = eNachService.fetchBankCode(merchantBankDetail.getIfscCode().substring(0,4), "NET");
		}
		if (ExperianConstants.LOCKDOWN && bankCode != null && merchant.getBusinessCategory() != null && creditApplication.getAmount() > 100000D && creditApplication.getLoanType() != null && creditApplication.getLoanType().equalsIgnoreCase("PREBOOK")) {
			preBookExecutor.submit(() -> checkPreBook(merchant, creditApplication));
		} else {
			notificationExecutor.submit(() -> sendNotification(merchant, creditApplication));
		}
		
		finalResponse.put("success",true);
		finalResponse.put("agreement_verified",true);
		return finalResponse;
	}

	private void checkPreBook(Merchant merchant,CreditApplication creditApplication) {
		LendingPrebookLoans lendingPrebookLoans = lendingPrebookLoansDao.findByMerchantId(merchant.getId());
		if (lendingPrebookLoans != null) {
			logger.info("Prebook loan already exists for merchant: {}", merchant.getId());
			notificationExecutor.submit(() -> sendNotification(merchant, creditApplication));
			return;
		}
		MerchantSummaryLending merchantSummaryLending = merchantSummaryLendingDao.findByMerchantId(merchant.getId());
		MerchantSummary merchantSummary = merchantSummaryDao.getByMerchantId(merchant.getId());
		LendingCategories lendingCategories = lendingCategoryDao.getByCategory(creditApplication.getCategory());
		List<String> preBookCategories = Arrays.asList("Grocery","Medical","Dairy");
		List<String> etcCategories = Arrays.asList("S1LG","S1DG","S2LG","S2DG");
		List<String> cities = Arrays.asList("Bangalore", "Hyderabad", "Pune", "Delhi");
		CreditApplicationAddress creditApplicationaddress=creditApplicationAddressDao.findByMerchantIdAndApplicationId(merchant.getId(),creditApplication.getId());
		
		if (preBookCategories.contains(merchant.getBusinessCategory()) && merchantSummaryLending != null && merchantSummaryLending.getSegment().equalsIgnoreCase("2") && merchantSummary.getBpScore() > 10 && lendingCategories.getMasterCategory() != null && etcCategories.contains(lendingCategories.getMasterCategory()) && cities.contains(creditApplicationaddress.getCity())) {
			Calendar c = Calendar.getInstance();
			c.setTime(creditApplication.getAgreementAt());
			c.add(Calendar.DATE, -9);
			Date startDate = c.getTime();
			List<Object[]> transactions = paymentTransactionNewDao.getCountForPreBook(merchant.getId(), startDate, creditApplication.getAgreementAt());
			if (transactions != null && transactions.size() >= 8) {
				Double previousLoanAmount = creditApplication.getAmount();
				AvailableLoan availableLoan = new AvailableLoan();
				availableLoan.setAmount(100000D);
				LoanCalculationUtil.LoanBreakupDetail breakup = LoanCalculationUtil.getLoanBreakup(availableLoan, lendingCategories, null);
				  
				creditApplication.setDisbursalAmount((double)breakup.getLoanAmount());
				creditApplication.setAmount((double)breakup.getLoanAmount());
				creditApplicationDao.save(creditApplication);
				lendingPrebookLoansDao.save(new LendingPrebookLoans(merchant.getId(), creditApplication.getId(), previousLoanAmount));
				logger.info("Updated loan amount to 100000 for merchant: {} with applicationId: {}", merchant.getId(), creditApplication.getId());
			}
		}
		notificationExecutor.submit(() -> sendNotification(merchant, creditApplication));
	}

	private void sendNotification(Merchant merchant, CreditApplication  creditApplication) {
		
		MerchantBankDetail merchantBankDetail = merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchant.getId(),"ACTIVE");
		if(merchantBankDetail == null) {
			return;
		}

		List<String> mobiles = new ArrayList<> ();
		mobiles.add(merchant.getMobile());
		Double loanAmount = creditApplication.getAmount();
		
		String smsContent = "Hi "+merchantBankDetail.getBeneficiaryName()+",\n\nYour loan application for INR "+loanAmount.intValue()+" has been received successfully.\n\nYour Application ID is "+creditApplication.getId()+".\n\nNote: Due to necessary precautions for Coronavirus, there may be some delay in processing your application. We'll keep you updated.";

		String prebookSms = "Hi "+merchantBankDetail.getBeneficiaryName()+",\nYou have successfully Pre-booked your Rs."+loanAmount.intValue()+" Loan with BharatPe which you will get in your " + merchantBankDetail.getBankName() + " A/c in 10 days post Lockdown.\nYou have scored 10 Runs which you can use to get Rewards on BharatPe App.";
		smsServiceHandler.sendSMS(mobiles, smsContent, NotificationProvider.SMS.GUPSHUP);
		smsServiceHandler.sendSMS(mobiles, prebookSms, NotificationProvider.SMS.GUPSHUP);

//		String whatsappContent = "Hi  " + merchantBankDetail.getBeneficiaryName() + ",\n" +
//				"\n" +
//				"Your loan application for INR " + loanAmount.intValue() + " has been received successfully.\n" +
//				"Your Application ID is " + lendingApplication.getExternalLoanId() + ".";
		
		whatsappNotificationService.send(merchant, null, smsContent, mobiles, null);

		MerchantFcmToken merchantFcmToken = merchantFcmTokenDao.findByMerchantId(merchant.getId());
		
		if(merchantFcmToken != null) {
			String pushContent = "Dear "+merchantBankDetail.getBeneficiaryName()+", Your loan application for INR "+loanAmount.intValue()+" has been received successfully.";
			pushNotificationHandler.sendPushNotification(merchantFcmToken.getFcmToken(), merchantFcmToken.getPlatform(), pushContent, "dynamic?key=loan");
			if (isPaymentBank(merchant, merchantBankDetail)) {
				String pushNotification = "Hi  " + merchantBankDetail.getBeneficiaryName() + ",\n" +
						"\n" +
						"We have received your Loan Application of Rs." + loanAmount.intValue() + ".Our lending partners do not support disbursal in Payment Banks. Please change your registered account with us to a non-payment bank to get Rs." + loanAmount.intValue() + " NOW!";
				pushNotificationHandler.sendPushNotification(merchantFcmToken.getFcmToken(), merchantFcmToken.getPlatform(), pushNotification, "dynamic?key=change-acc");
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

  
