package com.bharatpe.lending.service;

import com.bharatpe.common.dao.*;
import com.bharatpe.common.entities.*;
import com.bharatpe.lending.common.dao.*;
import com.bharatpe.lending.common.entity.*;
import com.bharatpe.lending.common.entity.MerchantDocumentProof;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.constant.ExperianConstants;
import com.bharatpe.lending.constant.LendingConstants;
import com.bharatpe.lending.dao.*;
import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.handlers.GupShupOTPHandler;
import com.bharatpe.lending.handlers.S3BucketHandler;
import com.bharatpe.lending.util.LoanCalculationUtil;
import com.bharatpe.lending.util.LoanCalculationUtil.LoanBreakupDetail;
import com.bharatpe.lending.util.LoanUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class LendingApplicationService {
	private Logger logger = LoggerFactory.getLogger(LendingApplicationService.class);
	
	@Autowired
	LendingCitiesDao lendingCitiesDao;
	
	@Autowired
	LendingApplicationDao lendingApplicationDao;
	
	@Autowired
	AvailableLoanDao availableLoanDao;
	
	@Autowired
	LendingCategoryDao lendingCategoryDao;

	@Autowired
	LendingAuditTrialDao lendingAuditTrialDao;
	
	@Autowired
	MerchantSummarySnapshotDao merchantSummarySnapshotDao;
	
	@Autowired
	MerchantSummaryDao merchantSummaryDao;

	@Autowired
	LendingDisbursalStageDao lendingDisbursalStageDao;

	@Autowired
	LendingGstDao lendingGstDao;

	@Autowired
	EligibleLoanDao eligibleLoanDao;

	@Autowired
	GupShupOTPHandler gupShupOTPHandler;

	@Autowired
	LendingPaymentScheduleDao lendingPaymentScheduleDao;

	@Autowired
	MerchantBankDetailDao merchantBankDetailDao;

	@Autowired
	LendingRedCitiesDao lendingRedCitiesDao;
	
	@Autowired
	ExperianDao experianDao;

	@Autowired
	ExperianSnapshotDao experianSnapshotDao;

	@Autowired
	LendingBBSDao lendingBBSDao;

	@Autowired
	LendingBBSSnapshotDao lendingBBSSnapshotDao;

	@Autowired
	MerchantScoreDao merchantScoreDao;

	@Autowired
	MerchantScoreSnapshotDao merchantScoreSnapshotDao;

	@Autowired
	KafkaTemplate<String, Object> kafkaTemplate;
	
	@Autowired
	LendingApplicationService lendingApplicationService;
	
	@Autowired
	PincodeCityStateMappingDao pincodeCityStateMappingDao;
	
	@Autowired
	SignAgreementService signAgreementService;

	@Autowired
	LendingMerchantDropoffDao lendingMerchantDropoffDao;

	@Autowired
	BPEnachDao bpEnachDao;

	@Autowired
	APIGatewayService apiGatewayService;

	@Autowired
	DocumentsIdProofDao documentsIdProofDao;

	@Autowired
	MerchantDocumentProofDao merchantDocumentProofDao;

	@Value("${aws.s3.bucket.kyc.documents}")
	String kycBucket;

	@Value("${aws.s3.bucket}")
	String imageBucket;

	@Autowired
	S3BucketHandler s3BucketHandler;


	public LendingApplicationResponseDTO createApplication(Merchant merchant, RequestDTO<LendingApplicationRequestDTO> requestDTO) {
		LendingApplicationResponseDTO lendingApplicationResponse=null;
		LendingApplication lendingApplication=null;
		Long merchantId = merchant.getId();
		LendingApplicationRequestDTO lendingApplicationRequest = requestDTO.getPayload();
		if(lendingApplicationRequest.getApplicationId() != null && lendingApplicationRequest.getApplicationId() > 0) {
			lendingApplication = lendingApplicationDao.findByIdAndMerchantAndStatus(lendingApplicationRequest.getApplicationId(), merchant, "draft");
			if(lendingApplication == null) {
				logger.info("No application found in draft status for given application id {}", lendingApplicationRequest.getApplicationId());
				lendingApplicationResponse = new LendingApplicationResponseDTO();
				lendingApplicationResponse.setSuccess(false);
				return lendingApplicationResponse;
			}
			lendingApplication = updateApplication(lendingApplication, lendingApplicationRequest);
			createGstDetail(merchant,lendingApplicationRequest);
			lendingApplicationDao.save(lendingApplication);
		}else {
			MerchantSummary summary =  merchantSummaryDao.getByMerchantId(merchant.getId());
			if(requestDTO.getPayload().getPincode() == null) {
				LendingApplication prevApplication=lendingApplicationDao.findTop1ByMerchantOrderByIdDesc(merchant);
				createGstDetail(merchant,lendingApplicationRequest);
				if(prevApplication!=null) {
					return createApplicationFromPrevLoan(prevApplication,requestDTO, lendingApplicationRequest.getOfferType());
				}
				else {
					logger.info("Not details received from frontend and no prev loan found for merchant:{} ",merchant.getId());
					return prepopulateData(merchant, requestDTO);
				}
			}
			else {
				String offerType = lendingApplicationRequest.getOfferType();
				List<EligibleLoan> eligibleLoans = fetchEligibleLoansForCreateApplication(merchantId, lendingApplicationRequest.getCategory(), offerType);
				if(eligibleLoans == null || eligibleLoans.isEmpty()) {
					logger.info("No loan available for Merchant {} and category {}", merchantId, lendingApplicationRequest.getCategory());
					lendingApplicationResponse = new LendingApplicationResponseDTO();
					lendingApplicationResponse.setSuccess(false);
					return lendingApplicationResponse;
				}
				lendingApplication = createApplication(merchant, eligibleLoans.get(0), lendingApplicationRequest);
				createGstDetail(merchant,lendingApplicationRequest);
				if (requestDTO.getMeta() != null && requestDTO.getMeta().getLatitude() != null && !requestDTO.getMeta().getLatitude().trim().equalsIgnoreCase("") && !requestDTO.getMeta().getLatitude().equalsIgnoreCase("undefined")) {
					lendingApplication.setLatitude(requestDTO.getMeta().getLatitude());
					lendingApplication.setLongitude(requestDTO.getMeta().getLongitude());
					lendingApplication.setIp(requestDTO.getMeta().getIp());
				}
				lendingApplication.setTotalLoansCount(summary == null || summary.getTotalLoansCount() == null ? 0 : summary.getTotalLoansCount());
				if(lendingApplication.getLoanType()!=null && (lendingApplication.getLoanType().equalsIgnoreCase("ZOMATO") || lendingApplication.getLoanType().equalsIgnoreCase("BHARAT_SWIPE"))) {
					lendingApplication.setLender("HINDON");
				}
				else {
					lendingApplication.setLender("LDC");
				}
				lendingApplicationDao.save(lendingApplication);
			}
			if (summary != null) {
				createMerchantSummarySnapshot(merchant, lendingApplication, summary);
			}
			createExperianSnapshot(merchant, lendingApplication);
			if(lendingApplication.getLoanType()!=null && lendingApplication.getLoanType().equalsIgnoreCase("NTB")) {
				createBBSSnapshot(lendingApplication);
			}
			createMerchantScoreSnapshot(lendingApplication);
			createStatusAuditTrail(lendingApplication);
			lendingMerchantDropoffDao.updateApplicationId(lendingApplication.getMerchant().getId(), lendingApplication.getId());
		}
		logger.info("Loan Application saved : {}",lendingApplication);
		return prepareAPIResponse(lendingApplication,false);
	}

	private LendingApplicationResponseDTO prepopulateData(Merchant merchant, RequestDTO<LendingApplicationRequestDTO> requestDTO) {
		logger.info("Pre populating data for merchant:{}", merchant.getId());
		try {
			String selectedCategory = requestDTO.getPayload().getCategory();
			if(selectedCategory==null || selectedCategory.isEmpty()) {
				logger.error("Loan category not found in the request:{}", requestDTO.toString());
				return new LendingApplicationResponseDTO(false, "Category missing");
			}
			List<EligibleLoan> eligibleLoans = fetchEligibleLoansForCreateApplication(merchant.getId(), selectedCategory, requestDTO.getPayload().getOfferType());
			if(eligibleLoans.isEmpty()) {
				logger.info("No eligible loan found for merchant:{}", merchant.getId());
				return new LendingApplicationResponseDTO(false,"No eligible loan found");
			}
			MerchantSummary merchantSummary = merchantSummaryDao.findByMerchantId(merchant.getId());
			Experian experian = experianDao.getByMerchantId(merchant.getId());
			EligibleLoan eligibleLoan = eligibleLoans.get(0);
			MerchantInfoDTO merchantInfoDTO = apiGatewayService.getMerchantAddress(merchant.getId());
			String address = fetchMerchantAddress(merchantInfoDTO);
			LendingApplicationRequestDTO lendingApplicationRequestDTO = requestDTO.getPayload();
			lendingApplicationRequestDTO.setStreetAddress(address);
			if (merchantInfoDTO != null && merchantInfoDTO.getData() != null && merchantInfoDTO.getData().get(0).getMerchantDetail() != null) {
				lendingApplicationRequestDTO.setBusinessName(merchantInfoDTO.getData().get(0).getMerchantDetail().getBussinessName());
			}
			if (experian != null && experian.getPincode() != null) {
				PincodeCityStateMapping pincodeCityStateMapping = pincodeCityStateMappingDao.findByPincode(experian.getPincode());
				if (pincodeCityStateMapping != null) {
					lendingApplicationRequestDTO.setPincode(pincodeCityStateMapping.getPincode().longValue());
					lendingApplicationRequestDTO.setCity(pincodeCityStateMapping.getCity());
					lendingApplicationRequestDTO.setState(pincodeCityStateMapping.getState());
				}
			}
			LendingApplication newApplication = createApplication(merchant, eligibleLoan, requestDTO.getPayload());
			if(!StringUtils.isEmpty(requestDTO.getMeta().getLatitude()) && !requestDTO.getMeta().getLatitude().trim().equalsIgnoreCase("undefined"))
				newApplication.setLatitude(requestDTO.getMeta().getLatitude());
			if(!StringUtils.isEmpty(requestDTO.getMeta().getLongitude()) && !requestDTO.getMeta().getLongitude().trim().equalsIgnoreCase("undefined"))
				newApplication.setLongitude(requestDTO.getMeta().getLongitude());
			newApplication.setIp(requestDTO.getMeta().getIp());
			newApplication.setTotalLoansCount(merchantSummary != null && merchantSummary.getTotalLoansCount() != null ? merchantSummary.getTotalLoansCount() : 0);
			if(newApplication.getLoanType()!=null && (newApplication.getLoanType().equalsIgnoreCase("ZOMATO") || newApplication.getLoanType().equalsIgnoreCase("BHARAT_SWIPE"))) {
				newApplication.setLender("HINDON");
			}
			else {
				newApplication.setLender("LDC");
			}
			newApplication = lendingApplicationDao.save(newApplication);
			if(merchantSummary != null) {
				createMerchantSummarySnapshot(newApplication.getMerchant(), newApplication, merchantSummary);
			}
			createExperianSnapshot(newApplication.getMerchant(), newApplication);
			if(newApplication.getLoanType()!=null && newApplication.getLoanType().equalsIgnoreCase("NTB")) {
				createBBSSnapshot(newApplication);
			}
			createMerchantScoreSnapshot(newApplication);
			lendingMerchantDropoffDao.updateApplicationId(newApplication.getMerchant().getId(), newApplication.getId());
			replicateDocumentsForNewApplication(newApplication, merchant, requestDTO.getMeta());
			return prepareAPIResponse(newApplication,true);
		} catch (Exception e) {
			logger.error("Exception in application pre populate---",e);
		}
		return new LendingApplicationResponseDTO(false, "Something went wrong");
	}

	private String fetchMerchantAddress(MerchantInfoDTO merchantInfoDTO) {
//		List<String> definedOrder = Arrays.asList("CPV", "FOS", "SELF", "REVISIT");
		if (merchantInfoDTO != null && merchantInfoDTO.getData() != null && !merchantInfoDTO.getData().isEmpty() && merchantInfoDTO.getData().get(0).getAddressDetail() != null) {
//			List<MerchantInfoDTO.AddressDetail> addressDetails = new ArrayList<>();
			for (MerchantInfoDTO.AddressDetail addressDetail : merchantInfoDTO.getData().get(0).getAddressDetail()) {
				if (addressDetail.getType() != null && addressDetail.getType().equalsIgnoreCase("CPV") && addressDetail.getAddress() != null && !addressDetail.getAddress().trim().equalsIgnoreCase("")) {
					return addressDetail.getAddress();
				}
			}
//			addressDetails.sort(Comparator.comparing(c -> definedOrder.indexOf(c.getType())));
//			if (addressDetails.get(0) != null) {
//				return addressDetails.get(0).getAddress();
//			}
		}
		return null;
	}

	public void replicateDocumentsForNewApplication(LendingApplication newApplication, Merchant merchant, MetaDTO meta) {
		MerchantDocumentProof selfie = merchantDocumentProofDao.findVerifiedProofType(merchant.getId(), "selfie");
		MerchantDocumentProof pancard = merchantDocumentProofDao.findVerifiedProofType(merchant.getId(), "pancard");
		MerchantDocumentProof poa = merchantDocumentProofDao.findVerifiedPOA(merchant.getId());
		DocumentsIdProof eAadhar = documentsIdProofDao.findLatestEadhar(merchant.getId());
		List<MerchantDocumentProof> merchantDocumentProofs = new ArrayList<MerchantDocumentProof>() {{
			add(selfie);
			add(pancard);
		}};
		if (eAadhar == null) {
			merchantDocumentProofs.add(poa);
		}
		merchantDocumentProofs.removeAll(Collections.singleton(null));
		for(MerchantDocumentProof documentsIdProof  : merchantDocumentProofs) {
			try {
				String frontUrl = documentsIdProof.getProofFrontSide();
				String backUrl = documentsIdProof.getProofBackSide();
				if (!documentsIdProof.getOwnerType().equalsIgnoreCase("LENDING")) {
					frontUrl = uploadDocumentInLending(frontUrl, merchant.getId(), kycBucket);
					if (backUrl != null) {
						backUrl = uploadDocumentInLending(backUrl, merchant.getId(), kycBucket);
					}
				}
				DocumentsIdProof toSaveDocuments = new DocumentsIdProof();
				toSaveDocuments.setMerchant(merchant);
				toSaveDocuments.setProofType(documentsIdProof.getProofType());
				toSaveDocuments.setProofFrontSide(frontUrl);
				toSaveDocuments.setProofBackSide(backUrl);
				toSaveDocuments.setLendingApplication(newApplication);
				toSaveDocuments.setStatus("pending_verification");
				int singleProofDoc;
				if (documentsIdProof.getProofBackSide() != null) {
					singleProofDoc = 0;
				} else {
					singleProofDoc = 1;
				}
				toSaveDocuments.setSinglePage(singleProofDoc);
				if (!StringUtils.isEmpty(meta.getLatitude()) && !meta.getLatitude().trim().equalsIgnoreCase("undefined"))
					toSaveDocuments.setLatitude(meta.getLatitude());
				if (!StringUtils.isEmpty(meta.getLongitude()) && !meta.getLongitude().trim().equalsIgnoreCase("undefined"))
					toSaveDocuments.setLongitude(meta.getLongitude());
				toSaveDocuments.setIp(meta.getIp());
				documentsIdProofDao.save(toSaveDocuments);
			} catch (Exception e) {
				logger.info("Exception while replicating doc for merchant:{}", merchant.getId(), e);
			}
		}
		if (eAadhar != null) {
			DocumentsIdProof toSaveDocuments = new DocumentsIdProof();
			toSaveDocuments.setMerchant(merchant);
			toSaveDocuments.setProofType(eAadhar.getProofType());
			toSaveDocuments.setProofFrontSide(eAadhar.getProofFrontSide());
			toSaveDocuments.setProofBackSide(eAadhar.getProofBackSide());
			toSaveDocuments.setLendingApplication(newApplication);
			toSaveDocuments.setStatus("pending_verification");
			int singleProofDoc;
			if (eAadhar.getProofBackSide() != null) {
				singleProofDoc = 0;
			} else {
				singleProofDoc = 1;
			}
			toSaveDocuments.setSinglePage(singleProofDoc);
			if (!StringUtils.isEmpty(meta.getLatitude()) && !meta.getLatitude().trim().equalsIgnoreCase("undefined"))
				toSaveDocuments.setLatitude(meta.getLatitude());
			if (!StringUtils.isEmpty(meta.getLongitude()) && !meta.getLongitude().trim().equalsIgnoreCase("undefined"))
				toSaveDocuments.setLongitude(meta.getLongitude());
			toSaveDocuments.setIp(meta.getIp());
			documentsIdProofDao.save(toSaveDocuments);
		}
	}

	private String uploadDocumentInLending(String frontUrl, Long merchantId, String bucket) {
		try {
			String imageURL = s3BucketHandler.getPreSignedPublicURL(frontUrl, bucket);
			String fileName = merchantId + "" + ((int) (Math.random() * ((100000 - 1) + 1)) + 1) + ".jpeg";
			File file = new File("/tmp/" + fileName);
			FileUtils.copyURLToFile(new URL(imageURL), file);
			s3BucketHandler.uploadFileToS3(file, imageBucket, fileName);
			return fileName;
		} catch (Exception e) {
			logger.info("Error while uploading image in lending bucket", e);
		}
		return null;
	}

	private LendingApplicationResponseDTO createApplicationFromPrevLoan(LendingApplication prevLoan,RequestDTO<LendingApplicationRequestDTO> requestDTO, String offerType) {
		try {
			if(prevLoan.getPincode() != null && !lendingApplicationService.checkLoanRequestPinCodeForLoanEligibilty((int)(long)prevLoan.getPincode())) {
				logger.info("This loan request was raised from the location whose pin code is not eligible for the loan");
				LendingApplicationResponseDTO lendingApplicationResponse=new LendingApplicationResponseDTO();
				lendingApplicationResponse.setCode(LendingConstants.LOAN_APPLICATION_OGL_CODE);
				lendingApplicationResponse.setMessage(LendingConstants.LOAN_APPLICATION_OGL_MESSAGE);
				lendingApplicationResponse.setSuccess(false);
				return lendingApplicationResponse;
			}
			else {
				return copyApplicationData(requestDTO ,prevLoan, offerType);
			}
		}
		catch(Exception e) {
			logger.error("Error occured while creating new loan from prev loan ",e);
		}
		return null;
	}
	
	private List<EligibleLoan> fetchEligibleLoansForCreateApplication(Long merchantId, String category, String offerType){
		if("CUSTOM".equalsIgnoreCase(offerType)){
			return eligibleLoanDao.findByMerchantIdAndCategoryAndOfferType(merchantId, category, offerType);
		}
		return eligibleLoanDao.findByMerchantIdAndCategory(merchantId, category);
	}

	private LendingApplicationResponseDTO  copyApplicationData(RequestDTO<LendingApplicationRequestDTO> requestDTO,LendingApplication prevLoan, String offerType) {
		try {
			String selectedCategory = requestDTO.getPayload().getCategory();
			if(selectedCategory==null || selectedCategory.isEmpty()) {
				logger.error("Loan category not found in the request {}",requestDTO.toString());
				return new LendingApplicationResponseDTO(false, "Category missing");
			}
			LendingCategories selectedCategoriesData = lendingCategoryDao.findByCategory(selectedCategory).get(0);
			List<EligibleLoan> eligibleLoans = fetchEligibleLoansForCreateApplication(prevLoan.getMerchant().getId(), selectedCategory, offerType);
			if(eligibleLoans.isEmpty()) {
				logger.error("No eligible loan found for merchant {}",prevLoan.getMerchant().getId());
				return new LendingApplicationResponseDTO(false,"No eligible loan found");
			}
			MerchantSummary merchantSummary = merchantSummaryDao.findByMerchantId(prevLoan.getMerchant().getId());
			EligibleLoan eligibleLoan=eligibleLoans.get(0);
			LendingApplication newApplication = copyApplicationDataWhenExperianEnabled(eligibleLoan, selectedCategoriesData, prevLoan, selectedCategory);
			if(!StringUtils.isEmpty(requestDTO.getMeta().getLatitude()) && !requestDTO.getMeta().getLatitude().trim().equalsIgnoreCase("undefined"))
				newApplication.setLatitude(requestDTO.getMeta().getLatitude());
			if(!StringUtils.isEmpty(requestDTO.getMeta().getLongitude()) && !requestDTO.getMeta().getLongitude().trim().equalsIgnoreCase("undefined"))
				newApplication.setLongitude(requestDTO.getMeta().getLongitude());
			newApplication.setIp(requestDTO.getMeta().getIp());
			newApplication.setTotalLoansCount(merchantSummary != null && merchantSummary.getTotalLoansCount() != null ? merchantSummary.getTotalLoansCount() : 0);
			if(newApplication.getLoanType()!=null && (newApplication.getLoanType().equalsIgnoreCase("ZOMATO") || newApplication.getLoanType().equalsIgnoreCase("BHARAT_SWIPE"))) {
				newApplication.setLender("HINDON");
			}
			else {
				newApplication.setLender("LDC");
			}
			newApplication = lendingApplicationDao.save(newApplication);
			if(merchantSummary != null) {
				createMerchantSummarySnapshot(newApplication.getMerchant(), newApplication, merchantSummary);
			}
			createExperianSnapshot(newApplication.getMerchant(), newApplication);
			if(newApplication.getLoanType()!=null && newApplication.getLoanType().equalsIgnoreCase("NTB")) {
				createBBSSnapshot(newApplication);
			}
			createMerchantScoreSnapshot(newApplication);
			lendingMerchantDropoffDao.updateApplicationId(newApplication.getMerchant().getId(), newApplication.getId());
			signAgreementService.replicateDocumentsForNewApplication(prevLoan, newApplication, prevLoan.getMerchant(), requestDTO.getMeta());
			return prepareAPIResponse(newApplication,true);
		}
		catch(Exception e) {
			logger.error("Error occured while creating loan application",e);
		}
		return new LendingApplicationResponseDTO(false,"Error occured while creating loan application");
	}
	
	private LendingApplication copyApplicationDataWhenExperianEnabled(EligibleLoan eligibleLoan, LendingCategories selectedCategoriesData, LendingApplication prevLoan,String selectedCategory) {
		Experian experian = experianDao.getByMerchantId(prevLoan.getMerchant().getId());
		LendingApplication newApplication=new LendingApplication();
		int processingFee = (int) Math.ceil(eligibleLoan.getAmount() * Double.parseDouble(selectedCategoriesData.getProcessingFee()));
		newApplication.setEdi(Double.valueOf(eligibleLoan.getEdi()));
		newApplication.setIoEdi(Double.valueOf(eligibleLoan.getIoEdi()));
		newApplication.setRepayment(Double.valueOf(eligibleLoan.getRepayment()));
		newApplication.setInterestRate(selectedCategoriesData.getInterestRate());
		newApplication.setProcessingFee((double)processingFee);
		newApplication.setLoanConstruct(eligibleLoan.getLoanConstruct());
		newApplication.setDisbursalAmount(eligibleLoan.getAmount() - processingFee);
		newApplication.setMerchant(prevLoan.getMerchant());
		newApplication.setShopNumber(prevLoan.getShopNumber());
		newApplication.setStreetAddress(prevLoan.getStreetAddress());
		newApplication.setArea(prevLoan.getArea());
		newApplication.setLandmark(prevLoan.getLandmark());
		if (experian != null && experian.getPincode() != null) {
			PincodeCityStateMapping pincodeCityStateMapping = pincodeCityStateMappingDao.findByPincode(experian.getPincode());
			if (pincodeCityStateMapping != null) {
				newApplication.setPincode(pincodeCityStateMapping.getPincode().longValue());
				newApplication.setCity(pincodeCityStateMapping.getCity());
				newApplication.setState(pincodeCityStateMapping.getState());
			}
		}
		newApplication.setBusinessName(prevLoan.getBusinessName());
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
		newApplication.setAlternateMobile(prevLoan.getAlternateMobile());
		
		return newApplication;
	}
	
	private boolean isLdc(LendingApplication lendingApplication) {
		Long todayApplicationCount = lendingApplicationDao.getLDCApplicationCountBetweenDate(DateTimeUtil.getStartTimeFromDateTime(new Date()), DateTimeUtil.getEndTimeFromDateTime(new Date()));
		return todayApplicationCount < 25 && lendingApplication.getTenureInMonths() != 15;
	}

	public void createMerchantScoreSnapshot(LendingApplication lendingApplication) {
		MerchantScore merchantScore = merchantScoreDao.findByMerchantId(lendingApplication.getMerchant().getId());
		if (merchantScore != null) {
			MerchantScoreSnapshot merchantScoreSnapshot = MerchantScoreSnapshot.createObject(merchantScore);
			merchantScoreSnapshot.setApplication_id(lendingApplication.getId());
			merchantScoreSnapshotDao.save(merchantScoreSnapshot);
		}
	}

	public void createBBSSnapshot(LendingApplication lendingApplication) {
		LendingBBS lendingBBS = lendingBBSDao.findByMerchantId(lendingApplication.getMerchant().getId());
		if (lendingBBS != null) {
			LendingBBSSnapshot lendingBBSSnapshot = LendingBBSSnapshot.createObject(lendingBBS);
			lendingBBSSnapshot.setApplicationId(lendingApplication.getId());
			lendingBBSSnapshotDao.save(lendingBBSSnapshot);
		}
	}

	private void createExperianSnapshot(Merchant merchant,LendingApplication lendingApplication) {
		Experian experian = experianDao.getByMerchantId(merchant.getId());
		if(experian!=null) {
			ExperianSnapshot experianSnapshot=new ExperianSnapshot();
			experianSnapshot.setMerchantId(experian.getMerchantId());
			experianSnapshot.setIp(experian.getIp());
			experianSnapshot.setLatitude(experian.getLatitude());
			experianSnapshot.setLongitude(experian.getLongitude());
			experianSnapshot.setResponse(experian.getResponse());
			experianSnapshot.setMerchantName(experian.getMerchantName());
			experianSnapshot.setEmail(experian.getEmail());
			experianSnapshot.setRejected(experian.getRejected());
			experianSnapshot.setReason(experian.getReason());
			experianSnapshot.setRequestedLoanAmount(experian.getRequestedLoanAmount());
			experianSnapshot.setPancardNumber(experian.getPancardNumber());
			experianSnapshot.setTnc(experian.getTnc());
			experianSnapshot.setBpScore(experian.getBpScore());
			experianSnapshot.setExperianScore(experian.getExperianScore());
			experianSnapshot.setCategory(experian.getCategory());
			experianSnapshot.setColor(experian.getColor());
			experianSnapshot.setRetryCount(experian.getRetryCount());
			experianSnapshot.setSkip(experian.isSkip());
			experianSnapshot.setPincode(experian.getPincode());
			experianSnapshot.setBureau(experian.getBureau());
			experianSnapshot.setApplicationId(lendingApplication.getId());

			experianSnapshotDao.save(experianSnapshot);
		}
	}
	private LendingApplication updateApplication(LendingApplication lendingApplication, LendingApplicationRequestDTO lendingApplicationRequest) {
		lendingApplication.setBusinessName(lendingApplicationRequest.getBusinessName());
		lendingApplication.setShopNumber(lendingApplicationRequest.getShopNumber());
		lendingApplication.setStreetAddress(lendingApplicationRequest.getStreetAddress());
		lendingApplication.setArea(lendingApplicationRequest.getArea());
		lendingApplication.setLandmark(lendingApplicationRequest.getLandmark());
		lendingApplication.setPincode(lendingApplicationRequest.getPincode());
		lendingApplication.setCity(lendingApplicationRequest.getCity());
		lendingApplication.setState(lendingApplicationRequest.getState());
		if (lendingApplicationRequest.getAlternativeContact() != null) {
			lendingApplication.setAlternateMobile(lendingApplicationRequest.getAlternativeContact().getPhoneNumber());
			lendingApplication.setAlternateName(lendingApplicationRequest.getAlternativeContact().getName());
		}
		return lendingApplication;
	}
	
	private LendingApplication createApplication(Merchant merchant, EligibleLoan eligibleLoan, LendingApplicationRequestDTO lendingApplicationRequest) {
		LendingApplication lendingApplication = new LendingApplication();
		LendingCategories lendingCategory = lendingCategoryDao.findByCategory(eligibleLoan.getCategory()).get(0);
		
		//LoanBreakupDetail breakupDetail = LoanCalculationUtil.getLoanBreakup(availableLoan, lendingCategory);
		int processingFee = (int) Math.ceil(eligibleLoan.getAmount() * Double.parseDouble(lendingCategory.getProcessingFee()));

		lendingApplication.setEdi(Double.valueOf(eligibleLoan.getEdi()));
		lendingApplication.setIoEdi(eligibleLoan.getIoEdi() != null ? Double.valueOf(eligibleLoan.getIoEdi()) : 0D);
		lendingApplication.setRepayment(Double.valueOf(eligibleLoan.getRepayment()));
		lendingApplication.setInterestRate(lendingCategory.getInterestRate());
		lendingApplication.setProcessingFee((double) processingFee);
		lendingApplication.setDisbursalAmount(eligibleLoan.getAmount() - processingFee);
		lendingApplication.setStatus("draft");
		lendingApplication.setMode("AUTO");
		lendingApplication.setMerchant(merchant);
		lendingApplication.setLoanAmount(eligibleLoan.getAmount());
		lendingApplication.setCategory(eligibleLoan.getCategory());
		lendingApplication.setTenure(lendingCategory.getPayableConverter());
		lendingApplication.setTenureInMonths(lendingCategory.getTenureMonths().intValue());
		lendingApplication.setPayableDays((long) lendingCategory.getPayableDays());
		lendingApplication.setEdiFreeDays(lendingCategory.getEdiFreeDays());
		lendingApplication.setIoPayableDays(lendingCategory.getIoPayableDays());
		lendingApplication.setLoanConstruct(eligibleLoan.getLoanConstruct());
		lendingApplication.setLoanType(eligibleLoan.getLoanType());
		lendingApplication = updateApplication(lendingApplication, lendingApplicationRequest);

		return lendingApplication;
	}

	private LendingApplication createApplication(Merchant merchant, AvailableLoan availableLoan, LendingApplicationRequestDTO lendingApplicationRequest) {
		LendingApplication lendingApplication = new LendingApplication();
		LendingCategories lendingCategory = lendingCategoryDao.findByCategory(availableLoan.getCategory()).get(0);

		LoanBreakupDetail breakupDetail = LoanCalculationUtil.getLoanBreakup(availableLoan, lendingCategory, null);

		lendingApplication.setEdi(Double.valueOf(breakupDetail.getEdi()));
		lendingApplication.setIoEdi(Double.valueOf(breakupDetail.getIoEdi()));
		lendingApplication.setRepayment(Double.valueOf(breakupDetail.getRepayment()));
		lendingApplication.setInterestRate(breakupDetail.getEffectiveInterestRate());
		lendingApplication.setProcessingFee(Double.valueOf(breakupDetail.getProcessingFee()));
		lendingApplication.setDisbursalAmount(Double.valueOf(breakupDetail.getDisbursementAmount()));
		lendingApplication.setStatus("draft");
		lendingApplication.setMode("AUTO");
		lendingApplication.setMerchant(merchant);
		lendingApplication.setLoanAmount(availableLoan.getAmount());
		lendingApplication.setCategory(availableLoan.getCategory());
		lendingApplication.setTenure(lendingCategory.getPayableConverter());
		lendingApplication.setTenureInMonths(lendingCategory.getTenureMonths().intValue());
		lendingApplication.setPayableDays(Long.valueOf(lendingCategory.getPayableDays()));
		lendingApplication.setEdiFreeDays(lendingCategory.getEdiFreeDays());
		lendingApplication.setIoPayableDays(lendingCategory.getIoPayableDays());
		lendingApplication.setLoanConstruct(availableLoan.getLoanConstruct());

		lendingApplication = updateApplication(lendingApplication, lendingApplicationRequest);

		return lendingApplication;
	}

	private void createStatusAuditTrail(LendingApplication lendingApplication) {
		LendingAuditTrial lendingAuditTrial = new LendingAuditTrial();
		lendingAuditTrial.setMerchantId(lendingApplication.getMerchant().getId());
		lendingAuditTrial.setApplicationId(lendingApplication.getId());
		lendingAuditTrial.setLoanId("");
		lendingAuditTrial.setUserId(Long.parseLong("0"));
		lendingAuditTrial.setNewStatus("draft");
		lendingAuditTrial.setType("APP_STATUS");
		lendingAuditTrialDao.save(lendingAuditTrial);
	}


	private void createGstDetail(Merchant merchant,LendingApplicationRequestDTO lendingApplicationRequest){
		if(lendingApplicationRequest.getApplicationId() != null && lendingApplicationRequest.getEntityType() != null){
			logger.info("gettinf GST NUmber{}",lendingApplicationRequest.getGstNumber());
			LendingGstDetail lendingGstDetail =lendingGstDao.findByApplicationId(lendingApplicationRequest.getApplicationId());
			if(lendingGstDetail == null){
				lendingGstDetail = new LendingGstDetail();
				lendingGstDetail.setMerchantId(merchant.getId());
				lendingGstDetail.setApplicationId(lendingApplicationRequest.getApplicationId());
			}
			lendingGstDetail.setBusinessCategory(lendingApplicationRequest.getBusinessCategory());
			lendingGstDetail.setEntityType(lendingApplicationRequest.getEntityType());
			lendingGstDetail.setExperience(lendingApplicationRequest.getExperience());
			lendingGstDetail.setGst(lendingApplicationRequest.getHasGST());
			lendingGstDetail.setGstNumber(lendingApplicationRequest.getGstNumber());
			lendingGstDetail.setSalary(lendingApplicationRequest.getSalary() != null && !lendingApplicationRequest.getSalary().trim().equals("") ? Double.parseDouble(lendingApplicationRequest.getSalary()) : 0D);
			lendingGstDao.save(lendingGstDetail);
		}
	}
	
	private LendingApplicationResponseDTO prepareAPIResponse(LendingApplication lendingApplication, Boolean prevLoanExists) {
		LendingCategories lendingCategories = lendingCategoryDao.getByCategory(lendingApplication.getCategory());
		LendingGstDetail lendingGstDetail = lendingGstDao.findByApplicationId(lendingApplication.getId());
		if(lendingGstDetail == null){
			lendingGstDetail = lendingGstDao.findTop1ByMerchantIdOrderByIdDesc(lendingApplication.getMerchant().getId());
		}
		logger.info("Lending GST Details aFInd IN Tvake:{}",lendingGstDetail);
		LendingApplicationResponseDTO lendingApplicationResponse = new LendingApplicationResponseDTO();
		LendingApplicationResponseDTO.LoanApplication loanApplication = lendingApplicationResponse.new LoanApplication();

		loanApplication.setApplicationId(lendingApplication.getId());
		loanApplication.setApplicationStatus(lendingApplication.getStatus());
		loanApplication.setSelectedLoan(LoanUtil.prepareSelectedLoanForClient(lendingApplication, lendingCategories));
		loanApplication.setShopDetails(LoanUtil.prepareShopDetailsForClient(lendingApplication,lendingGstDetail));
		lendingApplicationResponse.setLoanApplication(loanApplication);
		lendingApplicationResponse.setSuccess(true);
		lendingApplicationResponse.setPrevLoanFound(prevLoanExists);

		return lendingApplicationResponse;
	}
	
	public void createMerchantSummarySnapshot(Merchant merchant, LendingApplication application, MerchantSummary summary) {
		try {
			MerchantSummarySnapshot snapshot = new MerchantSummarySnapshot();
			List<Object[]> data = availableLoanDao.getMaxEligibilityDataForMerchant(merchant.getId());
			
			snapshot.setApplication(application.getId());
			snapshot.setMerchant(merchant);
			snapshot.setLastTransactionDate(summary.getLastTransactionDate());
			snapshot.setTotalTxnCount(summary.getDailyTxnCount());
			snapshot.setTotalTxnAmount(summary.getDailyTxnAmount());
			snapshot.setCategory(summary.getCategory());
			snapshot.setAvgTpv(summary.getAvgTpv());
//			snapshot.setMaxEligibleLoanAmount(((BigDecimal) data.get(0)[0]).doubleValue());
//			snapshot.setEligibleLoanCategories((String) data.get(0)[1]);
			snapshot.setLoanType(summary.getLoanType());
			snapshot.setTpv1Mon(summary.getTpv1Mon());
			snapshot.setTpv2Mon(summary.getTpv2Mon());
			snapshot.setTpv3Mon(summary.getTpv3Mon());
			snapshot.setTxnDayCount1Mon(summary.getTxnDayCount1Mon());
			snapshot.setTxnDayCount2Mon(summary.getTxnDayCount2Mon());
			snapshot.setTxnDayCount3Mon(summary.getTxnDayCount3Mon());
			snapshot.setTotalTxns1Month(summary.getTotalTxns1Month());
			snapshot.setTotalTxns2Month(summary.getTotalTxns2Month());
			snapshot.setTotalTxns3Month(summary.getTotalTxns3Month());
			snapshot.setTotalLoansCount(summary.getTotalLoansCount());
			snapshot.setBpScore(summary.getBpScore());
			snapshot.setUniqueCustomer1mon(summary.getUniqueCustomer1mon());
			merchantSummarySnapshotDao.save(snapshot);
		} catch(Exception ex) {
			logger.error("Exception while creating merchant summary snapshot for merchant id {} and application id {}, Exception is {}", merchant.getId(), application.getId(), ex);
		}
	}
	
	public boolean checkLoanRequestPinCodeForLoanEligibilty(int pinCode) {
		try {
			PincodeCityStateMapping pincodeCityStateMapping=pincodeCityStateMappingDao.findByPincode(pinCode);
			if(pincodeCityStateMapping==null) return false;
			LendingCities lendingCities=lendingCitiesDao.findActiveCityByPincode(pinCode);
			LendingRedCities redCity = lendingRedCitiesDao.findByPincode(pinCode);
			if(lendingCities==null && redCity != null) return false;
			return true;
		}
		catch(Exception e){
			logger.error("error occured while fetching the lending city details for pin code {}",pinCode);
		}
		return false;
	}

	public ResponseDTO sendOtp(Merchant merchant) {
		String message = "BharatPe: %code% is your OTP to register yourself on BharatPe Merchant App. BharatPe.com";
		Boolean otp = gupShupOTPHandler.sendOTP(merchant.getMobile(), message);
		if (otp) {
			logger.info("OTP sent on mobile: {} for merchant: {}", merchant.getMobile(), merchant.getId());
			ResponseDTO responseDTO = new ResponseDTO(true, null, null);
			responseDTO.setMobile(merchant.getMobile());
			return responseDTO;
		}
		return new ResponseDTO(false, "Unable to send otp", null);
	}

	public TncDto getTnc(Merchant merchant, long applicationId) {
		String html=populateHtml(merchant, applicationId);
		TncDto tnc=new TncDto();
		if(html==null) {
			tnc.setSuccess(false);
			tnc.setMessage("Error occured while fetching tnc");
			return tnc;
		}
		tnc.setSuccess(true);
		tnc.setHtmlString(html);
		return tnc;
	}

	public String populateHtml(Merchant merchant, long applicationId){
		Map<String, String> detail=getDetails(merchant, applicationId);
		if(detail==null) {
			return null;
		}
		String html;
		String lender=detail.get("Lender");
		
		if(lender!=null && lender.equalsIgnoreCase("LDC")) {
			html=getLdcTnc(detail);
		}
		else {
			html ="<p><br /><br /><br /></p>\n" +
						"<p><strong>Loan Proposal Letter</strong></p>\n" +
						"<p>&nbsp;</p>\n" +
						"<p><span style=\"font-weight: 400;\">This sanction letter includes the Most Important Terms and Conditions (MITC).</span></p>\n" +
						"<p>&nbsp;</p>\n" +
						"<table>\n" +
						"<tbody>\n" +
						"<tr>\n" +
						"<td>\n" +
						"<p><span style=\"font-weight: 400;\">Name of the Borrower</span></p>\n" +
						"</td>\n" +
						"<td>"+detail.getOrDefault("Name of the Borrower", "")+"</td>\n" +
						"</tr>\n" +
						"<tr>\n" +
						"<td>\n" +
						"<p><span style=\"font-weight: 400;\">Loan Amount (in INR)</span></p>\n" +
						"</td>\n" +
						"<td>"+detail.getOrDefault("Loan Amount", "")+"</td>\n" +
						"</tr>\n" +
						"<tr>\n" +
						"<td>\n" +
						"<p><span style=\"font-weight: 400;\">Tenure (in Months)</span></p>\n" +
						"</td>\n" +
						"<td>"+detail.getOrDefault("Tenure", "")+"</td>\n" +
						"</tr>\n" +
						"<tr>\n" +
						"<td>\n" +
						"<p><span style=\"font-weight: 400;\">Rate of Interest (per annum)</span></p>\n" +
						"</td>\n" +
						"<td>"+detail.getOrDefault("Rate of Interest", "")+"</td>\n" +
						"</tr>\n" +
						"<tr>\n" +
						"<td>\n" +
						"<p><span style=\"font-weight: 400;\">Penal Interest (per annum)</span></p>\n" +
						"</td>\n" +
						"<td>"+detail.getOrDefault("Penal Interest", "")+"</td>\n" +
						"</tr>\n" +
						"<tr>\n" +
						"<td>\n" +
						"<p><span style=\"font-weight: 400;\">Security</span></p>\n" +
						"</td>\n" +
						"<td>\n" +
						"<p><span style=\"font-weight: 400;\">Unsecured</span></p>\n" +
						"</td>\n" +
						"</tr>\n" +
						"<tr>\n" +
						"<td>\n" +
						"<p><span style=\"font-weight: 400;\">Co-Applicant Details</span></p>\n" +
						"</td>\n" +
						"<td>\n" +
						"<p><span style=\"font-weight: 400;\">NA</span></p>\n" +
						"</td>\n" +
						"</tr>\n" +
						"</tbody>\n" +
						"</table>\n" +
						"<p>&nbsp;</p>\n" +
						"<p><span style=\"font-weight: 400;\">This sanction letter is subject to terms & conditions.</span></p>\n" +
						"<p><span style=\"font-weight: 400;\">The said proposed loan amount will be financed by Hindon Merchantile Limited (“Hindon”) as Lender to the Borrower, as provided by its sourcing partner, Resilient Innovations Private Limited (BharatPe).</span></p>\n" +
						"<p><span style=\"font-weight: 400;\">Please note that this is an indicative in nature and should not be binding upon either party, Lender or Borrower. </span></p>\n" +
						"   <p class=\"p4\">&nbsp;</p>\n" +
						"   <p class=\"p4\">&nbsp;</p>\n" +
						"    <p class=\"p5\"><strong>Declaration / Undertaking/Representation by Borrower</strong></p>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li6\">I/We hereby apply for a finance facility as proposition made by <strong>Resilient Innovation Private Limited (&ldquo;BharatPe&rdquo;)</strong> as in terms of Loan Agreement as below and declare that all the particulars, information and details provided and other documents submitted by me/us are true, correct, complete and up-to-date in all respects and that I/We have not withheld any material information.</li>\n" +
						"    <li class=\"li6\">I/We hereby authorize <span class=\"s2\">Lender</span>/BharatPe to exchange or share information and details relating to my application to its group companies or any third party, as may be required or deemed fit, for the purpose of processing this loan application and/or related offerings or other products / services that I/We may apply for from time to time.</li>\n" +
						"    <li class=\"li6\">By submitting this application, I/We hereby expressly authorize <span class=\"s2\">Lender</span>/BharatPe to send me communications regarding loans, insurance and other products from <span class=\"s2\">Lender</span>/BharatPe, its group<span class=\"Apple-converted-space\">&nbsp; </span>companies and / or third parties through telephone calls / SMSs / emails / post etc. including but not limited to promotional, transactional communications. I/We confirm that I shall not challenge receipt of such communications by me as unsolicited communication, defined under TRAI Regulations on Unsolicited Commercial Communications.</li>\n" +
						"    <li class=\"li6\">I authorize BharatPe / <span class=\"s2\">Lender</span> to evaluate my transaction history on the BharatPe platform in order to check my eligibility for the loan and understand and acknowledge that <span class=\"s2\">Lender</span>/BharatPe has the absolute discretion, without assigning any reasons to reject my application and that <span class=\"s2\">Lender</span>/BharatPe is not answerable / liable to me, in any manner whatsoever,<span class=\"Apple-converted-space\">&nbsp; </span>for<span class=\"Apple-converted-space\">&nbsp; </span>rejecting<span class=\"Apple-converted-space\">&nbsp; </span>my application.</li>\n" +
						"    <li class=\"li6\">I / We agrees and accept that <span class=\"s2\">Lender</span>/BharatPe may in its sole discretion, by its self or through authorised persons, advocate, agencies, bureau, etc. verify any information given, check credit references, employment details and obtain credit reports to determine creditworthiness from time to time.</li>\n" +
						"    </ul>\n" +
						"    <p class=\"p7\">&nbsp;</p>\n" +
						"    <p class=\"p8\" style=\"text-align: center;\"><strong>LOAN AGREEMENT</strong></p>\n" +
						"    <p class=\"p9\">&nbsp;</p>\n" +
						"    <p class=\"p10\">&nbsp;</p>\n" +
						"    <p class=\"p11\">This <strong>Loan Agreement</strong> (&ldquo;<strong>Agreement</strong>&rdquo;) is made and executed at the place mentioned in <strong>Schedule I</strong> (<em>Terms of the Facility</em>) and on the date mentioned in <strong>Schedule I</strong> (<em>Terms of the Facility</em>) by and between:</p>\n" +
						"    <p class=\"p11\">HINDON MERCANTILE LIMITED, a non-banking finance company, having its registered office at Unit No 307, Third Floor Plot\n" +
						"No. H-1 Garg Tower, NSP, Pitampura Delhi (hereinafter referred to as the &ldquo;<strong>Lender</strong>&rdquo; which expression shall, unless repugnant to the context or meaning thereof, be deemed to mean and include its successor(s) and permitted assign(s)) of the One Part;</p>\n" +
						"    <p class=\"p11\"><strong>AND</strong></p>\n" +
						"    <p class=\"p11\"><strong><em>[Details from the Schedule I]</em></strong>, hereto as the borrower and co-borrower (if any) (wherever the context so requires) (hereinafter referred to as the &ldquo;<strong>Borrower</strong>&rdquo; which expression shall, unless repugnant to the context or meaning thereof, be deemed to mean and include his/her/their heir(s), successor(s), legal representative(s), executor(s), administrator(s) and permitted assign(s)) of the Other Part.</p>\n" +
						"    <p class=\"p11\">The Lender and the Borrower are hereinafter collectively referred to as the &ldquo;<strong>Parties</strong>&rdquo; and each individually as the &ldquo;<strong>Party</strong>&rdquo;.</p>\n" +
						"    <p class=\"p11\"><strong>WHEREAS</strong>:</p>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li11\">The Lender is a non-banking financing company, registered with the Reserve Bank of India, having registration no. B-14-00518],\n" +
						"and is <em>inter alia</em> engaged in the business of advancing loans and other financial facilities.</li>\n" +
						"    <li class=\"li11\">The Borrower has approached the Lender and has requested for grant of loan facility for the purpose of <strong><em>as mentioned in Schedule I </em></strong>and in reliance on the acceptance of the terms, conditions, assurances, representations and warranties of the Borrower, the Lender has agreed to grant loan facility to the Borrower, subject to the terms and conditions contained in this Agreement.</li>\n" +
						"    <li class=\"li11\">The Parties hereto are now desirous of <em>inter alia</em> entering into this Agreement to set out the terms and conditions in relation to the Facility.</li>\n" +
						"    </ul>\n" +
						"    <p class=\"p11\"><strong>Now, therefore, in view of the foregoing and in consideration of the mutual covenants and agreements herein set forth, the parties hereby agree as follows:</strong></p>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li12\">DEFINITIONS AND INTERPRETATION</li>\n" +
						"    </ul>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li13\"><strong>Definitions</strong></li>\n" +
						"    </ul>\n" +
						"    </ul>\n" +
						"    <p class=\"p14\">&ldquo;<strong>Borrower Account</strong>&rdquo; means the following bank account of the Borrower <strong><em>as mentioned in Schedule I</em></strong>, unless otherwise notified by the Borrower in writing.</p>\n" +
						"    <p class=\"p15\">&ldquo;<strong>Due Date</strong>&rdquo; means the date(s) on which any amounts from the Borrower to the Lender including the principal amounts of the Facility, interest and/or any other Outstanding Amounts, fall due as per <strong>Schedule I</strong> (<em>Terms of the Facility</em>) of this Agreement or any other Facility Document, or as demanded by the Lender in accordance with a Facility Document.</p>\n" +
						"    <p class=\"p15\">&ldquo;<strong>Events of Default</strong>&rdquo; shall have the meaning ascribed to it under the terms herein.</p>\n" +
						"    <p class=\"p15\">&ldquo;<strong>Facility</strong>&rdquo; means the facility amount mentioned in <strong>Schedule I</strong> (<em>Terms of the Facility</em>).</p>\n" +
						"    <p class=\"p15\">&ldquo;<strong>Final Settlement Date</strong>&rdquo; means the date on which all the Outstanding Amounts have been fully paid and the Facility has been irrevocably discharged to the satisfaction of the Lender.</p>\n" +
						"    <p class=\"p15\">&ldquo;<strong>Financing Documents</strong>&rdquo; means this Agreement and such other documents as may be executed or required to be executed between the Lender and/or the Borrower in order to perfect or validate this Agreement.</p>\n" +
						"    <p class=\"p14\">&ldquo;<strong>Government Authority</strong>&rdquo; means any governmental department, commission, board, bureau, agency, regulatory authority, instrumentality, court or other judicial, quasi-judicial or administrative body, whether central, state, provincial or local, having jurisdiction over the subject matter or matters in question. For avoidance of doubt, it is hereby clarified that the term &ldquo;Government Authority&rdquo; does not include any bank/financial institution acting solely in its capacity as a lender to the Borrower.</p>\n" +
						"    <p class=\"p15\">&ldquo;<strong>Interest Rate</strong>&rdquo; means the rate of interest mentioned in <strong>Schedule I</strong> (<em>Terms of the Facility</em>).</p>\n" +
						"    <p class=\"p14\">&ldquo;<strong>Laws</strong>&rdquo; means any statute, law, regulation, ordinance, rule, judgment, order, decree, bye-laws, rule of law, directives, guidelines policy, requirement, or any governmental restriction or any similar form of decision of, or determination by, or any interpretation or administration having the force of law of any of the foregoing, by any Government Authority having jurisdiction over the matter in subject, whether in effect as of the date of this Agreement or hereafter.</p>\n" +
						"    <p class=\"p15\">&ldquo;<strong>Loan Application</strong>&rdquo; means the application made by the Borrower in the form specified by the Lender for availing the Facility and where the context so requires, all other information, particulars submitted by the Borrower to the Lender with a view to avail the Facility.</p>\n" +
						"    <p class=\"p15\">&ldquo;<strong>Material Adverse Effect</strong>&rdquo; means adverse effect on: (a) the ability of the Borrower to observe and perform in a timely manner their respective obligations under any of the Financing Documents to which it is or would be a party or; (b) the legality, validity, binding nature or enforceability of any of the Financing Documents; or (d) the Business or financial condition of the Borrower which is reasonably likely to impair its ability to service the Facility as and when becoming due; or (e) the rights and remedies of the Lender under the Financing Documents.</p>\n" +
						"    <p class=\"p15\">&ldquo;<strong>Outstanding Amounts</strong>&rdquo; mean principal amount of the Facility outstanding from time to time, and all interests, Penal Interest, prepayment charges, costs, commissions, fees &amp; charges, expenses and other amounts due under or in respect of this Agreement.</p>\n" +
						"    <p class=\"p15\">&ldquo;<strong>Payment Mechanism</strong>&rdquo; means ECS, ACH, NEFT, RTGS or payment by way of cheque, as the case may be.</p>\n" +
						"    <p class=\"p15\">&ldquo;<strong>Person</strong>&rdquo; shall, unless specifically provided otherwise, mean any individual, corporation, partnership, association of persons, company, joint stock company, trust or Government Authority, as the context may admit.</p>\n" +
						"    <p class=\"p15\">&ldquo;<strong>Prepayment</strong>&rdquo; means the premature repayment of the Facility as per the terms and conditions approved by the Lender in this regard and prevailing at the time of such premature repayment by the Borrower.</p>\n" +
						"    <p class=\"p15\">&ldquo;<strong>Purpose</strong>&rdquo; means the purpose for which the Facility has been agreed to be utilised by the Borrower, as mentioned in <strong>Schedule I</strong> (<em>Terms of the Facility</em>) to this Agreement.</p>\n" +
						"    <p class=\"p15\">&ldquo;<strong>RBI</strong>&rdquo; means the Reserve Bank of India.</p>\n" +
						"    <p class=\"p15\">&ldquo;<strong>Tax</strong>&rdquo; means any tax, levy, impost, duty or other charge or withholding of a similar nature (including any penalty or interest payable in connection with the failure to pay or delay in paying any of the same).</p>\n" +
						"    <p class=\"p16\">&ldquo;<strong>Term</strong>&rdquo; or &ldquo;<strong>Tenure</strong>&rdquo; means the period as specified in <strong>Schedule I</strong> (<em>Terms of the Facility</em>) of this Agreement, within which the Facility has to be repaid by the Borrower to the Lender along with interest, cost, expenses, fees &amp; charges and other amount as specified in this Agreement.</p>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li5\"><strong>Principles of Interpretation</strong>: In this Agreement, unless the context otherwise requires:</li>\n" +
						"    </ul>\n" +
						"    </ul>\n" +
						"    <p class=\"p14\"><strong>T</strong>he headings are for convenience or reference only and shall not be used in and shall not affect the construction or interpretation of this Agreement.</p>\n" +
						"    <p class=\"p14\"><strong>T</strong>he words &ldquo;include&rdquo; and &ldquo;including&rdquo; are to be construed without limitation.</p>\n" +
						"    <p class=\"p14\"><strong>W</strong>ords importing a particular gender shall include all genders.</p>\n" +
						"    <p class=\"p14\"><strong>R</strong>eferences to any law shall include references to such law as it may, after the date of this Agreement, from time to time be amended, supplemented or re-enacted.</p>\n" +
						"    <p class=\"p14\"><strong>T</strong>he Schedule(s) annexed to this Agreement form an integral part of this Agreement and will be of full force and effect as though they were expressly set out in the body of the Agreement;</p>\n" +
						"    <p class=\"p14\"><strong>R</strong>eference to any agreement, including this Agreement, deed, document, instrument, rule, regulation, notification, statute or the like shall mean a reference to the same as may have been duly amended, modified or replaced. For the avoidance of doubt, a document shall be construed as amended, modified or replaced only if such amendment, modification or replacement is executed in compliance with the provisions of such document(s);</p>\n" +
						"    <p class=\"p17\"><strong>I</strong>n the event of any disagreement or dispute between the Lender and the Borrower regarding the materiality or reasonableness of any matter, the opinion of Lender as to the materiality shall be final and binding on the Borrower.</p>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li12\">FACILITY</li>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li12\"><strong>The Lender at the request of the Borrower agrees to grant to the Borrower and the Borrower agrees to borrow from the Lender, the Facility, on the basis and subject to the covenants and terms and conditions set forth herein. </strong></li>\n" +
						"    <li class=\"li12\"><strong>If in future, the Borrower approaches the Lender for grant of an additional facility or increase in the amount of Facility, the Lender shall have the sole discretion for granting the same and the Lender can either proceed with<span class=\"Apple-converted-space\">&nbsp; </span>the execution of fresh loan agreement with the Borrower or execute a supplemental loan agreement.</strong></li>\n" +
						"    <li class=\"li12\"><strong>Disbursement shall be made directly and only to Borrower. </strong></li>\n" +
						"    <li class=\"li12\"><strong>The Lender shall have the right to adjust and/or set off any Outstanding Amounts or other dues against any subsequent amount of the Facility due to be disbursed by the Lender to the Borrower.</strong></li>\n" +
						"    <li class=\"li12\"><strong>Notwithstanding anything stated herein, the continuation of the Facility shall be at sole and absolute discretion of the Lender and the Lender may at any time in its sole discretion and without assigning any reason call upon the Borrower to pay the Outstanding Balance and upon such demand by the Lender, the Borrower shall, within 48 hours of being so called upon, pay the whole of the Outstanding Balance to the Lender without any delay or demur. </strong></li>\n" +
						"    <li class=\"li12\"><strong>The Lender may, at its discretion, maintain appropriate entries in its books of accounts in relation to the Facility and such entries shall be final and binding upon the Borrower.</strong></li>\n" +
						"    </ul>\n" +
						"    <li class=\"li12\">MODE OF DISBURSAL</li>\n" +
						"    </ul>\n" +
						"    <p class=\"p18\"><strong>The Facility shall be made by the Lender by RTGS/NEFT to the Borrower Account and charges for the same, if any, shall be borne by the Borrower. Such charges shall be deemed to form part of the Outstanding Amounts.</strong></p>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li12\">INTEREST</li>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li12\"><strong>The Borrower shall pay interest on the principal amount of the Facility outstanding from time to time at the Interest Rate mentioned in </strong>Schedule I<strong> (<em>Terms of the Facility</em>) to this Agreement. </strong></li>\n" +
						"    <li class=\"li12\"><strong>Interest on the Facility will begin to accrue in favour of the Lender as and from the date of disbursal of amount of Facility. Interest shall accrue from day to day and shall be computed on the basis of 365 days a year (irrespective of leap year) and the actual number of days elapsed. However, in the event of the Borrower intends to Prepay the Facility, Interest would be calculated up to the date of actual prepayment, subject to payment of Prepayment charges as applicable.</strong></li>\n" +
						"    <li class=\"li12\"><strong>Without prejudice to the Lender's rights, Interest and any other Outstanding Amounts shall be charged/debited to the Borrower Account.</strong></li>\n" +
						"    <li class=\"li12\"><strong>Lender at its sole discretion, may change in the prevailing rate of interest on the Facility, either due to change in its policies, or issuance of RBI guidelines and notifications with respect to the same or for any other reason whatsoever and in such an event the term 'Interest Rate' shall for all purposes mean the revised interest rate, which shall always be construed as agreed to be paid by the Borrower and hereby secured.</strong></li>\n" +
						"    </ul>\n" +
						"    <li class=\"li12\">FEES &amp; REPAYMENT</li>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li12\"><strong>The Borrower shall, on or before or after the disbursement of the Facility, bear, pay and reimburse to the Lender all cost, fee, charges, including stamp duty charges, applicable on the Financing Documents and any increased costs expenses incurred and/or to be incurred by the Lender, on a full indemnity basis, in connection with the Facility.</strong></li>\n" +
						"    <li class=\"li12\"><strong>The Borrower shall, on or before the disbursement of the Facility, pay to the Lender processing/service fee calculated at the rate provided in </strong>Schedule I<strong> (<em>Terms of the Facility</em>) to this Agreement, on the amount of the Facility sanctioned by the Lender along-with applicable GST. The processing/service fee shall be non-refundable. The Lender shall be entitled to recover the non-refundable processing fees and GST by way of deduction from Drawdown(s). </strong></li>\n" +
						"    <li class=\"li13\">All fees and charges payable by the Borrower to the Lender under this Clause shall be reimbursed by the Borrower to the Lender within 7 (seven) days from the date of notice of demand from the Lender and shall be debited to the Borrower Account.</li>\n" +
						"    <li class=\"li13\">The Lender have appointed Resilient Innovations Private Limited (BharatPe) having registered office at 90/20, Malviya Nagar, New Delhi 110017 as its collection agent and for such other services as agreed between the Lender and BharatPe, from time to time. All Outstanding Balance shall be payable/paid as may be directed &amp; advised by Lender/BharatPe.</li>\n" +
						"    <li class=\"li13\">The Borrower shall repay the Facility, if not demanded earlier by Lender pursuant to a Financing Document, as stipulated in and in accordance with and subject to the terms and conditions of the Repayment Schedule set out in <strong>Schedule II </strong>(<em>Repayment Schedule</em>).</li>\n" +
						"    <li class=\"li13\">No notice, reminder or intimation in any manner shall be given by the Lender to the Borrower regarding its obligation and responsibility to ensure prompt and regular payment of the Outstanding Amounts to the Lender on Due Dates. It shall be entirely the Borrower's responsibility to ensure prompt and regular payment of the Outstanding Amount.</li>\n" +
						"    <li class=\"li13\">The Borrower agrees that the repayment of the amount of Facility together with interest, Penal Interest, if any, and all such other sums due and payable by the Borrower to the Lender shall be payable to the Lender Account by way of a Payment Mechanism approved by the Lender, provided that the Lender may, at its sole discretion, require the Borrower to adopt or switch to any alternative mode of payment and the Borrower shall comply with such request, without demur or delay. The Borrower undertakes to remit all Outstanding Amounts to the Lender on the respective Due Date.</li>\n" +
						"    <li class=\"li13\">Any instruction under the Payment Mechanism which is revoked/ dishonoured shall make the Borrower liable for payment of charges as per the prevailing rules of the Lender in force from time to time, in addition to any Penal Interest that may be levied by the Lender and without prejudice to the Lender's right to take appropriate legal action against the Borrower for such revocation / dishonour.</li>\n" +
						"    <li class=\"li13\">The Lender expressly reserves its right to call upon the Borrower to pay the whole or part of the Outstanding Amounts at any time after the date of first Drawdown in the event of a default by the Borrower under any Financing Document.</li>\n" +
						"    <li class=\"li13\">In the event of any change in Repayment Schedule (at the request of the Borrower or due to an Event of Default), the Borrower shall be liable to pay rescheduling charges at the rate specified in <strong>Schedule I</strong> (<em>Terms of the Facility</em>) to this Agreement. Such payment of rescheduling charges shall be in addition to any other rights and remedies available with the Lender in the Event of Default or otherwise.</li>\n" +
						"    </ul>\n" +
						"    <li class=\"li13\">SECURITY</li>\n" +
						"    </ul>\n" +
						"    <p class=\"p17\">The Borrower hereby agrees, undertakes and confirms that it shall deliver to the Lender such security, if applicable, as may be required pursuant to <strong>Schedule I </strong>(<em>Terms of the Facility</em>) to this Agreement, as security towards the payment of the Outstanding Amounts with the Lender named as the payee therein.</p>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li12\">PENAL INTEREST</li>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li12\"><strong>Upon occurrence of any of the events mentioned in Article 13 below, the Borrower shall be liable to pay Penal Interest which shall be in addition to the Interest payable by the Borrower under Article 5.1.</strong></li>\n" +
						"    <li class=\"li12\"><strong>The Borrower expressly agrees that the rate of Penal Interest is a fair estimate of the loss likely to be suffered by the Lender by reason of such delay/default on the part of the Borrower.</strong></li>\n" +
						"    <li class=\"li12\"><strong>Penal Interest shall accrue from day to day and shall be computed on the basis of 365 (three hundred and sixty) days a year (irrespective of leap year).</strong></li>\n" +
						"    <li class=\"li12\"><strong>Penal Interest shall be computed for (i) in case the Penal Interest is payable due to default/delay in any payment, then the period commencing from the Due Date of payment of the amount in default/delay up to the payment of amount in default/delay along-with Penal Interest and (ii) in case of occurrence of any other Event of Default, for the period during which the Event of Default or breach, as the case may be, persists.</strong></li>\n" +
						"    </ul>\n" +
						"    <li class=\"li12\">PREPAYMENT / FORECLOSURE</li>\n" +
						"    </ul>\n" +
						"    <p class=\"p17\">The Borrower shall be entitled to prepay/ foreclose the Outstanding Amounts, subject to payment of prepayment charges as set out in <strong>Schedule I</strong> (<em>Terms of the Facility</em>).</p>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li12\">TAXES</li>\n" +
						"    </ul>\n" +
						"    <p class=\"p16\">The Borrower shall make all payments to be made by it hereunder without and free from any Tax deduction and/or other deduction and/or withholding and/or statutory levies/duties/charges (&ldquo;<strong>Withholding</strong>&rdquo;), unless a Withholding is required by Law.</p>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li12\">PURPOSE OF THE FACILITY</li>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li13\">The Borrower undertakes and confirms that the entire Facility amount shall be utilized/ deployed only for the Purpose and for no other purpose that shall include without limitation to invest in share market, real estate or in any subsidiary/ associates of the Borrower.</li>\n" +
						"    <li class=\"li13\">Any default, fraud, legal incompetence during the currency of the limits, non-compliance of agreed terms and conditions, non-submission of required papers, any other irregularities by the Borrower will enable the Lender to recall the Facility.</li>\n" +
						"    <li class=\"li13\">The Borrower further confirms and/or undertakes that the Facility shall not be utilized for the following:</li>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li11\">Subscription to or purchase of shares/debentures;</li>\n" +
						"    <li class=\"li11\">Extending unsecured loans to subsidiary company/ associates or for making inter corporate deposits;</li>\n" +
						"    <li class=\"li11\">Any speculative purposes or any anti-social purpose or any unlawful purpose.</li>\n" +
						"    </ul>\n" +
						"    </ul>\n" +
						"    <li class=\"li12\">COVENANTS</li>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li13\">The Borrower agrees to promptly notify, in writing, the Lender about any litigation, arbitration, investigative, regulatory or administrative proceeding/action having a Material Adverse Effect.</li>\n" +
						"    <li class=\"li13\">All terms and conditions of this Agreement including the Repayment Schedule in relation to the Facility shall remain same even if any amount under the Facility is being taken over by/assigned to any new lender.</li>\n" +
						"    <li class=\"li13\">The Borrower declares that all the amounts including the amount of own contribution paid/ payable in connection with the Facility, is/ shall be through legitimate source and does not/ shall not constitute an offence of money laundering under the Prevention of Money Laundering Act, 2002.</li>\n" +
						"    <li class=\"li13\">The Borrower shall perform, on request of the Lender, such acts as may be necessary to carry out the intent of the Financing Documents.</li>\n" +
						"    <li class=\"li13\">The Borrower shall deliver to the Lender in form and detail, such details, information, documents etc to the satisfaction of the Lender, as may reasonably be required, within such period as required by the Lender from time to time.</li>\n" +
						"    <li class=\"li13\">In case the Borrower is a body corporate, it shall not induct any person on the board of directors or as partners who have been identified as a wilful defaulter by the RBI. The Borrower confirms that neither it nor any member of its organisation has been declared as wilful defaulter.</li>\n" +
						"    <li class=\"li12\"><strong>The Borrower hereby agrees, undertakes and covenants that unless the Lender otherwise agrees in writing, so long as the Facility or any part thereof is outstanding and an Event of Default has occurred and continuing, until full and final payment of all money owing hereunder, the Borrower </strong>SHALL NOT<strong>:</strong></li>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li11\">Grant any loans; grant any credit (except in the ordinary course of business) to or for the benefit of any Person other than itself.</li>\n" +
						"    <li class=\"li11\">Allow its principal shareholders/ directors/ promoters/ partners to withdraw monies brought in by them or withdraw the profits earned in the business/capital invested in the business.</li>\n" +
						"    </ul>\n" +
						"    </ul>\n" +
						"    <li class=\"li12\">REPRESENTATIONS AND WARRANTIES</li>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li11\">The Borrower hereby represents and warrants to the Lender on a continuing basis that:</li>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li13\"><span class=\"s1\"><strong>Confirmation of Loan Application</strong></span></li>\n" +
						"    </ul>\n" +
						"    </ul>\n" +
						"    </ul>\n" +
						"    <p class=\"p17\">The Borrower acknowledges and confirms that all the factual information provided by the Borrower to the Lender in the Loan Application or otherwise in order to avail the Facility and any prior or subsequent information or explanation given to the Lender in this regard is true and accurate in all material respects as at the date it was provided and does not omit to state a material fact necessary in order to make the statements contained therein misleading in the light of the circumstances under which such statements were or are made.</p>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <ul class=\"ul1\">\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li13\"><span class=\"s1\"><strong>Compliance with Laws</strong></span></li>\n" +
						"    </ul>\n" +
						"    </ul>\n" +
						"    </ul>\n" +
						"    <p class=\"p17\">The Borrower has complied with all the applicable Laws and is not a party to any litigation, arbitration or administrative or regulatory proceedings or investigations of a material character and that the Borrower is not aware, to the best of its knowledge and belief, of any facts likely to give rise to such litigation, arbitration or administrative or regulatory proceedings or investigations or to material claims against the Borrower.</p>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <ul class=\"ul1\">\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li13\"><span class=\"s1\"><strong>Litigation</strong></span></li>\n" +
						"    </ul>\n" +
						"    </ul>\n" +
						"    </ul>\n" +
						"    <p class=\"p17\">Where applicable, the Borrower shall supply to the Lender, promptly upon becoming aware of them, details of any filing by any creditor (financial creditor or operational creditor) which are made or threatened against them, in accordance with the provisions of the Insolvency and Bankruptcy Code, 2016 or any analogous laws.</p>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <ul class=\"ul1\">\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li13\"><span class=\"s1\"><strong>Compliance of Know Your Customer (KYC) Policy:</strong></span></li>\n" +
						"    </ul>\n" +
						"    </ul>\n" +
						"    </ul>\n" +
						"    <p class=\"p17\">The Borrower is fully aware of the KYC Policy of the Lender and RBI and confirms that the information/clarification/documents/signage provided by it on its identity, address, authorised signatory, board resolution, PAN and all other material facts are true and correct and the transaction, etc. are <em>bonafide </em>and as per Law. The Borrower further confirms that it has disclosed all facts/information as are required to be disclosed for the adherence and compliance of the provisions related to the KYC Policy. The Lender reserve the right to recall the Facility or close the account in case the required documents are not provided by the Borrower to the Lender.</p>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <ul class=\"ul1\">\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li13\"><strong>The Lender/BharatPe shall, without notice to or without any consent of the Borrower, be absolutely entitled and have full right, power and authority to make disclosure of any information relating to Borrower including personal information, details in relation to documents, Loan, defaults, security, obligations of Borrower, to the Credit Information Bureau of India (CIBIL) and/or any other governmental/regulatory/statutory or private agency/entity, credit bureau, RBI, the Lender&rsquo;s other branches/ subsidiaries / affiliates / rating agencies, service providers, other Lenders / financial institutions, any third parties, any assignees/potential assignees or transferees, who may need the information and may process the information, publish in such manner and through such medium as may be deemed necessary by the publisher/ Lender/ RBI, including publishing the name as part of willful defaulter&rsquo;s list from time to time, as also use for KYC information verification, credit risk analysis, or for other related purposes. The Borrower waives the privilege of privacy and privity of contract. </strong></li>\n" +
						"    <li class=\"li13\"><strong>The execution and delivery of this Agreement and documents to be executed in pursuance hereof, and the performance of the Borrower's obligations hereunder and thereunder does not and will not (i) contravene any applicable Law, statute or regulation or any judgment or decree to which any of the Borrowers and/or their Assets and/or business and/or their undertaking is subject, or (ii) conflict with or result in any breach of, any of the terms of or constitute default of any covenants, conditions and stipulations under any existing agreement or contract or binding to which any of the Borrowers are a party or subject or (iii) conflict or contravene any provision of the memorandum and the articles of association and/or any constituting/governing documents of Borrowers. </strong></li>\n" +
						"    <li class=\"li13\"><strong>The Borrower has informed the Lender about all loans/finances/advances availed by the Borrower from other banks/financial institutions/third parties up to the date of this Agreement to the Lender.</strong></li>\n" +
						"    <li class=\"li13\"><span class=\"s1\"><strong>No</strong> <strong>default</strong></span></li>\n" +
						"    </ul>\n" +
						"    </ul>\n" +
						"    </ul>\n" +
						"    <p class=\"p16\">The Borrower and/or its group companies, affiliates have no over dues/not defaulted in repayment of any amount due and payable to any other bank/financial institutions.</p>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <ul class=\"ul1\">\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li13\"><span class=\"s1\"><strong>Material Adverse Effect</strong></span></li>\n" +
						"    </ul>\n" +
						"    </ul>\n" +
						"    </ul>\n" +
						"    <p class=\"p17\">There are no facts or circumstances, conditions or occurrences, which could collectively or otherwise be expected to result in the Borrower being unable to perform their respective obligations under the Financing Documents to which they are expressed to be a party, or which could affect the legality, validity, binding nature or enforceability of this Agreement or other Financing Documents or is otherwise expected to have an Material Adverse Effect.</p>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li12\">EVENT OF DEFAULT AND CONSEQUENCES</li>\n" +
						"    </ul>\n" +
						"    <p class=\"p17\">The Borrower expressly and irrevocably hereby agrees and declares that each of the following events or events similar thereto shall constitute an &ldquo;<strong>Events of Default</strong>&rdquo;: The following events shall constitute events of default (each an &ldquo;Event of Default&rdquo;), and upon the occurrence of any of them the entire Outstanding Balance shall become immediately due and payable by the Borrower and further enable the Lender inter alia to recall the entire Outstanding Balance and/or enforce any security and transfer/sell the same and/or take, initiate and pursue any actions/proceedings as deemed necessary by the Lender for recovery of the dues, or such other action as the Lender may deem fit: (a) Failure on Borrower&rsquo;s part to perform any of the obligations or terms or conditions or covenants applicable in relation to the Loan including under this document/other documents including non-payment in full of any part of the Outstanding Balance when due or when demanded by Lender/BharatPe; (b) any misrepresentations or misstatement by the Borrower; or (c) occurrence of any circumstance or event which adversely affects Borrower&rsquo;s ability/capacity to pay/repay the Outstanding Balance or any part thereof or perform any of the obligations; (d) the event of death, insolvency, cessation, failure in business of the Borrower, or change or termination of employment/profession/business for any reason whatsoever<span class=\"s3\">. </span></p>\n" +
						"    <p class=\"p17\">On and any time after the occurrence of Event of Default, the Lender may, without prejudice to any other rights that it may have under this Agreement or applicable Law (including right to accelerate payment obligations of the Borrower under the Financing Documents) take one or more of the following actions: (a) recall or declare the Outstanding Amounts to be forthwith due and payable, whereupon such amounts shall become forthwith due and payable without presentment, demand, protest or any other notice of any kind, all of which are hereby expressly waived, anything contained herein to the contrary notwithstanding;<strong> (b) </strong>exercise any and all rights specified in the Financing Documents including, without limitation, to enforce any security created/provided;<strong> (c) </strong>to initiate, appropriate proceedings for recovery of its dues by invoking the jurisdiction of appropriate court at its sole discretion, in addition to taking further action or actions under any other statute in force; and/or (d) exercise such other remedies as permitted or available under applicable law in the sole discretion of the Lender; and/or<strong> (e) </strong>disclose the name of the Borrower, and its promoters/directors/partners to RBI, TransUnion CIBIL and/or any other authorised agency.</p>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li12\">SUCCESSION</li>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li11\">In case of the death of the Borrower, where the Borrower is an individual and the Lender agrees to continue extending the Facility, the legal representative of the Borrower, with such other requirements as the Lender may deem fit.</li>\n" +
						"    </ul>\n" +
						"    <li class=\"li12\">MISCELLANEOUS</li>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li11\"><span class=\"s1\">Governing Law and Jurisdiction</span></li>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li11\">This Agreement and the rights and obligations of the Parties hereunder shall be construed in accordance with and be governed by the laws of India.</li>\n" +
						"    <li class=\"li11\">The Parties agree that the courts in New Delhi shall have exclusive jurisdiction to settle any disputes which may arise out of or in connection with the Financing Documents.</li>\n" +
						"    <li class=\"li11\">The Borrower irrevocably waive any objection, now or in future, to the venue of any Proceedings being the courts at New Delhi or any claim that any such Proceedings have been brought in an inconvenient forum.</li>\n" +
						"    <li class=\"li11\">Nothing contained herein shall limit any right of the Lender to take Proceedings in any other court of competent jurisdiction, nor shall the taking of proceedings in one or more jurisdictions preclude the taking of proceedings in any other jurisdiction whether concurrently or not and the Borrower irrevocably waive any objection it may have now or in the future to the laying of the venue of any Proceedings on the grounds that such Proceedings have been brought in an inconvenient forum.</li>\n" +
						"    </ul>\n" +
						"    <li class=\"li11\"><span class=\"s1\">Arbitration</span></li>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li11\">Without prejudice to the other legal remedies available to the Lender under applicable law (including under the SARFAESI Act, 2002 and Insolvency and Bankruptcy Code, 2016), any dispute arising out of or in connection with the Financing Documents shall be referred to and finally resolved by arbitration under the Arbitration and Conciliation Act, 1996 (as amended from time to time).</li>\n" +
						"    <li class=\"li11\">The arbitration shall be referred to a sole arbitrator appointed by the Lender. The seat and venue of the arbitration shall be New Delhi. The language of the arbitration and the award of the arbitrator shall be in the English language. The award of the arbitrator shall be final and binding on the Parties and the expenses of the arbitration shall be borne in such manner as the arbitrator may determine.</li>\n" +
						"    </ul>\n" +
						"    <li class=\"li11\"><span class=\"s1\">Indemnity</span></li>\n" +
						"    </ul>\n" +
						"    </ul>\n" +
						"    <p class=\"p16\">Without prejudice to and in addition to other provisions contained in the Financing Documents, the Borrower hereby agrees to indemnify the Lender/BharatPe and its directors, officers, representatives and agents against any losses, liabilities, claims, damages or the like (including, without limitation, reasonable attorneys' fees and expenses) which may be sustained or incurred by any of them as a result of, or in connection with, or arising out of:</p>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <ul class=\"ul1\">\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li11\">the Borrower failing to comply with the provisions of any Financing Documents and applicable Laws; and / or</li>\n" +
						"    <li class=\"li11\">the occurrence of any Event of Default; and / or</li>\n" +
						"    <li class=\"li11\">levy by any Government Authority of any charge, Taxes or penalty in connection with regularising or perfecting any of the Financing Documents as may be required under applicable Law at any time during the currency of the Facility, or getting any of the Financing Documents admitted into evidence, or relying on any Financing Documents for proving any claim; and/or</li>\n" +
						"    <li class=\"li11\">the exercise of any of the rights by the Lender under this Agreement and any of the Financing Documents; and/or</li>\n" +
						"    <li class=\"li11\">any of the representations and warranties of the Borrower under the Financing Documents are found to be false or untrue or incorrect on a future date.</li>\n" +
						"    </ul>\n" +
						"    <li class=\"li11\"><span class=\"s1\">Confidentiality</span></li>\n" +
						"    </ul>\n" +
						"    </ul>\n" +
						"    <p class=\"p16\">Any information supplied by a Party to another Party pursuant hereto which is by its nature can reasonably be construed to be proprietary or confidential or is marked &ldquo;confidential&rdquo; (&ldquo;<strong>Confidential Information</strong>&rdquo;) shall be kept confidential by the recipient unless or until compelled to disclose the same (i) by judicial or administrative process, or (ii) by law, or unless the same (iii) is in or is a part of public domain, or (iv) is required to be furnished to the bankers or investors or potential investors in the either Party, or (v) is required to be furnished to any Government Authority having jurisdiction over the recipient, or (vi) can be shown by the receiving Party to the reasonable satisfaction of the disclosing Party to have been known to the receiving Party prior to it being disclosed by the disclosing Party to the receiving Party, or (vii) subsequently comes lawfully into the possession of the receiving Party from a third party, and in such cases the confidentiality obligations shall cease to the extent required under the foregoing circumstances.</p>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li11\"><span class=\"s1\">Amendments and Waivers</span></li>\n" +
						"    </ul>\n" +
						"    </ul>\n" +
						"    <p class=\"p16\">This Agreement (including the schedules, annexure and appendices hereto) may not be amended, supplemented or modified and no other Financing Document may be amended, supplemented or modified and no term or condition thereof may be waived without the written consent of the Parties to such Financing Document.</p>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li11\"><span class=\"s1\">Severability</span></li>\n" +
						"    </ul>\n" +
						"    </ul>\n" +
						"    <p class=\"p16\">Any provision of this Agreement or any other Financing Document which is prohibited or unenforceable shall be ineffective to the extent of prohibition or unenforceability but shall not invalidate the remaining provisions of this Agreement or any Financing Document.</p>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li11\"><span class=\"s1\">Survival</span></li>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li11\">This Agreement shall be in force until all the Outstanding Amounts under this Agreement have been fully and irrevocably paid in accordance with the terms and provisions hereof.</li>\n" +
						"    <li class=\"li11\">The obligations of the Borrower under the Financing Documents will not be affected by:</li>\n" +
						"    </ul>\n" +
						"    </ul>\n" +
						"    </ul>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li11\">any unenforceability, illegality or invalidity of any obligation of any Person under a Financing Document; or</li>\n" +
						"    <li class=\"li11\">the breach, frustration or non-fulfilment of any provisions of, or claim arising out of or in connection with a Financing Document.</li>\n" +
						"    </ul>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li11\"><span class=\"s1\">Right of Set-off</span></li>\n" +
						"    </ul>\n" +
						"    </ul>\n" +
						"    <p class=\"p16\">In addition to any rights now or hereafter granted under Applicable Law or otherwise, and not by way of limitation of any such rights, upon the occurrence and continuation of an Event of Default, the Lender is hereby authorised by the Borrower to, from time to time, without presentment, demand, protest or other notice of any kind to the Borrower, or to any other Person, set off and/or appropriate and/or apply any and all deposits (general or special) at any time held or owing by the Lender (including, without limitation, by any branches and agencies other than the lending office of Lender) to or for the credit or the account of the Borrower against and on account of the obligations and liabilities of the Borrower to the Lender under this Agreement or under any of the other Financing Documents.</p>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li11\"><span class=\"s1\">Notices</span></li>\n" +
						"    </ul>\n" +
						"    </ul>\n" +
						"    <p class=\"p16\">All notices and other communications provided at various places in this Agreement shall be in writing and (a) sent by hand delivery, or (b) prepaid registered post with acknowledgment due, or (c) by e-mail followed by prepaid registered post with acknowledgment due, at the address and/or email first above written. All such notices and communications shall be deemed to have been delivered effective: (i) if sent by email, when sent (provided the email enters the sent folder of the sender), (ii) if sent by prepaid registered post, 3 (three) Business Days after its dispatch.</p>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li11\"><span class=\"s1\">Effectiveness</span></li>\n" +
						"    </ul>\n" +
						"    </ul>\n" +
						"    <p class=\"p17\">This Agreement shall become binding on the Parties hereto on and from the date hereof and shall be in force and effect till the Final Settlement Date.</p>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li11\"><span class=\"s1\">Entire Agreement</span></li>\n" +
						"    </ul>\n" +
						"    </ul>\n" +
						"    <p class=\"p17\">This Agreement and other Financing Documents shall represent the entire understanding of the Parties on the subject matter hereof and shall override all the previous understanding and agreement between the Parties hereto.</p>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li11\"><span class=\"s1\">No Discrimination</span></li>\n" +
						"    </ul>\n" +
						"    </ul>\n" +
						"    <p class=\"p17\">The Borrower shall, at all times during the term of this Agreement, ensure that no fraudulent preference is given to other lender of the Borrower, both present and future, so as to defeat Lender&rsquo;s rights, either present and future under this Agreement or to fraudulently service the dues owed to other lenders in preference to the dues owed to the Lender or to wilfully act in or consent to any third party acting in a manner as would cause a Material Adverse Effect.</p>\n" +
						"    <p class=\"p19\">&nbsp;</p>\n" +
						"    <p class=\"p19\">&nbsp;</p>\n" +
						"    <p class=\"p20\">&nbsp;</p>\n" +
						"    <p class=\"p20\">&nbsp;</p>\n" +
						"    <p class=\"p8\" style=\"text-align: center;\"><strong>SCHEDULE I</strong></p>\n" +
						"    <p class=\"p21\">&nbsp;</p>\n" +
						"    <p class=\"p8\" style=\"text-align: center;\"><strong>TERMS OF THE FACILITY</strong></p>\n" +
						"    <p class=\"p9\">&nbsp;</p>\n" +
						"    <table class=\"t1 new-table1\" cellspacing=\"0\" cellpadding=\"0\">\n" +
						"    <tbody>\n" +
						"    <tr>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p22\"><strong>S. NO.</strong></p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p23\"><strong>PARTICULARS</strong></p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" +
						"    <p class=\"p24\"><strong>DETAILS</strong></p>\n" +
						"    </td>\n" +
						"    </tr>\n" +
						"    <tr>\n" +
						"    <td class=\"td1\" valign=\"middle\">1&nbsp;</td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p25\">Date of Agreement</p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" +
						"    <p class=\"p20\">"+detail.getOrDefault("Date", "")+"&nbsp;</p>\n" +
						"    </td>\n" +
						"    </tr>\n" +
						"    <tr>\n" +
						"    <td class=\"td1\" valign=\"middle\">2&nbsp;</td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p25\">Place of Agreement</p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" +
						"    <p class=\"p20\">"+detail.getOrDefault("City", "")+"&nbsp;</p>\n" +
						"    </td>\n" +
						"    </tr>\n" +
						"    <tr>\n" +
						"    <td class=\"td1\" valign=\"middle\">3&nbsp;</td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p25\">Loan Agreement No.</p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" +
						"    <p class=\"p20\">&nbsp;</p>\n" +
						"    </td>\n" +
						"    </tr>\n" +
						"    <tr>\n" +
						"    <td class=\"td1\" valign=\"middle\">4&nbsp;</td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p25\">Name of Borrower</p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" +
						"    <p class=\"p20\">"+detail.getOrDefault("Name of the Borrower", "")+"&nbsp;</p>\n" +
						"    </td>\n" +
						"    </tr>\n" +
						"    <tr>\n" +
						"    <td class=\"td1\" valign=\"middle\">5&nbsp;</td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p25\">Address of Borrower</p>\n" +
						"    <p class=\"p26\">&nbsp;</p>\n" +
						"    <p class=\"p25\">Email Address of Borrower</p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" +
						"    <p class=\"p20\">"+detail.getOrDefault("Shop/Business Address", "")+"&nbsp;</p>\n" +
						"    <p class=\"p20\">"+detail.getOrDefault("Email", "")+"&nbsp;</p>\n" +
						"    </td>\n" +
						"    </tr>\n" +
						"    <tr>\n" +
						"    <td class=\"td1\" valign=\"middle\">6&nbsp;</td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p25\">Borrower's constitution</p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" +
						"    <p class=\"p27\">&nbsp;</p>\n" +
						"    <p class=\"p27\">&nbsp;</p>\n" +
						"    <p class=\"p28\">"+"Individual"+"</p>\n" +
						"    </td>\n" +
						"    </tr>\n" +
						"    <tr>\n" +
						"    <td class=\"td1\" valign=\"middle\">7&nbsp;</td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p25\">Purpose of the Facility/ Proposed utilization of the Facility</p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" +
						"    <p class=\"p20\">"+"For General"+"&nbsp;</p>\n" +
						"    </td>\n" +
						"    </tr>\n" +
						"    <tr>\n" +
						"    <td class=\"td1\" valign=\"middle\">8&nbsp;</td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p25\">Amount of Loan</p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" +
						"    <p class=\"p29\">"+detail.getOrDefault("Loan Amount", "")+"&nbsp;</p>\n" +
						"    </td>\n" +
						"    </tr>\n" +
						"    <tr>\n" +
						"    <td class=\"td1\" valign=\"middle\">9&nbsp;</td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p25\">Availability Period</p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" +
						"    <p class=\"p6\">The period of days/months commencing from the date of execution of this Agreement or by such extended time as may be allowed by the Lender, available for draw down by the Borrower.</p>\n" +
						"    </td>\n" +
						"    </tr>\n" +
						"    <tr>\n" +
						"    <td class=\"td1\" valign=\"middle\">10&nbsp;</td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p25\">Business of the Borrower</p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" +
						"    <p class=\"p20\">"+(merchant.getBusinessCategory()==null?"":merchant.getBusinessCategory())+"&nbsp;</p>\n" +
						"    </td>\n" +
						"    </tr>\n" +
						"    <tr>\n" +
						"    <td class=\"td1\" valign=\"middle\">11&nbsp;</td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p25\">Penal Interest Rate</p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" +
						"    <p class=\"p20\">NIL&nbsp;</p>\n" +
						"    </td>\n" +
						"    </tr>\n" +
						"    <tr>\n" +
						"    <td class=\"td1\" valign=\"middle\">12&nbsp;</td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p25\">Interest Rate</p>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li6\">Interest chargeable (In case of Fixed/Monthly Loans)</li>\n"+
						"    </ul>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p20\">"+detail.getOrDefault("Monthly rate of interest", "")+" per month&nbsp;</p>\n" +
						"    </td>\n" +
						"    </tr>\n" +
						"    <tr>\n" +
						"    <td class=\"td1\" valign=\"middle\">13&nbsp;</td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p25\">Non-refundable Processing Fees /</p>\n" +
						"    <p class=\"p25\">service charge</p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" +
						"    <p class=\"p20\">Nil&nbsp;</p>\n" +
						"    </td>\n" +
						"    </tr>\n" +
						"    </tbody>\n" +
						"    </table>\n" +
						"    <p class=\"p32\">&nbsp;</p>\n" +
						"    <p class=\"p35\" style=\"text-align: center;\"><strong>TABLE OF CHARGES</strong></p>\n" +
						"    <p class=\"p21\">&nbsp;</p>\n" +
						"    <table class=\"t1 new-table2\" cellspacing=\"0\" cellpadding=\"0\">\n" +
						"    <tbody>\n" +
						"    <tr>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p36\"><strong>Type of Charges</strong></p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p37\">&nbsp;</p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p38\"><strong>Type of Charges</strong></p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p37\">&nbsp;</p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p39\"><strong>Type of Charges</strong></p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p37\">&nbsp;</p>\n" +
						"    </td>\n" +
						"    </tr>\n" +
						"    <tr>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p40\">Late payment Charges</p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p20\">NIL&nbsp;</p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p41\">Part Prepayment Charges</p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p20\">NIL&nbsp;</p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p42\">Title Search Report Charges (Legal Charges)</p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p20\">NIL&nbsp;</p>\n" +
						"    </td>\n" +
						"    </tr>\n" +
						"    <tr>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p40\">Stamping Charges</p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p20\">NIL&nbsp;</p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p41\">Processing Fee</p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p20\">NIL&nbsp;</p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p43\">Other Charges</p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p20\">NIL&nbsp;</p>\n" +
						"    </td>\n" +
						"    </tr>\n" +
						"    </tbody>\n" +
						"    </table>\n" +
						"    <p class=\"p32\">&nbsp;</p>\n" +
						"    <p class=\"p32\">&nbsp;</p>\n" +
						"    <p class=\"p8\" style=\"text-align: center;\"><strong>SCHEDULE II</strong></p>\n" +
						"    <p class=\"p21\">&nbsp;</p>\n" +
						"    <p class=\"p8\" style=\"text-align: center;\"><strong>REPAYMENT SCHEDULE</strong></p>\n" +
						"    <p class=\"p9\">&nbsp;</p>\n" +
						"    <table class=\"t1 new-table1\" cellspacing=\"0\" cellpadding=\"0\">\n" +
						"    <tbody>\n" +
						"    <tr>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p44\"><strong>S. No</strong></p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p45\"><strong>Particulars</strong></p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p46\"><strong>Details</strong></p>\n" +
						"    </td>\n" +
						"    </tr>\n" +
						"    <tr>\n" +
						"    <td class=\"td1\" valign=\"middle\">1&nbsp;</td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p47\">Number of EDI</p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p20\">"+detail.getOrDefault("EDI Count", "")+"&nbsp;</p>\n" +
						"    </td>\n" +
						"    </tr>\n" +
						"    <tr>\n" +
						"    <td class=\"td1\" valign=\"middle\">2&nbsp;</td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p47\">Date of Commencement of EDI</p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p20\">"+((new Date().getDay()!=6?new Date(): DateTimeUtil.addDays(new Date(), 2)))+"&nbsp;</p>\n" +
						"    </td>\n" +
						"    </tr>\n" +
						"    <tr>\n" +
						"    <td class=\"td1\" valign=\"middle\">3&nbsp;</td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p47\">Mode of repayment</p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p20\">QR Settlement&nbsp;</p>\n" +
						"    </td>\n" +
						"    </tr>\n" +
						"    </tbody>\n" +
						"    </table>\n" +
						"    <p class=\"p32\">&nbsp;</p>\n" +
						"    <p class=\"p32\">&nbsp;</p>\n" +
						"    <p class=\"p20\">&nbsp;</p>";
		}
		return html;
	}
	
	public Double getInterest(String category) {
		List<LendingCategories> lendingCategoriesList=lendingCategoryDao.findByCategory(category);
		if(!lendingCategoriesList.isEmpty()) {
			LendingCategories lendingCategories=lendingCategoriesList.get(0);
			return lendingCategories.getInterestRate();
		}
		return null;
	}
	
	public Map<String,String> getDetails(Merchant merchant, long applicationId){
		Map<String,String> detail=new HashMap<>();
		try {
			LendingApplication lendingApplication=lendingApplicationDao.findByIdAndMerchant(applicationId, merchant);
			if(lendingApplication == null) {
				logger.error("Lending application not found for id {}",applicationId);
				return null;
			}
			detail.put("Loan Amount", lendingApplication.getLoanAmount().toString());
			detail.put("Tenure", lendingApplication.getTenureInMonths().toString());
			detail.put("Rate of Interest", Double.toString(lendingApplication.getInterestRate() * 12));
			detail.put("Interest", Double.toString(lendingApplication.getInterestRate()));
			detail.put("Penal Interest", "NA");
			detail.put("Loan ID", "Will be generated later");
			detail.put("Date", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
			detail.put("EDI Start Date", new Date().getDay()!=6?new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()): new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(DateTimeUtil.addDays(new Date(), 2)));
			detail.put("Amount of EDI",lendingApplication.getEdi().toString());
			detail.put("Registered Mobile Number",merchant.getMobile());
			detail.put("Location",lendingApplication.getCity());
			detail.put("Shop/Business Address", lendingApplication.getShopNumber()+", "+lendingApplication.getStreetAddress()+", "+lendingApplication.getArea());
			detail.put("Landmark", lendingApplication.getLandmark());
			detail.put("PIN", lendingApplication.getPincode().toString());
			detail.put("City", lendingApplication.getCity());
			detail.put("State", lendingApplication.getState());
			detail.put("Email", lendingApplication.getEmail() != null ? lendingApplication.getEmail() : "");
			detail.put("EDI Count", lendingApplication.getPayableDays().toString());
			detail.put("Lender",lendingApplication.getLender());
			detail.put("Pancard", getPanCard(merchant));
			Double monthlyRateOfInterest=getInterest(lendingApplication.getCategory());
			detail.put("Monthly rate of interest", monthlyRateOfInterest==null?"":monthlyRateOfInterest.toString());
			detail.put("Annual rate of interest", monthlyRateOfInterest==null?"":(monthlyRateOfInterest*12+""));
			MerchantBankDetail merchantBankDetail=merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchant.getId(), "ACTIVE");
			if(merchantBankDetail!=null) {
				detail.put("Name of the Borrower",merchantBankDetail.getBeneficiaryName());
				detail.put("Bank Name", merchantBankDetail.getBankName());
				detail.put("Account No", merchantBankDetail.getAccountNumber());
				detail.put("Account Type", merchantBankDetail.getAccType());
				detail.put("IFSC Code", merchantBankDetail.getIfscCode());
			}
			detail.put("IP Address", lendingApplication.getIp());
			detail.put("Timestamp", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
			return detail;
		}
		catch(Exception e) {
			logger.error("Error occured while fetching details",e);
			return null;
		}
	}
	
	private String getPanCard(Merchant merchant) {
		Experian experian=experianDao.getByMerchantId(merchant.getId());
		return experian.getPancardNumber();
	}
	
	private String getLdcTnc(Map<String,String> detail) {
		String html="<html>\n" + 
				"<body>\n" + 
				"<p class=\"p1\"><span class=\"s1\"><strong>Necessary Loan Details/Master TnCs</strong></span></p>\n" + 
				"<p class=\"p2\">&nbsp;</p>\n" + 
				"<p class=\"p3\">Loan ID: "+detail.getOrDefault("Loan ID", "")+"</p>\n" +
				"<p class=\"p3\">Date: " + DateTimeUtil.getDate(new Date()) + "</p>\n" +
				"<p class=\"p3\">Loan Amount (INR): "+detail.getOrDefault("Loan Amount", "")+"</p>\n" +
				"<p class=\"p3\">Tenure (Months): "+detail.getOrDefault("Tenure", "")+"</p>\n" +
				"<p class=\"p3\">Flat Rate of Interest (% per month): "+detail.getOrDefault("Monthly rate of interest", "")+"</p>\n" +
				"<p class=\"p3\">Flat Rate of Interest (% per annum): "+detail.getOrDefault("Annual rate of interest", "")+"</p>\n" +
				"<p class=\"p3\">Amount of EDI: "+detail.getOrDefault("Amount of EDI", "")+"</p>\n" +
				"<p class=\"p3\">BharatPe Registered Mobile Number: "+detail.getOrDefault("Registered Mobile Number", "")+"</p>\n" +
				"<p class=\"p3\">Location: "+detail.getOrDefault("Location", "")+"</p>\n" +
				"<p class=\"p3\">EDI Due Date: Everyday from Monday to Saturday from the successive day of disbursal</p>\n" +
				"<p class=\"p3\">Business Address: "+detail.getOrDefault("Shop/Business Address", "")+"</p>\n" +
				"<p class=\"p3\">Landmark: "+detail.getOrDefault("Landmark", "")+"  PIN: "+detail.getOrDefault("PIN", "")+"  City: "+detail.getOrDefault("City", "")+"  State: "+detail.getOrDefault("State", "")+"</p>\n" +
				"<p class=\"p4\">Shop/ Business Phone Number: "+detail.getOrDefault("Registered Mobile Number", "")+"</p>\n" +
				"<p class=\"p5\">&nbsp;</p>\n" + 
				"<p class=\"p5\">&nbsp;</p>\n" + 
				"<p class=\"p6\"><strong>Declaration / Undertaking/Representation by Borrower:</strong></p>\n" + 
				"<p class=\"p7\">&nbsp;</p>\n" + 
				"<ul class=\"ul1\">\n" + 
				"<li class=\"li8\">I/We hereby apply for a finance facility through Resilient Innovation Private Limited (&ldquo;BharatPe&rdquo;), which is Lender&rsquo;s collection agent and for such other services as agreed between the Lender and BharatPe, from time to time and in terms of Loan Agreement, declare that all the particulars, information and details provided and other documents submitted by me/us are true, correct, complete and up-to-date in all respects and that I/We have not withheld any material information. All outstanding balances including principal, interest, charges etc. shall be payable/paid as may be directed &amp; advised by Lender/BharatPe.</li>\n" + 
				"<li class=\"li8\">I/We undertake to remit all Outstanding Amounts to the Lender on the respective Due Date. Further I agree to the Lender&rsquo;s its right to call upon me/us to pay the whole or part of the outstanding amount at any time in the event of a default under any financing document.</li>\n" + 
				"<li class=\"li8\">I/We hereby authorize Lender/BharatPe to exchange or share information and details relating to my application to its group companies or any third party, as may be required or deemed fit, for the purpose of processing this loan application and/or related offerings or other products / services that I/We may apply for from time to time.</li>\n" + 
				"<li class=\"li8\">By submitting this application, I/We hereby expressly authorize Lender/BharatPe to send me communications regarding loans, insurance and other products from Lender/BharatPe, its group<span class=\"Apple-converted-space\">&nbsp; </span>companies and / or third parties through telephone calls / SMSs / emails / post etc. including but not limited to promotional, transactional communications. I/We confirm that I shall not challenge receipt of such communications by me as unsolicited communication, defined under TRAI Regulations on Unsolicited Commercial Communications.</li>\n" + 
				"<li class=\"li8\">I authorize Lender/BharatPe to evaluate my transaction history on the BharatPe platform in order to check my eligibility for the loan and understand and acknowledge that Lender/BharatPe has the absolute discretion, without assigning any reasons to reject my application and that Lender/BharatPe is not answerable / liable to me, in any manner whatsoever, for rejecting my application.</li>\n" +
				"<li class=\"li9\">I / We agrees and accept that Lender/BharatPe may in its sole discretion, by its self or through authorised persons, advocate, agencies, bureau, etc. verify any information given, check credit references, employment details and obtain credit reports to determine creditworthiness from time to time.</li>\n" + 
				"<li class=\"li9\">I/We further confirms and/or undertakes that the Facility shall not be utilized for the following:</li>\n" + 
				"<ul class=\"ul1\">\n" + 
				"<li class=\"li9\">Subscription to or purchase of shares/debentures;</li>\n" + 
				"<li class=\"li9\">Extending unsecured loans to subsidiary company/ associates or for making inter corporate deposits;</li>\n" + 
				"<li class=\"li9\">Any speculative purposes or any anti-social purpose or any unlawful purpose.</li>\n" + 
				"</ul>\n" + 
				"</ul>\n" + 
				"<p class=\"p10\">&nbsp;</p>\n" + 
				"<ul class=\"ul1\">\n" + 
				"<li class=\"li9\">I/We hereby represents and warrants to the Lender on a continuing basis that:</li>\n" + 
				"<ul class=\"ul1\">\n" + 
				"<li class=\"li9\">has complied with all the applicable Laws and is not a party to any litigation, arbitration or administrative or regulatory proceedings or investigations of a material character and is not aware, to the best of its knowledge and belief, of any facts likely to give rise to such litigation, arbitration or administrative or regulatory proceedings or investigations or to material claims against me/us.</li>\n" + 
				"<li class=\"li9\">is fully aware of the KYC Policy of the Lender and RBI and confirms that the information/clarification/documents/signage provided by it on its identity, address, authorised signatory, board resolution, PAN and all other material facts are true and correct and the transaction, etc. are <em>bonafide </em>and as per Law. I/we further confirms that it has disclosed all facts/information as are required to be disclosed for the adherence and compliance of the provisions related to the KYC Policy. The Lender reserve the right to recall the Facility or close the account in case the required documents are not provided by me/us to the Lender.</li>\n" + 
				"</ul>\n" + 
				"<li class=\"li11\">The Lender/BharatPe shall, without notice to or without any consent me/us, be absolutely entitled and have full right, power and authority to make disclosure of any information including personal information, details in relation to documents, Loan, defaults, security, obligations, to the Credit Information Bureau of India (CIBIL) and/or any other governmental/regulatory/statutory or private agency/entity, credit bureau, RBI, the Lender&rsquo;s other branches/ subsidiaries / affiliates / rating agencies, service providers, other Lenders / financial institutions, any third parties, any assignees/potential assignees or transferees, who may need the information and may process the information, publish in such manner and through such medium as may be deemed necessary by the publisher/ Lender/ RBI, including publishing the name as part of willful defaulter&rsquo;s list from time to time, as also use for KYC information verification, credit risk analysis, or for other related purposes.</li>\n" +
				"<li class=\"li11\">I/we have informed the Lender about all loans/finances/advances availed by me/us from other banks/financial institutions/third parties to the Lender.</li>\n" + 
				"</ul>\n" + 
				"<p class=\"p7\">&nbsp;</p>"+
				"<p><br /><br /><br /></p>\n" + 
				
				"\n" +
				"    <p class=\"p1\" style=\"text-align: center;\"><span class=\"s1\"><strong>LOAN AGREEMENT</strong></span></p>\n" + 
				"    <p><br />This Loan Agreement (the \"Agreement\") is executed at Mumbai on "+DateTimeUtil.getDate(new Date())+"</p>\n" + 
				"    <p>BETWEEN</p>\n" + 
				"    <p>The lender(s) arranged by Innofin Solutions Private Limited (\"LenDenClub\"), a P2P NBFC platform registered with RBI, hereinafter referred to as \"Lender\" and collectively referred to as the \"Lenders\" which expression shall, unless repugnant to the context thereof, mean and include their respective successors, legal representatives, heirs and permitted assigns),electronically agrees to this agreement, of the First Part</p>\n" + 
				"    <p>AND</p>\n" + 
				"    <p>Innofin Solutions Private Limited, a company incorporated under the provisions of the Companies Act, 2013 with corporate identity number [U74999MH2015PTC266499] having NBFC registration number as N-13.02267 (hereinafter referred to as \"LenDenClub\" , which expression shall, unless excluded by or repugnant to the context or meaning thereof, include its successors and permitted assigns) of the Second Part;</p>\n" +
				"    <p>AND</p>\n" + 
				"    <p>"+detail.getOrDefault("Name of the Borrower", "")+" having PAN No. "+detail.getOrDefault("Pancard", "")+", and "+detail.getOrDefault("Shop/Business Address", "")+", hereinafter referred to as \"the Borrower\" (which expression unless it be repugnant to the context or meaning thereof be deemed to mean and include his/her legal representative, assignee and administrator) of the Third Part.</p>\n" + 
				"    <p>The Lender, Borrower and the LenDenClub are, wherever the context so requires, hereinafter collectively referred to as the 'Parties' and individually as 'Party'.</p>\n" + 
				"    <p>&bull; WHEREAS, the Lender and the Borrower have come across each other for lending and borrowing unsecured loans through the LenDenClub having website as www.lendenclub.com;</p>\n" + 
				"    <p>&bull; The Borrower is desirous of availing a loan of an aggregate amount of INR "+detail.getOrDefault("Loan Amount", "")+" (\"Loan Amount\") from the Lender</p>\n" + 
				"    <p><br /> &bull; At the request of the Borrower and relying on representations and warranties, and covenants undertaken by the Borrower and subject to the terms and conditions contained herein, each of the individuals and/or institutions captured under Lender Group &lt;Lender Group Id&gt; hereby agrees to lend their proportion of the Loan Amount to the Borrower as captured in the information technology system of LenDenClub and the Borrower agrees to borrow from the Lender Group &lt;Lender Group Id&gt; the Loan Amount.</p>\n" + 
				"    <p>&bull; WHEREAS, Lender desires for the LenDenClub,to mobilize the repayment amounts received from the Borrower and provided other services as detailed in this agreement and LenDenClub desires to provide such services, in accordance with the terms and conditions set forth in this Agreement.</p>\n" + 
				"    <p>NOW, IN CONSIDERATION OF THE MUTUAL UNDERSTANDING AND OBLIGATIONS SET OUT IN THIS LOAN AGREEMENT, THE SUFFICIENCY OF WHICH IS HEREBY ACKNOWLEDGED, THE ABOVE PARTIES, INTENDING TO BE LEGALLY BOUND, AGREES AS FOLLOWS: -</p>\n" + 
				"    <p>&bull; DEFINITIONS AND INTERPRETATIONS</p>\n" + 
				"    <p>In this Loan Agreement, unless the context otherwise requires: (a) headings are for convenience only and shall not affect interpretation, (b) where a word or phrase is defined, other parts of speech and grammatical forms of that word or phrase shall have corresponding meanings, (c) words denoting any gender shall include all genders, (d) references to days, months and years are to calendar days, calendar months and calendar years respectively and (e) \"including\" and \"inter alia\" shall be deemed to be followed by \"without limitation\" or \"but not limited to\".</p>\n" + 
				"    <p>&bull; DISBURSEMENT</p>\n" + 
				"    <p>The Lender hereby agrees that platform can transfer respective Loan Amount to the Borrower. The date of disbursal of the loan shall be the Loan Date (\"Loan Date\"). In case, the borrower has availed a loan to purchase a product/service, the borrower has electronically authorised LenDenClub to transfer the Loan Amount to the person/entity/institution/business which has provided a product/services to the Borrower. The details of the bank account, where the Loan Amount has been transferred, after due authorisation of the Borrower is (\"Borrower's Bank Account\") as under:--</p>\n" + 
				"    <p>Bank Account<br />"+detail.getOrDefault("Account No", "")+"</p>\n" + 
				"    <p><br />Account Holder Name<br />"+detail.getOrDefault("Name of the Borrower", "")+"</p>\n" + 
				"    <p><br />Type of Account<br />CURRENT</p>\n" + 
				"    <p><br />Bank Name<br />"+detail.getOrDefault("Bank Name", "")+"</p>\n" + 
				"    <p><br />IFSC Code<br />"+detail.getOrDefault("IFSC Code", "")+"</p>\n" + 
				"    <p>&nbsp;</p>\n" + 
				"    <p>&nbsp;</p>\n" + 
				"    <p>&nbsp;</p>\n" + 
				"    <p>3. RATE OF INTEREST</p>\n" + 
				"    <p>The borrower pays "+detail.getOrDefault("Interest", "")+" % per month. Rate of Interest on this loan. Borrower pays EMI as per clause no. 6.</p>\n" + 
				"    <p>&nbsp;</p>\n" + 
				"    <p>&bull; RATE OF DEFAULT INTEREST &amp; DELAY CHARGES</p>\n" + 
				"    <p>&bull; The Borrower do hereby agrees and confirms that in addition to the aforesaid Penal Interest, the Borrower shall be liable to pay delay charges as mentioned in Annexure 1<br /> &bull; The Borrower do hereby agree and confirms that the Lender's right to recover Penal Interest and Delay Charges shall be without prejudice to Lender's other rights available as per this Loan Agreement.<br /> &bull; The Borrower do hereby agree and confirms that his/her obligations to pay Penal Interest and Delay Charges shall not entitle the Borrower to set up the defense that no event of default as mentioned hereunder has occurred.<br />page 2.</p>\n" + 
				"    <p>&bull; DUE DATE OF FIRST &amp; SUBSEQUENT INSTALLMENTS</p>\n" + 
				"    <p>The Parties do hereby agree and confirm that the first installment shall become due and payable by the Borrower to the Lender from successive date of the loan disbursal. All subsequent installments shall become due and payable on a daily basis, from Monday to Saturday, as equated daily installments (EDI) till the repayment of Interest and Principal.</p>\n" + 
				"    <p>&bull; LOAN REPAYMENT SCHEDULE</p>\n" + 
				"    <p>The Borrower do hereby covenants with the Lender(s) to repay to the Lender(s) the Loan Amount as equated daily installments (EDIs) of "+ detail.getOrDefault("Amount of EDI", "") +" with interest payable in "+detail.getOrDefault("EDI Count", "")+" installments, in the following manner:</p>\n" +
				"    <p>"+detail.getOrDefault("EDI Count", "")+" EDIs -> Rs."+detail.getOrDefault("Amount of EDI", "")+" each=Rs."+((detail.get("EDI Count")!=null && detail.get("Amount of EDI")!=null)?Double.parseDouble(detail.get("EDI Count")) * Double.parseDouble(detail.get("Amount of EDI")):"")+"</p>\n" +
				"    <p>&nbsp;</p>\n" + 
				"    <p>&nbsp;</p>\n" + 
				"    <p>&nbsp;</p>\n" + 
				"    <p><br /> &bull; MODE OF REPAYMENT OF LOAN</p>\n" + 
				"    <p>&bull; The Borrower do hereby covenants with the Lender that for the purpose of repayment of the Loan Amount together with Interest and if applicable, Default Interest and Delay Charges, thereon by way of EDIs in the manner provided in Clause 6 above. <br /> &bull; The Borrower hereby authorizes LenDenClub to disburse the Loan amount to the Bank Account mentioned in Clause 2 <br /> &bull; The Borrower hereby further authorizes to LenDenClub or its appointed agent to deduct money against due and payable EDIs from the Borrower's daily QR codes based settlement and credit it to LenDenClub repayment escrow account to facilitate the repayment of loan.<br /> &bull; The borrower agrees to provide National Automated Clearing House Mandate (\"NACH Mandate\") for the tenure of loan covering all the installments, in favour of the Lender or any other person or entity duly authorized by the Lender in this behalf or by handing over postdated duly signed cheques to LenDenClub for repayment of a loan instalment to the Lender. <br /> &bull; The Borrower do hereby further covenants that without prior written consent of the Lender, the Borrower shall not close that bank account from which the NACH Mandate has been facilitated or from which the cheques has been issued, without prior intimation to Lender and making alternate arrangement for the payment of the balance Installments. Any instruction for closure of account without the consent of the Lender shall be deemed to be an event of default and consequence as setout in Clause 8 shall ensue.</p>\n" + 
				"    <p>page 3.</p>\n" + 
				"    <p><br /> &bull; EVENTS OF DEFAULTS</p>\n" + 
				"    <p>8.1. The occurrence of the following events shall be an \"Event of Default\":</p>\n" + 
				"    <p>&bull; The Borrower fails to repay 60 sixty consecutive EDIs the due dates;<br /> &bull; The Borrower has given instruction to the bank to the close the Borrower's Bank Account without the prior written of the Lender.<br /> &bull; The Borrower commits breach of any terms and conditions set out in this Loan Agreement; and/or<br /> &bull; If any attachment, distress execution or any other such process is initiated against the Borrower; and/or<br /> &bull; If the Borrower ceases or threatens to cease or carry on his/her business or profession or employment.</p>\n" +
				"    <p>8.2. Upon occurrence of an Event of Default, notwithstanding any elsewhere contained in this Loan Agreement, the outstanding amount (including the Interest, Default Interest and Delay Charges) as on the date of Event of Default shall immediately become due and payable and the Lender shall be entitled to take all steps /actions available to him under law/equity or otherwise to recover the amount.</p>\n" + 
				"    <p>&bull; APPROPRIATION OF PAYMENTS</p>\n" + 
				"    <p>The Borrower hereby agrees and confirms that any payment made by him/her to the Lender under and/or in terms of this Loan Agreement shall be appropriated in the following manner:-</p>\n" + 
				"    <p>Firstly, towards the costs, fees, expenses and other charges, if any, which the Lender may have to incur for the recovery of amounts payable by the Borrower under this Loan Agreement;</p>\n" + 
				"    <p>Secondly, towards the payment of Default Interest and Delay Charges, due and payable under this Loan Agreement;</p>\n" + 
				"    <p>Thirdly, towards the payment of Interest, due and payable on the Loan Amount;</p>\n" + 
				"    <p>Lastly, towards the payment of installment of the Principal Loan Amount.</p>\n" + 
				"    <p>&bull; PREPAYMENT OF LOAN</p>\n" + 
				"    <p>The Lender hereby agrees and confirms that the Borrower shall have the option to repay in full or part before the due dates of any or all of the EDIs installments, which may be outstanding at that point of time, provided that the Borrower shall remain liable to pay the interest on the Loan Amount outstanding till the date of payment.<br />The lender hereby agrees and confirms that the borrower shall have the option to prepay the loan in full or part at anytime.</p>\n" + 
				"    <p>&bull; SCOPE OF SERVICES OF LenDenClub</p>\n" + 
				"    <p>Each of the Lender has electronically authorized the LenDenClub to undertake the following activities on its behalf:</p>\n" + 
				"    <p>&bull; To facilitate mobilization of repayment amounts received from the Borrower account to Lender account;<br /> &bull; To initiate legal action against the Borrower in case of default in payment or non- payment by the Borrower for more than 30 days as and when need arises and on such fees as may be mutually agreed between the Collection LenDenClub and the Lender.</p>\n" + 
				"    <p>For avoidance of doubt, it is hereby clarified that the services provided by the LenDenClub under this Agreement are limited to point (a) and point (b) above, and under no circumstances LenDenClub will be responsible for collection of money and/or towards payment of any EDI or delay or penal charges on behalf of Borrower. The Lender and Borrower agree, understand and acknowledge that payment of any EDI or delay or penal charges is always the sole responsibility of the Borrower.</p>\n" + 
				"    <p>&nbsp;</p>\n" + 
				"    <p>&bull; REPRESENTATIONS AND WARRANTIES OF THE BORROWER</p>\n" + 
				"    <p>The Borrower do hereby represents and warrants as under:</p>\n" + 
				"    <p>&bull; All information which has been given by Borrower to the Lender with respect to himself is true and accurate in all respects.<br /> &bull; That the Borrower shall utilized the Loan Amount for the purpose of ADVANCE SALARY and for no other purpose;<br /> &bull; That there are no any circumstances of whatsoever nature that would render the transaction contemplated by this Loan Agreement, void or voidable at the option of the Borrower, under the provisions of any Law in force in India;<br /> &bull; That the Borrower is not a party to any litigation of a material character and that the Borrower is not aware of any fact likely to give rise to any litigation or to any material claims against the Borrower;<br /> &bull; that there is no action, suit proceeding, order or investigation pending and/or continuing or to the knowledge of the Borrower initiated by or against the Borrower before any Court of Law;</p>\n" + 
				"    <p>&nbsp;</p>\n" + 
				"    <p>&bull; REPRESENTATIONS AND WARRANTIES OF THE LENDER</p>\n" + 
				"    <p>The Lender do hereby represents and warrants as under:<br /> &bull; That the Lender has adequate legal capacity to enter into this Loan Agreement and perform his or her obligations hereunder.<br /> &bull; That the Lender is not restricted to enter into this Loan Agreement by any Law or any other Loan Agreement;<br /> &bull; That this Loan Agreement and all documents required to be executed under and/or in relation to this Loan Agreement constitute and will constitute valid and binding obligations of the Lender enforceable in accordance with law.</p>\n" + 
				"    <p>&bull; POWERS CONFERRED BY THE BORROWER IN FAVOUR OF THE LENDER</p>\n" + 
				"    <p>The Borrower hereby irrevocably empowers and appoints the Lender to exercise any rights mentioned in this Loan Agreement and/or exercisable by the Lender on behalf of the Borrower and also to act and represent the Borrower in such of the acts expressed to be necessary for the purpose of carrying out the terms and conditions of this Loan Agreement and to represent the Borrower before all authorities including the Borrower's employer, banker, etc. and the Borrower undertakes to furnish all such information, statements, documents and the papers to be submitted or furnished or to be filed before any authority.</p>\n" + 
				"    <p>&bull; COVENANTS AND UNDERTAKINGS OF THE BORROWER</p>\n" + 
				"    <p>The Borrower do hereby agree, covenants and undertakes as under<br /> &bull; That the Borrower shall pay and bear all expenses including stamp Duty and registration charges on actual basis and other charges and expenses which may be incurred in preparation of this Loan Agreement and/or any other related or incidental documents as may be required to be executed in future in connection with the disbursal of the loan to the Borrower by the Lender.<br /> &bull; That in case, if the Lender incurs any legal or other charges or expenses for the recovery of any part of the Loan Amount together with Interest, Default Interest and Delay Charges, then in that event the Borrower shall also be liable to pay the amounts paid by the Lender for such purposes along with interest on such amounts at the rate of Default Interest mentioned hereinabove, from the date of receipt of demand notice by the Borrower.<br /> &bull; That the statement of account forward by the Lender or by any other person or entity duly authorized by the Lender in that behalf to the Borrower, shall be accepted by the Borrower as the conclusive proof of the correctness of the Lender's claim due and payable by the Borrower on that particular date.<br /> &bull; That the Lender as well as the LenDenClub shall be entitled to disclose and furnish to any of the Credit Information Agencies duly authorized by the RBI in that behalf, all such data and information describing the manner in which the Borrower is performing his/her obligations arising under this Loan Agreement and the Borrower shall not be entitled to raise any objection/s in respect thereof.<br /> &bull; That the data and the information so disclosed and furnished by the Lender as well as by the LenDenClub to any of the Credit Information Agencies may be used or processed by such Agencies in the manner as they may deem fit and proper to test the creditworthiness of the Borrower;<br /> &bull; That the rights, powers and remedies given to the Lender by this Loan Agreement shall be in addition to all rights, powers and remedies given to the Lender by virtue of any other statute or rule of law.<br /> &bull; That the Lender at his/her absolute discretion may at any point of time set-off any of the obligation/s and/or the part of the obligation/s of the Borrower arising under this Loan Agreement.<br /> &bull; That at the absolute discretion of the Lender, the Lender shall be entitled to enforce this Loan Agreement himself/herself personally or through any other person or entity duly authorized in writing by the Lender in that behalf and in that event all the covenants and undertakings given by the Borrower to the Lender shall be deemed to have been duly given by the Borrower to such other person or entity who is duly authorized in writing by the Lender;<br /> &bull; That the Borrower shall comply with all covenants, terms, conditions stipulated in this Loan Agreement and shall fully indemnify and keep indemnified the Lender from and against all actions, proceedings, liabilities, claims, demands, loses, damages, costs, charges and expenses whatsoever in respect of or in relation to or arising out all obligations and liabilities of the Borrower under this Loan Agreement.</p>\n" + 
				"    <p>&bull; JOINT COVENANTS OF THE LENDER &amp; THE BORROWER</p>\n" + 
				"    <p>&bull; The Parties do hereby covenants with each other that LenDenClub has only facilitated their virtual meeting and as such the LenDenClub is not obliged under this Loan Agreement. The Loan Agreement has been executed on a principal to principal basis between the two Parties. However, it is clarified that in case if the need so arises, any of the Parties may approach the LenDenClub and duly authorize the LenDenClub in writing to take necessary steps for the full and faithful compliance of this Loan Agreement by the other party and in such an event, the defaulting party shall not be entitled to take any objections in respect thereof.<br /> &bull; That the information, representations and warranties furnished by the Parties from time to time on the website of LenDenClub and as mentioned in this Loan Agreement are true and correct and as such no part of the information, representations and warranties furnished by the Parties are incorrect.<br /> &bull; That the Parties have read and understood all the terms and conditions, privacy policy and other material available on the website of LenDenClub and do hereby covenant and undertake to unconditionally abide by the same, without raising any defense of whatsoever nature in respect thereof.</p>\n" + 
				"    <p><br />17. INDEMNITY<br />Without prejudice to and in addition to other provisions contained in the Financing Documents, the Borrower hereby agrees to indemnify the Lender/LenDenClub and its directors, officers, representatives and agents against any losses, liabilities, claims, damages or the like (including, without limitation, reasonable attorneys' fees and expenses) which may be sustained or incurred by any of them as a result of, or in connection with, or arising out of:<br /> &bull; the Borrower failing to comply with the provisions of any Financing Documents and applicable Laws; and / or the occurrence of any Event of Default; and / or<br /> &bull; levy by any Government Authority of any charge, Taxes or penalty in connection with regularising or perfecting any of the Financing Documents as may be required under applicable Law at any time during the currency of the Facility, or getting any of the Financing Documents admitted into evidence, or relying on any Financing Documents for proving any claim; and/or<br /> &bull; the exercise of any of the rights by the Lender under this Agreement and any of the Financing Documents; and/or<br /> &bull; any of the representations and warranties of the Borrower under the Financing Documents are found to be false or untrue or incorrect on a future date.</p>\n" + 
				"    <p>page 5.</p>\n" + 
				"    <p>18. PROVISIONS REGARDING TERMINATION/CANCELLATION OF THIS LOAN AGREEMENT<br />Notwithstanding anything contained in this Loan Agreement, the Lender may at his/her option and without necessity of any demand or notice to the Borrower, all of which are hereby expressly waived off by the Borrower, terminate this Loan Agreement upon happening of any of the following events and thereupon all the amounts due and outstanding by the Borrower to the Lender on such day shall at once become due and payable by the Borrower to the Lender, irrespective of any agreed maturity date:<br /> &bull; If the Borrower makes default in payment of 60 installments, due and payable to the Lender;<br /> &bull; If any event or circumstances has occurred or may arise which is prejudicial to or impairs or imperils or depreciates or jeopardizes or is likely to prejudice, imperil or depreciate or jeopardize any of the rights of the Lender arising under this Loan Agreement;<br /> &bull; If any information furnished or representations made by the Borrower is found to be false, incorrect or incomplete in material particulars; and<br /> &bull; If the Borrower is continuously, for a period of 15 days not traceable and/or not responding to the communications made by the Lender and/or the LenDenClub.</p>\n" +
				"    <p>19. ALTERATION OR MODIFICATION</p>\n" + 
				"    <p><br />The Parties shall be at liberty to mutually amend or alter or modify any of the terms and conditions of this Loan Agreement and in particular to defer, postpone or revise the repayment of the Loan Amount, Interest, Penal Interest and Delay Charges and/or any other monies which may become due and payable by the Borrower to the Lender, including any increase or decrease in the rate of interest. However, it is clarified that all such amendment, alteration or modification must be writing and duly signed by both the Parties.</p>\n" + 
				"    <p>20. WAIVER</p>\n" + 
				"    <p>Any forbearance or failure or delay by the Lender in exercising any right, power or remedy hereunder shall not be deemed to be waiver of such right, power or remedy and any single or partial exercise of any right, power or remedy hereunder shall not preclude the further exercise thereof and every right, power and remedy of the Lender shall continue to be in full force and effect until such right, power and remedy is specifically waived by an instrument in writing executed by the Lender.</p>\n" + 
				"    <p>21. SEVERABILITY</p>\n" + 
				"    <p>If any provision or any part thereof of this Loan Agreement is determined to be illegal, invalid or unenforceable for any reason, such illegality, invalidity or unenforceability shall attach only to such provision or the applicable part of such provision and the remaining part of such provision and all other provisions of this Loan Agreement shall continue to remain in full force and effect.</p>\n" + 
				"    <p>22. GOVERNING LAW/JURISDICTION</p>\n" + 
				"    <p>This Loan Agreement shall be governed by and construed in accordance with the Laws of Maharashtra, India and any dispute between the Parties relating to or arising out of this Loan Agreement shall be subject to the exclusive jurisdiction of Courts at Mumbai, India.</p>\n" + 
				"    <p>23. ENTIRE UNDERSTANDING</p>\n" + 
				"    <p>This Loan Agreement constitutes the whole Agreement between the Parties and supersedes any previous arrangement between the Parties in relation to the matters dealt with in this Loan Agreement, provided that this clause shall not exclude any liability for (or remedy in respect of) fraudulent misrepresentation.</p>\n" + 
				"    <p>24. SURVIVAL</p>\n" + 
				"    <p>The Clauses of this Agreement which by their nature survive termination shall survive the expiry or termination of this Agreement.</p>\n" + 
				"    <p>25. TIME IS THE ESSENCE</p>\n" + 
				"    <p>The Parties hereby agree that time is the essence with respect to all dates and periods mentioned in this Loan Agreement (as may be modified/extended wherever permitted under this Loan Agreement), for performance of their respective obligations under this Loan Agreement.</p>\n" + 
				"    <p>26. NOTICES</p>\n" + 
				"    <p>Any notice or demand to be given under this Loan Agreement shall be in writing; and shall be deemed to have been duly given if sent by email or by a courier service or registered A. D. or personally delivered. Each notice or demand shall be addressed to the other Parties at the address mentioned above and a notice or demand so given or made shall be deemed to be given or made on the day it was so left or; as the case may be, two business days following date on which it was so posted and shall be effectual notwithstanding that the same may be returned undelivered and notwithstanding the Borrower's change of address.</p>\n" + 
				"    <p>THE PARTIES HERETO HAVE EXECUTED THESE PRESENTS ON THE DAY, MONTH AND YEAR FIRST HEREINABOVE WRITTEN. ELECTRONICALLY SIGNED by,</p>\n" + 
				"    <p>Borrower: "+detail.getOrDefault("Name of the Borrower", "")+"</p>\n" +
				"    <p>Date: "+DateTimeUtil.getDate(new Date())+"</p>\n" +
				"    <p>Location: "+detail.getOrDefault("Location", "")+"</p>\n" +
				"    <p>IP: "+detail.getOrDefault("IP Address", "")+"</p>\n" +
				"    <p>Lender(s): "+detail.getOrDefault("Lender", "")+"</p>\n" +
				"    <p>LenderGroup ID:</p>\n" + 
				"    <p>Date: "+DateTimeUtil.getDate(new Date())+"</p>\n" + 
				"    <p>Agreed by LenDenClub on behalf of Lender(s) based on Electronic Authorization given by Lender(s)</p>\n" + 
				"    <p>LenDenClub: Innofin Solutions Pvt. Ltd</p>\n" + 
				"    <p><br />Date: "+DateTimeUtil.getDate(new Date())+"</p>\n" + 
				"    <p>Annexure 1 : Charges applicable to user</p>\n" + 
				"    <p><br />Details of the charges applicable in case "+detail.getOrDefault("Name of the Borrower", "")+" would be as following:</p>\n" +
				"    <p>Type of Charges: Applicable charge, Delay Charges: 0%, Penal Interest Charge: 0%</p>\n" +
				"    <p><br />Applicable Charges will be divided among Lenders and the LenDenClub.</p>\n" + 
				"    <p>In addition to the above charges, Borrower will also be liable to pay visiting charges to the LenDenClub if any of the LenDenClub representative visits Borrower's home or office to collect EMI or when Borrower is not contactable through his registered email address or mobile number.</p>\n" + 
				"    <p>&nbsp;</p>\n" + 
				"    <p>Charges conveyed &amp; accepted by:</p>\n" + 
				"    <p>&nbsp;</p>\n" + 
				"    <p>"+detail.getOrDefault("Name of the Borrower", "")+"</p>\n" + 
				"    <p>"+DateTimeUtil.getDate(new Date())+"</p>";
		
		return html;
	}

	public void publishKafka(CreateTxnRequestDTO requestDTO, Long merchantId) {
		try {
			logger.info("Publishing to kafka:{}", requestDTO);
			Map<String, Object> payloadMap =new HashMap<>();
			payloadMap.put("merchant_id", merchantId);
			payloadMap.put("amount", requestDTO.getAmount());
			payloadMap.put("order_id", requestDTO.getOrderId());
			kafkaTemplate.send("lending_pull_payment", merchantId.toString(), payloadMap);
		}
		catch(Exception e){
			logger.error("Error publishing to kafka ", e);
		}
	}

	public ResponseDTO fosLoan(Long merchantId) {
		ResponseDTO responseDTO = new ResponseDTO(true, null, null);
		Map<String,Object> data= new HashMap<>();
		data.put("rejected",Boolean.FALSE);
		data.put("merchantId",merchantId.toString());
		data.put("activeLoan",Boolean.FALSE);
		data.put("eligible",Boolean.FALSE);
		data.put("experian",Boolean.TRUE);
		data.put("applicationPending",Boolean.FALSE);
		try{
			Experian experian = experianDao.getByMerchantId(merchantId);
			if(experian == null){
				data.put("message","Merchant Experian Not Pulled");
				data.put("experian",Boolean.FALSE);
				responseDTO.setData(data);
				return  responseDTO;
			}
			String reason = experian.getReason();
			if("ENACH".equalsIgnoreCase(reason)){
				reason = "Merchant's Bank A/C does Not Allow Enach.";
			}else if("OGL".equalsIgnoreCase(reason)){
				reason = "PIN Code ares does Not Serviceable right now.";
			}else if("LOW_TPV".equalsIgnoreCase(reason)){
				reason = "Transact More to become Eligble Soon.";
			}

			if(experian.getRejected()){
				data.put("message",reason);
				data.put("rejected",Boolean.TRUE);
				data.put("eligible",Boolean.FALSE);
				responseDTO.setData(data);
				return responseDTO;
			}

			if(experian.getReason() != null){
				data.put("message",reason);
				data.put("rejected",Boolean.TRUE);
				responseDTO.setData(data);
				return  responseDTO;
			}
			EligibleLoan eligibleLoan=eligibleLoanDao.findTop1ByMerchantIdOrderByIdDesc(merchantId);
			LendingApplication lendingApplication=lendingApplicationDao.findBymerchantId(merchantId);
			LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.findByMerchantIdAndStatus(merchantId,"ACTIVE");
			logger.info("Payment Schedule:{}",lendingPaymentSchedule);
			if(lendingPaymentSchedule != null){
				data.put("message","Merchant Has a Active Loan.");
				data.put("activeLoan",Boolean.TRUE);
				responseDTO.setData(data);
				return responseDTO;
			}
			if(eligibleLoan == null){
				data.put("message","Merchant Not Eligible For Loan.");
				data.put("eligible",Boolean.FALSE);
				responseDTO.setData(data);
				return responseDTO;
			}
			if(lendingApplication == null){
				data.put("message","Merchant is Eligible For Loan.");
				data.put("eligible",Boolean.TRUE);
				responseDTO.setData(data);
				return responseDTO;
			}else{
				data.put("applicationPending",Boolean.TRUE);
				data.put("applicationRejected",Boolean.FALSE);
				data.put("eligible",Boolean.TRUE);
				data.put("nachRequired",Boolean.FALSE);
				data.put("created_at",lendingApplication.getCreatedAt().toString());
				data.put("loanType",lendingApplication.getLoanType());
				data.put("loanAmount",lendingApplication.getLoanAmount());
				data.put("loanId",lendingApplication.getExternalLoanId());
				data.put("nachStatus", "APPROVED".equals(lendingApplication.getNachStatus()) ? "APPROVED" : "PENDING");
				String loanType = lendingApplication.getLoanType();

				if("draft".equals(lendingApplication.getStatus())){
					data.put("message","Application Is Draft Mode.");
					responseDTO.setData(data);
					return  responseDTO;
				}

				if("approved".equals(lendingApplication.getStatus())){
					data.put("message","Merchant Application Is Approved State.");
					data.put("agreement_at",lendingApplication.getAgreementAt().toString());
					responseDTO.setData(data);
					return  responseDTO;
				}

				if("pending_verification".equals(lendingApplication.getStatus())){
					data.put("message","Merchant Loan Application Is Pending Verification State.");
					data.put("agreement_at",lendingApplication.getAgreementAt().toString());
					if(("NTB".equals(loanType) || "OGL".equals(loanType) || "BHARAT_SWIPE".equals(loanType)) && !"APPROVED".equals(lendingApplication.getNachStatus())){
						data.put("message","Please Complete Enach For Further Process Application.");
						data.put("nachRequired",Boolean.TRUE);
					}
					responseDTO.setData(data);
					return  responseDTO;
				}else{
					data.put("message","Merchant Loan Application Is Rejected State.");
					data.put("applicationPending", Boolean.FALSE);
					data.put("applicationRejected",Boolean.TRUE);
					responseDTO.setData(data);
					return  responseDTO;
				}
			}
		}catch(Exception ex){
			logger.error("Error Fos Loan Details API", ex);
			return responseDTO;
		}
	}

	public ResponseDTO applicationStatus(Merchant merchant,RequestDTO<ApplicationStatusRequestDTO> requestDTO,String clientIp, String token) {
		Long application_id = requestDTO.getPayload().getApplicationId();
		logger.info("Appplication Id :{}", application_id);
		if (application_id == null) {
			logger.info("Application id is null for merchant:{}", merchant.getId());
			return new ResponseDTO(Boolean.FALSE, "Application not found");
		}
		Optional<LendingApplication> lendingApplication = lendingApplicationDao.findById(application_id);
		ResponseDTO responseDTO = new ResponseDTO(Boolean.TRUE, "");
		ApplicationStatusResponseDTO applicationStatusResponseDTO = new ApplicationStatusResponseDTO();
		if (!lendingApplication.isPresent()) {
			return new ResponseDTO(Boolean.FALSE, "Application not found");
		}
		if("draft".equalsIgnoreCase(lendingApplication.get().getStatus()) || "deleted".equalsIgnoreCase(lendingApplication.get().getStatus())){
			return new ResponseDTO(Boolean.FALSE, "Application not in pending state");
		}
		BpEnach successEnach = bpEnachDao.findSuccessEnach(merchant.getId());
		List<ApplicationDTO> applicationDTO = new ArrayList<>();
		ApplicationStatusResponseDTO.ApplicationLoanDetailsDTO applicationLoanDetailsDTO = new ApplicationStatusResponseDTO.ApplicationLoanDetailsDTO();
		applicationLoanDetailsDTO.setAmount(lendingApplication.get().getLoanAmount());
		applicationLoanDetailsDTO.setFailedMsg("");
		applicationLoanDetailsDTO.setOrderID(lendingApplication.get().getExternalLoanId());
		applicationLoanDetailsDTO.setTransferDays("8 Days");
		applicationLoanDetailsDTO.setStatus(lendingApplication.get().getStatus());
		logger.info("get Agreement:{}", lendingApplication.get().getAgreement());
		if ("1".equals(String.valueOf(lendingApplication.get().getAgreement()))) {
			ApplicationDTO applicationDTO1 = new ApplicationDTO();
			applicationDTO1.setStatus("APPROVED");
			applicationDTO1.setText("Application Submitted");
			ApplicationDTO.DateDTO dateDTO = new ApplicationDTO.DateDTO();
			dateDTO.setDay(lendingApplication.get().getAgreementAt().toString());
			dateDTO.setTime(lendingApplication.get().getAgreementAt().toString());
			applicationDTO1.setDateDTO(dateDTO);
			applicationDTO.add(applicationDTO1);
		}

		ApplicationDTO applicationDTO2 = new ApplicationDTO();
		if (successEnach != null) {
			applicationDTO2.setStatus(successEnach.getStatus());
			applicationDTO2.setText("eNACH Done");
			applicationDTO2.setButtonContextDTO(null);
			ApplicationDTO.DateDTO dateDTO = new ApplicationDTO.DateDTO();
			dateDTO.setDay(successEnach.getCreatedAt().toString());
			dateDTO.setTime(successEnach.getCreatedAt().toString());
			applicationDTO2.setDateDTO(dateDTO);
			applicationDTO.add(applicationDTO2);
		} else if ("pending_verification".equalsIgnoreCase(lendingApplication.get().getStatus())){
			applicationDTO2.setStatus("PENDING");
			applicationDTO2.setText("eNACH Pending");
			applicationDTO2.setComment("Register eNACH for Instant Loan Approval. Get Rs100 cashback");
			ApplicationDTO.ButtonContextDTO buttonContextDTO = new ApplicationDTO.ButtonContextDTO();
			buttonContextDTO.setAction("Enach");
			buttonContextDTO.setText("Do eNACH");
			buttonContextDTO.setDeeplink(apiGatewayService.getEnachProvider(token, merchant.getId()));
			applicationDTO2.setButtonContextDTO(buttonContextDTO);
			applicationDTO.add(applicationDTO2);
		}
		boolean enachMandatory = true;
		if (successEnach != null) {
			enachMandatory = false;
		} else if (lendingApplication.get().getAgreementAt() != null && "REGULAR".equals(lendingApplication.get().getLoanType()) && lendingApplication.get().getLoanAmount() > 50000 && LoanUtil.getDateDiffInDays(lendingApplication.get().getAgreementAt(), new Date()) > 3) {
			enachMandatory = false;
		} else if ("BHARAT_SWIPE".equals(lendingApplication.get().getLoanType())) {
			enachMandatory = false;
		}
		String kycStatus = lendingApplication.get().getManualKyc() != null && (lendingApplication.get().getManualKyc().equalsIgnoreCase("APPROVED") || lendingApplication.get().getManualKyc().equalsIgnoreCase("REJECTED")) ? lendingApplication.get().getManualKyc() : "PENDING";
		String kycComment = null;
		if (lendingApplication.get().getManualKycReason() != null) {
			kycComment = lendingApplication.get().getManualKycReason();
		} else if ("PENDING".equalsIgnoreCase(kycStatus)) {
			kycComment = "(We're verifying documents submitted by you)";
		}
		if ("REJECTED".equalsIgnoreCase(lendingApplication.get().getManualCibil())) {
			kycStatus = "REJECTED";
			kycComment = lendingApplication.get().getManualCibilReason();
		}
		ApplicationDTO applicationDTO3 = new ApplicationDTO();
		applicationDTO3.setText("Document Verification");
		applicationDTO3.setDisabled(enachMandatory);
		if (kycStatus.equalsIgnoreCase("APPROVED") || kycStatus.equalsIgnoreCase("REJECTED")) {
			applicationDTO3.setDisabled(false);
		}
		applicationDTO3.setStatus(kycStatus);
//		applicationDTO3.setComment(kycComment);
		if (lendingApplication.get().getManualKyc() != null && !"null".equalsIgnoreCase(lendingApplication.get().getManualKyc()) && lendingApplication.get().getKycApprovedDate() != null) {
			ApplicationDTO.DateDTO dateDTO = new ApplicationDTO.DateDTO();
			dateDTO.setDay(lendingApplication.get().getKycApprovedDate().toString());
			dateDTO.setTime(lendingApplication.get().getKycApprovedDate().toString());
			applicationDTO3.setDateDTO(dateDTO);
		} else if ("REJECTED".equalsIgnoreCase(lendingApplication.get().getManualCibil()) && lendingApplication.get().getCibilApprovedDate() != null) {
			ApplicationDTO.DateDTO dateDTO = new ApplicationDTO.DateDTO();
			dateDTO.setDay(lendingApplication.get().getCibilApprovedDate().toString());
			dateDTO.setTime(lendingApplication.get().getCibilApprovedDate().toString());
			applicationDTO3.setDateDTO(dateDTO);
		}
		applicationDTO.add(applicationDTO3);
		boolean cpvRequired = !canSkipCpv(merchant.getId(), lendingApplication.get().getLoanAmount(), lendingApplication.get().getLoanType(), successEnach);
		LendingDisbursalStage lendingDisbursalStage = lendingDisbursalStageDao.findByApplicationId(application_id);
		if ((cpvRequired && !"REJECTED".equalsIgnoreCase(kycStatus)) || "REJECTED".equalsIgnoreCase(lendingApplication.get().getPhysicalVerificationStatus())) {
			String cpvComment;
			if (lendingApplication.get().getPhysicalVerificationStatus() == null || lendingApplication.get().getPhysicalVerificationStatus().equalsIgnoreCase("null") || lendingApplication.get().getPhysicalVerificationStatus().equalsIgnoreCase("ASSIGNED")) {
				cpvComment = "(Our agent will be visiting your shop in the next 3-4 days to verify & collect documents)";
			} else if (lendingApplication.get().getPhysicalVerificationStatus() != null && !lendingApplication.get().getPhysicalVerificationStatus().equalsIgnoreCase("APPROVED") && !lendingApplication.get().getPhysicalVerificationStatus().equalsIgnoreCase("REJECTED")) {
				cpvComment = "(Documents collected from your shop by our agent are being verified by us)";
			} else {
				cpvComment = lendingApplication.get().getPhysicalReason();
			}
			ApplicationDTO applicationDTO4 = new ApplicationDTO();
			applicationDTO4.setStatus(lendingApplication.get().getPhysicalVerificationStatus() != null && (lendingApplication.get().getPhysicalVerificationStatus().equalsIgnoreCase("APPROVED") || lendingApplication.get().getPhysicalVerificationStatus().equalsIgnoreCase("REJECTED")) ? lendingApplication.get().getPhysicalVerificationStatus() : "PENDING");
//			applicationDTO4.setComment(cpvComment);
			applicationDTO4.setText("Physical verification");
			applicationDTO4.setDisabled(!"APPROVED".equalsIgnoreCase(kycStatus));
			if (lendingApplication.get().getPhysicalVerificationStatus() != null && !"null".equalsIgnoreCase(lendingApplication.get().getPhysicalVerificationStatus()) && lendingApplication.get().getPhysicalApprovedDate() != null) {
				ApplicationDTO.DateDTO dateDTO = new ApplicationDTO.DateDTO();
				dateDTO.setTime(lendingApplication.get().getPhysicalApprovedDate().toString());
				dateDTO.setDay(lendingApplication.get().getPhysicalApprovedDate().toString());
				applicationDTO4.setDateDTO(dateDTO);
			}
			applicationDTO.add(applicationDTO4);
		}
		String applicationStatus = lendingApplication.get().getStatus();
		if ("NTB".equalsIgnoreCase(lendingApplication.get().getLoanType()) && (!"rejected".equalsIgnoreCase(lendingApplication.get().getStatus()) || lendingDisbursalStage != null)) {
			ApplicationDTO applicationDTO5 = new ApplicationDTO();
			applicationDTO5.setDisabled(!"approved".equalsIgnoreCase(lendingApplication.get().getStatus()));
			applicationDTO5.setText("Disbursal Review & Calling");
			if (lendingDisbursalStage != null) {
				String callingStatus;
				if ("YES".equalsIgnoreCase(lendingDisbursalStage.getCallStage())) {
					callingStatus = "APPROVED";
				} else if ("NO".equalsIgnoreCase(lendingDisbursalStage.getReadyStage())) {
					callingStatus = "REJECTED";
//					applicationDTO5.setComment("Credit Review failed");
				} else if ("NO".equalsIgnoreCase(lendingDisbursalStage.getCallStage())) {
					callingStatus = "REJECTED";
//					applicationDTO5.setComment("Call not picked");
				} else if ("rejected".equalsIgnoreCase(lendingApplication.get().getStatus())) {
					callingStatus = "REJECTED";
				} else {
					callingStatus = "PENDING";
					applicationStatus = "PENDING";
				}
				ApplicationDTO.DateDTO dateDTO = new ApplicationDTO.DateDTO();
				dateDTO.setDay(lendingDisbursalStage.getCallTimestamp());
				dateDTO.setTime(lendingDisbursalStage.getCallTimestamp());
				applicationDTO5.setDateDTO(dateDTO);
				applicationDTO5.setStatus(callingStatus);
				applicationDTO5.setDisabled(Boolean.FALSE);
			} else if ("approved".equalsIgnoreCase(lendingApplication.get().getStatus())) {
				applicationDTO5.setStatus("PENDING");
				applicationStatus = "PENDING";
			}
			applicationLoanDetailsDTO.setStatus(applicationDTO5.getStatus());
			applicationDTO.add(applicationDTO5);
		}

		if (!"rejected".equalsIgnoreCase(lendingApplication.get().getStatus())) {
			ApplicationDTO applicationDTO6 = new ApplicationDTO();
			applicationDTO6.setDisabled(!applicationStatus.equalsIgnoreCase("approved"));
			applicationDTO6.setText("Disbursal!");
			applicationDTO.add(applicationDTO6);
			if (!applicationDTO6.isDisabled()) {
				applicationDTO6.setStatus("PENDING");
			}
		}
		applicationLoanDetailsDTO.setStatus(applicationStatus);
		ApplicationStatusResponseDTO.HeaderDTO headerDTO = new ApplicationStatusResponseDTO.HeaderDTO();
		if (applicationLoanDetailsDTO.getStatus().equalsIgnoreCase("REJECTED")) {
			headerDTO.setTitle("Loan Verification Failed");
			headerDTO.setComment("Loan has not been approved");
		} else if (applicationLoanDetailsDTO.getStatus().equalsIgnoreCase("APPROVED")) {
			headerDTO.setTitle("Congratulations");
			headerDTO.setComment("Application is Approved & Pending Disbursal");
		} else {
			headerDTO.setTitle("Congratulations");
			headerDTO.setComment("Application Submitted & Pending Verification");
		}
		applicationStatusResponseDTO.setApplicationLoanDetailsDTO(applicationLoanDetailsDTO);
		applicationStatusResponseDTO.setHeader(headerDTO);
		applicationStatusResponseDTO.setApplicationDTOList(applicationDTO);
		responseDTO.setData(applicationStatusResponseDTO);
		return responseDTO;
	}

	public boolean canSkipCpv(Long merchantId, Double amount, String loanType, BpEnach lendingEnach) {

		if(lendingEnach != null && lendingEnach.getInternalNachType().equalsIgnoreCase("ENACH")) {
			if ("BHARAT_SWIPE".equals(loanType)) {
				return true;
			}
			boolean isNTC = isNTC(merchantId);
			if(isFirstLoan(merchantId)) {
				if(isNTC && amount<=50000D) {
					return true;
				} else if(!isNTC && amount<=100000D) {//etc
					return true;
				}
			}
			else return amount <= 300000D;
		}
		return false;
	}

	public boolean isFirstLoan(Long merchantId) {
		List<LendingPaymentSchedule> prevLoans = lendingPaymentScheduleDao.findPreviousLoansByMerchantAndCreditLoan(merchantId, false);
		return prevLoans.isEmpty();
	}

	public boolean isNTC(Long merchantId) {
		Experian experian = experianDao.getByMerchantId(merchantId);
		if (experian == null || experian.getCategory() == null) {
			return true;
		}
		if (experian.getReason() != null && experian.getReason().equalsIgnoreCase("ZOMATO_ETC")) {
			return false;
		}
		List<String> ntcCategories = Arrays.asList("1N","2N","3N","4N");
		return ntcCategories.contains(experian.getCategory());
	}
}