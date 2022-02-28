  
package com.bharatpe.lending.service;

import com.bharatpe.common.dao.*;
import com.bharatpe.common.entities.*;
import com.bharatpe.lending.common.dao.LendingEkycDao;
import com.bharatpe.lending.common.dao.LendingResubmitTaskDao;
import com.bharatpe.lending.common.dao.LendingShopDocumentsDao;
import com.bharatpe.lending.common.entity.LendingEkyc;
import com.bharatpe.lending.common.entity.LendingResubmitTask;
import com.bharatpe.lending.common.entity.LendingShopDocuments;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.constant.LendingConstants;
import com.bharatpe.lending.dao.*;
import com.bharatpe.lending.dto.MetaDTO;
import com.bharatpe.lending.dto.RequestDTO;
import com.bharatpe.lending.dto.SignAgreementDTO;
import com.bharatpe.lending.enums.KycStatus;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.handlers.BharatPeOtpHandler;
import com.bharatpe.lending.handlers.KycHandler;
import com.bharatpe.lending.loanV2.dto.KycStatusDTO;
import com.bharatpe.lending.util.LoanUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class SignAgreementService {
	Logger logger = LoggerFactory.getLogger(SignAgreementService.class);
	
	@Autowired
	LendingApplicationDao lendingApplicationDao;
	
	@Autowired
	DocumentsIdProofDao documentsIdProofDao;

	@Autowired
	LendingShopDocumentsDao lendingShopDocumentsDao;

	@Autowired
	LendingGstDao lendingGstDao;

	@Autowired
	MerchantSummaryDao merchantSummaryDao;
	
	@Autowired
	LendingPaymentScheduleDao lendingPaymentScheduleDao;
	
	@Autowired
	LendingCategoryDao lendingCategoryDao;
	
	@Autowired
	LendingAuditTrialDao lendingAuditTrialDao;
	
	@Autowired
	DocAuthenticationDao docAuthenticationDao;
	
	@Autowired
	DocKycDetailsDao docKycDetailsDao;
	
	@Autowired
	BharatPeOtpHandler bharatPeOtpHandler;
	
	@Autowired
	LendingApplicationService lendingApplicationService;

	@Autowired
	EligibleLoanDao eligibleLoanDao;

	@Autowired
	APIGatewayService apiGatewayService;

	@Autowired
	LenderMappingService lenderMappingService;

	@Autowired
	KycHandler kycHandler;

	@Autowired
	LendingEkycDao lendingEkycDao;

	@Autowired
	LendingResubmitTaskDao lendingResubmitTaskDao;

	@Autowired
	LoanUtil loanUtil;

	@Autowired
	EasyLoanUtil easyLoanUtil;


	ExecutorService executorService = Executors.newFixedThreadPool(10);

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
			finalResponse = verifyApplicationAndSendOTP(merchant, applicationId, requestDTO.getPayload().getAppSign());
		} else {
			finalResponse = createNewApplicationAndSendOTP(requestDTO, merchant);
		}

		return finalResponse;
	}
	
	private Map<String, Object> verifyApplicationAndSendOTP(Merchant merchant, Long applicationId, String appSign) {
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("success",false);
		response.put("otp_flow",false);
		
		LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchant(applicationId, merchant);
		LendingResubmitTask lendingResubmitTask =lendingResubmitTaskDao.findTopByApplicationId(applicationId);
		if((Objects.isNull(lendingResubmitTask) || lendingResubmitTask.getDowngradeDone())){
			if(lendingApplication == null || !"draft".equals(lendingApplication.getStatus())) {
				logger.info("Application is empty or status is not in draft with id {}, returing.", applicationId);
				return response;
			}
		}
		if (!StringUtils.isEmpty(lendingApplication.getCkycId())) {
			KycStatusDTO kycStatus = kycHandler.getKycStatus(lendingApplication.getMerchant().getId());
			logger.info("kyc status:{} for application:{}", kycStatus, lendingApplication.getId());
			if (kycStatus.getKycStatus().equals(KycStatus.NEW) || kycStatus.getKycStatus().equals(KycStatus.DRAFT)) {
				logger.info("kyc not done for application:{}", applicationId);
				return response;
			}
		} else {
			List<DocumentsIdProof> documentsIdProofList = documentsIdProofDao.findByMerchantAndLendingApplication(merchant, lendingApplication);
			List<LendingShopDocuments> shopDocuments = lendingShopDocumentsDao.findByMerchantIdAndApplicationId(merchant.getId(), lendingApplication.getId());
			if(documentsIdProofList == null || documentsIdProofList.size() == 0 || shopDocuments.isEmpty()) {
				logger.info("documents not found for application:{}", applicationId);
				return response;
			}
		}
        executorService.execute(() -> loanUtil.publishDSData(lendingApplication));
		response =  sendOTP(merchant, appSign);
		response.put("application_id", applicationId);
		return response;
	}
	
	private Map<String, Object> createNewApplicationAndSendOTP(RequestDTO<SignAgreementDTO> requestDTO, Merchant merchant) {
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("success",false);
		response.put("otp_flow",false);
		List<String> topupLoans = Arrays.asList(LoanType.TOPUP.name(), LoanType.HALF_TOPUP.name(), LoanType.IO_TOPUP.name());
		List<String> ioHalfTopupLoans = Arrays.asList(LoanType.HALF_TOPUP.name(), LoanType.IO_TOPUP.name());
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

		LendingApplication checkDupe = lendingApplicationDao.findOpenApplication(merchant.getId());
		if(checkDupe != null){
			logger.error("Merchant Has Already Active Application for merchantId: {} And ApplicationId:{}", merchant.getId(),checkDupe.getId());
			return response;
		}

		LendingPaymentSchedule prevLendingSchedule = lendingPaymentScheduleDao.findLatestLendingPaymentScheduleByMerchantId(merchant.getId());
		LendingApplication prevApplication = lendingApplicationDao.findTop1ByMerchantAndStatusOrderByIdDesc(merchant, "APPROVED");

		if(prevLendingSchedule == null || prevApplication == null) {
			logger.error("User not eligible, last loan not found or last application is not disbursed/found");
			return response;
		}
		if (topupLoans.contains(prevApplication.getLoanType()) && "ACTIVE".equalsIgnoreCase(prevLendingSchedule.getStatus()) && (!"deleted".equalsIgnoreCase(prevApplication.getStatus()) && !"DISBURSED".equalsIgnoreCase(prevApplication.getLoanDisbursalStatus()))) {
			logger.error("Topup loan already created for merchant:{}", merchant.getId());
			return response;
		}
		LendingCategories selectedCategoriesData = lendingCategoryDao.getByCategory(selectedCategory);
		EligibleLoan eligibleLoan = eligibleLoanDao.findTopByMerchantIdAndOfferTypeOrderByIdDesc(merchant.getId(), selectedCategory);
		if(Objects.isNull(eligibleLoan)) {
			logger.error("No availabel loan found with merchant id {} and loan category {}", merchant.getId(), selectedCategory);
			return response;
		}
		// pin code check for loan eligibility(removing this check for topup loan)
		try {
			logger.info("Starting pin code check for loan eligibilty ");
			response.put("code",LendingConstants.LOAN_APPLICATION_SUCCESS_CODE);
			response.put("message",LendingConstants.LOAN_APPLICATION_SUCCESS_MESSAGE);
			if(prevApplication.getPincode() != null && !topupLoans.contains(eligibleLoan.getLoanType()) && !lendingApplicationService.checkLoanRequestPinCodeForLoanEligibilty((int)(long)prevApplication.getPincode())){
				logger.info("Pincode {} not eligible for the loan",(int)(long)prevApplication.getPincode());
				response.put("code",LendingConstants.LOAN_APPLICATION_OGL_CODE);
				response.put("message",LendingConstants.LOAN_APPLICATION_OGL_MESSAGE);
				return response;
			}
		}
		catch(Exception e) {
			logger.error("Error ocuured while checking loan eligibilty for pin code", e);
		}
		
		LendingApplication newApplication = new LendingApplication();

        if(!topupLoans.contains(eligibleLoan.getLoanType()) && (!prevLendingSchedule.getStatus().equals("CLOSED") || (!"deleted".equalsIgnoreCase(prevApplication.getStatus()) && !"DISBURSED".equalsIgnoreCase(prevApplication.getLoanDisbursalStatus())))) {
            logger.info("Last loan not closed for merchant ID {}", merchant.getId());
            return response;
        }
        int processingFee;
        if(apiGatewayService.eligibleForProcessingFee(merchant.getId())){
            processingFee = 0;
        }else {
            processingFee = eligibleLoan.getProcessingFee();
        }
        if (ioHalfTopupLoans.contains(eligibleLoan.getLoanType())) {
            processingFee = loanUtil.getIoHalfPF(prevLendingSchedule);
        }
        newApplication.setEdi(Double.valueOf(eligibleLoan.getEdi()));
        newApplication.setIoEdi(Double.valueOf(eligibleLoan.getIoEdi()));
        newApplication.setRepayment(Double.valueOf(eligibleLoan.getRepayment()));
        if ("TOPUP".equalsIgnoreCase(eligibleLoan.getLoanType())) {
            newApplication.setInterestRate(1.75D);
        } else {
            newApplication.setInterestRate(eligibleLoan.getRateOfInterest());
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
        newApplication.setTenure(eligibleLoan.getTenure());
        newApplication.setTenureInMonths(eligibleLoan.getTenureInMonths());
        newApplication.setPayableDays((long) eligibleLoan.getEdiCount());
        newApplication.setEdiFreeDays(eligibleLoan.getEdiFreeDays());
        newApplication.setIoPayableDays(eligibleLoan.getIoEdiDays());
        newApplication.setLoanAmount(eligibleLoan.getAmount());
        newApplication.setLoanType(eligibleLoan.getLoanType());
		if(!StringUtils.isEmpty(requestDTO.getMeta().getLatitude()) && !requestDTO.getMeta().getLatitude().trim().equalsIgnoreCase("undefined"))
			newApplication.setLatitude(requestDTO.getMeta().getLatitude());
		if(!StringUtils.isEmpty(requestDTO.getMeta().getLongitude()) && !requestDTO.getMeta().getLongitude().trim().equalsIgnoreCase("undefined"))
			newApplication.setLongitude(requestDTO.getMeta().getLongitude());
		newApplication.setIp(requestDTO.getMeta().getIp());
		newApplication.setTotalLoansCount(merchantSummary.getTotalLoansCount() == null ? 0 : merchantSummary.getTotalLoansCount());
        newApplication = lendingApplicationDao.save(newApplication);
        loanUtil.publishApplicationEvent(newApplication);
		lenderMappingService.lenderMapping(newApplication);

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
			response = sendOTP(merchant, requestDTO.getPayload().getAppSign());
			Instant end = Instant.now();
			logger.info("Time Taken by GUPSHUP Send OTP API : {} miliseconds", Duration.between(start, end).toMillis());
			response.put("application_id", newApplication.getId());
			loanUtil.createApplicationSnapshot(newApplication);
		}
        LendingApplication finalNewApplication = newApplication;
        executorService.execute(() -> apiGatewayService.globalLimitTxn(finalNewApplication.getMerchant().getId(), "DEBIT", finalNewApplication.getLoanAmount()));
        executorService.execute(() -> loanUtil.publishDSData(finalNewApplication));
		return response;
	}
	
	public void replicateDocumentsForNewApplication(LendingApplication prevApplication, LendingApplication newApplication, Merchant merchant, MetaDTO meta) {
		DocKycDetails panDoc = docKycDetailsDao.fetchLatestPanCardDetails(prevApplication.getMerchant().getId(),prevApplication.getId());
		DocKycDetails poaDoc = docKycDetailsDao.fetchLatestAddressDetails(prevApplication.getMerchant().getId(),prevApplication.getId());
		DocumentsIdProof selfie = documentsIdProofDao.findTop1ByMerchantAndLendingApplicationAndProofTypeAndDeletedAtIsNullOrderByIdDesc(merchant,prevApplication,"selfie");

		if(panDoc == null){
			panDoc = docKycDetailsDao.fetchPanMerchantId(merchant.getId());
		}

		if(poaDoc == null){
			poaDoc = docKycDetailsDao.fetchPoaMerchantId(merchant.getId());
		}

		if(panDoc != null){
			DocumentsIdProof panDocument = new DocumentsIdProof();
			panDocument.setMerchant(merchant);
			panDocument.setProofType(panDoc.getDocumentsIdProof().getProofType());
			panDocument.setProofFrontSide(panDoc.getDocumentsIdProof().getProofFrontSide());
			panDocument.setProofBackSide(panDoc.getDocumentsIdProof().getProofBackSide());
			panDocument.setLendingApplication(newApplication);
			panDocument.setIdentityId(panDoc.getDocumentsIdProof().getIdentityId());
			panDocument.setAccesstoken(panDoc.getDocumentsIdProof().getAccesstoken());
			panDocument.setFaceMatch(panDoc.getDocumentsIdProof().getFaceMatch());
			panDocument.setFacePercentage(panDoc.getDocumentsIdProof().getFacePercentage());
			panDocument.setIdType(panDoc.getDocumentsIdProof().getIdType());
			panDocument.setPanNameMatch(panDoc.getDocumentsIdProof().getPanNameMatch());
			panDocument.setPanNamePercentage(panDoc.getDocumentsIdProof().getPanNamePercentage());
			panDocument.setStatus(panDoc.getDocumentsIdProof().getStatus());
			panDocument.setSinglePage(panDoc.getDocumentsIdProof().getSinglePage());
			if(!StringUtils.isEmpty(meta.getLatitude()) && !meta.getLatitude().trim().equalsIgnoreCase("undefined"))
				panDocument.setLatitude(meta.getLatitude());
			if(!StringUtils.isEmpty(meta.getLongitude()) && !meta.getLongitude().trim().equalsIgnoreCase("undefined"))
				panDocument.setLongitude(meta.getLongitude());
			panDocument.setIp(meta.getIp());
			documentsIdProofDao.save(panDocument);

			DocKycDetails panKyc = insertIntoDocKycDetails(panDoc, panDocument);
		}

		if(poaDoc != null){
			DocumentsIdProof poaDocument = new DocumentsIdProof();
			poaDocument.setMerchant(merchant);
			poaDocument.setProofType(poaDoc.getDocumentsIdProof().getProofType());
			poaDocument.setProofFrontSide(poaDoc.getDocumentsIdProof().getProofFrontSide());
			poaDocument.setProofBackSide(poaDoc.getDocumentsIdProof().getProofBackSide());
			poaDocument.setLendingApplication(newApplication);
			poaDocument.setIdentityId(poaDoc.getDocumentsIdProof().getIdentityId());
			poaDocument.setAccesstoken(poaDoc.getDocumentsIdProof().getAccesstoken());
			poaDocument.setFaceMatch(poaDoc.getDocumentsIdProof().getFaceMatch());
			poaDocument.setFacePercentage(poaDoc.getDocumentsIdProof().getFacePercentage());
			poaDocument.setIdType(poaDoc.getDocumentsIdProof().getIdType());
			poaDocument.setPanNameMatch(poaDoc.getDocumentsIdProof().getPanNameMatch());
			poaDocument.setPanNamePercentage(poaDoc.getDocumentsIdProof().getPanNamePercentage());
			poaDocument.setStatus(poaDoc.getDocumentsIdProof().getStatus());
			poaDocument.setSinglePage(poaDoc.getDocumentsIdProof().getSinglePage());
			if(!StringUtils.isEmpty(meta.getLatitude()) && !meta.getLatitude().trim().equalsIgnoreCase("undefined"))
				poaDocument.setLatitude(meta.getLatitude());
			if(!StringUtils.isEmpty(meta.getLongitude()) && !meta.getLongitude().trim().equalsIgnoreCase("undefined"))
				poaDocument.setLongitude(meta.getLongitude());
			poaDocument.setIp(meta.getIp());
			documentsIdProofDao.save(poaDocument);

			List<DocKycDetails> poaList = docKycDetailsDao.findByDocumentsIdProof(poaDoc.getDocumentsIdProof());

			for(DocKycDetails poa :poaList) {
				DocKycDetails poaKyc = insertIntoDocKycDetails(poa, poaDocument);
			}
		}

		if(selfie != null){
			DocumentsIdProof selfieDoc = new DocumentsIdProof();
			selfieDoc.setLendingApplication(newApplication);
			selfieDoc.setMerchant(merchant);
			selfieDoc.setProofType(selfie.getProofType());
			selfieDoc.setProofFrontSide(selfie.getProofFrontSide());
			selfieDoc.setProofBackSide(selfie.getProofBackSide());
			selfieDoc.setLendingApplication(newApplication);
			selfieDoc.setIdentityId(selfie.getIdentityId());
			selfieDoc.setAccesstoken(selfie.getAccesstoken());
			selfieDoc.setFaceMatch(selfie.getFaceMatch());
			selfieDoc.setFacePercentage(selfie.getFacePercentage());
			selfieDoc.setIdType(selfie.getIdType());
			selfieDoc.setPanNameMatch(selfie.getPanNameMatch());
			selfieDoc.setPanNamePercentage(selfie.getPanNamePercentage());
			selfieDoc.setStatus(selfie.getStatus());
			selfieDoc.setSinglePage(selfie.getSinglePage());
			if(!StringUtils.isEmpty(meta.getLatitude()) && !meta.getLatitude().trim().equalsIgnoreCase("undefined"))
				selfieDoc.setLatitude(meta.getLatitude());
			if(!StringUtils.isEmpty(meta.getLongitude()) && !meta.getLongitude().trim().equalsIgnoreCase("undefined"))
				selfieDoc.setLongitude(meta.getLongitude());
			selfieDoc.setIp(meta.getIp());
			documentsIdProofDao.save(selfieDoc);
		}

		LendingGstDetail lendingGstDetail =lendingGstDao.findByApplicationId(prevApplication.getId());
		if(lendingGstDetail != null){
			LendingGstDetail replicateGst = lendingGstDao.findByApplicationId(newApplication.getId());
			if(ObjectUtils.isEmpty(replicateGst)) {
				replicateGst = new LendingGstDetail();
				replicateGst.setApplicationId(newApplication.getId());
				replicateGst.setMerchantId(newApplication.getMerchant().getId());
			}
			replicateGst.setGst(lendingGstDetail.getGst());
			replicateGst.setBusinessCategory(lendingGstDetail.getBusinessCategory());
			replicateGst.setExperience(lendingGstDetail.getExperience());
			replicateGst.setGstNumber(lendingGstDetail.getGstNumber());
			replicateGst.setSalary(lendingGstDetail.getSalary());
			replicateGst.setEntityType(lendingGstDetail.getEntityType());
			replicateGst.setShopType(lendingGstDetail.getShopType());
			lendingGstDao.save(replicateGst);
		}

		List<LendingShopDocuments> lendingShopDocuments = lendingShopDocumentsDao.findByMerchantIdAndLendingApplicationId(prevApplication.getMerchant().getId(),prevApplication.getId());
		if(lendingShopDocuments.size() > 0 && !lendingShopDocuments.isEmpty()){
			for(LendingShopDocuments shopDocuments : lendingShopDocuments){
				LendingShopDocuments replicateShopDocument = new LendingShopDocuments();
				replicateShopDocument.setApplicationId(newApplication.getId());
				replicateShopDocument.setMerchantId(newApplication.getMerchant().getId());
				replicateShopDocument.setIp(shopDocuments.getIp());
				replicateShopDocument.setProofType(shopDocuments.getProofType());
				replicateShopDocument.setProofFrontSide(shopDocuments.getProofFrontSide());
				replicateShopDocument.setProofBackSide(shopDocuments.getProofBackSide());
				replicateShopDocument.setLongitude(shopDocuments.getLongitude());
				replicateShopDocument.setLatitude(shopDocuments.getLatitude());
				replicateShopDocument.setStatus(shopDocuments.getStatus());
				lendingShopDocumentsDao.save(replicateShopDocument);
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

		LendingEkyc lendingEkyc = lendingEkycDao.fetchEkycByMerchantId(oldDocKycDetails.getMerchant().getId());
		if("eaadhar".equalsIgnoreCase(documentsIdProof.getProofType()) && Objects.nonNull(lendingEkyc)) {
			docKycDetails.setDocNo(lendingEkyc.getMaskedAadhar());
		}
		else {
			docKycDetails.setDocNo(oldDocKycDetails.getDocNo());
		}

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
	
	private Map<String, Object> sendOTP(Merchant merchant, String appSign) {
		Map<String, Object> finalResponse = new LinkedHashMap<>();

		if (easyLoanUtil.isDummyMerchant(merchant.getId())) {
			finalResponse.put("success", Boolean.TRUE);
			finalResponse.put("otp_flow", true);
			finalResponse.put("uuid", UUID.randomUUID().toString());
			return finalResponse;
		}

		finalResponse.put("success",false);
		finalResponse.put("otp_flow",false);
		
		if(merchant.getMobile().length() == 12) {
			String hash = appSign != null ? appSign : "";
			String message = "<#> BharatPe: {otp} is your OTP to complete loan agreement for BharatPe Loans. NEVER SHARE THIS OTP WITH ANYONE. " + hash;
//			String message = "<#> BharatPe: %code% is your OTP to register yourself on BharatPe Merchant App. BharatPe.com";
			Map<String, Object> response = bharatPeOtpHandler.sendOtp(merchant.getMobile(), message);
			if(response != null) {
				finalResponse.put("success", response.get("success"));
				finalResponse.put("otp_flow",true);
				finalResponse.put("uuid",response.get("uuid"));
			}
		}
		return finalResponse;
	}
}