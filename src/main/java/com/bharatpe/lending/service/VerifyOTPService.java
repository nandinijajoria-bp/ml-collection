package com.bharatpe.lending.service;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;

import com.bharatpe.common.dao.MerchantFcmTokenDao;
import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingAuditTrial;
import com.bharatpe.common.entities.Merchant;
import com.bharatpe.common.entities.MerchantFcmToken;
import com.bharatpe.common.entities.Validate;
import com.bharatpe.common.enums.NotificationProvider;
import com.bharatpe.common.handlers.PushNotificationHandler;
import com.bharatpe.common.handlers.SmsServiceHandler;
import com.bharatpe.common.objects.CommonAPIRequest;
import com.bharatpe.lending.constants.LendingConstants;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingAuditTrialDao;
import com.bharatpe.lending.dao.ValidateDao;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@Service
public class VerifyOTPService {
	private Logger logger = LoggerFactory.getLogger(VerifyOTPService.class);
	
	@Autowired
	LendingApplicationDao lendingApplicationDao;
	
	@Autowired
	LendingAuditTrialDao lendingAuditTrialDao;
	
	@Autowired
	ValidateDao validateDao;
	
	@Autowired
	MerchantFcmTokenDao merchantFcmTokenDao;
	
	@Autowired
	PushNotificationHandler pushNotificationHandler;
	
	@Autowired
	SmsServiceHandler smsServiceHandler;

	public Map<String, Boolean> verifyOTP(Merchant merchant, @RequestBody CommonAPIRequest commonAPIRequest) {
		Map<String, Boolean> finalResponse = new LinkedHashMap<>();
		finalResponse.put("success",false);
		finalResponse.put("agreement_verified",false);
		
		Long merchantId = merchant.getId();
		String mobile = merchant.getMobile();
		
		Long applicationId =  commonAPIRequest.getPayload().get("application_id") != null ? Long.parseLong(commonAPIRequest.getPayload().get("application_id").toString()) : null;
		String otp =  commonAPIRequest.getPayload().get("otp") != null ? commonAPIRequest.getPayload().get("otp").toString() : null;
		Double loanAmount;
		
		Map<String, String> selectedLoan = (Map<String, String>) commonAPIRequest.getPayload().get("selected_loan");
		
		if(otp != null && !otp.isEmpty()) {
			if(applicationId == null) {
				LendingApplication lendingApplication = lendingApplicationDao.findTop1ByMerchantIdOrderByApplicationIdDesc(merchantId);
				loanAmount = lendingApplication.getLoanAmount();
				applicationId = lendingApplication.getApplicationId();
			}else {
				loanAmount = Double.parseDouble(selectedLoan.get("loan_amount"));
			}
			Instant start = Instant.now();
			finalResponse = verifyOTP(mobile, otp, merchantId, applicationId, loanAmount);
			Instant end = Instant.now();
			logger.info("Time Taken by GUPSHUP verify OTP API : {} miliseconds", Duration.between(start, end).toMillis());
		}
		return finalResponse;
	}
	
	private Map<String, Boolean> verifyOTP(String mobile, String otp, Long merchantId, Long applicationId, Double loanAmount) {
		Map<String, Boolean> finalResponse = new LinkedHashMap<>();
		finalResponse.put("success",false);
		finalResponse.put("agreement_verified",false);
		
		if(mobile.length() == 12) {
			OkHttpClient client = new OkHttpClient();

			Request request = new Request.Builder()
			  .url("https://enterprise.smsgupshup.com/GatewayAPI/rest?userid="+LendingConstants.GUPSHUP_OTP_API_USERID+"&password="+LendingConstants.GUPSHUP_OTP_API_PASSWORD+"&method=TWO_FACTOR_AUTH&v=1.1&phone_no="+mobile+"&otp_code="+otp)
			  .get()
			  .addHeader("cache-control", "no-cache")
			  .build();
			logger.info("VerifyOTPService otp api request : {}", request);
			try {
				Response response = client.newCall(request).execute();
				String responseBody = response.body().string();
				logger.info("VerifyOTPService otp api response : {}", responseBody);
				if(response.isSuccessful()) {
					responseBody = responseBody.replaceAll("\\s","");
					String[] responseSplit = responseBody.split("\\|");
					
					if(responseSplit[0].equals("success") == true) {
						finalResponse = updateApplicationStatusAndSuccessSms(merchantId, applicationId, mobile, loanAmount);
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
				logger.info("VerifyOTPService otp api exception : {} ",e.getMessage());
			}
		}
		return finalResponse;
	}
	
	private Map<String, Boolean> updateApplicationStatusAndSuccessSms(Long merchantId, Long applicationId, String mobile, Double loanAmount) {
		Map<String, Boolean> finalResponse = new LinkedHashMap<>();
		finalResponse.put("success",false);
		finalResponse.put("agreement_verified",false);
		
		int updatedId = lendingApplicationDao.updateApplicationStatusAndAgreement("pending_verification", applicationId, merchantId, "draft", "pending_verification");
		if(updatedId != 0) {
			DateFormat df = new SimpleDateFormat("dMMY");
		    Date dateobj = new Date();
			
			String loanId = "BPL" + df.format(dateobj) + applicationId;
			LendingAuditTrial lendingAuditTrial = new LendingAuditTrial();
			lendingAuditTrial.setApplicationId(applicationId);
			lendingAuditTrial.setMerchantId(merchantId);
			lendingAuditTrial.setLoanId(loanId);
			lendingAuditTrial.setUserId(Long.parseLong("0"));
			lendingAuditTrial.setOldStatus("draft");
			lendingAuditTrial.setNewStatus("pending_verification");
			lendingAuditTrial.setType("APP_STATUS");
			
			lendingAuditTrialDao.save(lendingAuditTrial);
			
			Validate validate = validateDao.findByMerchantId(merchantId);
			
			if(validate != null) {
				Instant start = Instant.now();
				List<String> mobiles = new ArrayList<> ();
				mobiles.add(mobile);
				String message = "Dear "+validate.getBeneficiaryName()+", Your loan application for Rs. "+loanAmount+" has been received successfully.";
				smsServiceHandler.sendSMS(mobiles, message, NotificationProvider.SMS.GUPSHUP);
				Instant end = Instant.now();
				logger.info("Time Taken by GUPSHUP sendMessage API : {} miliseconds", Duration.between(start, end).toMillis());
				
				start = Instant.now();
				sendNotification(validate, merchantId, loanAmount);
				end = Instant.now();
				logger.info("Time Taken by GUPSHUP fcm google API : {} miliseconds", Duration.between(start, end).toMillis());
			}
			
			finalResponse.put("success",true);
			finalResponse.put("agreement_verified",true);
		}
		return finalResponse;
	}
		
	private void sendNotification(Validate validate, Long merchantId, Double loanAmount) {
		MerchantFcmToken merchantFcmToken = merchantFcmTokenDao.findByMerchantId(merchantId);
		
		if(merchantFcmToken != null) {
			String message = "Dear "+validate.getBeneficiaryName()+", Your loan application for INR "+loanAmount+" has been received successfully.";
			pushNotificationHandler.sendPushNotification(merchantFcmToken.getFcmToken(), merchantFcmToken.getPlatform(), message, "homepage.html");
		}
	}
}
