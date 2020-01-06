package com.bharatpe.lending.service;

import com.bharatpe.common.dao.MerchantBankDetailDao;
import com.bharatpe.common.dao.MerchantFcmTokenDao;
import com.bharatpe.common.entities.*;
import com.bharatpe.common.enums.NotificationProvider;
import com.bharatpe.common.handlers.PushNotificationHandler;
import com.bharatpe.common.handlers.SmsServiceHandler;
import com.bharatpe.common.objects.CommonAPIRequest;
import com.bharatpe.common.objects.Meta;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingAuditTrialDao;
import com.bharatpe.lending.handlers.GupShupOTPHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;


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

		sendNotification(merchant, lendingApplication.getLoanAmount());
		finalResponse.put("success",true);
		finalResponse.put("agreement_verified",true);
		return finalResponse;
	}
		
	private void sendNotification(Merchant merchant, Double loanAmount) {
		MerchantBankDetail merchantBankDetail = merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchant.getId(),"ACTIVE");
		if(merchantBankDetail == null) {
			return;
		}

		List<String> mobiles = new ArrayList<> ();
		mobiles.add(merchant.getMobile());
		String message = "Dear "+merchantBankDetail.getBeneficiaryName()+", Your loan application for Rs. "+loanAmount+" has been received successfully.";
		smsServiceHandler.sendSMS(mobiles, message, NotificationProvider.SMS.GUPSHUP);

		MerchantFcmToken merchantFcmToken = merchantFcmTokenDao.findByMerchantId(merchant.getId());
		
		if(merchantFcmToken != null) {
			message = "Dear "+merchantBankDetail.getBeneficiaryName()+", Your loan application for INR "+loanAmount+" has been received successfully.";
			pushNotificationHandler.sendPushNotification(merchantFcmToken.getFcmToken(), merchantFcmToken.getPlatform(), message, "homepage.html");
		}
	}
}
