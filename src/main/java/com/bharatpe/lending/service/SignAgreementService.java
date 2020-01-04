package com.bharatpe.lending.service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
import com.bharatpe.common.entities.Merchant;
import com.bharatpe.common.entities.MerchantSummary;
import com.bharatpe.common.entities.TmpLoanGenerate;
import com.bharatpe.common.objects.CommonAPIRequest;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingAuditTrialDao;
import com.bharatpe.lending.dao.LendingCategoryDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dao.TmpLoanGenerateDao;
import com.bharatpe.lending.handlers.GupShupOTPHandler;
import com.bharatpe.lending.util.LoanCalculationUtil;
import com.bharatpe.lending.util.LoanCalculationUtil.LoanBreakupDetail;

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
	
	@Autowired
	GupShupOTPHandler gupShupOTPHandler;

	public Map<String,Boolean> signAgreement(Merchant merchant, @RequestBody CommonAPIRequest commonAPIRequest) {
		Map<String, Boolean> finalResponse = new LinkedHashMap<>();
		finalResponse.put("success",false);
		finalResponse.put("otp_flow",false);
		
		Long merchantId = merchant.getId();
		Long mobile = Long.parseLong(merchant.getMobile());
		
		String latitude = commonAPIRequest.getMeta().getLatitude();
		String longitude = commonAPIRequest.getMeta().getLongitude();
		String ip = commonAPIRequest.getMeta().getIp();
		
		Long applicationId =  commonAPIRequest.getPayload().get("application_id") != null ? Long.parseLong(commonAPIRequest.getPayload().get("application_id").toString()) : null;
		Boolean agreement =  commonAPIRequest.getPayload().get("agreement") != null ? (boolean) commonAPIRequest.getPayload().get("agreement") : false;
		Map<String, String> selectedLoan = (Map<String, String>) commonAPIRequest.getPayload().get("selected_loan");
		
		if(agreement == true) {
			if(applicationId != null) {
				finalResponse = verifyApplicationAndSendOTP(merchantId, applicationId, mobile);
			} else {
				finalResponse = createNewApplicationAndSendOTP(selectedLoan, merchantId, mobile, latitude, longitude, ip);
			}
		}
		
		return finalResponse;
	}
	
	private Map<String, Boolean> verifyApplicationAndSendOTP(Long merchantId, Long applicationId, Long mobile) {
		Map<String, Boolean> response = new LinkedHashMap<>();
		response.put("success",false);
		response.put("otp_flow",false);
		
		LendingApplication lendingApplication = lendingApplicationDao.findByApplicationId(applicationId);
		
		if(lendingApplication == null || !"draft".equals(lendingApplication.getStatus())) {
			logger.info("Application is empty or status is not in draft with id {}, returing.", applicationId);
		}
		
		List<DocumentsIdProof> documentsIdProofList = documentsIdProofDao.findByMerchantIdAndApplicationId(merchantId, applicationId);
		if(documentsIdProofList.size() > 0) {
			response = sendOTP(mobile);
		}
		return response;
	}
	
	private Map<String, Boolean> createNewApplicationAndSendOTP(Map<String, String> selectedLoan, Long merchantId, Long mobile, String latitude, String longitude, String ip) {
		Map<String, Boolean> response = new LinkedHashMap<>();
		response.put("success",false);
		response.put("otp_flow",false);
		Double loanAmount = null;
		String selectedCategory = selectedLoan.get("category");
		
		MerchantSummary merchantSummary = merchantSummaryDao.findByMerchantId(merchantId);
		if(merchantSummary != null) {
			
			LendingPaymentSchedule prevLendingScheule = lendingPaymentScheduleDao.findLatestLendingPaymentScheduleByMerchantId(merchantId);
			LendingApplication prevApplication = lendingApplicationDao.findTop1ByMerchantIdOrderByApplicationIdDesc(merchantId);
			
			if(prevLendingScheule == null || prevApplication == null || !prevLendingScheule.getStatus().equals("CLOSED") || !"DISBURSED".equals(prevApplication.getLoanDisbursalStatus())) {
				logger.error("User not eligible, last loan not closed/found or last application is not disbursed/found");
				return response;
			}
			
			List<AvailableLoan> availableLoanList = availableLoanDao.findByMerchantIdAndTypeOrderByAmountDesc(merchantId, merchantSummary.getLoanType());
			
			AvailableLoan selectedAvailableLoan = null;
			
			for(AvailableLoan current : availableLoanList) {
				if(current.getCategory().equals(selectedCategory)) {
					selectedAvailableLoan = current;
					break;
				}
			}
			
			if(selectedAvailableLoan == null) {
				logger.error("No availabel loan found with merchant id {} and loan category {}", merchantId, selectedCategory);
				return response;
			}
			
			LendingCategories selectedCategoriesData = lendingCategoryDao.findByCategory(selectedCategory).get(0);
			
			LoanBreakupDetail breakup = LoanCalculationUtil.getLoanBreakup(selectedAvailableLoan, selectedCategoriesData);
			
			LendingApplication newApplication = new LendingApplication();
			
			newApplication.setEdi(Double.valueOf(breakup.getEdi()));
			newApplication.setIoEdi(Double.valueOf(breakup.getIoEdi()));
			newApplication.setRepayment(Double.valueOf(breakup.getRepayment()));
			newApplication.setInterestRate(breakup.getEffectiveInterestRate());
			newApplication.setProcessingFee(Double.valueOf(breakup.getProcessingFee()));
			newApplication.setMerchantId(merchantId);
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
			newApplication.setTenure(selectedCategoriesData.getPayableConverter());
			newApplication.setTenureInMonths(selectedCategoriesData.getTenureMonths().intValue());
			newApplication.setPayableDays(Long.valueOf(selectedCategoriesData.getPayableDays()));
			newApplication.setEdiFreeDays(selectedCategoriesData.getEdiFreeDays());
			newApplication.setIoPayableDays(selectedCategoriesData.getIoPayableDays());
			newApplication.setLoanAmount(loanAmount);
			newApplication.setLatitude(latitude);
			newApplication.setLongitude(longitude);
			newApplication.setIp(ip);
			lendingApplicationDao.save(newApplication);
			
			if(newApplication.getApplicationId() != null) {
				LendingAuditTrial lendingAuditTrial = new LendingAuditTrial();
				lendingAuditTrial.setMerchantId(merchantId);
				lendingAuditTrial.setApplicationId(newApplication.getApplicationId());
				lendingAuditTrial.setLoanId("");
				lendingAuditTrial.setUserId(Long.parseLong("0"));
				lendingAuditTrial.setNewStatus("draft");
				lendingAuditTrial.setType("APP_STATUS");
				lendingAuditTrialDao.save(lendingAuditTrial);
				
				TmpLoanGenerate tmpLoanGenerate = new TmpLoanGenerate();
				tmpLoanGenerate.setMerchantId(merchantId);
				tmpLoanGenerate.setMaxLoanAmount(loanAmount);
				tmpLoanGenerate.setApplicationId(newApplication.getApplicationId());
				
				if("AUTO".equals(prevApplication.getMode())) {
					replicateDocumentsForNewApplication(prevApplication.getApplicationId(), newApplication.getApplicationId(), merchantId, latitude, longitude, ip);
				} else {
					logger.info("Application mode is {}, not replicating documents for new application id {} and merchant id {}", newApplication.getApplicationId(), merchantId);
				}
				
				Instant start = Instant.now();
				response = sendOTP(mobile);
				Instant end = Instant.now();
				logger.info("Time Taken by GUPSHUP Send OTP API : {} miliseconds", Duration.between(start, end).toMillis());
			}
		}
		return response;
	}
	
	private void replicateDocumentsForNewApplication(Long prevApplicationId, Long newApplicationId, Long merchantId, String latitude, String longitude, String ip) {
		Long newKycInsertId = null;
		List<DocumentsIdProof> documentsIdProofList = documentsIdProofDao.findByMerchantIdAndApplicationId(merchantId, prevApplicationId);
		for(DocumentsIdProof documentsIdProof  : documentsIdProofList) {
			DocumentsIdProof toSaveDocuments = new DocumentsIdProof();
			toSaveDocuments.setMerchantId(merchantId);
			toSaveDocuments.setProofType(documentsIdProof.getProofType());
			toSaveDocuments.setProofFrontSide(documentsIdProof.getProofFrontSide());
			toSaveDocuments.setProofBackSide(documentsIdProof.getProofBackSide());
			toSaveDocuments.setApplicationId(newApplicationId);
			toSaveDocuments.setStatus("pending_verification");
			toSaveDocuments.setSinglePage(documentsIdProof.getSinglePage());
			toSaveDocuments.setLatitude(latitude);
			toSaveDocuments.setLongitude(longitude);
			toSaveDocuments.setIp(ip);
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
	
	private Map<String, Boolean> sendOTP(Long mobile) {
		Map<String, Boolean> finalResponse = new LinkedHashMap<>();
		finalResponse.put("success",false);
		finalResponse.put("otp_flow",false);
		
		String mobileString = mobile.toString();
		if(mobileString.length() == 12) {
			Boolean isOTPSent = gupShupOTPHandler.sendOTP(mobileString);
			if(isOTPSent) {
				finalResponse.put("success",true);
				finalResponse.put("otp_flow",true);
			}
		}
		return finalResponse;
	}
}
