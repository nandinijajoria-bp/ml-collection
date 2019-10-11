package com.bharatpe.lending.service;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;

import com.bharatpe.common.dao.MerchantFcmTokenDao;
import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingAuditTrial;
import com.bharatpe.common.entities.MerchantFcmToken;
import com.bharatpe.common.entities.Validate;
import com.bharatpe.common.objects.CommonAPIRequest;
import com.bharatpe.lending.constants.LendingConstants;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingAuditTrialDao;
import com.bharatpe.lending.dao.ValidateDao;

import okhttp3.MediaType;
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

	public Map<String, Boolean> runService(HttpServletRequest request, HttpServletResponse response, @RequestBody CommonAPIRequest commonAPIRequest) {
		Map<String, Boolean> finalResponse = new LinkedHashMap<>();
		finalResponse.put("success",false);
		finalResponse.put("agreement_verified",false);
		
		Long merchantId = Long.parseLong(request.getAttribute("merchantId").toString());
		String mobile = request.getAttribute("mobile").toString();
		
		Long applicationId =  commonAPIRequest.getPayload().get("application_id") != null ? Long.parseLong(commonAPIRequest.getPayload().get("application_id").toString()) : null;
		String otp =  commonAPIRequest.getPayload().get("otp") != null ? commonAPIRequest.getPayload().get("otp").toString() : null;
		Double loanAmount;
		
		Map<String, String> selectedLoan = (Map<String, String>) commonAPIRequest.getPayload().get("selected_loan");
		
		if(otp != null && !otp.isBlank()) {
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
				sendSuccessSMS(validate, mobile, loanAmount);
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
	
	private void sendSuccessSMS(Validate validate, String mobile, Double loanAmount) {
		OkHttpClient client = new OkHttpClient();

		Request request = new Request.Builder()
				  .url("http://enterprise.smsgupshup.com/GatewayAPI/rest?method=sendMessage&send_to="+mobile+"&msg=Dear%20"+validate.getBeneficiaryName()+",%20Your%20loan%20application%20for%20Rs.%20"+loanAmount+"%20has%20been%20received%20successfully.%20&msg_type=TEXT&userid="+LendingConstants.GUPSHUP_SENDMESSAGE_API_USERID+"&password="+LendingConstants.GUPSHUP_SENDMESSAGE_API_PASSWORD+"&auth_scheme=PLAIN&format=JSON")
				  .get()
				  .addHeader("Content-Type", "application/json")
				  .addHeader("Accept", "*/*")
				  .addHeader("Cache-Control", "no-cache")
				  .addHeader("Host", "enterprise.smsgupshup.com")
				  .addHeader("Accept-Encoding", "gzip, deflate")
				  .addHeader("Connection", "keep-alive")
				  .addHeader("cache-control", "no-cache")
				  .build();
		logger.info("VerifyOTP SendSuccessSMS api request : {}", request);
		try {
			Response response = client.newCall(request).execute();
			String responseBody = response.body().string();
			logger.info("VerifyOTP SendSuccessSMS api response : {}", responseBody);
		} catch (IOException e) {
			e.printStackTrace();
			logger.info("VerifyOTP SendSuccessSMS api exception : {} ",e.getMessage());
		}
	}
	
	private void sendNotification(Validate validate, Long merchantId, Double loanAmount) {
		MerchantFcmToken merchantFcmToken = merchantFcmTokenDao.findByMerchantId(merchantId);
		
		if(merchantFcmToken != null) {
			OkHttpClient client = new OkHttpClient();

			MediaType mediaType = MediaType.parse("application/json");
			okhttp3.RequestBody body = okhttp3.RequestBody.create(mediaType, "{\"to\":\""+merchantFcmToken.getFcmToken()+"\",\"data\" : {\"title\":\"BharatPe\",\"body\":\"Dear "+validate.getBeneficiaryName()+", Your loan application for INR "+loanAmount+" has been received successfully.\",\"soundname\":\"bharatpenotification\",\"image\":\"icon\",\"image-type\":\"circular\",\"url\":\"loan.html\"}}");
			Request request = new Request.Builder()
			  .url("https://fcm.googleapis.com/fcm/send")
			  .post(body)
			  .addHeader("Content-Type", "application/json")
			  .addHeader("Authorization", "key="+LendingConstants.FCM_GOOGLE_API_KEY)
			  .addHeader("Accept", "*/*")
			  .addHeader("Cache-Control", "no-cache")
			  .addHeader("Host", "fcm.googleapis.com")
			  .addHeader("Accept-Encoding", "gzip, deflate")
			  .addHeader("Content-Length", "254")
			  .addHeader("Connection", "keep-alive")
			  .addHeader("cache-control", "no-cache")
			  .build();
			logger.info("VerifyOTP SendSuccessNotification api request : {}", request);
			try {
				Response response = client.newCall(request).execute();
				String responseBody = response.body().string();
				logger.info("VerifyOTP SendSuccessNotification api response : {}", responseBody);
			} catch (IOException e) {
				e.printStackTrace();
				logger.info("VerifyOTP SendSuccessNotification api exception : {} ",e.getMessage());
			}
		}
		
	}
}
