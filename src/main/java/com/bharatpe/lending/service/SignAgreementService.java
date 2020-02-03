package com.bharatpe.lending.service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.bharatpe.common.dao.*;
import com.bharatpe.common.entities.*;
import com.bharatpe.common.objects.Meta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestBody;

import com.bharatpe.common.objects.CommonAPIRequest;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingAuditTrialDao;
import com.bharatpe.lending.dao.LendingCategoryDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dao.TmpLoanGenerateDao;
import com.bharatpe.lending.dto.MetaDTO;
import com.bharatpe.lending.dto.RequestDTO;
import com.bharatpe.lending.dto.SignAgreementDTO;
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
	
	@Autowired
	LendingApplicationService lendingApplicationService;

	@Autowired
	EligibleLoanDao eligibleLoanDao;

	public Map<String, Object> signAgreement(Merchant merchant, RequestDTO<SignAgreementDTO> requestDTO) {
		Map<String, Object> finalResponse = new LinkedHashMap<>();
		finalResponse.put("success",false);
		finalResponse.put("otp_flow",false);

		Boolean agreement =  requestDTO.getPayload().getAgreement();
		if(agreement == null || !agreement) {
			return finalResponse;
		}

		Long applicationId =  requestDTO.getPayload().getApplicationId();

		if(applicationId != null && applicationId != 0) {
			finalResponse = verifyApplicationAndSendOTP(merchant, applicationId);
		} else {
			finalResponse = createNewApplicationAndSendOTP(requestDTO, merchant);
		}

		return finalResponse;
	}
	
	private Map<String, Object> verifyApplicationAndSendOTP(Merchant merchant, Long applicationId) {
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("success",false);
		response.put("otp_flow",false);
		
		LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchant(applicationId, merchant);
		
		if(lendingApplication == null || !"draft".equals(lendingApplication.getStatus())) {
			logger.info("Application is empty or status is not in draft with id {}, returing.", applicationId);
			return response;
		}
		
		List<DocumentsIdProof> documentsIdProofList = documentsIdProofDao.findByMerchantAndLendingApplication(merchant, lendingApplication);
		if(documentsIdProofList == null || documentsIdProofList.size() == 0) {
			return response;
		}
		response =  sendOTP(merchant.getMobile());
		response.put("application_id", applicationId);
		return response;
	}
	
	private Map<String, Object> createNewApplicationAndSendOTP(RequestDTO<SignAgreementDTO> requestDTO, Merchant merchant) {
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("success",false);
		response.put("otp_flow",false);
		
		String selectedCategory = requestDTO.getPayload().getCategory();
		
		if(StringUtils.isEmpty(selectedCategory)) {
			logger.error("Selected category is null/empty for merchant {}", merchant.getId());
			return response;
		}
		
		MerchantSummary merchantSummary = merchantSummaryDao.findByMerchantId(merchant.getId());
		if(merchantSummary == null) {
			logger.error("Merchant summary is empty for merchant with id {}", merchant.getId());
			return response;
		}

		LendingPaymentSchedule prevLendingSchedule = lendingPaymentScheduleDao.findLatestLendingPaymentScheduleByMerchantId(merchant.getId());
		LendingApplication prevApplication = lendingApplicationDao.findTop1ByMerchantOrderByIdDesc(merchant);

		if(prevLendingSchedule == null || prevApplication == null || !prevLendingSchedule.getStatus().equals("CLOSED") || (!"deleted".equalsIgnoreCase(prevApplication.getStatus()) && !"DISBURSED".equals(prevApplication.getLoanDisbursalStatus()))) {
			logger.error("User not eligible, last loan not closed/found or last application is not disbursed/found");
			return response;
		}

		List<EligibleLoan> eligibleLoans = eligibleLoanDao.findByMerchantIdAndCategory(merchant.getId(), selectedCategory);
//
//		for(AvailableLoan current : availableLoanList) {
//			if(current.getCategory().equals(selectedCategory)) {
//				selectedAvailableLoan = current;
//				break;
//			}
//		}

		if(eligibleLoans == null || eligibleLoans.isEmpty()) {
			logger.error("No availabel loan found with merchant id {} and loan category {}", merchant.getId(), selectedCategory);
			return response;
		}
		EligibleLoan eligibleLoan = eligibleLoans.get(0);

		LendingCategories selectedCategoriesData = lendingCategoryDao.findByCategory(selectedCategory).get(0);
		//LoanBreakupDetail breakup = LoanCalculationUtil.getLoanBreakup(selectedAvailableLoan, selectedCategoriesData);

		LendingApplication newApplication = new LendingApplication();
		newApplication.setEdi(Double.valueOf(eligibleLoan.getEdi()));
		newApplication.setIoEdi(Double.valueOf(eligibleLoan.getIoEdi()));
		newApplication.setRepayment(Double.valueOf(eligibleLoan.getRepayment()));
		newApplication.setInterestRate(selectedCategoriesData.getInterestRate());
		newApplication.setProcessingFee(0D);
		newApplication.setLoanConstruct(eligibleLoan.getLoanConstruct());
		newApplication.setDisbursalAmount(eligibleLoan.getAmount());
		newApplication.setMerchant(merchant);
		newApplication.setShopNumber(prevApplication.getShopNumber());
		newApplication.setStreetAddress(prevApplication.getStreetAddress());
		newApplication.setArea(prevApplication.getArea());
		newApplication.setLandmark(prevApplication.getLandmark());
		newApplication.setPincode(prevApplication.getPincode());
		newApplication.setCity(prevApplication.getCity());
		newApplication.setState(prevApplication.getState());
		newApplication.setBusinessName(prevApplication.getBusinessName());
		newApplication.setStatus("draft");
		newApplication.setMode("AUTO");
		newApplication.setCategory(selectedCategory);
		newApplication.setTenure(selectedCategoriesData.getPayableConverter());
		newApplication.setTenureInMonths(selectedCategoriesData.getTenureMonths().intValue());
		newApplication.setPayableDays((long) selectedCategoriesData.getPayableDays());
		newApplication.setEdiFreeDays(selectedCategoriesData.getEdiFreeDays());
		newApplication.setIoPayableDays(selectedCategoriesData.getIoPayableDays());
		newApplication.setLoanAmount(eligibleLoan.getAmount());
		newApplication.setLatitude(requestDTO.getMeta().getLatitude());
		newApplication.setLongitude(requestDTO.getMeta().getLongitude());
		newApplication.setIp(requestDTO.getMeta().getIp());
		newApplication.setTotalLoansCount(merchantSummary.getTotalLoansCount() == null ? 0 : merchantSummary.getTotalLoansCount());
		lendingApplicationDao.save(newApplication);

		if(newApplication.getId() != null) {
			LendingAuditTrial lendingAuditTrial = new LendingAuditTrial();
			lendingAuditTrial.setMerchantId(merchant.getId());
			lendingAuditTrial.setApplicationId(newApplication.getId());
			lendingAuditTrial.setLoanId("");
			lendingAuditTrial.setUserId(Long.parseLong("0"));
			lendingAuditTrial.setNewStatus("draft");
			lendingAuditTrial.setType("APP_STATUS");
			lendingAuditTrialDao.save(lendingAuditTrial);

			if("AUTO".equalsIgnoreCase(prevApplication.getMode())) {
				replicateDocumentsForNewApplication(prevApplication, newApplication, merchant, requestDTO.getMeta());
			} else {
				logger.info("Application mode is {}, not replicating documents for new application id {} and merchant id {}", newApplication.getId(), merchant.getId());
			}

			Instant start = Instant.now();
			response = sendOTP(merchant.getMobile());
			Instant end = Instant.now();
			logger.info("Time Taken by GUPSHUP Send OTP API : {} miliseconds", Duration.between(start, end).toMillis());
			response.put("application_id", newApplication.getId());
			
			lendingApplicationService.createMerchantSummarySnapshot(merchant, newApplication, merchantSummary);
		}
		return response;
	}
	
	private void replicateDocumentsForNewApplication(LendingApplication prevApplication, LendingApplication newApplication, Merchant merchant, MetaDTO meta) {
		List<DocumentsIdProof> documentsIdProofList = documentsIdProofDao.findByMerchantAndLendingApplication(merchant, prevApplication);
		for(DocumentsIdProof documentsIdProof  : documentsIdProofList) {
			DocumentsIdProof toSaveDocuments = new DocumentsIdProof();
			toSaveDocuments.setMerchant(merchant);
			toSaveDocuments.setProofType(documentsIdProof.getProofType());
			toSaveDocuments.setProofFrontSide(documentsIdProof.getProofFrontSide());
			toSaveDocuments.setProofBackSide(documentsIdProof.getProofBackSide());
			toSaveDocuments.setLendingApplication(newApplication);
			toSaveDocuments.setStatus("pending_verification");
			Integer singleProofDoc = documentsIdProof.getSinglePage();
			if(singleProofDoc == null) {
				if(documentsIdProof.getProofBackSide() != null) {
					singleProofDoc = 0;
				}
			}
			toSaveDocuments.setSinglePage(singleProofDoc);
			toSaveDocuments.setLatitude(meta.getLatitude());
			toSaveDocuments.setLongitude(meta.getLongitude());
			toSaveDocuments.setIp(meta.getIp());
			documentsIdProofDao.save(toSaveDocuments);

			if(documentsIdProof.getProofType().equals("selfie")) {
				continue;
			}

			List<DocKycDetails> docKycDetailsList = docKycDetailsDao.findByDocumentsIdProof(documentsIdProof);

			for(DocKycDetails docKycDetails : docKycDetailsList) {
				DocKycDetails newDocKycDetails = insertIntoDocKycDetails(docKycDetails, toSaveDocuments);
				if("FRONT".equalsIgnoreCase(docKycDetails.getDocSide()) && "pancard".equalsIgnoreCase(toSaveDocuments.getProofType())) {
					List<DocAuthentication> docAuthenticationList = docAuthenticationDao.findByDocumentsIdProof(documentsIdProof);
					if(docAuthenticationList != null && docAuthenticationList.size() > 0) {
						insertIntoDocAuthentication(docAuthenticationList.get(0), newDocKycDetails, toSaveDocuments);
					}
				}
			}
		}
	}
	
	private DocKycDetails insertIntoDocKycDetails(DocKycDetails oldDocKycDetails, DocumentsIdProof documentsIdProof) {
		DocKycDetails docKycDetails = new DocKycDetails();
		
		docKycDetails.setMerchant(oldDocKycDetails.getMerchant());
		docKycDetails.setDocSide(oldDocKycDetails.getDocSide());
		docKycDetails.setDocumentsIdProof(documentsIdProof);
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
		return docKycDetails;
	}
	
	private void insertIntoDocAuthentication(DocAuthentication oldDocAuthentication, DocKycDetails docKycDetails, DocumentsIdProof documentsIdProof) {
		DocAuthentication docAuthentication = new DocAuthentication();
		docAuthentication.setDocKycDetails(docKycDetails);
		docAuthentication.setDocumentsIdProof(documentsIdProof);
		docAuthentication.setMerchant(oldDocAuthentication.getMerchant());
		docAuthentication.setDocType(oldDocAuthentication.getDocType());
		docAuthentication.setStatus(oldDocAuthentication.getStatus());
		docAuthentication.setDuplicate(oldDocAuthentication.getDuplicate());
		docAuthentication.setNameMatch(oldDocAuthentication.getNameMatch());
		docAuthentication.setDobMatch(oldDocAuthentication.getDobMatch());
		docAuthentication.setFullResponse(oldDocAuthentication.getFullResponse());
		docAuthentication.setDocStatus(oldDocAuthentication.getDocStatus());
		
		docAuthenticationDao.save(docAuthentication);
	}
	
	private Map<String, Object> sendOTP(String mobile) {
		Map<String, Object> finalResponse = new LinkedHashMap<>();
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
