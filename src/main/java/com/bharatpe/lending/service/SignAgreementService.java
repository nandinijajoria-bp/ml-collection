package com.bharatpe.lending.service;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;

import com.bharatpe.common.dao.AvailableLoanDao;
import com.bharatpe.common.dao.DocAuthenticationDao;
import com.bharatpe.common.dao.DocKycDetailsDao;
import com.bharatpe.common.dao.DocumentsIdProofDao;
import com.bharatpe.common.dao.MerchantSummaryDao;
import com.bharatpe.common.entities.AvailableLoan;
import com.bharatpe.common.entities.DocAuthentication;
import com.bharatpe.common.entities.DocKycDetails;
import com.bharatpe.common.entities.DocumentsIdProof;
import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingAuditTrial;
import com.bharatpe.common.entities.LendingCategories;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.common.entities.MerchantSummary;
import com.bharatpe.common.entities.TmpLoanGenerate;
import com.bharatpe.common.objects.CommonAPIRequest;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingAuditTrialDao;
import com.bharatpe.lending.dao.LendingCategoryDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dao.TmpLoanGenerateDao;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@Service
public class SignAgreementService {
	Logger logger = LoggerFactory.getLogger(SignAgreementService.class);
	
	@Autowired
	LendingApplicationDao lendingApplicationDao;
	
	@Autowired
	DocumentsIdProofDao documentsIdProofDao;

	@Autowired
	MerchantSummaryDao merchantSummaryDao;
	
	@Autowired
	LendingPaymentScheduleDao lendingPaymentScheduleDao;
	
	@Autowired
	AvailableLoanDao availableLoanDao;
	
	@Autowired
	LendingCategoryDao lendingCategoryDao;
	
	@Autowired
	LendingAuditTrialDao lendingAuditTrialDao;
	
	@Autowired
	TmpLoanGenerateDao tmpLoanGenerateDao;
	
	@Autowired
	DocAuthenticationDao docAuthenticationDao;
	
	@Autowired
	DocKycDetailsDao docKycDetailsDao;
	
	private Long merchantId;
	private Long mobile;
	private Long applicationId;
	private String latitude;
	private String longitude;
	private String ip;
	private Map<String, Boolean> finalResponse = new LinkedHashMap<>();

	public Map<String,Boolean> runService(HttpServletRequest request, HttpServletResponse response, @RequestBody CommonAPIRequest commonAPIRequest) {
		this.finalResponse.put("success",false);
		this.finalResponse.put("otp_flow",false);
		
		this.merchantId = Long.parseLong(request.getAttribute("merchantId").toString());
		this.mobile = Long.parseLong(request.getAttribute("mobile").toString());
		
		this.latitude = commonAPIRequest.getMeta().getLatitude();
		this.longitude = commonAPIRequest.getMeta().getLongitude();
		this.ip = commonAPIRequest.getMeta().getIp();
		
		this.applicationId =  commonAPIRequest.getPayload().get("application_id") != null ? Long.parseLong(commonAPIRequest.getPayload().get("application_id").toString()) : null;
		Boolean agreement =  commonAPIRequest.getPayload().get("agreement") != null ? (boolean) commonAPIRequest.getPayload().get("agreement") : false;
		
		if(agreement == true) {
			if(applicationId != null) {
				verifyApplicationAndSendOTP();
			}else {
				createNewApplicationAndSendOTP();
			}
		}
		
		return this.finalResponse;
	}
	
	private void verifyApplicationAndSendOTP() {
		LendingApplication lendingApplication = lendingApplicationDao.findByApplicationId(this.applicationId);
		if(lendingApplication != null) {
			List<DocumentsIdProof> documentsIdProofList = documentsIdProofDao.findByMerchantIdAndApplicationId(this.merchantId, this.applicationId);
			if(documentsIdProofList.size() > 0) {
				sendOTP();
			}
		}
	}
	
	private void createNewApplicationAndSendOTP() {
		String prevTenure = "";
		Double loanAmount = null;
		String selectedCategory = "";
		
		MerchantSummary merchantSummary = merchantSummaryDao.findByMerchantId(this.merchantId);
		if(merchantSummary != null) {
			LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.findLatestLendingPaymentScheduleByMerchantId(this.merchantId);
			if(lendingPaymentSchedule != null && lendingPaymentSchedule.getStatus().equals("CLOSED")) {
				LendingApplication lendingApplication = lendingApplicationDao.findByApplicationId(lendingPaymentSchedule.getApplicationId());
				prevTenure = lendingApplication.getTenure();
			}
			List<AvailableLoan> availableLoanList = availableLoanDao.findByMerchantIdAndTypeOrderByAmountDesc(this.merchantId, merchantSummary.getLoanType());
			for(AvailableLoan availableLoan : availableLoanList) {
				List<LendingCategories> lendingCategoriesList = lendingCategoryDao.findByCategory(availableLoan.getCategory());
				if(lendingCategoriesList.size() == 1) {
					String payableConverter = lendingCategoriesList.get(0).getPayableConverter();
					if((prevTenure.equals("2 Weeks") || prevTenure.equals("1 Months")) && payableConverter == "3 Months") {
						selectedCategory = lendingCategoriesList.get(0).getCategory();
						loanAmount = availableLoan.getAmount();
					}else if(prevTenure.equals("3 Months") && payableConverter.equals("6 Months")) {
						selectedCategory = lendingCategoriesList.get(0).getCategory();
						loanAmount = availableLoan.getAmount();
					}else if(prevTenure.equals("6 Months") && payableConverter.equals("12 Months")) {
						selectedCategory = lendingCategoriesList.get(0).getCategory();
						loanAmount = availableLoan.getAmount();
					}
				}
			}
			List<LendingCategories> selectedCategoriesList = lendingCategoryDao.findByCategory(selectedCategory);
			if(selectedCategoriesList.size() > 0) {
				Float tenureMonths = selectedCategoriesList.get(0).getTenureMonths();
				if(loanAmount != 5000 || tenureMonths == 1.00) {
					selectedCategoriesList.get(0).setProcessingFee("0");
				}else {
					selectedCategoriesList.get(0).setInterestRate(Double.valueOf(0));
				}
				int edi = (int) Math.ceil((loanAmount + (loanAmount * (selectedCategoriesList.get(0).getInterestRate() / 100) * tenureMonths) + Double.parseDouble(selectedCategoriesList.get(0).getProcessingFee())) / selectedCategoriesList.get(0).getPayableDays());
				int repayment = Math.round(selectedCategoriesList.get(0).getPayableDays() * edi);
				Double interestRate = (((edi * selectedCategoriesList.get(0).getPayableDays() - loanAmount) / loanAmount) / tenureMonths) * 100;
				
				LendingApplication prevApplication = lendingApplicationDao.findTop1ByMerchantIdOrderByApplicationIdDesc(this.merchantId);
				if(prevApplication != null) {
					LendingApplication newApplication = new LendingApplication();
					newApplication.setMerchantId(this.merchantId);
					newApplication.setShopNumber(prevApplication.getShopNumber());
					newApplication.setStreetAddress(prevApplication.getStreetAddress());
					newApplication.setArea(prevApplication.getArea());
					newApplication.setLandmark(prevApplication.getLandmark());
					newApplication.setPincode(prevApplication.getPincode());
					newApplication.setCity(prevApplication.getCity());
					newApplication.setState(prevApplication.getState());
					newApplication.setBusinessName(prevApplication.getBusinessName());
					newApplication.setStatus("draft");
					newApplication.setCategory(selectedCategory);
					newApplication.setProcessingFee(Double.parseDouble(selectedCategoriesList.get(0).getProcessingFee()));
					newApplication.setEdi(Double.valueOf(edi));
					newApplication.setInterestRate(interestRate);
					newApplication.setRepayment(Double.valueOf(repayment));
					newApplication.setTenure(tenureMonths.toString());
					newApplication.setPayableDays((long) selectedCategoriesList.get(0).getPayableDays());
					newApplication.setLoanAmount(loanAmount);
					newApplication.setLatitude(this.latitude);
					newApplication.setLongitude(this.longitude);
					newApplication.setIp(this.ip);
					lendingApplicationDao.save(newApplication);
					
					if(newApplication.getApplicationId() != null) {
						LendingAuditTrial lendingAuditTrial = new LendingAuditTrial();
						lendingAuditTrial.setMerchantId(this.merchantId);
						lendingAuditTrial.setApplicationId(newApplication.getApplicationId());
						lendingAuditTrial.setLoanId("");
						lendingAuditTrial.setUserId(0);
						lendingAuditTrial.setNewStatus("draft");
						lendingAuditTrial.setType("APP_STATUS");
						lendingAuditTrialDao.save(lendingAuditTrial);
						
						TmpLoanGenerate tmpLoanGenerate = new TmpLoanGenerate();
						tmpLoanGenerate.setMerchantId(this.merchantId);
						tmpLoanGenerate.setMaxLoanAmount(loanAmount);
						tmpLoanGenerate.setApplicationId(newApplication.getApplicationId());
						
						replicateDocumentsForNewApplication(prevApplication.getApplicationId(), newApplication.getApplicationId());
						
						Instant start = Instant.now();
						sendOTP();
						Instant end = Instant.now();
						logger.info("Time Taken by GUPSHUP Send OTP API : {} miliseconds", Duration.between(start, end).toMillis());
					}
				}
			}
		}
	}
	
	private void replicateDocumentsForNewApplication(Long prevApplicationId, Long newApplicationId) {
		Long newKycInsertId = null;
		List<DocumentsIdProof> documentsIdProofList = documentsIdProofDao.findByMerchantIdAndApplicationId(this.merchantId, prevApplicationId);
		for(DocumentsIdProof documentsIdProof  : documentsIdProofList) {
			DocumentsIdProof toSaveDocuments = new DocumentsIdProof();
			toSaveDocuments.setMerchantId(this.merchantId);
			toSaveDocuments.setProofType(documentsIdProof.getProofType());
			toSaveDocuments.setProofFrontSide(documentsIdProof.getProofFrontSide());
			toSaveDocuments.setProofBackSide(documentsIdProof.getProofBackSide());
			toSaveDocuments.setApplicationId(newApplicationId);
			toSaveDocuments.setStatus("pending_verification");
			toSaveDocuments.setSinglePage(documentsIdProof.getSinglePage());
			toSaveDocuments.setLatitude(this.latitude);
			toSaveDocuments.setLongitude(this.longitude);
			toSaveDocuments.setIp(this.ip);
			documentsIdProofDao.save(toSaveDocuments);
			
			DocAuthentication docAuthentication = docAuthenticationDao.findByDocId(documentsIdProof.getId());
			DocKycDetails docKycDetails = docKycDetailsDao.findTop1ByDocIdOrderByIdDesc(documentsIdProof.getId());
			
			if(docAuthentication != null && docKycDetails != null) {
				String docType = docAuthentication.getDocType();
				
				if(documentsIdProof.getProofFrontSide() != null) {
					if(!documentsIdProof.getProofType().equals("selfie")) {
						newKycInsertId = insertIntoDocKycDetails(docKycDetails, toSaveDocuments ,"FRONT");
					}
					if(docType.equals("pancard")) {
						insertIntoDocAuthentication(docAuthentication, newKycInsertId, toSaveDocuments);
					}
				}
				if(documentsIdProof.getProofBackSide() != null) {
					if(!documentsIdProof.getProofType().equals("selfie")) {
						newKycInsertId = insertIntoDocKycDetails(docKycDetails, toSaveDocuments ,"BACK");
					}
					if(docType.equals("pancard")) {
						insertIntoDocAuthentication(docAuthentication, newKycInsertId, toSaveDocuments);
					}
				}
			}
		}
	}
	
	private Long insertIntoDocKycDetails(DocKycDetails oldDocKycDetails, DocumentsIdProof documentsIdProof, String docSide) {
		DocKycDetails docKycDetails = new DocKycDetails();
		
		docKycDetails.setMerchantId(oldDocKycDetails.getMerchantId());
		docKycDetails.setDocSide(docSide);
		docKycDetails.setDocId(documentsIdProof.getId());
		docKycDetails.setDocType(documentsIdProof.getProofType());
		docKycDetails.setQr(oldDocKycDetails.getQr());
		docKycDetails.setPersonName(oldDocKycDetails.getPersonName());
		docKycDetails.setDob(oldDocKycDetails.getDob());
		docKycDetails.setGender(oldDocKycDetails.getGender());
		docKycDetails.setFatherName(oldDocKycDetails.getFatherName());
		docKycDetails.setYob(oldDocKycDetails.getYob());
		docKycDetails.setDocNo(oldDocKycDetails.getDocNo());
		docKycDetails.setMotherName(oldDocKycDetails.getMotherName());
		docKycDetails.setAddress(oldDocKycDetails.getAddress());
		docKycDetails.setCity(oldDocKycDetails.getCity());
		docKycDetails.setState(oldDocKycDetails.getState());
		docKycDetails.setPincode(oldDocKycDetails.getPincode());
		docKycDetails.setCountryCode(oldDocKycDetails.getCountryCode());
		docKycDetails.setResponse(oldDocKycDetails.getResponse());
		docKycDetails.setStatus("pending_verification");
		docKycDetails.setModule(oldDocKycDetails.getModule());
		docKycDetails.setMode(oldDocKycDetails.getMode());
		
		docKycDetailsDao.save(docKycDetails);
		return docKycDetails.getId();
	}
	
	private void insertIntoDocAuthentication(DocAuthentication oldDocAuthentication, Long newKycDetailsId, DocumentsIdProof documentsIdProof) {
		DocAuthentication docAuthentication = new DocAuthentication();
		
		docAuthentication.setDocKycDetailsId(newKycDetailsId);
		docAuthentication.setDocId(documentsIdProof.getId());
		docAuthentication.setMerchantId(oldDocAuthentication.getMerchantId());
		docAuthentication.setDocType(oldDocAuthentication.getDocType());
		docAuthentication.setStatus(oldDocAuthentication.getStatus());
		docAuthentication.setDuplicate(oldDocAuthentication.getDuplicate());
		docAuthentication.setNameMatch(oldDocAuthentication.getNameMatch());
		docAuthentication.setDobMatch(oldDocAuthentication.getDobMatch());
		docAuthentication.setFullResponse(oldDocAuthentication.getFullResponse());
		docAuthentication.setDocStatus(oldDocAuthentication.getDocStatus());
		
		docAuthenticationDao.save(docAuthentication);
	}
	
	private void sendOTP() {
		String mobileString = mobile.toString();
		if(mobileString.length() == 12) {
			OkHttpClient client = new OkHttpClient();

			Request request = new Request.Builder()
			  .url("https://enterprise.smsgupshup.com/GatewayAPI/rest?userid=2000182191&password=uelCIwOHu&method=TWO_FACTOR_AUTH&v=1.1&phone_no="+mobileString+"&msg=BharatPe%3A%20%25code%25%20is%20your%20OTP%20to%20register%20yourself%20on%20BharatPe%20Merchant%20App.%20BharatPe.com&format=text&otpCodeLength=4&otpCodeType=NUMERIC")
			  .get()
			  .addHeader("Accept", "*/*")
			  .addHeader("Cache-Control", "no-cache")
			  .addHeader("Host", "enterprise.smsgupshup.com")
			  .addHeader("Accept-Encoding", "gzip, deflate")
			  .addHeader("Connection", "keep-alive")
			  .addHeader("cache-control", "no-cache")
			  .build();
			logger.info("SignAgreement otp api request : {}", request);
			try {
				Response response = client.newCall(request).execute();
				String responseBody = response.body().string();
				logger.info("SignAgreement otp api response : {}", responseBody);
				if(response.isSuccessful()) {
					responseBody = responseBody.replaceAll("\\s","");
					String[] responseSplit = responseBody.split("\\|");
					
					if(responseSplit[0].equals("success") == true) {
						this.finalResponse.put("success",true);
						this.finalResponse.put("otp_flow",true);
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
				logger.info("SignAgreementService otp api exception : {} ",e.getMessage());
			}
		}
	}
}
