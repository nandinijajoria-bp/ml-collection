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
import com.bharatpe.lending.dao.LendingGstDao;
import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.handlers.DsHandler;
import com.bharatpe.lending.lendingplatform.authentication.dto.response.ApiResponse;
import com.bharatpe.lending.loanV3.revamp.constants.LoanDetailsConstant;
import com.bharatpe.lending.loanV3.revamp.response.LoanDashboardApiVersion;
import com.bharatpe.lending.loanV3.revamp.services.LoanDashboardService;
import com.bharatpe.lending.util.BQPublisherUtil;
import com.bharatpe.lending.util.LoanUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.handlers.S3BucketHandler;

@Service
public class UploadDocumentService {
	Logger logger = LoggerFactory.getLogger(UploadDocumentService.class);
	
	@Autowired
	DocumentsIdProofDaoMaster documentsIdProofDaoMaster;

	@Autowired
	LendingShopDocumentsDao lendingShopDocumentsDao;

	@Autowired
	LendingApplicationDao lendingApplicationDao;
	
	@Autowired
	S3BucketHandler s3BucketHandler;

	@Autowired
	LendingResubmitTaskDao lendingResubmitTaskDao;

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

    @Value("${sid.threshold}")
    Double sidThreshold;

    @Value("${sid.rollout.percent}")
    Integer sidRolloutPercent;

	@Value("${aws.s3.bucket}")
	private String bucket;

	public ApiResponse<UploadDocumentResponseDTO> uploadDocument(BasicDetailsDto merchant, RequestDTO<UploadDocumentRequestDTO> requestDTO) {
		try {
			UploadDocumentResponseDTO uploadDocumentResponse = new UploadDocumentResponseDTO();
			uploadDocumentResponse.setSuccess(false);
			uploadDocumentResponse.setInvalidPhoto(false);
			uploadDocumentResponse.setSidGreaterThanRequired(false);

			UploadDocumentRequestDTO uploadDocumentRequest = requestDTO.getPayload();
			Long applicationId = uploadDocumentRequest.getApplicationId();
			List<UploadDocumentRequestDTO.Document> documents = uploadDocumentRequest.getDocuments();

			if (applicationId == null || applicationId <= 0 || documents == null || documents.isEmpty()) {
				logger.error("Invalid Application ID or Documents for merchant: {}", merchant.getId());
				return ApiResponse.error(String.valueOf(HttpStatus.BAD_REQUEST.value()), "Invalid Application ID or Documents", null);
			}

			LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchantIdAndStatus(applicationId, merchant.getId(), "draft");
			LendingResubmitTask lendingResubmitTask = lendingResubmitTaskDao.findTopByApplicationId(applicationId);

			if (lendingApplication == null && (Objects.isNull(lendingResubmitTask) || lendingResubmitTask.getResubmitDone())) {
				logger.error("Lending Application not found or resubmit not allowed for application ID: {}", applicationId);
				return ApiResponse.error(String.valueOf(HttpStatus.NOT_FOUND.value()), "Lending Application not found or resubmit not allowed", null);
			}

			boolean resubmitRequest = false;
			if (lendingApplication == null && Objects.nonNull(lendingResubmitTask) && lendingResubmitTask.getResubmit() &&
					(lendingResubmitTask.getResubmitDone() == null || !lendingResubmitTask.getResubmitDone())) {
				lendingApplication = lendingApplicationDao.findById(applicationId).orElse(null);
				resubmitRequest = true;
			}

			if (lendingApplication == null) {
				logger.error("Lending Application not found for application ID: {}", applicationId);
				return ApiResponse.error(String.valueOf(HttpStatus.NOT_FOUND.value()), "Lending Application not found", null);
			}

			LoanDashboardApiVersion loanDashboardApiVersion = loanDashboardService.getLoanDashboardApiVersion(merchant.getId(), lendingApplication);

			// Process SID (Shop Inferred Distance) check
			LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = lendingRiskVariablesSnapshotDao.findByApplicationId(lendingApplication.getId());
			if (lendingRiskVariablesSnapshot != null && !RiskSegment.TOPUP.equals(lendingRiskVariablesSnapshot.getRiskSegment())) {
				Double SID = null;
				if (!ObjectUtils.isEmpty(requestDTO.getMeta())) {
					SID = calculateShopInferredDistance(requestDTO.getMeta().getLatitude(), requestDTO.getMeta().getLongitude(), lendingApplication.getMerchantId());
				}

				logger.info("Calculated Shop Inferred Distance for merchant:{} and application:{} is:{}",
						lendingApplication.getMerchantId(), lendingApplication.getId(), SID);

				if (SID != null && SID > sidThreshold && easyLoanUtil.percentScaleUp(lendingApplication.getMerchantId(), sidRolloutPercent)) {
					logger.info("SID is greater than threshold for merchant:{} and application:{}",
							lendingApplication.getMerchantId(), lendingApplication.getId());
					uploadDocumentResponse.setSidGreaterThanRequired(true);

					if (LoanDetailsConstant.VERSION_V2.equalsIgnoreCase(loanDashboardApiVersion.getApiVersion())) {
						funnelService.submitEventV3(lendingApplication.getMerchantId(), null, lendingApplication.getId(),
								FunnelEnums.StageId.SHOP_PHOTO, FunnelEnums.StageEvent.DISTANCE_CHECK_MODAL_SHOWN,
								String.valueOf(SID), LoanDetailsConstant.FUNNEL_VERSION_TAG);
					} else {
						funnelService.submitEvent(lendingApplication.getMerchantId(), null, lendingApplication.getId(),
								FunnelEnums.StageId.SHOP_PHOTO, FunnelEnums.StageEvent.DISTANCE_CHECK_MODAL_SHOWN,
								String.valueOf(SID));
					}
				}
			}

			List<DocumentsIdProofMaster> documentsIdProofList =
					documentsIdProofDaoMaster.findByMerchantIdAndLendingApplicationId(merchant.getId(), lendingApplication.getId());
			List<LendingShopDocuments> lendingShopDocumentsList =
					lendingShopDocumentsDao.findByMerchantIdAndApplicationId(merchant.getId(), lendingApplication.getId());

			boolean isUpdateDocument = !documentsIdProofList.isEmpty();
			boolean isUpdateMoreDocument = !lendingShopDocumentsList.isEmpty();

			// Process and upload documents
			ApiResponse<List<UploadDocumentResponseDTO.Document>> documentResponse = processAndUploadDocuments(
					documents, isUpdateDocument, merchant, lendingApplication, requestDTO.getMeta(),
					uploadDocumentResponse, isUpdateMoreDocument, resubmitRequest,
					loanDashboardApiVersion.getApiVersion()
			);

			if (!documentResponse.isSuccess()) {
				String errorMessage = documentResponse.getError() != null
						? documentResponse.getError().getMessage()
						: "Error occurred while processing documents";
				String errorCode = documentResponse.getError() != null
						? documentResponse.getError().getStatusCode()
						: String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value());

				logger.error("Error processing documents: {}", errorMessage);

				return ApiResponse.error(errorCode, errorMessage, null);
			}

			List<UploadDocumentResponseDTO.Document> documentList = documentResponse.getData();

			if (documentList.isEmpty()) {
				logger.error("No documents processed for application ID: {}", applicationId);
				return ApiResponse.error(String.valueOf(HttpStatus.UNPROCESSABLE_ENTITY.value()), "No documents processed", uploadDocumentResponse);
			}

			uploadDocumentResponse.setSuccess(true);
			uploadDocumentResponse.setDocuments(documentList);

			if (LoanDetailsConstant.VERSION_V2.equalsIgnoreCase(loanDashboardApiVersion.getApiVersion())) {
				funnelService.submitEventV3(merchant.getId(), null, applicationId,
						FunnelEnums.StageId.SHOP_PHOTO, FunnelEnums.StageEvent.SUBMITTED,
						LocalDateTime.now().toString(), LoanDetailsConstant.FUNNEL_VERSION_TAG);
			} else {
				funnelService.submitEvent(merchant.getId(), null, applicationId,
						FunnelEnums.StageId.SHOP_PHOTO, FunnelEnums.StageEvent.SUBMITTED,
						LocalDateTime.now().toString());
			}

			return ApiResponse.success(uploadDocumentResponse);

		} catch (Exception e) {
			logger.error("Error occurred while uploading documents: {}", e.getMessage(), e);
			return ApiResponse.error(String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value()), "Internal Server Error", null);
		}
	}


	private ApiResponse<List<UploadDocumentResponseDTO.Document>> processAndUploadDocuments(
			List<UploadDocumentRequestDTO.Document> documents,
			Boolean isUpdate,
			BasicDetailsDto merchantBasicDetails,
			LendingApplication lendingApplication,
			MetaDTO meta,
			UploadDocumentResponseDTO uploadDocumentResponse,
			Boolean isUpdateMoreDocument,
			Boolean resubmitRequest,
			String version) {

		List<UploadDocumentResponseDTO.Document> documentList = new ArrayList<>();
		List<LendingShopDocumentsAudit> lendingShopDocumentsAuditList = new ArrayList<>();

		try {
			if (documents == null || documents.isEmpty()) {
				logger.error("No documents to process for merchant: {}, application: {}",
						merchantBasicDetails.getId(), lendingApplication.getId());
				return ApiResponse.error(String.valueOf(HttpStatus.BAD_REQUEST.value()), "No documents to process", null);
			}

			for (UploadDocumentRequestDTO.Document document : documents) {
				try {
					if (isUpdate && !document.getChangeFlag()) {
						continue;
					}

					if (document.getProof() == null || document.getProof().isEmpty() || document.getProof().get(0) == null) {
						logger.error("Empty document proof for merchant: {}, application: {}, proofType: {}",
								merchantBasicDetails.getId(), lendingApplication.getId(), document.getProofType());
						continue;
					}

					String proofType = document.getProofType();
					int singlePageDocument = document.getSinglePageDocument() ? 1 : 0;

					// Process and upload proof images to S3
					Map<String, String> proofSides = processAndUploadProof(document.getProof(), merchantBasicDetails);
					String frontSide = proofSides.get("frontSide");
					String backSide = proofSides.get("backSide");

					if (frontSide.isEmpty()) {
						logger.error("Failed to upload document front side for merchant: {}, application: {}, proofType: {}",
								merchantBasicDetails.getId(), lendingApplication.getId(), proofType);
						continue;
					}

					DocumentsIdProofMaster documentsIdProof = null;
					LendingShopDocuments lendingShopDocuments = null;

					if ("shop-front".equalsIgnoreCase(proofType) ||
							"shop-stock".equalsIgnoreCase(proofType) ||
							"shop-qr".equalsIgnoreCase(proofType)) {

						if (Boolean.TRUE.equals(isUpdateMoreDocument)) {
							lendingShopDocuments = updateShopDocuments(
									proofType, frontSide, backSide, merchantBasicDetails,
									lendingApplication, meta, version);
						} else {
							lendingShopDocuments = insertShopDocuments(
									proofType, frontSide, backSide,
									merchantBasicDetails, lendingApplication, meta);
						}

						if (lendingShopDocuments != null) {
							UploadDocumentResponseDTO.Document documentResponse = new UploadDocumentResponseDTO.Document();
							documentResponse.setProofId(lendingShopDocuments.getId());
							documentResponse.setProofType(proofType);
							documentResponse.setSinglePageDocument(1);
							documentList.add(documentResponse);

							validateShopImages(lendingShopDocuments, merchantBasicDetails, uploadDocumentResponse, lendingApplication);

							lendingShopDocumentsAuditList.add(new LendingShopDocumentsAudit(
									lendingShopDocuments, resubmitRequest));
						}
					} else {
						if (isUpdate) {
							documentsIdProof = updateDocumentIdProof(
									proofType, frontSide, backSide, singlePageDocument,
									merchantBasicDetails, lendingApplication, meta);
						} else {
							documentsIdProof = insertDocumentIdProof(
									proofType, frontSide, backSide, singlePageDocument,
									lendingApplication, meta);
						}

						if (documentsIdProof != null) {
							UploadDocumentResponseDTO.Document documentResponse = new UploadDocumentResponseDTO.Document();
							documentResponse.setProofId(documentsIdProof.getId());
							documentResponse.setProofType(proofType);
							documentResponse.setSinglePageDocument(singlePageDocument);
							documentList.add(documentResponse);
						}
					}
				} catch (Exception e) {
					logger.error("Error processing document: {}, for merchant: {}, application: {}: {}",
							document.getProofType(), merchantBasicDetails.getId(),
							lendingApplication.getId(), e.getMessage(), e);
				}
			}

			// Publish shop documents audit data to BQ if available
			if (!lendingShopDocumentsAuditList.isEmpty()) {
				logger.info("Publishing data to BQ for lending shop docs for merchant id {}",
						merchantBasicDetails.getId());
				bqPublisherUtil.publish("Lending", "lending_shop_documents_audit",
						lendingShopDocumentsAuditList);
			}

			if (documentList.isEmpty()) {
				logger.error("No documents were successfully processed for merchant: {}, application: {}",
						merchantBasicDetails.getId(), lendingApplication.getId());
				return ApiResponse.error(String.valueOf(HttpStatus.UNPROCESSABLE_ENTITY.value()), "No documents were successfully processed", null);
			}

			return ApiResponse.success(documentList);

		} catch (Exception e) {
			logger.error("Fatal error in processAndUploadDocuments for merchant: {}, application: {}: {}",
					merchantBasicDetails.getId(), lendingApplication.getId(), e.getMessage(), e);
			return ApiResponse.error(String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value()), "Failed to process documents: " + e.getMessage(), null);
		}
	}

	/**
	 * Validates shop images based on their type and updates response accordingly
	 */
	private void validateShopImages(LendingShopDocuments lendingShopDocuments,
									BasicDetailsDto merchantBasicDetails,
									UploadDocumentResponseDTO uploadDocumentResponse,
									LendingApplication lendingApplication) {

		if (merchantBasicDetails.getId() % 10 != 0 || lendingShopDocuments == null) return;

		String proofType = lendingShopDocuments.getProofType();
		boolean isShopFront = "shop-front".equalsIgnoreCase(proofType);
		boolean isShopStock = "shop-stock".equalsIgnoreCase(proofType);

		if (!isShopFront && !isShopStock) return;

		DsImageValidationRequestDto requestDto = new DsImageValidationRequestDto(
				lendingShopDocuments.getProofFrontSide(),
				isShopFront, isShopStock, false, isShopFront
		);

		DsImageValidationResponseDto responseDto = apiGatewayService.validateImage(requestDto);

		if (ObjectUtils.isEmpty(responseDto)) return;

		if (isShopFront) {
			processShopFrontValidation(lendingShopDocuments, uploadDocumentResponse, responseDto, lendingApplication);
		} else if (isShopStock) {
			processShopStockValidation(lendingShopDocuments, responseDto);
		}
	}

	private void processShopFrontValidation(LendingShopDocuments lendingShopDocuments,
											UploadDocumentResponseDTO uploadDocumentResponse,
											DsImageValidationResponseDto responseDto,
											LendingApplication lendingApplication) {

		DsImageValidationResponseDto.ShopParams shopFrontExistence = responseDto.getShopFrontExistence();
		DsImageValidationResponseDto.ShopParams shopFrontStructure = responseDto.getShopFrontStructure();

		if (!ObjectUtils.isEmpty(shopFrontExistence)) {
			saveLendingShopDocumentsDsParams(
					lendingShopDocuments,
					shopFrontExistence.getDsClass(),
					shopFrontExistence.getConfidence(),
					shopFrontExistence.getVerifiedShop()
			);

			boolean isInvalidPhoto = Boolean.FALSE.equals(shopFrontExistence.getVerifiedShop())
					&& "NO_SHOP".equalsIgnoreCase(shopFrontExistence.getDsClass())
					&& shopFrontExistence.getConfidence() > 0.75;

			uploadDocumentResponse.setInvalidPhoto(isInvalidPhoto);
		}

		if (!ObjectUtils.isEmpty(shopFrontStructure)) {
			updateGstDetailsWithShopStructure(lendingApplication.getId(), shopFrontStructure);
		}
	}

	private void processShopStockValidation(LendingShopDocuments lendingShopDocuments,
											DsImageValidationResponseDto responseDto) {

		DsImageValidationResponseDto.ShopParams shopStockCategory = responseDto.getShopStockCategory();

		if (!ObjectUtils.isEmpty(shopStockCategory)) {
			saveLendingShopDocumentsDsParams(
					lendingShopDocuments,
					shopStockCategory.getDsClass(),
					shopStockCategory.getConfidence(),
					shopStockCategory.getVerifiedShop()
			);
		}
	}

	private void updateGstDetailsWithShopStructure(Long applicationId, DsImageValidationResponseDto.ShopParams structure) {
		LendingGstDetail lendingGstDetail = lendingGstDao.findByApplicationId(applicationId);

		if (ObjectUtils.isEmpty(lendingGstDetail)) return;

		if (lendingGstDetail.getShopType() == null && !ObjectUtils.isEmpty(structure.getDsClass())) {
			lendingGstDetail.setShopType(structure.getDsClass());
		}

		lendingGstDetail.setComputedShopType(structure.getDsClass());
		lendingGstDetail.setConfidence(structure.getConfidence());

		lendingGstDao.save(lendingGstDetail);
	}


	public void saveLendingShopDocumentsDsParams (LendingShopDocuments lendingShopDocuments, String outputClass, Double confidence, Boolean verified) {
		lendingShopDocuments.setOutputClass(outputClass);
		lendingShopDocuments.setConfidence(confidence);
		lendingShopDocuments.setVerified(verified);
		lendingShopDocumentsDao.save(lendingShopDocuments);
	}

	private Map<String, String> processAndUploadProof(List<String> proof, BasicDetailsDto merchant) {
		Map<String, String> proofSides = new LinkedHashMap<>();
		proofSides.put("frontSide", "");
		proofSides.put("backSide", "");

		String frontBase64Encoded = processBase64String(proof.get(0));
		String fileName = merchant.getId() + "_" + UUID.randomUUID().toString() + ".jpeg";
		String frontUrl = s3BucketHandler.uploadToS3Bucket(frontBase64Encoded, fileName, bucket);
		proofSides.put("frontSide", frontUrl);

		if(proof.size() > 1 && !StringUtils.isEmpty(proof.get(1))) {
			String backBase64Encoded = processBase64String(proof.get(1));
			fileName = merchant.getId() + "_" + UUID.randomUUID().toString() + ".jpeg";
			String backUrl = s3BucketHandler.uploadToS3Bucket(backBase64Encoded, fileName, bucket);
			proofSides.put("backSide", backUrl);
		}
		return proofSides;
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
}