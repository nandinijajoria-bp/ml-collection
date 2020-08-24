  
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestBody;

import com.bharatpe.common.objects.CommonAPIRequest;
import com.bharatpe.lending.constant.LendingConstants;
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

	@Value("${experian.enable:true}")
	Boolean EXPERIAN_ENABLED;

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

		if(prevLendingSchedule == null || prevApplication == null) {
			logger.error("User not eligible, last loan not found or last application is not disbursed/found");
			return response;
		}
		if ("TOPUP".equalsIgnoreCase(prevApplication.getLoanType()) && "ACTIVE".equalsIgnoreCase(prevLendingSchedule.getStatus()) && (!"deleted".equalsIgnoreCase(prevApplication.getStatus()) && !"DISBURSED".equalsIgnoreCase(prevApplication.getLoanDisbursalStatus()))) {
			logger.error("Topup loan already created for merchant:{}", merchant.getId());
			return response;
		}
		LendingCategories selectedCategoriesData = lendingCategoryDao.findByCategory(selectedCategory).get(0);
		List<EligibleLoan> eligibleLoans = eligibleLoanDao.findByMerchantIdAndCategory(merchant.getId(), selectedCategory);
		if(eligibleLoans == null || eligibleLoans.isEmpty()) {
			logger.error("No availabel loan found with merchant id {} and loan category {}", merchant.getId(), selectedCategory);
			return response;
		}
		EligibleLoan eligibleLoan = eligibleLoans.get(0);
		// pin code check for loan eligibility(removing this check for topup loan)
		try {
			logger.info("Starting pin code check for loan eligibilty ");
			response.put("code",LendingConstants.LOAN_APPLICATION_SUCCESS_CODE);
			response.put("message",LendingConstants.LOAN_APPLICATION_SUCCESS_MESSAGE);
			if(!"TOPUP".equalsIgnoreCase(eligibleLoan.getLoanType()) && !lendingApplicationService.checkLoanRequestPinCodeForLoanEligibilty((int)(long)prevApplication.getPincode())){
				logger.error("Pincode {} not eligible for the loan",(int)(long)prevApplication.getPincode());
				response.put("code",LendingConstants.LOAN_APPLICATION_OGL_CODE);
				response.put("message",LendingConstants.LOAN_APPLICATION_OGL_MESSAGE);
				return response;
			}
		}
		catch(Exception e) {
			logger.error("Error ocuured while checking loan eligibilty for pin code {}",(long)prevApplication.getPincode());
		}
		
		LendingApplication newApplication = new LendingApplication();

		if (EXPERIAN_ENABLED) {
			
			if(!"TOPUP".equalsIgnoreCase(eligibleLoan.getLoanType()) && (!prevLendingSchedule.getStatus().equals("CLOSED") || (!"deleted".equalsIgnoreCase(prevApplication.getStatus()) && !"DISBURSED".equalsIgnoreCase(prevApplication.getLoanDisbursalStatus())))) {
				logger.error("Last loan not closed for merchant ID {}", merchant.getId());
				return response;
			}
			int processingFee = (int) Math.ceil(eligibleLoan.getAmount() * Double.parseDouble(selectedCategoriesData.getProcessingFee()));
			newApplication.setEdi(Double.valueOf(eligibleLoan.getEdi()));
			newApplication.setIoEdi(Double.valueOf(eligibleLoan.getIoEdi()));
			newApplication.setRepayment(Double.valueOf(eligibleLoan.getRepayment()));
			if ("TOPUP".equalsIgnoreCase(eligibleLoan.getLoanType())) {
				newApplication.setInterestRate(1.75D);
			} else {
				newApplication.setInterestRate(selectedCategoriesData.getInterestRate());
			}
			newApplication.setProcessingFee((double)processingFee);
			newApplication.setLoanConstruct(eligibleLoan.getLoanConstruct());
			newApplication.setDisbursalAmount(eligibleLoan.getAmount() - processingFee);
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
			newApplication.setLoanType(eligibleLoan.getLoanType());
		} else {
			List<AvailableLoan> availableLoanList = availableLoanDao.findByMerchantIdAndTypeOrderByAmountDesc(merchant.getId(), merchantSummary.getLoanType());
			AvailableLoan selectedAvailableLoan = null;
			
			if(!prevLendingSchedule.getStatus().equals("CLOSED") || (!"deleted".equalsIgnoreCase(prevApplication.getStatus()) && !"DISBURSED".equalsIgnoreCase(prevApplication.getLoanDisbursalStatus()))) {
				logger.error("Last loan not closed for merchant ID {}", merchant.getId());
				return response;
			}
			
			for(AvailableLoan current : availableLoanList) {
				if(current.getCategory().equals(selectedCategory)) {
					selectedAvailableLoan = current;
					break;
				}
			}
			if(selectedAvailableLoan == null) {
				logger.error("No availabel loan found with merchant id {} and loan category {}", merchant.getId(), selectedCategory);
				return response;
			}
			LoanBreakupDetail breakup = LoanCalculationUtil.getLoanBreakup(selectedAvailableLoan, selectedCategoriesData, null);
			newApplication.setEdi(Double.valueOf(breakup.getEdi()));
			newApplication.setIoEdi(Double.valueOf(breakup.getIoEdi()));
			newApplication.setRepayment(Double.valueOf(breakup.getRepayment()));
			newApplication.setInterestRate(breakup.getEffectiveInterestRate());
			newApplication.setProcessingFee(Double.valueOf(breakup.getProcessingFee()));
			newApplication.setLoanConstruct(breakup.getConstruct());
			newApplication.setDisbursalAmount(Double.valueOf(breakup.getDisbursementAmount()));
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
			newApplication.setLoanAmount(Double.valueOf(breakup.getLoanAmount()));
		}
		if(!StringUtils.isEmpty(requestDTO.getMeta().getLatitude()))
			newApplication.setLatitude(requestDTO.getMeta().getLatitude());
		if(!StringUtils.isEmpty(requestDTO.getMeta().getLongitude()))
			newApplication.setLongitude(requestDTO.getMeta().getLongitude());
		newApplication.setIp(requestDTO.getMeta().getIp());
		newApplication.setTotalLoansCount(merchantSummary.getTotalLoansCount() == null ? 0 : merchantSummary.getTotalLoansCount());
		newApplication.setLender("LDC");
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
	
	public void replicateDocumentsForNewApplication(LendingApplication prevApplication, LendingApplication newApplication, Merchant merchant, MetaDTO meta) {
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
			String message = "BharatPe: %code% is your OTP to register yourself on BharatPe Merchant App. BharatPe.com";
			Boolean isOTPSent = gupShupOTPHandler.sendOTP(mobileString, message);
			if(isOTPSent) {
				finalResponse.put("success",true);
				finalResponse.put("otp_flow",true);
			}
		}
		return finalResponse;
	}
}