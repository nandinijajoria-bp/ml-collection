package com.bharatpe.lending.service;

import java.io.FileNotFoundException;
import java.time.LocalDateTime;
import java.util.*;

import com.bharatpe.common.entities.*;
import com.bharatpe.lending.common.Handler.PhonebookHandler;
import com.bharatpe.lending.common.bpnewmaster.dao.DocKycDetailsDaoMaster;
import com.bharatpe.lending.common.bpnewmaster.dao.DocumentsIdProofDaoMaster;
import com.bharatpe.lending.common.bpnewmaster.entity.DocKycDetailsMaster;
import com.bharatpe.lending.common.bpnewmaster.entity.DocumentsIdProofMaster;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.dao.LendingEkycDao;
import com.bharatpe.lending.common.dao.LendingResubmitTaskDao;
import com.bharatpe.lending.common.dao.LendingRiskVariablesSnapshotDao;
import com.bharatpe.lending.common.dao.LendingShopDocumentsDao;
import com.bharatpe.lending.common.dto.PhonebookDTO;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.entity.LendingEkyc;
import com.bharatpe.lending.common.entity.LendingResubmitTask;
import com.bharatpe.lending.common.entity.LendingRiskVariablesSnapshot;
import com.bharatpe.lending.common.entity.LendingShopDocuments;
import com.bharatpe.lending.common.enums.FunnelEnums;
import com.bharatpe.lending.common.enums.RiskSegment;
import com.bharatpe.lending.common.service.FunnelService;
import com.bharatpe.lending.common.service.merchant.dto.BankDetailsDto;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.handlers.DsHandler;
import com.bharatpe.lending.loanV3.revamp.constants.LoanDetailsConstant;
import com.bharatpe.lending.loanV3.revamp.response.LoanDashboardApiVersion;
import com.bharatpe.lending.loanV3.revamp.services.LoanDashboardService;
import com.bharatpe.lending.util.LoanUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.bharatpe.common.objects.CommonAPIRequest;
import com.bharatpe.lending.handlers.S3BucketHandler;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

@Service
public class ImageURLService {
	Logger logger = LoggerFactory.getLogger(ImageURLService.class);
	
	@Autowired
	DocumentsIdProofDaoMaster documentsIdProofDaoMaster;

	@Autowired
	LendingShopDocumentsDao lendingShopDocumentsDao;

	@Autowired
	LendingApplicationDao lendingApplicationDao;

	@Autowired
	S3BucketHandler s3BucketHandler;
	
	@Autowired
	LendingEkycDao lendingEkycDao;

	@Autowired
	LendingResubmitTaskDao lendingResubmitTaskDao;

	@Autowired
	PhonebookHandler phonebookHandler;

	@Autowired
	RedisNotificationService redisNotificationService;

	@Autowired
	DocKycDetailsDaoMaster docKycDetailsDaoMaster;

	@Autowired
	MerchantService merchantService;

	@Autowired
	DsHandler dsHandler;

	@Autowired
	LoanUtil loanUtil;

	@Autowired
	LendingRiskVariablesSnapshotDao lendingRiskVariablesSnapshotDao;

	@Autowired
	LendingApplicationDetailsDao lendingApplicationDetailsDao;

	@Autowired
	FunnelService funnelService;

	@Autowired
	EasyLoanUtil easyLoanUtil;

	@Autowired
	private LoanDashboardService loanDashboardService;

	@Value("${aws.s3.bucket}")
	private String bucket;

	@Value("${sid.threshold}")
	Double sidThreshold;

	@Value("${sid.rollout.percent}")
	Integer sidRolloutPercent;

	public Map<String, Object> fetchAndWrapResult(BasicDetailsDto merchant, CommonAPIRequest commonAPIRequest) {
		Map<String, Object> result = new HashMap<String, Object>();
		Map<String, String> panNameCheck = new HashMap<>();
		Long applicationId =  commonAPIRequest.getPayload().get("application_id") != null ? Long.parseLong(commonAPIRequest.getPayload().get("application_id").toString()) : null;
		if(applicationId == null || applicationId <= 0) {
			logger.info("Invalid Application Id: {} for merchant : {}", applicationId, merchant.getId());
			result.put("success", false);
			return result;
		}
		LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchantIdAndStatus(applicationId,
		merchant.getId(), "draft");
		LendingResubmitTask lendingResubmitTask =lendingResubmitTaskDao.findTopByApplicationId(applicationId);
		if(lendingApplication == null  && (Objects.isNull(lendingResubmitTask) || lendingResubmitTask.getResubmitDone())) {
			logger.info("Application not found for Id: {} for merchant : {}", applicationId, merchant.getId());
			result.put("success", false);
			return result;
		}
		if(lendingApplication == null && Objects.nonNull(lendingResubmitTask) && lendingResubmitTask.getResubmit() && (lendingResubmitTask.getResubmitDone() == null || !lendingResubmitTask.getResubmitDone())){
			lendingApplication =lendingApplicationDao.findById(applicationId).get();
		}

		logger.info("Application: {}", lendingApplication);

		panNameCheck = getBanificaryAndPanName(merchant, lendingApplication);
		result.put("panNameCheck", panNameCheck);

		LoanDashboardApiVersion loanDashboardApiVersion = loanDashboardService.getLoanDashboardApiVersion(merchant.getId());

		Boolean ekycDone = false;
		if(!LoanDetailsConstant.VERSION_V2.equalsIgnoreCase(loanDashboardApiVersion.getApiVersion())){
			ekycDone=isEkycDone(merchant, lendingApplication.getId());
			if(ekycDone==null){
				result.put("success", false);
				return result;
			}
		}

		boolean finalCall = commonAPIRequest.getPayload().get("finalCall") != null && (boolean) commonAPIRequest.getPayload().get("finalCall");
		if (finalCall) {
			List<PhonebookDTO> phonebook = phonebookHandler.getPhonebook(merchant.getId());
			if (phonebook.isEmpty()) {
				logger.info("Contacts not synced for merchant:{}", merchant.getId());
				result.put("success", false);
				result.put("message", "CONTACTS_NOT_SYNCED");
				return result;
			}
		}
		result.put("isEKYC",ekycDone);
		result.put("allow_route", allowRoute(lendingApplication, merchant, ekycDone));
		List<Map<String, Object>> data = fetchImageUrl(merchant, lendingApplication, commonAPIRequest);
		result.put("proofs", data);
		result.put("success", true);
		result.put("qrMandatory", !lendingApplication.getLoanType().equals("NTB"));
		if (finalCall) {
			redisNotificationService.sendNotificationForAppliedApplication(merchant.getId(), lendingApplication);
		}
		return result;
	}

	public Map<String, String> getBanificaryAndPanName(BasicDetailsDto merchant, LendingApplication lendingApplication){
		Map<String, String> result = new HashMap<>();
		DocumentsIdProofMaster documentsIdProof = documentsIdProofDaoMaster.findByMerchantIdApplicationIdAndProofType(merchant.getId(), lendingApplication.getId(), "pancard");
		if(Objects.nonNull(documentsIdProof) && Objects.nonNull(documentsIdProof.getPanNameMatch()) && !documentsIdProof.getPanNameMatch().isEmpty() && documentsIdProof.getPanNameMatch().equals("NO")){
			DocKycDetailsMaster docKycDetails = docKycDetailsDaoMaster.fetchLatestPanCardDetails(merchant.getId(), lendingApplication.getId());

			final Optional<BankDetailsDto> bankDetailsDtoOptional = merchantService.fetchMerchantBankDetails(merchant.getId());
			BankDetailsDto merchantBankDetail = null;
			if (bankDetailsDtoOptional.isPresent())
				merchantBankDetail = bankDetailsDtoOptional.get();

			String benificiaryName =  merchantBankDetail!= null ? (merchantBankDetail.getBeneficiaryName()!= null ? merchantBankDetail.getBeneficiaryName() : "") : "";
			String nameInPan = docKycDetails != null && Objects.nonNull(docKycDetails.getPersonName()) ? docKycDetails.getPersonName() : "";

			if(nameInPan.isEmpty()){
				return null;
			}

			result.put("benificiaryName", benificiaryName);
			result.put("nameInPan", nameInPan);

			return result;
		}

		return null;
	}

	private boolean allowRoute(LendingApplication lendingApplication, BasicDetailsDto merchant, Boolean isEkycDone) {
		boolean selfie = false;
		boolean pancard = false;
		boolean poa = false;

		List<DocumentsIdProofMaster> documentsIdProofList =
		documentsIdProofDaoMaster.findByMerchantIdAndLendingApplicationId(merchant.getId(), lendingApplication.getId());
		for (DocumentsIdProofMaster documentsIdProof : documentsIdProofList) {
			if (documentsIdProof.getProofType().equalsIgnoreCase("selfie")) {
				selfie = true;
			} else if (documentsIdProof.getProofType().equalsIgnoreCase("pancard")) {
				pancard = true;
			} else {
				poa = true;
			}
		}
		return selfie && pancard && (isEkycDone || poa);
	}
	
	public Boolean isEkycDone(BasicDetailsDto merchant, Long applicationId) {
		try{
			LendingEkyc lendingEkyc = lendingEkycDao.findSuccessEkyc(merchant.getId(), applicationId);
			DocumentsIdProofMaster ekycDoc = documentsIdProofDaoMaster.findByMerchantIdApplicationIdAndProofType(merchant.getId(), applicationId, "eAadhar");
			return lendingEkyc != null && ekycDoc != null;
		}
		catch(Exception e) {
			logger.error("Error occured while checking for ekyc status",e);
			return false;
		}
	}

	public List<Map<String, Object>> fetchImageUrl(BasicDetailsDto merchant, LendingApplication lendingApplication, CommonAPIRequest commonAPIRequest) {
		List<Map<String, Object>> finalResponse = new ArrayList<>();
		List<DocumentsIdProofMaster> documentsIdProofList = documentsIdProofDaoMaster.findByMerchantAndLendingApplication(merchant.getId(), lendingApplication.getId());
		List<LendingShopDocuments> lendingShopDocumentsList  = lendingShopDocumentsDao.findByMerchantIdAndApplicationId(merchant.getId(), lendingApplication.getId());
		LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = lendingRiskVariablesSnapshotDao.findByApplicationId(lendingApplication.getId());
		String shopDocType = ObjectUtils.isEmpty(commonAPIRequest.getPayload().get("shop_doc_type")) ? null : commonAPIRequest.getPayload().get("shop_doc_type").toString();
		for(DocumentsIdProofMaster documentsIdProof : documentsIdProofList) {
			if (documentsIdProof.getProofType().equalsIgnoreCase("eAadhar")) {
				continue;
			}
			Map<String, Object> proof = new LinkedHashMap<>();
			proof.put("proof_type",documentsIdProof.getProofType());
			proof.put("single_page_document", documentsIdProof.getSinglePage() == null || documentsIdProof.getSinglePage() == 1);

			List<String> imageURL = new ArrayList<>();
			try {
				if(StringUtils.isEmpty(documentsIdProof.getProofFrontSide())) {
					logger.error("Empty front Url for documentsIdProof: {}", documentsIdProof.getId());
					continue;
				}
				String frontURL = s3BucketHandler.getTemporaryPublicURL(documentsIdProof.getProofFrontSide(), bucket);
				imageURL.add(frontURL);

				if(!StringUtils.isEmpty(documentsIdProof.getProofBackSide())) {
					String backURL = s3BucketHandler.getTemporaryPublicURL(documentsIdProof.getProofBackSide(), bucket);
					imageURL.add(backURL);

				}
			} catch (FileNotFoundException e) {
				logger.info("ImageURLService file not found in S3 bucket for key : {}", documentsIdProof.getProofBackSide());
			} catch (Exception e) {
				logger.info("ImageURLService exception while fetching S3 bucket for key : {}, message : {}", documentsIdProof.getProofBackSide(), e.getMessage());
			}
			proof.put("proof",imageURL);
			proof.put("updated_at", documentsIdProof.getUpdatedAt());
			finalResponse.add(proof);
		}

		LoanDashboardApiVersion loanDashboardApiVersion = loanDashboardService.getLoanDashboardApiVersion(merchant.getId(), lendingApplication);

		if(!ObjectUtils.isEmpty(lendingShopDocumentsList)){

			Boolean skipDistanceCheck = false;

			LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(lendingApplication.getId());
			if (ObjectUtils.isEmpty(lendingApplicationDetails)) {
				lendingApplicationDetails = new LendingApplicationDetails();
				lendingApplicationDetails.setApplicationId(lendingApplication.getId());
				lendingApplicationDetailsDao.save(lendingApplicationDetails);
			}

			if (!ObjectUtils.isEmpty(lendingApplicationDetails.getSkipDistanceCheck())) {
				skipDistanceCheck = lendingApplicationDetails.getSkipDistanceCheck();
			} else if (ObjectUtils.isEmpty(lendingApplicationDetails.getSkipDistanceCheck()) && !ObjectUtils.isEmpty(commonAPIRequest.getPayload().get("skip_distance_check"))) {
				skipDistanceCheck = Boolean.valueOf(commonAPIRequest.getPayload().get("skip_distance_check").toString());
				lendingApplicationDetails.setSkipDistanceCheck(skipDistanceCheck);
				lendingApplicationDetailsDao.save(lendingApplicationDetails);

				if(LoanDetailsConstant.VERSION_V2.equalsIgnoreCase(loanDashboardApiVersion.getApiVersion())){
					funnelService.submitEventV3(lendingApplication.getMerchantId(), null, lendingApplication.getId(),
							FunnelEnums.StageId.SHOP_PHOTO, FunnelEnums.StageEvent.DISTANCE_CHECK_SKIPPED, String.valueOf(skipDistanceCheck), LoanDetailsConstant.FUNNEL_VERSION_TAG);
				}
				else{
					funnelService.submitEvent(lendingApplication.getMerchantId(), null, lendingApplication.getId(),
							FunnelEnums.StageId.SHOP_PHOTO, FunnelEnums.StageEvent.DISTANCE_CHECK_SKIPPED, String.valueOf(skipDistanceCheck));
				}
			}

			Double distanceBetweenShopAndInferredLocation = null;

			skipDistanceCheck = easyLoanUtil.percentScaleUp(lendingApplication.getMerchantId(), sidRolloutPercent) ? skipDistanceCheck : true;

			if (!skipDistanceCheck) {
				distanceBetweenShopAndInferredLocation = calculateDistanceBetweenInferredLocationAndShopDocumentLocation(lendingShopDocumentsList.get(0),
						merchant.getId());
			}

			for(LendingShopDocuments lendingShopDocuments : lendingShopDocumentsList){
				Map<String, Object> moreDocument = new LinkedHashMap<>();
				moreDocument.put("proof_type",lendingShopDocuments.getProofType());
				moreDocument.put("single_page_document",Boolean.TRUE);

				List<String> imageURL = new ArrayList<>();
				try {
					if(StringUtils.isEmpty(lendingShopDocuments.getProofFrontSide()) || (!StringUtils.isEmpty(shopDocType) && !shopDocType.contains(lendingShopDocuments.getProofType()))) {
						continue;
					}

					// if the distance between the inferred location and where the image is uploaded from is more than 2.5KM then don't return the images for repeat loans
					if (!skipDistanceCheck) {
						logger.info("Applying distance check for applicationId : {} where distance id : {}", lendingApplication.getId(), distanceBetweenShopAndInferredLocation);
						if (!RiskSegment.TOPUP.equals(lendingRiskVariablesSnapshot.getRiskSegment())) {
							if (distanceBetweenShopAndInferredLocation != null && distanceBetweenShopAndInferredLocation > sidThreshold){
								//removing old existing shop links.
								lendingShopDocuments.setProofFrontSide(null);
								lendingShopDocuments.setProofBackSide(null);
								lendingShopDocumentsDao.save(lendingShopDocuments);

								if(LoanDetailsConstant.VERSION_V2.equalsIgnoreCase(loanDashboardApiVersion.getApiVersion())){
									funnelService.submitEventV3(lendingApplication.getMerchantId(), null, lendingApplication.getId(),
											FunnelEnums.StageId.SHOP_PHOTO, FunnelEnums.StageEvent.OLD_PHOTO_DELETED, String.valueOf(distanceBetweenShopAndInferredLocation), LoanDetailsConstant.FUNNEL_VERSION_TAG);
								}
								else{
									funnelService.submitEvent(lendingApplication.getMerchantId(), null, lendingApplication.getId(),
											FunnelEnums.StageId.SHOP_PHOTO, FunnelEnums.StageEvent.OLD_PHOTO_DELETED, String.valueOf(distanceBetweenShopAndInferredLocation));
								}
								continue;
							}
						}
					}

					String frontURL = s3BucketHandler.getTemporaryPublicURL(lendingShopDocuments.getProofFrontSide(), bucket);
					imageURL.add(frontURL);

					if(!StringUtils.isEmpty(lendingShopDocuments.getProofBackSide())) {
						String backURL = s3BucketHandler.getTemporaryPublicURL(lendingShopDocuments.getProofBackSide(), bucket);
						imageURL.add(backURL);
					}
				} catch (FileNotFoundException e) {
					logger.info("ImageURLService file not found in S3 bucket for key : {}", lendingShopDocuments.getProofBackSide());
				} catch (Exception e) {
					logger.info("ImageURLService exception while fetching S3 bucket for key : {}, message : {}", lendingShopDocuments.getProofBackSide(), e.getMessage());
				}
				moreDocument.put("proof",imageURL);
				moreDocument.put("updated_at", lendingShopDocuments.getUpdatedAt());
				finalResponse.add(moreDocument);
			}
		}

		finalResponse.sort(Comparator.comparing(o -> ((Date) o.get("updated_at"))));
		return finalResponse;
	}

	public Double calculateDistanceBetweenInferredLocationAndShopDocumentLocation(LendingShopDocuments lendingShopDocuments, Long merchantId){
		logger.info("Calculating shop inferred distance for merchant:{}", merchantId);
		try{
			// send more than 2500 for internal merchant
			if(loanUtil.isInternalMerchant(merchantId)){
				return 3000D;
			}
			Map<String, Double> dsResponse = dsHandler.fetchDsLocation(merchantId);
			if(ObjectUtils.isEmpty(lendingShopDocuments) || ObjectUtils.isEmpty(lendingShopDocuments.getLatitude()) || ObjectUtils.isEmpty(lendingShopDocuments.getLongitude()) || ObjectUtils.isEmpty(dsResponse)
					|| !dsResponse.containsKey("latitude") || ObjectUtils.isEmpty(dsResponse.get("latitude")) || !dsResponse.containsKey("longitude") || ObjectUtils.isEmpty(dsResponse.get("longitude"))){
				return null;
			}
			Double lat1 = Double.valueOf(lendingShopDocuments.getLatitude());
			Double lon1 = Double.valueOf(lendingShopDocuments.getLongitude());
			Double lat2 = dsResponse.get("latitude");
			Double lon2 = dsResponse.get("longitude");

			Double inferredDistance = loanUtil.calculateLatLonDistance(lat1, lon1, lat2, lon2);
			logger.info("SID:{} for merchantId : {}", inferredDistance, merchantId);

			return (inferredDistance == -1D) ? null : inferredDistance;

		}catch (Exception ex){
			logger.error("Exception occurred while calculating inferred distance for merchant:{}, {}, {}", merchantId, ex.getMessage(), Arrays.asList(ex.getStackTrace()));
		}
		return null;
	}
}
