
package com.bharatpe.lending.service;

import com.bharatpe.cache.service.LendingCache;
import com.bharatpe.common.dao.*;
import com.bharatpe.common.entities.*;
import com.bharatpe.lending.common.Handler.MerchantSummaryHandler;
import com.bharatpe.lending.common.bpnewmaster.dao.DocKycDetailsDaoMaster;
import com.bharatpe.lending.common.bpnewmaster.dao.DocumentsIdProofDaoMaster;
import com.bharatpe.lending.common.bpnewmaster.entity.DocKycDetailsMaster;
import com.bharatpe.lending.common.bpnewmaster.entity.DocumentsIdProofMaster;
import com.bharatpe.lending.common.dao.LendingEkycDao;
import com.bharatpe.lending.common.dao.LendingResubmitTaskDao;
import com.bharatpe.lending.common.dao.LendingShopDocumentsDao;
import com.bharatpe.lending.common.dto.MerchantResponseDTO;
import com.bharatpe.lending.common.entity.LendingEkyc;
import com.bharatpe.lending.common.entity.LendingResubmitTask;
import com.bharatpe.lending.common.entity.LendingShopDocuments;
import com.bharatpe.lending.common.enums.*;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.constant.LendingConstants;
import com.bharatpe.lending.dao.*;
import com.bharatpe.lending.dto.MetaDTO;
import com.bharatpe.lending.dto.RequestDTO;
import com.bharatpe.lending.dto.SignAgreementDTO;
import com.bharatpe.lending.dto.CreateApplicationRequestForTopupDTO;

import com.bharatpe.lending.enums.KycStatus;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.handlers.BharatPeOtpHandler;
import com.bharatpe.lending.handlers.KycHandler;
import com.bharatpe.lending.loanV2.dto.KycStatusDTO;
import com.bharatpe.lending.service.impl.LenderAssignService;
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
	DocumentsIdProofDaoMaster documentsIdProofDaoMaster;

	@Autowired
	LendingShopDocumentsDao lendingShopDocumentsDao;

	@Autowired
	LendingGstDao lendingGstDao;

//	@Autowired
//	MerchantSummaryDao merchantSummaryDao;

	@Autowired
	LendingPaymentScheduleDao lendingPaymentScheduleDao;

	@Autowired
	LendingCategoryDao lendingCategoryDao;

	@Autowired
	LendingAuditTrialDao lendingAuditTrialDao;

	@Autowired
	DocKycDetailsDaoMaster docKycDetailsDaoMaster;

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

	@Autowired
	LendingCache lendingCache;

//	@Autowired
//	MerchantDao merchantDao;

	@Autowired
	MerchantSummaryHandler merchantSummaryHandler;

	@Autowired
	LenderAssignService lenderAssignService;

	@Autowired
	LendingLedgerDao lendingLedgerDao;

	ExecutorService executorService = Executors.newFixedThreadPool(10);



	public Map<String, Object> signAgreement(BasicDetailsDto merchantBasicDetails, RequestDTO<SignAgreementDTO> requestDTO) {

		if (!ObjectUtils.isEmpty(merchantBasicDetails.getId())) {
			String loanDetailsCacheKey = "LENDING_LOAN_DETAILS_" + merchantBasicDetails.getId();
			logger.info("deleting cached key of loan details where merchant initiates agreement: {}",
					merchantBasicDetails.getId());
			if (Objects.nonNull(lendingCache.get(loanDetailsCacheKey))) {
				lendingCache.delete(loanDetailsCacheKey);
			}
		}
		Map<String, Object> finalResponse = new LinkedHashMap<>();
		finalResponse.put("success", false);
		finalResponse.put("otp_flow", false);

		Boolean agreement = requestDTO.getPayload().getAgreement();
		if (agreement == null || !agreement) {
			return finalResponse;
		}

//		Merchant merchant = merchantDao.getById(merchantBasicDetails.getId());
		Long applicationId =  requestDTO.getPayload().getApplicationId();

		if(applicationId != null && applicationId != 0) {
			finalResponse = verifyApplicationAndSendOTP(merchantBasicDetails, applicationId, requestDTO.getPayload().getAppSign());
		} else {
			finalResponse = createNewApplicationAndSendOTP(requestDTO, merchantBasicDetails);
		}

		return finalResponse;
	}



	private Map<String, Object> verifyApplicationAndSendOTP(BasicDetailsDto merchant, Long applicationId, String appSign) {
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("success", false);
		response.put("otp_flow", false);

		LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchantId(applicationId,
				merchant.getId());
		LendingResubmitTask lendingResubmitTask = lendingResubmitTaskDao.findTopByApplicationId(applicationId);
		if ((Objects.isNull(lendingResubmitTask))) {
			if (lendingApplication == null || !"draft".equals(lendingApplication.getStatus())) {
				logger.info("Application is empty or status is not in draft with id {}, returing.", applicationId);
				return response;
			}
		}
		if (Objects.nonNull(lendingResubmitTask)) {

			boolean isDownGradeRequired = ObjectUtils.nullSafeEquals(lendingResubmitTask.getDowngrade(), true);
			boolean isDownGradeDone = ObjectUtils.nullSafeEquals(lendingResubmitTask.getDowngradeDone(), true);
			boolean isResignRequired = ObjectUtils.nullSafeEquals(lendingResubmitTask.getResign(), true);
			boolean isResignDone = ObjectUtils.nullSafeEquals(lendingResubmitTask.getResignDone(), true);

			boolean draftCheck = true;

			if (isDownGradeRequired)
				draftCheck = isDownGradeDone;

			if (isResignRequired)
				draftCheck = draftCheck && isResignDone;


			if (draftCheck) {
				if (lendingApplication == null || !"draft".equals(lendingApplication.getStatus())) {
					logger.info("Application is empty or status is not in draft with id {}, returing.", applicationId);
					return response;
				}
			}
		}
		if (!StringUtils.isEmpty(lendingApplication.getCkycId())) {
			KycStatusDTO kycStatus = kycHandler.getKycStatus(lendingApplication.getMerchantId());
			logger.info("kyc status:{} for application:{}", kycStatus, lendingApplication.getId());
			if (kycStatus.getKycStatus().equals(KycStatus.NEW) || kycStatus.getKycStatus().equals(KycStatus.DRAFT)) {
				logger.info("kyc not done for application:{}", applicationId);
				return response;
			}
		} else {
			List<DocumentsIdProofMaster> documentsIdProofList = documentsIdProofDaoMaster.findByMerchantIdAndLendingApplicationId(merchant.getId(), lendingApplication.getId());
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


	private Map<String, Object> createNewApplicationAndSendOTP(RequestDTO<SignAgreementDTO> requestDTO, BasicDetailsDto merchant) {
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("success", false);
		response.put("otp_flow", false);
		List<String> topupLoans = Arrays.asList(LoanType.TOPUP.name(), LoanType.HALF_TOPUP.name(),
				LoanType.IO_TOPUP.name());
		List<String> ioHalfTopupLoans = Arrays.asList(LoanType.HALF_TOPUP.name(), LoanType.IO_TOPUP.name());
//		String selectedCategory = requestDTO.getPayload().getCategory();
		Integer selectedTenure = requestDTO.getPayload().getTenureInMonths();

//		if(StringUtils.isEmpty(selectedCategory)) {
//			logger.error("Selected category is null/empty for merchant {}", merchant.getId());
//			return response;
//		}
		if (selectedTenure == null || selectedTenure == 0) {
			logger.error("Selected tenure is null/empty for merchant {}", merchant.getId());
			return response;
		}

//		MerchantSummary merchantSummary = merchantSummaryDao.findByMerchantId(merchant.getId());
		MerchantResponseDTO merchantResponseDTO = merchantSummaryHandler.getMerchantSummary(merchant.getId());
		if(merchantResponseDTO == null) {
			logger.error("Merchant summary is empty for merchant with id {}", merchant.getId());
			return response;
		}

		LendingApplication checkDupe = lendingApplicationDao.findOpenApplication(merchant.getId());
		if (checkDupe != null) {
			logger.error("Merchant Has Already Active Application for merchantId: {} And ApplicationId:{}",
					merchant.getId(), checkDupe.getId());
			return response;
		}

		LendingPaymentSchedule prevLendingSchedule =
				lendingPaymentScheduleDao.findLatestLendingPaymentScheduleByMerchantId(merchant.getId());
		LendingApplication prevApplication =
				lendingApplicationDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchant.getId()
						, "APPROVED");

		if (prevLendingSchedule == null || prevApplication == null) {
			logger.error("User not eligible, last loan not found or last application is not disbursed/found");
			return response;
		}
		if (topupLoans.contains(prevApplication.getLoanType()) && "ACTIVE".equalsIgnoreCase(prevLendingSchedule.getStatus()) && (!"deleted".equalsIgnoreCase(prevApplication.getStatus()) && !"DISBURSED".equalsIgnoreCase(prevApplication.getLoanDisbursalStatus()))) {
			logger.error("Topup loan already created for merchant:{}", merchant.getId());
			return response;
		}
//		LendingCategories selectedCategoriesData = lendingCategoryDao.getByCategory(selectedCategory);
		EligibleLoan eligibleLoan = eligibleLoanDao.findTop1ByMerchantIdAndTenureInMonthsAndLoanTypeOrderByIdDesc(merchant.getId(), selectedTenure, "TOPUP");
		if(Objects.isNull(eligibleLoan)) {
			logger.error("No available loan found with merchant id {} and loan tenure {}", merchant.getId(), selectedTenure);
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
//        if ("TOPUP".equalsIgnoreCase(eligibleLoan.getLoanType())) {
//            newApplication.setInterestRate(1.75D);
//        } else {
//            newApplication.setInterestRate(eligibleLoan.getRateOfInterest());
//        }
		newApplication.setInterestRate(eligibleLoan.getRateOfInterest());
		newApplication.setProcessingFee((double)processingFee);
		newApplication.setLoanConstruct(eligibleLoan.getLoanConstruct());
		newApplication.setDisbursalAmount(eligibleLoan.getAmount() - processingFee);
		newApplication.setMerchantId(merchant.getId());
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
		newApplication.setCategory(eligibleLoan.getCategory());
		newApplication.setTenure(eligibleLoan.getTenure());
		newApplication.setTenureInMonths(eligibleLoan.getTenureInMonths());
		newApplication.setPayableDays((long) eligibleLoan.getEdiCount());
		newApplication.setEdiFreeDays(eligibleLoan.getEdiFreeDays());
		newApplication.setIoPayableDays(eligibleLoan.getIoEdiDays());
		newApplication.setLoanAmount(eligibleLoan.getAmount());
		newApplication.setLoanType(eligibleLoan.getLoanType());
		if("BHARATPE_ACCOUNT".equalsIgnoreCase(merchant.getSettlementType())) {
			newApplication.setCkycId(String.valueOf(merchant.getId()));
		}
		if(!StringUtils.isEmpty(requestDTO.getMeta().getLatitude()) && !requestDTO.getMeta().getLatitude().trim().equalsIgnoreCase("undefined"))
			newApplication.setLatitude(requestDTO.getMeta().getLatitude());
		if(!StringUtils.isEmpty(requestDTO.getMeta().getLongitude()) && !requestDTO.getMeta().getLongitude().trim().equalsIgnoreCase("undefined"))
			newApplication.setLongitude(requestDTO.getMeta().getLongitude());
		logger.info("ip from meta before setting to application : {} meta : {}",requestDTO.getMeta().getIp(), requestDTO.getMeta() );
		newApplication.setIp(requestDTO.getMeta().getIp());
		newApplication.setTotalLoansCount(merchantResponseDTO.getTotalLoansCount() == null ? 0 : merchantResponseDTO.getTotalLoansCount());
		newApplication = lendingApplicationDao.save(newApplication);
		loanUtil.publishApplicationEvent(newApplication);

//		lenderMappingService.lenderMapping(newApplication);
		lenderAssignService.assignLender(newApplication, EdiModel.SIX_DAY_MODEL);

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
			loanUtil.createApplicationSnapshot(newApplication, merchant);
		}
		LendingApplication finalNewApplication = newApplication;
		executorService.execute(() -> apiGatewayService.globalLimitTxn(finalNewApplication.getMerchantId(), "DEBIT", finalNewApplication.getLoanAmount()));
		executorService.execute(() -> loanUtil.publishDSData(finalNewApplication));
		return response;
	}

	public void replicateDocumentsForNewApplication(LendingApplication prevApplication, LendingApplication newApplication, BasicDetailsDto merchant, MetaDTO meta) {
		DocKycDetailsMaster panDoc = docKycDetailsDaoMaster.fetchLatestPanCardDetails(prevApplication.getMerchantId(),prevApplication.getId());
		DocKycDetailsMaster poaDoc = docKycDetailsDaoMaster.fetchLatestAddressDetails(prevApplication.getMerchantId(),prevApplication.getId());
		DocumentsIdProofMaster selfie = documentsIdProofDaoMaster.findTop1ByMerchantIdAndLendingApplicationIdAndProofTypeAndDeletedAtIsNullOrderByIdDesc(merchant.getId(),prevApplication.getId(),"selfie");

		if(panDoc == null){
			panDoc = docKycDetailsDaoMaster.fetchPanMerchantId(merchant.getId());
		}

		if(poaDoc == null){
			poaDoc = docKycDetailsDaoMaster.fetchPoaMerchantId(merchant.getId());
		}

		if(panDoc != null){
			DocumentsIdProofMaster panDocument = new DocumentsIdProofMaster();
			panDocument.setMerchantId(merchant.getId());
			panDocument.setProofType(panDoc.getDocumentsIdProof().getProofType());
			panDocument.setProofFrontSide(panDoc.getDocumentsIdProof().getProofFrontSide());
			panDocument.setProofBackSide(panDoc.getDocumentsIdProof().getProofBackSide());
			panDocument.setLendingApplicationId(newApplication.getId());
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
			documentsIdProofDaoMaster.save(panDocument);

			DocKycDetailsMaster panKyc = insertIntoDocKycDetails(panDoc, panDocument);
		}

		if(poaDoc != null){
			DocumentsIdProofMaster poaDocument = new DocumentsIdProofMaster();
			poaDocument.setMerchantId(merchant.getId());
			poaDocument.setProofType(poaDoc.getDocumentsIdProof().getProofType());
			poaDocument.setProofFrontSide(poaDoc.getDocumentsIdProof().getProofFrontSide());
			poaDocument.setProofBackSide(poaDoc.getDocumentsIdProof().getProofBackSide());
			poaDocument.setLendingApplicationId(newApplication.getId());
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
			documentsIdProofDaoMaster.save(poaDocument);

			List<DocKycDetailsMaster> poaList = docKycDetailsDaoMaster.findByDocumentsIdProof(poaDoc.getDocumentsIdProof());

			for(DocKycDetailsMaster poa :poaList) {
				DocKycDetailsMaster poaKyc = insertIntoDocKycDetails(poa, poaDocument);
			}
		}

		if(selfie != null){
			DocumentsIdProofMaster selfieDoc = new DocumentsIdProofMaster();
			selfieDoc.setLendingApplicationId(newApplication.getId());
			selfieDoc.setMerchantId(merchant.getId());
			selfieDoc.setProofType(selfie.getProofType());
			selfieDoc.setProofFrontSide(selfie.getProofFrontSide());
			selfieDoc.setProofBackSide(selfie.getProofBackSide());
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
			documentsIdProofDaoMaster.save(selfieDoc);
		}

		LendingGstDetail lendingGstDetail =lendingGstDao.findByApplicationId(prevApplication.getId());
		if(lendingGstDetail != null){
			LendingGstDetail replicateGst = lendingGstDao.findByApplicationId(newApplication.getId());
			if(ObjectUtils.isEmpty(replicateGst)) {
				replicateGst = new LendingGstDetail();
				replicateGst.setApplicationId(newApplication.getId());
				replicateGst.setMerchantId(newApplication.getMerchantId());
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

		List<LendingShopDocuments> lendingShopDocuments = lendingShopDocumentsDao.findByMerchantIdAndLendingApplicationId(prevApplication.getMerchantId(),prevApplication.getId());
		if(lendingShopDocuments.size() > 0 && !lendingShopDocuments.isEmpty()){
			for(LendingShopDocuments shopDocuments : lendingShopDocuments){
				LendingShopDocuments replicateShopDocument = new LendingShopDocuments();
				replicateShopDocument.setApplicationId(newApplication.getId());
				replicateShopDocument.setMerchantId(newApplication.getMerchantId());
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

	private DocKycDetailsMaster insertIntoDocKycDetails(DocKycDetailsMaster oldDocKycDetails, DocumentsIdProofMaster documentsIdProof) {
		DocKycDetailsMaster docKycDetails = new DocKycDetailsMaster();

		docKycDetails.setMerchantId(oldDocKycDetails.getMerchantId());
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

		LendingEkyc lendingEkyc = lendingEkycDao.fetchEkycByMerchantId(oldDocKycDetails.getMerchantId());
		if("eaadhar".equalsIgnoreCase(documentsIdProof.getProofType()) && Objects.nonNull(lendingEkyc)) {
			docKycDetails.setDocNo(lendingEkyc.getMaskedAadhar());
		}
		else {
			docKycDetails.setDocNo(oldDocKycDetails.getDocNo());
		}

		docKycDetailsDaoMaster.save(docKycDetails);
		return docKycDetails;
	}
//
//	private void insertIntoDocAuthentication(DocAuthentication oldDocAuthentication, DocKycDetails docKycDetails, DocumentsIdProof documentsIdProof) {
//		DocAuthentication docAuthentication = new DocAuthentication();
//		docAuthentication.setDocKycDetails(docKycDetails);
//		docAuthentication.setDocumentsIdProof(documentsIdProof);
//		docAuthentication.setMerchantId(oldDocAuthentication.getMerchantId());
//		docAuthentication.setDocType(oldDocAuthentication.getDocType());
//		docAuthentication.setStatus(oldDocAuthentication.getStatus());
//		docAuthentication.setDuplicate(oldDocAuthentication.getDuplicate());
//		docAuthentication.setNameMatch(oldDocAuthentication.getNameMatch());
//		docAuthentication.setDobMatch(oldDocAuthentication.getDobMatch());
//		docAuthentication.setFullResponse(oldDocAuthentication.getFullResponse());
//		docAuthentication.setDocStatus(oldDocAuthentication.getDocStatus());
//
//		docAuthenticationDao.save(docAuthentication);
//	}

	private Map<String, Object> sendOTP(BasicDetailsDto merchant, String appSign) {
		Map<String, Object> finalResponse = new LinkedHashMap<>();

		if (easyLoanUtil.isDummyMerchant(merchant.getId())) {
			finalResponse.put("success", Boolean.TRUE);
			finalResponse.put("otp_flow", true);
			finalResponse.put("uuid", UUID.randomUUID().toString());
			return finalResponse;
		}

		finalResponse.put("success", false);
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

	public Map<String, Object> createNewApplicationAndSendOTPForTopup(BasicDetailsDto merchant, CreateApplicationRequestForTopupDTO requestDTO) {
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("success", false);

		if(ObjectUtils.isEmpty(requestDTO) || ObjectUtils.isEmpty(requestDTO.getEligibleLoanId())){
			logger.info("Loan ID not found.");
			response.put("message", "Eligible loan id is null");
			return response;
		}

		List<String> topupLoans = Arrays.asList(LoanType.TOPUP.name(), LoanType.HALF_TOPUP.name(),
				LoanType.IO_TOPUP.name());
		List<String> ioHalfTopupLoans = Arrays.asList(LoanType.HALF_TOPUP.name(), LoanType.IO_TOPUP.name());
//		String selectedCategory = requestDTO.getPayload().getCategory();
//		Integer selectedTenure = requestDTO.getPayload().getTenureInMonths();

//		if(StringUtils.isEmpty(selectedCategory)) {
//			logger.error("Selected category is null/empty for merchant {}", merchant.getId());
//			return response;
//		}
//		if (selectedTenure == null || selectedTenure == 0) {
//			logger.error("Selected tenure is null/empty for merchant {}", merchant.getId());
//			return response;
//		}

//		MerchantSummary merchantSummary = merchantSummaryDao.findByMerchantId(merchant.getId());

		LendingApplication previousDraftApplication = lendingApplicationDao.findByMerchantIdAndStatus(merchant.getId(), "draft");
		if(Objects.nonNull(previousDraftApplication) && "TOPUP".equals(previousDraftApplication.getLoanType())){
			response.put("message", "Open topup application already exists.");
			response.put("application_id", previousDraftApplication.getId());
			response.put("success", true);
			return response;
		}

		MerchantResponseDTO merchantResponseDTO = merchantSummaryHandler.getMerchantSummary(merchant.getId());
		if(merchantResponseDTO == null) {
			logger.error("Merchant summary is empty for merchant with id {}", merchant.getId());
			return response;
		}

		LendingApplication checkDupe = lendingApplicationDao.findOpenApplication(merchant.getId());
		if (checkDupe != null) {
			logger.error("Merchant Has Already Active Application for merchantId: {} And ApplicationId:{}",
					merchant.getId(), checkDupe.getId());
			response.put("message", "Merchant Has Already Active Application");
			return response;
		}

		LendingPaymentSchedule prevLendingSchedule =
				lendingPaymentScheduleDao.findByMerchantIdAndStatus(merchant.getId(), "ACTIVE");
		LendingApplication prevApplication =
				lendingApplicationDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchant.getId()
						, "APPROVED");
		if (prevLendingSchedule == null || prevApplication == null) {
			logger.error("User not eligible, last loan not found or last application is not disbursed/found");
			response.put("message", "User not eligible, last loan not found or last application is not disbursed/found");
			return response;
		}
		if (topupLoans.contains(prevApplication.getLoanType()) && "ACTIVE".equalsIgnoreCase(prevLendingSchedule.getStatus()) && (!"deleted".equalsIgnoreCase(prevApplication.getStatus()) && !"DISBURSED".equalsIgnoreCase(prevApplication.getLoanDisbursalStatus()))) {
			logger.error("Topup loan already created for merchant:{}", merchant.getId());
			response.put("message", "Topup loan already created for merchant");
			return response;
		}
//		LendingCategories selectedCategoriesData = lendingCategoryDao.getByCategory(selectedCategory);
		Optional<EligibleLoan> optionalEligibleLoan = eligibleLoanDao.findById(requestDTO.getEligibleLoanId());
		if(!optionalEligibleLoan.isPresent()) {
			logger.error("No available loan found with merchant id {}", merchant.getId());
			response.put("message", "No available loan found with merchant");
			return response;
		}
		EligibleLoan eligibleLoan = optionalEligibleLoan.get();

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
		newApplication.setMerchantName(merchant.getBeneficiaryName());

		if(!topupLoans.contains(eligibleLoan.getLoanType()) && (!prevLendingSchedule.getStatus().equals("CLOSED") || (!"deleted".equalsIgnoreCase(prevApplication.getStatus()) && !"DISBURSED".equalsIgnoreCase(prevApplication.getLoanDisbursalStatus())))) {
			logger.info("Last loan not closed for merchant ID {}", merchant.getId());
			response.put("message","Last loan not closed for merchant {}");
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
//        if ("TOPUP".equalsIgnoreCase(eligibleLoan.getLoanType())) {
//            newApplication.setInterestRate(1.75D);
//        } else {
//            newApplication.setInterestRate(eligibleLoan.getRateOfInterest());
//        }
		newApplication.setInterestRate(eligibleLoan.getRateOfInterest());
		newApplication.setProcessingFee(Math.ceil(processingFee));
		newApplication.setLoanConstruct(eligibleLoan.getLoanConstruct());
		Double disbursalAmount = "TOPUP".equals(eligibleLoan.getLoanType())? eligibleLoan.getAmount() - processingFee - loanUtil.getForeclosureAmount(prevLendingSchedule)
				:eligibleLoan.getAmount() - processingFee;
		newApplication.setDisbursalAmount(Math.floor(disbursalAmount));
		newApplication.setMerchantId(merchant.getId());
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
		newApplication.setCategory(eligibleLoan.getCategory());
		newApplication.setTenure(eligibleLoan.getTenure());
		newApplication.setTenureInMonths(eligibleLoan.getTenureInMonths());
		newApplication.setPayableDays((long) eligibleLoan.getEdiCount());
		newApplication.setEdiFreeDays(eligibleLoan.getEdiFreeDays());
		newApplication.setIoPayableDays(eligibleLoan.getIoEdiDays());
		newApplication.setLoanAmount(eligibleLoan.getAmount());
		newApplication.setLoanType(eligibleLoan.getLoanType());
		newApplication.setNachStatus("TOPUP".equals(eligibleLoan.getLoanType())?"APPROVED":null);

		if("BHARATPE_ACCOUNT".equalsIgnoreCase(merchant.getSettlementType())) {
			newApplication.setCkycId(String.valueOf(merchant.getId()));
		}
		if(!StringUtils.isEmpty(requestDTO.getLatitude()) && !requestDTO.getLatitude().trim().equalsIgnoreCase("undefined"))
			newApplication.setLatitude(requestDTO.getLatitude());
		if(!StringUtils.isEmpty(requestDTO.getLongitude()) && !requestDTO.getLongitude().trim().equalsIgnoreCase("undefined"))
			newApplication.setLongitude(requestDTO.getLongitude());
		logger.info("ip from meta before setting to application : {} meta : {}",requestDTO.getIp(), requestDTO);
		newApplication.setIp(requestDTO.getIp());
		newApplication.setTotalLoansCount(merchantResponseDTO.getTotalLoansCount() == null ? 0 : merchantResponseDTO.getTotalLoansCount());
		newApplication = lendingApplicationDao.save(newApplication);
		loanUtil.publishApplicationEvent(newApplication);

		if("TOPUP".equals(eligibleLoan.getLoanType())){
			lenderAssignService.assignTopupLender(newApplication);
		}else {
			lenderAssignService.assignLender(newApplication, EdiModel.SIX_DAY_MODEL);
		}
		newApplication.setNachLender("TOPUP".equals(eligibleLoan.getLoanType())? loanUtil.enachServiceLenderMapper(newApplication.getLender()):null);
		lendingApplicationDao.save(newApplication);

//		lenderMappingService.lenderMapping(newApplication);

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
				MetaDTO metaDTO = new MetaDTO();
				metaDTO.setIp(requestDTO.getIp());
				metaDTO.setLongitude(requestDTO.getLongitude());
				metaDTO.setLattitude(requestDTO.getLatitude());
				replicateDocumentsForNewApplication(prevApplication, newApplication, merchant, metaDTO);
			} else {
				logger.info("Application mode is {}, not replicating documents for new application id {} and merchant id {}", newApplication.getId(), merchant.getId());
			}

//			Instant start = Instant.now();
//			response = sendOTP(merchant, requestDTO.getPayload().getAppSign());
//			Instant end = Instant.now();
//			logger.info("Time Taken by GUPSHUP Send OTP API : {} miliseconds", Duration.between(start, end).toMillis());

			response.put("application_id", newApplication.getId());
			loanUtil.createApplicationSnapshot(newApplication, merchant);
		}
		LendingLedger lendingLedger = lendingLedgerDao.findLastPaymentEntryByMerchantAndLoan(prevLendingSchedule.getMerchantId(), prevLendingSchedule.getId());

		LendingApplication finalNewApplication = newApplication;
		executorService.execute(() -> apiGatewayService.globalLimitTxn(finalNewApplication.getMerchantId(), "DEBIT", finalNewApplication.getLoanAmount()));
		executorService.execute(() -> loanUtil.publishDSData(finalNewApplication));
		response.put("success", true);
		response.put("message","Application created Successfully");
		return response;
	}

	private void rejectLendingApplication(LendingApplication lendingApplication, String rejectionReason) {
		if("TOPUP".equals(lendingApplication.getLoanType())){ return;}

		lendingApplication.setStatus("rejected");
		lendingApplication.setManualCibil("REJECTED");
		lendingApplication.setManualCibilReason(rejectionReason);
		lendingApplication.setCibilApprovedDate(new Date());
		lendingApplicationDao.save(lendingApplication);
	}

//	private Map<String, Object> sendOTP(Merchant merchant, String appSign) {
//		Map<String, Object> finalResponse = new LinkedHashMap<>();
//
//		if (easyLoanUtil.isDummyMerchant(merchant.getId())) {
//			finalResponse.put("success", Boolean.TRUE);
//			finalResponse.put("otp_flow", true);
//			finalResponse.put("uuid", UUID.randomUUID().toString());
//			return finalResponse;
//		}
//
//		finalResponse.put("success",false);
//		finalResponse.put("otp_flow",false);
//
//		if(merchant.getMobile().length() == 12) {
//			String hash = appSign != null ? appSign : "";
//			String message = "<#> BharatPe: {otp} is your OTP to complete loan agreement for BharatPe Loans. NEVER SHARE THIS OTP WITH ANYONE. " + hash;
////			String message = "<#> BharatPe: %code% is your OTP to register yourself on BharatPe Merchant App. BharatPe.com";
//			Map<String, Object> response = bharatPeOtpHandler.sendOtp(merchant.getMobile(), message);
//			if(response != null) {
//				finalResponse.put("success", response.get("success"));
//				finalResponse.put("otp_flow",true);
//				finalResponse.put("uuid",response.get("uuid"));
//			}
//		}
//		return finalResponse;
//	}
}