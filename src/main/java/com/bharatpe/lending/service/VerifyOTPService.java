package com.bharatpe.lending.service;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
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
	
	private Long merchantId;
	private String mobile;
	private Double loanAmount;
	private Long applicationId;
	private String otp;
	
	private Map<String, Boolean> finalResponse = new LinkedHashMap<>();

	public Map<String, Boolean> runService(HttpServletRequest request, HttpServletResponse response, @RequestBody CommonAPIRequest commonAPIRequest) {
		this.finalResponse.put("success",false);
		this.finalResponse.put("agreement_verified",false);
		
		this.merchantId = Long.parseLong(request.getAttribute("merchantId").toString());
		this.mobile = request.getAttribute("mobile").toString();
		
		this.applicationId =  commonAPIRequest.getPayload().get("application_id") != null ? Long.parseLong(commonAPIRequest.getPayload().get("application_id").toString()) : null;
		this.otp =  commonAPIRequest.getPayload().get("otp") != null ? commonAPIRequest.getPayload().get("otp").toString() : null;
		
		Map<String, String> selectedLoan = (Map<String, String>) commonAPIRequest.getPayload().get("selected_loan");
		
		if(otp != null || !otp.isBlank()) {
			if(applicationId == null) {
				LendingApplication lendingApplication = lendingApplicationDao.findTop1ByMerchantIdOrderByApplicationIdDesc(merchantId);
				this.loanAmount = lendingApplication.getLoanAmount();
				this.applicationId = lendingApplication.getApplicationId();
			}else {
				this.loanAmount = Double.parseDouble(selectedLoan.get("loan_amount"));
			}
			verifyOTP();
		}
		return this.finalResponse;
	}
	
	private void verifyOTP() {
		if(this.mobile.length() == 12) {
			OkHttpClient client = new OkHttpClient();

			Request request = new Request.Builder()
			  .url("https://enterprise.smsgupshup.com/GatewayAPI/rest?userid=2000182191&password=uelCIwOHu&method=TWO_FACTOR_AUTH&v=1.1&phone_no="+this.mobile+"&otp_code="+this.otp)
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
						updateApplicationStatusAndSuccessSms();
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
				logger.info("VerifyOTPService otp api exception : {} ",e.getMessage());
			}
		}
	}
	
	private void updateApplicationStatusAndSuccessSms() {
		int updatedId = lendingApplicationDao.updateApplicationStatusAndAgreement("pending_verification", this.applicationId, this.merchantId, "draft", "pending_verification");
		if(updatedId != 0) {
			DateFormat df = new SimpleDateFormat("dMMY");
		    Date dateobj = new Date();
			
			String loanId = "BPL" + df.format(dateobj) + this.applicationId;
			LendingAuditTrial lendingAuditTrial = new LendingAuditTrial();
			lendingAuditTrial.setApplicationId(this.applicationId);
			lendingAuditTrial.setMerchantId(this.merchantId);
			lendingAuditTrial.setLoanId(loanId);
			lendingAuditTrial.setUserId(0);
			lendingAuditTrial.setOldStatus("draft");
			lendingAuditTrial.setNewStatus("pending_verification");
			lendingAuditTrial.setType("APP_STATUS");
			
			lendingAuditTrialDao.save(lendingAuditTrial);
			
			Validate validate = validateDao.findByMerchantId(this.merchantId);
			
			if(validate != null) {
				sendSuccessSMS(validate);
				sendNotification(validate);
			}
			
			this.finalResponse.put("success",true);
			this.finalResponse.put("agreement_verified",true);
		}
	}
	
	private void sendSuccessSMS(Validate validate) {
		OkHttpClient client = new OkHttpClient();

		Request request = new Request.Builder()
				  .url("http://enterprise.smsgupshup.com/GatewayAPI/rest?method=sendMessage&send_to="+this.mobile+"&msg=Dear%20"+validate.getBeneficiaryName()+",%20Your%20loan%20application%20for%20Rs.%20"+this.loanAmount+"%20has%20been%20received%20successfully.%20&msg_type=TEXT&userid=2000182193&password=GuqyK1xzG&auth_scheme=PLAIN&format=JSON")
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
	
	private void sendNotification(Validate validate) {
		MerchantFcmToken merchantFcmToken = merchantFcmTokenDao.findByMerchantId(this.merchantId);
		
		if(merchantFcmToken != null) {
			OkHttpClient client = new OkHttpClient();

			MediaType mediaType = MediaType.parse("application/json");
			okhttp3.RequestBody body = okhttp3.RequestBody.create(mediaType, "{\n\t\"to\":\""+merchantFcmToken.getFcmToken()+"\",\n\t\"data\" : {\n\t\t\"title\":\"BharatPe\",\n\t\t\"body\":\"Dear "+validate.getBeneficiaryName()+", Your loan application for INR "+this.loanAmount+" has been received successfully.\",\n\t\t\"soundname\":\"bharatpenotification\",\n\t\t\"image\":\"icon\",\n\t\t\"image-type\":\"circular\",\n\t\t\"url\":\"loan.html\"\n\t}\n}");
			Request request = new Request.Builder()
			  .url("https://fcm.googleapis.com/fcm/send")
			  .post(body)
			  .addHeader("Content-Type", "application/json")
			  .addHeader("Authorization", "key=AAAAoLytUlg:APA91bEtdXQK6Iyawl9wZWvMXlyQJNOznVIEErS-ZSQS3n4JE0RQRfS7amO3VQXfGnHgMC2nsbkgZqKqbZCCwT5c9FpHY-OO_k-iWTNlWRKRIjMEMNb_HtruEsG7ZXGhl7Y7zPI0zM0D")
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
