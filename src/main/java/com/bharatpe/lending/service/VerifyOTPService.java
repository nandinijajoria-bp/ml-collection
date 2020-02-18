package com.bharatpe.lending.service;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.bharatpe.common.entities.*;
import com.bharatpe.lending.dao.BankListDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.bharatpe.common.dao.MerchantBankDetailDao;
import com.bharatpe.common.dao.MerchantFcmTokenDao;
import com.bharatpe.common.enums.NotificationProvider;
import com.bharatpe.common.handlers.PushNotificationHandler;
import com.bharatpe.common.handlers.SmsServiceHandler;
import com.bharatpe.common.objects.CommonAPIRequest;
import com.bharatpe.common.objects.Meta;
import com.bharatpe.common.service.WhatsappNotificationService;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingAuditTrialDao;
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
	
	ExecutorService notificationExecutor = Executors.newFixedThreadPool(5);

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
		Map<String, Boolean> finalResponse = new LinkedHashMap<>();
		DateFormat df = new SimpleDateFormat("ddMMyy");
		Date dateobj = new Date();
		String loanId = "BPL" + df.format(dateobj) + lendingApplication.getId();
		
		finalResponse.put("success",false);
		finalResponse.put("agreement_verified",false);

		lendingApplication.setStatus("pending_verification");
		lendingApplication.setAgreementAt(new Date());
		lendingApplication.setAgreement(1);
		lendingApplication.setLatitude(meta.getLatitude());
		lendingApplication.setLongitude(meta.getLongitude());
		lendingApplication.setIp(meta.getIp());
		lendingApplication.setExternalLoanId(loanId);
		lendingApplicationDao.save(lendingApplication);


		LendingAuditTrial lendingAuditTrial = new LendingAuditTrial();
		lendingAuditTrial.setApplicationId(lendingApplication.getId());
		lendingAuditTrial.setMerchantId(merchant.getId());
		lendingAuditTrial.setLoanId(loanId);
		lendingAuditTrial.setUserId(Long.parseLong("0"));
		lendingAuditTrial.setOldStatus("draft");
		lendingAuditTrial.setNewStatus("pending_verification");
		lendingAuditTrial.setType("APP_STATUS");

		lendingAuditTrialDao.save(lendingAuditTrial);

		notificationExecutor.submit(() -> sendNotification(merchant, lendingApplication));
		
		finalResponse.put("success",true);
		finalResponse.put("agreement_verified",true);
		return finalResponse;
	}
		
	private void sendNotification(Merchant merchant, LendingApplication lendingApplication) {
		
		MerchantBankDetail merchantBankDetail = merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchant.getId(),"ACTIVE");
		if(merchantBankDetail == null) {
			return;
		}

		List<String> mobiles = new ArrayList<> ();
		mobiles.add(merchant.getMobile());
		Double loanAmount = lendingApplication.getLoanAmount();
		
		String smsContent = "Hi "+merchantBankDetail.getBeneficiaryName()+", loan application for Rs."+loanAmount.intValue()+" recd. (Loan ID- "+lendingApplication.getExternalLoanId()+"). Pls. submit addl. info for faster disbursal. bharatpe.in/loan";
		smsServiceHandler.sendSMS(mobiles, smsContent, NotificationProvider.SMS.GUPSHUP);

//		String whatsappContent = "Hi  " + merchantBankDetail.getBeneficiaryName() + ",\n" +
//				"\n" +
//				"Your loan application for INR " + loanAmount.intValue() + " has been received successfully.\n" +
//				"Your Application ID is " + lendingApplication.getExternalLoanId() + ".";
		
		whatsappNotificationService.send(merchant, null, smsContent, mobiles);

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
