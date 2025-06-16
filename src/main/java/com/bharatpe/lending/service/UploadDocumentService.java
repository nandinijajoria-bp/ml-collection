package com.bharatpe.lending.service;

import java.time.LocalDateTime;
import java.util.*;

import com.bharatpe.common.entities.*;
import com.bharatpe.lending.common.bpnewmaster.dao.DocumentsIdProofDaoMaster;
import com.bharatpe.lending.common.bpnewmaster.entity.DocumentsIdProofMaster;
import com.bharatpe.lending.common.dao.LendingResubmitTaskDao;
import com.bharatpe.lending.common.dao.LendingRiskVariablesSnapshotDao;
import com.bharatpe.lending.common.dao.LendingShopDocumentsDao;
import com.bharatpe.lending.common.entity.LendingResubmitTask;
import com.bharatpe.lending.common.entity.LendingRiskVariablesSnapshot;
import com.bharatpe.lending.common.entity.LendingShopDocuments;
import com.bharatpe.lending.common.enums.FunnelEnums;
import com.bharatpe.lending.common.enums.RiskSegment;
import com.bharatpe.lending.common.service.FunnelService;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.dao.LendingCategoryDao;
import com.bharatpe.lending.dao.LendingGstDao;
import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.handlers.DsHandler;
import com.bharatpe.lending.handlers.S3BucketHandler;
import com.bharatpe.lending.loanV3.revamp.constants.LoanDetailsConstant;
import com.bharatpe.lending.loanV3.revamp.response.LoanDashboardApiVersion;
import com.bharatpe.lending.loanV3.revamp.services.LoanDashboardService;
import com.bharatpe.lending.util.BQPublisherUtil;
import com.bharatpe.lending.util.CommonUtil;
import com.bharatpe.lending.util.LoanUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.handlers.KarzaHandler;

@Service
public class UploadDocumentService {
	Logger logger = LoggerFactory.getLogger(UploadDocumentService.class);

	@Autowired
	DocumentsIdProofDaoMaster documentsIdProofDaoMaster;

	@Autowired
	LendingShopDocumentsDao lendingShopDocumentsDao;

//	@Autowired
//	DocAuthenticationDao docAuthenticationDao;

	@Autowired
	LendingApplicationDao lendingApplicationDao;

	@Autowired
	S3BucketHandler s3BucketHandler;

	@Autowired
	LendingResubmitTaskDao lendingResubmitTaskDao;

	@Autowired
	KarzaHandler karzaHandler;

	@Autowired
	LendingCategoryDao lendingCategoryDao;

	@Value("${aws.s3.bucket}")
	private String bucket;

//	@Autowired
//	UploadDocumentUtil uploadDocumentUtil;

	@Autowired
	APIGatewayService apiGatewayService;

	@Autowired
	LendingGstDao lendingGstDao;

	@Autowired
	BQPublisherUtil bqPublisherUtil;

	@Autowired
	FunnelService funnelService;

	@Autowired
	LendingRiskVariablesSnapshotDao lendingRiskVariablesSnapshotDao;

	@Autowired
	LoanUtil loanUtil;

	@Autowired
	DsHandler dsHandler;

	@Autowired
	EasyLoanUtil easyLoanUtil;

	@Autowired
	private LoanDashboardService loanDashboardService;

	@Autowired
	private CommonUtil commonUtil;

	@Value("${sid.threshold}")
	Double sidThreshold;

	@Value("${sid.rollout.percent}")
	Integer sidRolloutPercent;

//	@Autowired
//	MerchantDao merchantDao;

	public UploadDocumentResponseDTO uploadDocument(BasicDetailsDto merchant, RequestDTO<UploadDocumentRequestDTO> requestDTO, boolean notS3Upload)  {
		Long merchantId = merchant.getId();
		UploadDocumentResponseDTO uploadDocumentResponse = new UploadDocumentResponseDTO();
		uploadDocumentResponse.setSuccess(false);
		uploadDocumentResponse.setInValidPhoto(false);

		if (requestDTO == null || requestDTO.getPayload() == null) {
			logger.warn("Invalid request payload for merchantId: {}", merchantId);
			return uploadDocumentResponse;
		}

		UploadDocumentRequestDTO uploadDocumentRequest = requestDTO.getPayload();
		logger.info("Processing uploadDocument request for merchantId: {}, applicationId: {}", merchantId, uploadDocumentRequest.getApplicationId());

		Long applicationId = uploadDocumentRequest.getApplicationId();
		List<UploadDocumentRequestDTO.Document> documents = uploadDocumentRequest.getDocuments();

		if (applicationId == null || applicationId <= 0 || documents == null || documents.isEmpty()) {
			logger.warn("Invalid applicationId: {} or empty documents for merchantId: {}", applicationId, merchantId);
			return uploadDocumentResponse;
		}

		LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchantIdAndStatus(applicationId, merchantId, "draft");
		LendingResubmitTask lendingResubmitTask = lendingResubmitTaskDao.findTopByApplicationId(applicationId);
		boolean resubmitRequest = false;

		if (lendingApplication == null && (Objects.isNull(lendingResubmitTask) || lendingResubmitTask.getResubmitDone())) {
			logger.warn("No valid LendingApplication or ResubmitTask for applicationId: {}, merchantId: {}", applicationId, merchantId);
			return uploadDocumentResponse;
		}

		if (lendingApplication == null && Objects.nonNull(lendingResubmitTask) && lendingResubmitTask.getResubmit() &&
				(lendingResubmitTask.getResubmitDone() == null || !lendingResubmitTask.getResubmitDone())) {
			logger.info("Found resubmit task for applicationId: {}, merchantId: {}", applicationId, merchantId);
			lendingApplication = lendingApplicationDao.findById(applicationId).orElse(null);
			resubmitRequest = true;

			if (lendingApplication == null) {
				logger.warn("Application not found even for resubmit task, applicationId: {}, merchantId: {}", applicationId, merchantId);
				return uploadDocumentResponse;
			}
		}

		LendingCategories lendingCategories = lendingCategoryDao.getByCategory(lendingApplication.getCategory());
		LoanDashboardApiVersion loanDashboardApiVersion = loanDashboardService.getLoanDashboardApiVersion(merchantId, lendingApplication);
		LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = lendingRiskVariablesSnapshotDao.findByApplicationId(lendingApplication.getId());

		if (!RiskSegment.TOPUP.equals(lendingRiskVariablesSnapshot.getRiskSegment())) {
			Double SID = calculateShopInferredDistance(requestDTO.getMeta(), lendingApplication);
			logger.info("Calculated SID for merchantId: {}, applicationId: {}: {}", merchantId, applicationId, SID);

			if (SID != null && SID > sidThreshold && easyLoanUtil.percentScaleUp(merchantId, sidRolloutPercent)) {
				logger.info("SID exceeds threshold for merchantId: {}, applicationId: {}", merchantId, applicationId);
				uploadDocumentResponse.setSidGreaterThanRequired(true);

				logSidFunnelEvent(merchantId, applicationId, SID, loanDashboardApiVersion.getApiVersion());
			}
		}

		List<DocumentsIdProofMaster> documentsIdProofList = documentsIdProofDaoMaster.findByMerchantIdAndLendingApplicationId(merchantId, lendingApplication.getId());
		List<LendingShopDocuments> lendingShopDocumentsList = lendingShopDocumentsDao.findByMerchantIdAndApplicationId(merchantId, lendingApplication.getId());
		logger.info("Found {} DocumentsIdProofMaster and {} LendingShopDocuments for merchantId: {}, applicationId: {}",
				documentsIdProofList.size(), lendingShopDocumentsList.size(), merchantId, applicationId);

		Boolean isUpdateDocument = !documentsIdProofList.isEmpty();
		Boolean isUpdateMoreDocument = !lendingShopDocumentsList.isEmpty();

		List<UploadDocumentResponseDTO.Document> documentList = processAndUploadDocuments(
				documents, isUpdateDocument, merchant, lendingApplication, requestDTO.getMeta(),
				uploadDocumentResponse, isUpdateMoreDocument, resubmitRequest, loanDashboardApiVersion.getApiVersion(), notS3Upload);

		if (!documentList.isEmpty()) {
			uploadDocumentResponse.setSuccess(true);
			logDocumentSubmissionEvent(merchantId, applicationId, loanDashboardApiVersion.getApiVersion());
		}

		uploadDocumentResponse.setDocument(documentList);
		logger.info("Completed uploadDocument for merchantId: {}, applicationId: {}, success: {}",
				merchantId, applicationId, uploadDocumentResponse.getSuccess());

		return uploadDocumentResponse;
	}

	private void logSidFunnelEvent(Long merchantId, Long applicationId, Double SID, String apiVersion) {
		if (LoanDetailsConstant.VERSION_V2.equalsIgnoreCase(apiVersion)) {
			funnelService.submitEventV3(merchantId, null, applicationId,
					FunnelEnums.StageId.SHOP_PHOTO, FunnelEnums.StageEvent.DISTANCE_CHECK_MODAL_SHOWN,
					String.valueOf(SID), LoanDetailsConstant.FUNNEL_VERSION_TAG);
		} else {
			funnelService.submitEvent(merchantId, null, applicationId,
					FunnelEnums.StageId.SHOP_PHOTO, FunnelEnums.StageEvent.DISTANCE_CHECK_MODAL_SHOWN,
					String.valueOf(SID));
		}
	}

	private void logDocumentSubmissionEvent(Long merchantId, Long applicationId, String apiVersion) {
		if (LoanDetailsConstant.VERSION_V2.equalsIgnoreCase(apiVersion)) {
			funnelService.submitEventV3(merchantId, null, applicationId,
					FunnelEnums.StageId.SHOP_PHOTO, FunnelEnums.StageEvent.SUBMITTED,
					LocalDateTime.now().toString(), LoanDetailsConstant.FUNNEL_VERSION_TAG);
		} else {
			funnelService.submitEvent(merchantId, null, applicationId,
					FunnelEnums.StageId.SHOP_PHOTO, FunnelEnums.StageEvent.SUBMITTED,
					LocalDateTime.now().toString());
		}
	}

	private Double calculateShopInferredDistance(MetaDTO meta, LendingApplication lendingApplication) {
		Long merchantId = lendingApplication.getMerchantId();
		logger.info("Calculating shop inferred distance for merchantId: {}", merchantId);

		if (meta == null || StringUtils.isEmpty(meta.getLatitude()) || StringUtils.isEmpty(meta.getLongitude())) {
			logger.info("Missing location data for merchantId: {}", merchantId);
			return null;
		}

		try {
			if (loanUtil.isInternalMerchant(merchantId)) {
				logger.info("Internal merchant detected for merchantId: {}, returning default SID", merchantId);
				return 3000D;
			}

			Map<String, Double> dsResponse = dsHandler.fetchDsLocation(merchantId);
			if (ObjectUtils.isEmpty(dsResponse) || !dsResponse.containsKey("latitude") ||
					ObjectUtils.isEmpty(dsResponse.get("latitude")) || !dsResponse.containsKey("longitude") ||
					ObjectUtils.isEmpty(dsResponse.get("longitude"))) {
				logger.info("Missing DS location data for merchantId: {}", merchantId);
				return null;
			}

			Double lat1 = Double.valueOf(meta.getLatitude());
			Double lon1 = Double.valueOf(meta.getLongitude());
			Double lat2 = dsResponse.get("latitude");
			Double lon2 = dsResponse.get("longitude");

			Double inferredDistance = loanUtil.calculateLatLonDistance(lat1, lon1, lat2, lon2);
			logger.info("Calculated SID: {} for merchantId: {}", inferredDistance, merchantId);

			return (inferredDistance == -1D) ? null : inferredDistance;
		} catch (Exception ex) {
			logger.error("Error calculating SID for merchantId: {}, error: {}", merchantId, ex.getMessage(), ex);
		}

		return null;
	}

	private List<UploadDocumentResponseDTO.Document> processAndUploadDocuments(List<UploadDocumentRequestDTO.Document> documents, Boolean isUpdate, BasicDetailsDto merchantBasicDetails, LendingApplication lendingApplication, MetaDTO meta, UploadDocumentResponseDTO uploadDocumentResponse, Boolean isUpdateMoreDocument, Boolean resubmitRequest, String version, Boolean notS3Upload) {
		logger.info("Processing and uploading documents for merchantId: {}, applicationId: {}, isUpdate: {}, isUpdateMoreDocument: {}, resubmitRequest: {}, version: {}",
				merchantBasicDetails.getId(), lendingApplication.getId(), isUpdate, isUpdateMoreDocument, resubmitRequest, version);

		List<UploadDocumentResponseDTO.Document> documentList = new ArrayList<>();
		List<LendingShopDocumentsAudit> lendingShopDocumentsAuditList = new ArrayList<>();

		for (UploadDocumentRequestDTO.Document document : documents) {
			logger.info("Processing document for merchantId: {}, applicationId: {}, proofType: {}, changeFlag: {}, singlePageDocument: {}",
					merchantBasicDetails.getId(), lendingApplication.getId(), document.getProofType(), document.getChangeFlag(), document.getSinglePageDocument());

			if (isUpdate && !document.getChangeFlag()) {
				logger.info("Skipping document for merchantId: {}, applicationId: {} as changeFlag is false", merchantBasicDetails.getId(), lendingApplication.getId());
				continue;
			}

			if (document.getProof() == null || document.getProof().isEmpty() || document.getProof().get(0) == null) {
				logger.error("Empty proof data for merchantId: {}, applicationId: {}", merchantBasicDetails.getId(), lendingApplication.getId());
				continue;
			}

			String proofType = document.getProofType();
			int singlePageDocument = document.getSinglePageDocument() ? 1 : 0;

			Map<String, String> proofSides = new HashMap<>();
			logger.info("Uploading proof for merchantId: {}, applicationId: {}, proofType: {}, document: {}", merchantBasicDetails.getId(), lendingApplication.getId(), proofType, document);
			if(Boolean.TRUE.equals(notS3Upload))
			{
				logger.info("Processing proof without S3 upload for merchantId: {}, applicationId: {}, proofType: {}", merchantBasicDetails.getId(), lendingApplication.getId(), proofType);
				proofSides = processAndUploadProofWithoutS3(document.getProof(), merchantBasicDetails);
			}
			else {
				logger.info("Processing proof with S3 upload for merchantId: {}, applicationId: {}, proofType: {}", merchantBasicDetails.getId(), lendingApplication.getId(), proofType);
				proofSides = processAndUploadProof(document.getProof(), merchantBasicDetails);
			}

			String frontSide = proofSides.get("frontSide");
			String backSide = proofSides.get("backSide");

			logger.info("Uploaded proof for merchantId: {}, applicationId: {}, proofType: {}, frontSide: {}, backSide: {}",
					merchantBasicDetails.getId(), lendingApplication.getId(), proofType, frontSide, backSide);

			DocumentsIdProofMaster documentsIdProof = null;
			LendingShopDocuments lendingShopDocuments = null;

			if ("shop-front".equalsIgnoreCase(proofType) || "shop-stock".equalsIgnoreCase(proofType) || "shop-qr".equalsIgnoreCase(proofType)) {
				if (isUpdateMoreDocument) {
					lendingShopDocuments = updateShopDocuments(proofType, frontSide, backSide, merchantBasicDetails, lendingApplication, meta, version);
				} else {
					lendingShopDocuments = insertShopDocuments(proofType, frontSide, backSide, merchantBasicDetails, lendingApplication, meta);
				}
			} else {
				if (isUpdate) {
					documentsIdProof = updateDocumentIdProof(proofType, frontSide, backSide, singlePageDocument, merchantBasicDetails, lendingApplication, meta);
				} else {
					documentsIdProof = insertDocumentIdProof(proofType, frontSide, backSide, singlePageDocument, lendingApplication, meta);
				}
			}

			if (documentsIdProof != null) {
				logger.info("DocumentIdProof created/updated for merchantId: {}, applicationId: {}, proofType: {}, proofId: {}",
						merchantBasicDetails.getId(), lendingApplication.getId(), proofType, documentsIdProof.getId());

				UploadDocumentResponseDTO.Document documentResponse = uploadDocumentResponse.new Document();
				documentResponse.setProofId(documentsIdProof.getId());
				documentResponse.setProofType(proofType);
				documentResponse.setSinglePageDocument(singlePageDocument);
				documentList.add(documentResponse);
			}

			if (lendingShopDocuments != null) {
				logger.info("LendingShopDocuments created/updated for merchantId: {}, applicationId: {}, proofType: {}, proofId: {}",
						merchantBasicDetails.getId(), lendingApplication.getId(), proofType, lendingShopDocuments.getId());

				UploadDocumentResponseDTO.Document documentResponse = uploadDocumentResponse.new Document();
				documentResponse.setProofId(lendingShopDocuments.getId());
				documentResponse.setProofType(proofType);
				documentResponse.setSinglePageDocument(1);
				documentList.add(documentResponse);

				lendingShopDocumentsAuditList.add(new LendingShopDocumentsAudit(lendingShopDocuments, resubmitRequest));
			}
		}

		if (!lendingShopDocumentsAuditList.isEmpty()) {
			logger.info("Pushing LendingShopDocumentsAudit data to BQ for merchantId: {}, applicationId: {}", merchantBasicDetails.getId(), lendingApplication.getId());
			bqPublisherUtil.publish("Lending", "lending_shop_documents_audit", lendingShopDocumentsAuditList);
		}

		logger.info("Completed processing and uploading documents for merchantId: {}, applicationId: {}", merchantBasicDetails.getId(), lendingApplication.getId());
		return documentList;
	}

	public void saveLendingShopDocumentsDsParams (LendingShopDocuments lendingShopDocuments, String outputClass, Double confidence, Boolean verified) {
		lendingShopDocuments.setOutputClass(outputClass);
		lendingShopDocuments.setConfidence(confidence);
		lendingShopDocuments.setVerified(verified);
		lendingShopDocumentsDao.save(lendingShopDocuments);
	}

//	public void sinzyCorrectPanCheck(DocumentsIdProofMaster documentsIdProof ,String proofType, BasicDetailsDto merchant, Long applicationId){
//
//		if(proofType.equals("pancard")){
//			new Thread(() -> {
//				Map<String,String> signzyApiDetails= uploadDocumentUtil.getDetailsOfSignzyApi();
//				if(Objects.nonNull(signzyApiDetails)){
//					uploadDocumentUtil.doOcrForPan(documentsIdProof, signzyApiDetails,proofType, merchant.getId(), applicationId);
//				}
//			}).start();
//		}
//	}

	private Map<String, String> processAndUploadProof(List<String> proof, BasicDetailsDto merchant) {
		Map<String, String> proofSides = new LinkedHashMap<>();
		proofSides.put("frontSide", "");
		proofSides.put("backSide", "");

		String frontBase64Encoded = processBase64String(proof.get(0));
		String fileName = merchant.getId() + "_" + UUID.randomUUID() + ".jpeg";
		String frontUrl = s3BucketHandler.uploadToS3Bucket(frontBase64Encoded, fileName, bucket);
		proofSides.put("frontSide", frontUrl);

		if(proof.size() > 1 && !StringUtils.isEmpty(proof.get(1))) {
			String backBase64Encoded = processBase64String(proof.get(1));
			fileName = merchant.getId() + "_" + UUID.randomUUID() + ".jpeg";
			String backUrl = s3BucketHandler.uploadToS3Bucket(backBase64Encoded, fileName, bucket);
			proofSides.put("backSide", backUrl);
		}
		return proofSides;
	}

	private Map<String, String> processAndUploadProofWithoutS3(List<String> proof, BasicDetailsDto merchant) {
		Map<String, String> proofSides = new LinkedHashMap<>();
		proofSides.put("frontSide", "");
		proofSides.put("backSide", "");

		String frontUrl = proof.get(0);
		logger.info("Processing front side URL: {}", frontUrl);
		if (frontUrl.contains("amazonaws.com")) {
			frontUrl = getTempUrl(frontUrl,merchant.getId());
			proofSides.put("frontSide", frontUrl);
		}
		else {
			proofSides.put("frontSide", frontUrl);
		}

		if(proof.size() > 1 && !StringUtils.isEmpty(proof.get(1))) {
			String backUrl = proof.get(1);
			logger.info("Processing back side URL: {}", backUrl);
			if (backUrl.contains("amazonaws.com")) {
				backUrl = getTempUrl(backUrl, merchant.getId());
				proofSides.put("backSide", backUrl);
			}
			else {
				proofSides.put("backSide", backUrl);
			}
		}
		return proofSides;
	}

	public String getTempUrl(String docUrl, Long merchantId) {
		try {
			String shopImage = commonUtil.getBase64FromImageURL(docUrl);
			String newShopPictureIdentifier = UUID.randomUUID().toString();
			String fileName =  merchantId + "_" + newShopPictureIdentifier + ".jpeg";
			String newTempUrl = "";
			if (shopImage != null) {
				newTempUrl = s3BucketHandler.uploadToS3Bucket(shopImage,fileName , bucket);
				logger.info("Uploading new shop picture to S3 {}", newTempUrl);
			}
			else {
				throw new Exception("Unable to get Base64 from Image URL");
			}
			return fileName;
		} catch (Exception e) {
			return "";
		}

	}

	public String processBase64String(String base64EncodedString) {
		base64EncodedString.replace(' ', '+');
		if(base64EncodedString.contains("base64,")) {
			String [] base64EncodedSplit = base64EncodedString.split("base64,");
			base64EncodedString = base64EncodedSplit[1];
		}
		return base64EncodedString;
	}

	private DocumentsIdProofMaster insertDocumentIdProof(String proofType, String frontSide, String backSide,
														 int singlePageDocument,
														 LendingApplication lendingApplication, MetaDTO meta) {
		DocumentsIdProofMaster documentsIdProof = new DocumentsIdProofMaster();
		documentsIdProof.setMerchantId(lendingApplication.getMerchantId());
		documentsIdProof.setLendingApplicationId(lendingApplication.getId());
		documentsIdProof.setProofType(proofType);
		documentsIdProof.setProofFrontSide(frontSide);
		documentsIdProof.setProofBackSide(backSide);
		documentsIdProof.setStatus("pending_verification");
		documentsIdProof.setSinglePage(singlePageDocument);
		if (meta != null && meta.getLatitude() != null && !meta.getLatitude().trim().equalsIgnoreCase("") && !meta.getLatitude().trim().equalsIgnoreCase("undefined")) {
			documentsIdProof.setLatitude(meta.getLatitude());
			documentsIdProof.setLongitude(meta.getLongitude());
			documentsIdProof.setIp(meta.getIp());
		}
		documentsIdProofDaoMaster.save(documentsIdProof);
		return documentsIdProof;
	}

	private LendingShopDocuments insertShopDocuments(String proofType, String frontSide, String backSide, BasicDetailsDto merchant, LendingApplication lendingApplication, MetaDTO meta) {
		LendingShopDocuments lendingShopDocuments = new LendingShopDocuments();
		lendingShopDocuments.setMerchantId(merchant.getId());
		lendingShopDocuments.setApplicationId(lendingApplication.getId());
		lendingShopDocuments.setProofType(proofType);
		lendingShopDocuments.setProofFrontSide(frontSide);
		lendingShopDocuments.setProofBackSide(backSide);
		lendingShopDocuments.setStatus("pending_verification");
		if (meta != null && meta.getLatitude() != null && !meta.getLatitude().trim().equalsIgnoreCase("") && !meta.getLatitude().trim().equalsIgnoreCase("undefined")) {
			lendingShopDocuments.setLatitude(meta.getLatitude());
			lendingShopDocuments.setLongitude(meta.getLongitude());
			lendingShopDocuments.setIp(meta.getIp());
		}
		lendingShopDocumentsDao.save(lendingShopDocuments);
		return lendingShopDocuments;
	}

	private DocumentsIdProofMaster updateDocumentIdProof(String proofType, String frontSide, String backSide,
														 int singlePageDocument, BasicDetailsDto merchant,
														 LendingApplication lendingApplication, MetaDTO meta) {
		if(!"pancard".equalsIgnoreCase(proofType) && !"selfie".equalsIgnoreCase(proofType)){
			DocumentsIdProofMaster poaDocument=documentsIdProofDaoMaster.fetchLatestAddressProof(merchant.getId(), lendingApplication.getId(), "LENDING");
			if(poaDocument != null && !poaDocument.getProofType().equalsIgnoreCase(proofType)){
				poaDocument.setDeletedAt(new Date());
				documentsIdProofDaoMaster.save(poaDocument);
			}
		}

		DocumentsIdProofMaster documentsIdProof = documentsIdProofDaoMaster.findTop1ByMerchantIdAndLendingApplicationIdAndProofTypeAndDeletedAtIsNullOrderByIdDesc(lendingApplication.getMerchantId(), lendingApplication.getId(), proofType);
		if(documentsIdProof != null) {
			documentsIdProof.setProofFrontSide(frontSide);
			documentsIdProof.setProofBackSide(backSide);
			documentsIdProof.setSinglePage(singlePageDocument);
			if (meta != null && meta.getLatitude() != null && !meta.getLatitude().trim().equalsIgnoreCase("") && !meta.getLatitude().trim().equalsIgnoreCase("undefined")) {
				documentsIdProof.setLatitude(meta.getLatitude());
				documentsIdProof.setLongitude(meta.getLongitude());
				documentsIdProof.setIp(meta.getIp());
			}
			documentsIdProofDaoMaster.save(documentsIdProof);
		} else {
			documentsIdProof = insertDocumentIdProof(proofType, frontSide, backSide, singlePageDocument, lendingApplication, meta);
		}
		return documentsIdProof;
	}

	private LendingShopDocuments updateShopDocuments(String proofType, String frontSide, String backSide, BasicDetailsDto merchant, LendingApplication lendingApplication, MetaDTO meta, String version) {
		LendingShopDocuments lendingShopDocuments = lendingShopDocumentsDao.findTop1ByMerchantIdAndApplicationIdAndProofTypeOrderByIdDesc(merchant.getId(), lendingApplication.getId(), proofType);

		if(lendingShopDocuments != null) {
			lendingShopDocuments.setProofFrontSide(frontSide);
			lendingShopDocuments.setProofBackSide(backSide);
			if (meta != null && meta.getLatitude() != null && !meta.getLatitude().trim().equalsIgnoreCase("") && !meta.getLatitude().trim().equalsIgnoreCase("undefined")) {
				lendingShopDocuments.setLatitude(meta.getLatitude());
				lendingShopDocuments.setLongitude(meta.getLongitude());
				lendingShopDocuments.setIp(meta.getIp());
			}
			lendingShopDocumentsDao.save(lendingShopDocuments);
			if(LoanDetailsConstant.VERSION_V2.equalsIgnoreCase(version)){
				funnelService.submitEventV3(lendingApplication.getMerchantId(), null, lendingApplication.getId(),
						FunnelEnums.StageId.SHOP_PHOTO, FunnelEnums.StageEvent.UPLOADED_AGAIN, lendingShopDocuments.getProofType(), LoanDetailsConstant.FUNNEL_VERSION_TAG);
			}
			else{
				funnelService.submitEvent(lendingApplication.getMerchantId(), null, lendingApplication.getId(),
						FunnelEnums.StageId.SHOP_PHOTO, FunnelEnums.StageEvent.UPLOADED_AGAIN, lendingShopDocuments.getProofType());
			}
		} else {
			lendingShopDocuments = insertShopDocuments(proofType, frontSide, backSide, merchant, lendingApplication, meta);
		}
		return lendingShopDocuments;
	}

	public Double calculateShopInferredDistance(String latitude, String longitude, Long merchantId){
		logger.info("Calculating shop inferred distance for merchant:{}", merchantId);
		try{
			// send more than 2500 for internal merchant
			if(loanUtil.isInternalMerchant(merchantId)){
				return 3000D;
			}

			Map<String, Double> dsResponse = dsHandler.fetchDsLocation(merchantId);
			if(ObjectUtils.isEmpty(latitude) || ObjectUtils.isEmpty(longitude) || ObjectUtils.isEmpty(dsResponse)
					|| !dsResponse.containsKey("latitude") || ObjectUtils.isEmpty(dsResponse.get("latitude")) || !dsResponse.containsKey("longitude") || ObjectUtils.isEmpty(dsResponse.get("longitude"))){
				return null;
			}
			Double lat1 = Double.valueOf(latitude);
			Double lon1 = Double.valueOf(longitude);
			Double lat2 = dsResponse.get("latitude");
			Double lon2 = dsResponse.get("longitude");

			Double inferredDistance = loanUtil.calculateLatLonDistance(lat1, lon1, lat2, lon2);
			logger.info("SID:{}", inferredDistance);

			return (inferredDistance == -1D) ? null:inferredDistance;

		}catch (Exception ex){
			logger.error("Exception occurred while calculating inferred distance for merchant:{}, {}, {}", merchantId, ex.getMessage(), Arrays.asList(ex.getStackTrace()));
		}
		return null;
	}

//	private void karzaVerification(String proofType, String frontSide, String backSide, int singlePageDocument, DocumentsIdProof documentsIdProof, Merchant merchant, LendingApplication lendingApplication) {
//		if(proofType.equals("pancard") || proofType.equals("adhaarcard") || proofType.equals("aadharcard") || proofType.equals("votercard") || proofType.equals("passport")) {
//			new Thread(() -> {
//				kycUsingKarzaAPI(proofType, frontSide, documentsIdProof, merchant, lendingApplication);
//				if (singlePageDocument == 0) {
//					kycUsingKarzaAPI(proofType, backSide, documentsIdProof, merchant, lendingApplication);
//				}
//			}).start();
//		}
//	}

//	private void kycUsingKarzaAPI(String proofType, String fileName, DocumentsIdProof documentsIdProof, Merchant merchant, LendingApplication lendingApplication) {
//		try {
//			Instant start = Instant.now();
//			String tempPublicURL = s3BucketHandler.getTemporaryPublicURL(fileName, bucket);
////			String tempPublicURL = "";
//			Instant end = Instant.now();
//			logger.info("Time Taken by AWS S3 ImageUrl API : {} miliseconds", Duration.between(start, end).toMillis());
//			boolean isDocAuthEntryMade = false;
//			if(!tempPublicURL.isEmpty()) {
//				start = Instant.now();
//				String response = karzaHandler.curlKarzaKycAPI(tempPublicURL);
//				end = Instant.now();
//				logger.info("Time Taken by Karza kyc API : {} miliseconds", Duration.between(start, end).toMillis());
//				if(!response.isEmpty()) {
//					ObjectMapper mapper = new ObjectMapper();
//	    	        Map<String, Object> responseMap = mapper.readValue(response, new TypeReference<Map<String, Object>>(){});
//	    	        Integer status = (Integer) responseMap.get("statusCode");
//
//					if(status == 101) {
//						DocKycDetails docKycDetails = processAndSaveKycResponse(response, proofType, documentsIdProof, merchant);
//						if(proofType.equals("pancard")) {
//							start = Instant.now();
//							pancardAuthenticationUsingKarzaAPI(responseMap, docKycDetails, documentsIdProof, merchant, lendingApplication);
//							end = Instant.now();
//							logger.info("Time Taken by Karza Pan Authentication API : {} miliseconds", Duration.between(start, end).toMillis());
//							isDocAuthEntryMade = true;
//						}
//					}else {
//						String requestId = (String) responseMap.get("requestId");
//						String failureResponse = (String) responseMap.get("error");
//						logger.info("UploadDocumentService karza kyc api failure for documentId : {} and api response : {} and karza requestId : {}",documentsIdProof.getId(), failureResponse, requestId);
//					}
//				}else {
//					logger.info("UploadDocumentService karza kyc api failure with blank response for documentId : {}",documentsIdProof.getId());
//				}
//			}else {
//				logger.info("UploadDocumentService blank tempURL from S3 bucket, merchant: {} for key : {}",merchant.getId(), fileName);
//			}
//
//			// TODO: Need to do entry first and update based on the response update the details
//			if(proofType.equals("pancard") && !isDocAuthEntryMade) {
//				logger.info("Marking blank entries for pancard in DocKycDetails and DocAuthentication for merchant id {}", merchant.getId());
//				DocKycDetails docDetails = createFailedDocKycDetails("pancard", documentsIdProof, merchant);
//				createFailedEntryForPancardDocAuthentication(docDetails, documentsIdProof, merchant, lendingApplication);
//			}
//		} catch (FileNotFoundException e) {
//			logger.info("UploadDocumentService exception while fetching tempURL from S3 bucket, merchantId: {},file not found for key : {}",merchant.getId(), fileName);
//		} catch (Exception e) {
//			e.printStackTrace();
//			logger.info("UploadDocumentService exception while fetching tempURL from S3 bucket, merchant: {}, message : {}",merchant.getId(), e.getMessage());
//		}
//	}

//	private DocKycDetails processAndSaveKycResponse(String responseString, String proofType, DocumentsIdProof documentsIdProof, Merchant merchant) {
//		ObjectMapper mapper = new ObjectMapper();
//        Map<String, Object> response = null;
//		try {
//			response = mapper.readValue(responseString, new TypeReference<Map<String, Object>>(){});
//		} catch (JsonParseException e1) {
//			e1.printStackTrace();
//		} catch (JsonMappingException e1) {
//			e1.printStackTrace();
//		} catch (IOException e1) {
//			e1.printStackTrace();
//		}
//		List<Map<String, Object>> result = (response != null) ? (List<Map<String, Object>>) response.get("result") : null;
//
//		if(result != null && result.size() > 0) {
//			String dob = "";
//			String doi = "";
//			DocKycDetails docKycDetails = new DocKycDetails();
//			docKycDetails.setDocumentsIdProof(documentsIdProof);
//			docKycDetails.setMerchantId(documentsIdProof.getMerchantId());
//			docKycDetails.setCreatedAt(new Date());
//			docKycDetails.setUpdatedAt(new Date());
//			docKycDetails.setModule("LENDING");
//			docKycDetails.setStatus("");
//			docKycDetails.setDocType(proofType);
//			docKycDetails.setResponse(responseString);
//			String type	= (String) result.get(0).get("type");
//			Map<String, Map<String, String>> details	= (Map<String, Map<String, String>>) result.get(0).get("details");
//			if(proofType.equals("votercard")) {
//				if(type.equals("Voterid Front")) {
//					docKycDetails.setDocNo(details.get("voterid").get("value"));
//					docKycDetails.setFatherName(details.get("relation").get("value"));
//					docKycDetails.setPersonName(details.get("name").get("value"));
//					dob = details.get("dob").get("value");
//					docKycDetails.setDocSide("FRONT");
//				}else if(type.equals("Voterid Back")) {
//					docKycDetails.setDocNo(details.get("voterid").get("value"));
//					dob = details.get("dob").get("value");
//					docKycDetails.setAddress(details.get("address").get("value"));
//					docKycDetails.setGender(details.get("gender").get("value"));
//					docKycDetails.setPincode(details.get("pin").get("value"));
//					docKycDetails.setCity(details.get("addressSplit").get("city"));
//					docKycDetails.setState(details.get("addressSplit").get("state"));
//					docKycDetails.setDocSide("BACK");
//				}
//			}else if(proofType.equals("pancard")) {
//				docKycDetails.setDocNo(details.get("panNo").get("value"));
//				dob = details.get("date").get("value");
//				docKycDetails.setPersonName(details.get("name").get("value"));
//				doi = details.get("dateOfIssue").get("value");
//				docKycDetails.setFatherName(details.get("father").get("value"));
//			}else if(proofType.equals("passport")) {
//				docKycDetails.setDocNo(details.get("passportNum").get("value"));
//				dob = details.get("dob").get("value");
//				docKycDetails.setPersonName(details.get("givenName").get("value") + " " + details.get("surname").get("value"));
//				doi = details.get("doi").get("value");
//				docKycDetails.setGender(details.get("gender").get("value"));
//				docKycDetails.setCountryCode(details.get("countryCode").get("value"));
//			}else if(proofType.equals("adhaarcard") || proofType.equals("aadharcard")) {
//				if(type.equals("Aadhaar Front Bottom")) {
//					docKycDetails.setDocNo(details.get("aadhaar").get("value"));
//					dob = details.get("dob").get("value");
//					docKycDetails.setPersonName(details.get("name").get("value"));
//					doi = details.get("yob").get("value");
//					docKycDetails.setGender(details.get("gender").get("value"));
//					docKycDetails.setQr(details.get("qr").get("value"));
//					docKycDetails.setFatherName(details.get("father").get("value"));
//					docKycDetails.setDocSide("FRONT");
//				}else if(type.equals("Aadhaar Back")) {
//					docKycDetails.setDocNo(details.get("aadhaar").get("value"));
//					docKycDetails.setQr(details.get("qr").get("value"));
//					docKycDetails.setFatherName(details.get("father").get("value"));
//					docKycDetails.setPincode(details.get("pin").get("value"));
//					docKycDetails.setCity(details.get("addressSplit").get("city"));
//					docKycDetails.setState(details.get("addressSplit").get("state"));
//					docKycDetails.setAddress(details.get("address").get("value"));
//					docKycDetails.setDocSide("BACK");
//				}
//			}
//			Date initDate;
//			try {
//				SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
//				if(!dob.isEmpty()) {
//					initDate = new SimpleDateFormat("dd/MM/yyyy").parse(dob);
//					docKycDetails.setDob(formatter.format(initDate));
//				}
//
//				if(!doi.isEmpty()) {
//					initDate = new SimpleDateFormat("dd/MM/yyyy").parse(doi);
//					formatter = new SimpleDateFormat("yyyy-MM-dd");
//					docKycDetails.setDoi(formatter.format(initDate));
//				}
//
//			} catch (ParseException e) {
//				logger.info("UploadDocumentService exception while parsing date, message : {}",e.getMessage());
//			}
//			docKycDetailsDao.save(docKycDetails);
//			return docKycDetails;
//		}
//		return null;
//	}

//	private void pancardAuthenticationUsingKarzaAPI(Map<String, Object> response, DocKycDetails docKycDetails, DocumentsIdProof documentsIdProof, Merchant merchant, LendingApplication lendingApplication) {
//		List<Map<String, Object>> result = (List<Map<String, Object>>) response.get("result");
//
//		if(result != null && result.size() > 0) {
//			Map<String, Map<String, String>> details = (Map<String, Map<String, String>>) result.get(0).get("details");
//			String panNumber = details.get("panNo").get("value");
//			String dob = details.get("date").get("value");
//			String name = details.get("name").get("value");
//
//			String curlResponse = karzaHandler.curlKarzaPanAuthenticationAPI(panNumber, name, dob);
//			if(!curlResponse.isEmpty()) {
//				processAndSavePanAuthenticationResponse(curlResponse, docKycDetails, documentsIdProof, lendingApplication);
//			}else {
//				logger.info("UploadDocumentService karza pan authentication api failure with blank response for panNumber : {}, dob : {}, name : {}",panNumber, dob, name);
//				createFailedEntryForPancardDocAuthentication(docKycDetails, documentsIdProof, merchant, lendingApplication);
//			}
//		}
//	}

//	private void processAndSavePanAuthenticationResponse(String response, DocKycDetails docKycDetails, DocumentsIdProof documentsIdProof, LendingApplication lendingApplication) {
//		ObjectMapper mapper = new ObjectMapper();
//        Map<String, Object> responseMap = null;
//		try {
//			responseMap = mapper.readValue(response, new TypeReference<Map<String, Object>>(){});
//		} catch (JsonParseException e) {
//			e.printStackTrace();
//		} catch (JsonMappingException e) {
//			e.printStackTrace();
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
//		String status = (responseMap != null) ? (String) responseMap.get("status-code") : "";
//
//		DocAuthentication docAuthentication = new DocAuthentication();
//		docAuthentication.setDocKycDetails(docKycDetails);
//		docAuthentication.setMerchantId(docKycDetails.getMerchantId());
//		docAuthentication.setDocType("pancard");
//		docAuthentication.setFullResponse(response);
//		docAuthentication.setDocumentsIdProof(documentsIdProof);
//		docAuthentication.setCreatedAt(new Date());
//		docAuthentication.setUpdatedAt(new Date());
//
//		if(status.equals("101")) {
//			Map<String, String> result = (Map<String, String>) responseMap.get("result");
//			docAuthentication.setDocStatus(result.get("status"));
//			docAuthentication.setDuplicate(String.valueOf(result.get("duplicate")));
//			docAuthentication.setNameMatch(String.valueOf(result.get("nameMatch")));
//			docAuthentication.setDobMatch(String.valueOf(result.get("dobMatch")));
//			if(String.valueOf(result.get("duplicate")).equals("false") && String.valueOf(result.get("nameMatch")).equals("true") && String.valueOf(result.get("dobMatch")).equals("true") ) {
//				docAuthentication.setStatus("ACCEPTED");
////				lendingApplicationDao.updateApplicationManualKyc("APPROVED", lendingApplication.getId());
//			}else {
//				docAuthentication.setStatus("REJECTED");
//			}
//
//		}else {
//			docAuthentication.setDocStatus("FAILED");
//			docAuthentication.setDuplicate("");
//			docAuthentication.setNameMatch("");
//			docAuthentication.setDobMatch("");
//			docAuthentication.setStatus("");
//		}
//		docAuthenticationDao.save(docAuthentication);
//	}

//	private void createFailedEntryForPancardDocAuthentication(DocKycDetails docKycDetails, DocumentsIdProof documentsIdProof, Merchant merchant, LendingApplication lendingApplication) {
//		DocAuthentication docAuthentication = new DocAuthentication();
//		docAuthentication.setDocKycDetails(docKycDetails);
//		docAuthentication.setMerchantId(docKycDetails.getMerchantId());
//		docAuthentication.setDocType("pancard");
//		docAuthentication.setFullResponse("");
//		docAuthentication.setDocumentsIdProof(documentsIdProof);
//		docAuthentication.setCreatedAt(new Date());
//		docAuthentication.setUpdatedAt(new Date());
//		docAuthentication.setDocStatus("FAILED");
//		docAuthentication.setDuplicate("");
//		docAuthentication.setNameMatch("");
//		docAuthentication.setDobMatch("");
//		docAuthentication.setStatus("");
//		docAuthenticationDao.save(docAuthentication);
//	}

//	private DocKycDetails createFailedDocKycDetails(String docType, DocumentsIdProof documentsIdProof, Merchant merchant) {
//		DocKycDetails docKycDetails = new DocKycDetails();
//		docKycDetails.setDocumentsIdProof(documentsIdProof);
//		docKycDetails.setMerchantId(documentsIdProof.getMerchantId());
//		docKycDetails.setCreatedAt(new Date());
//		docKycDetails.setUpdatedAt(new Date());
//		docKycDetails.setModule("LENDING");
//		docKycDetails.setDocSide("FRONT");
//		docKycDetails.setStatus("");
//		docKycDetails.setDocType(docType);
//		docKycDetails.setResponse("");
//		docKycDetails.setDocNo("");
//		docKycDetails.setDob("1970-01-01");
//		docKycDetails.setPersonName("");
//		docKycDetails.setFatherName("");
//		docKycDetailsDao.save(docKycDetails);
//		return docKycDetails;
//	}

}