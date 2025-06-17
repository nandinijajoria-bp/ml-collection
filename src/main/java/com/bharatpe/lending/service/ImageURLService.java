package com.bharatpe.lending.service;

import java.io.FileNotFoundException;
import java.time.LocalDateTime;
import java.util.*;

import com.bharatpe.common.entities.*;
import com.bharatpe.lending.common.bpnewmaster.dao.DocumentsIdProofDaoMaster;
import com.bharatpe.lending.common.bpnewmaster.entity.DocumentsIdProofMaster;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.dao.LendingResubmitTaskDao;
import com.bharatpe.lending.common.dao.LendingShopDocumentsDao;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.entity.LendingResubmitTask;
import com.bharatpe.lending.common.entity.LendingShopDocuments;
import com.bharatpe.lending.common.enums.FunnelEnums;
import com.bharatpe.lending.common.service.FunnelService;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dto.ImageProofRequestDto;
import com.bharatpe.lending.dto.ImageProofResponseDto;
import com.bharatpe.lending.handlers.DsHandler;
import com.bharatpe.lending.lendingplatform.authentication.dto.response.ApiResponse;
import com.bharatpe.lending.loanV3.revamp.constants.LoanDetailsConstant;
import com.bharatpe.lending.loanV3.revamp.response.LoanDashboardApiVersion;
import com.bharatpe.lending.loanV3.revamp.services.LoanDashboardService;
import com.bharatpe.lending.util.LoanUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
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
	LendingResubmitTaskDao lendingResubmitTaskDao;

	@Autowired
	LendingApplicationDetailsDao lendingApplicationDetailsDao;

	@Autowired
	FunnelService funnelService;

	@Autowired
	LoanUtil loanUtil;

	@Autowired
	DsHandler dsHandler;

	@Autowired
	private LoanDashboardService loanDashboardService;

	@Value("${aws.s3.bucket}")
	private String bucket;

	public ApiResponse<ImageProofResponseDto> fetchAndWrapResult(BasicDetailsDto merchant, ImageProofRequestDto imageProofRequestDto) {
		logger.info("Fetching image proofs for merchantId: {}, payload: {}", merchant.getId(), imageProofRequestDto);

		Long applicationId = imageProofRequestDto.getApplicationId() != null
				? Long.parseLong(imageProofRequestDto.getApplicationId().toString())
				: null;

		if (applicationId == null || applicationId <= 0) {
			logger.warn("Missing or invalid Application Id: {} for merchant: {}", applicationId, merchant.getId());
			return ApiResponse.error(String.valueOf(HttpStatus.BAD_REQUEST.value()), "Missing or invalid application id", null);
		}

		LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchantIdAndStatus(
				applicationId, merchant.getId(), "draft");
		LendingResubmitTask lendingResubmitTask = lendingResubmitTaskDao.findTopByApplicationId(applicationId);

		if (lendingApplication == null && (lendingResubmitTask == null || Boolean.TRUE.equals(lendingResubmitTask.getResubmitDone()))) {
			logger.warn("Application not found or already resubmitted for Id: {} merchant: {}", applicationId, merchant.getId());
			return ApiResponse.error(String.valueOf(HttpStatus.NOT_FOUND.value()), "Application not found or already resubmitted", null);
		}

		if (lendingApplication == null && lendingResubmitTask != null
				&& Boolean.TRUE.equals(lendingResubmitTask.getResubmit())
				&& (lendingResubmitTask.getResubmitDone() == null || !lendingResubmitTask.getResubmitDone())) {

			Optional<LendingApplication> appOptional = lendingApplicationDao.findById(applicationId);
			if (!appOptional.isPresent()) {
				logger.error("LendingApplication not found for resubmission, applicationId: {}", applicationId);
				return ApiResponse.error(String.valueOf(HttpStatus.NOT_FOUND.value()), "Application not found for resubmission", null);
			}
			lendingApplication = appOptional.get();
		}

		LoanDashboardApiVersion loanDashboardApiVersion = loanDashboardService.getLoanDashboardApiVersion(merchant.getId());
		if (LoanDetailsConstant.VERSION_V2.equalsIgnoreCase(loanDashboardApiVersion.getApiVersion())) {
			funnelService.submitEventV3(lendingApplication.getMerchantId(), null, lendingApplication.getId(),
					FunnelEnums.StageId.SHOP_PHOTO, FunnelEnums.StageEvent.INITIATED,
					LocalDateTime.now().toString(), LoanDetailsConstant.FUNNEL_VERSION_TAG);
		} else {
			funnelService.submitEvent(lendingApplication.getMerchantId(), null, lendingApplication.getId(),
					FunnelEnums.StageId.SHOP_PHOTO, FunnelEnums.StageEvent.INITIATED,
					LocalDateTime.now().toString());
		}

		logger.info("Processing application: {}", lendingApplication.getId());

		try {
			List<ImageProofResponseDto.Proof> proofList = fetchImageUrl(merchant, lendingApplication, imageProofRequestDto);
			ImageProofResponseDto responseDto = new ImageProofResponseDto();
			responseDto.setProofs(proofList);
			responseDto.setQrMandatory(!"NTB".equals(lendingApplication.getLoanType()));

			logger.info("Successfully fetched {} image proofs for applicationId: {}", proofList.size(), lendingApplication.getId());
			return ApiResponse.success(responseDto);
		} catch (Exception e) {
			logger.error("Error fetching image proofs for applicationId: {}, error: {}", applicationId, e.getMessage(), e);
			return ApiResponse.error(String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value()), "Failed to process image proofs: " + e.getMessage(), null);
		}
	}

	public List<ImageProofResponseDto.Proof> fetchImageUrl(BasicDetailsDto merchant, LendingApplication lendingApplication, ImageProofRequestDto imageProofRequestDto) {
		logger.info("Fetching image URLs for merchantId: {}, applicationId: {}", merchant.getId(), lendingApplication.getId());
		List<ImageProofResponseDto.Proof> proofList = new ArrayList<>();

		try {
			List<DocumentsIdProofMaster> documentsIdProofList = documentsIdProofDaoMaster.findByMerchantAndLendingApplication(
					merchant.getId(), lendingApplication.getId());
			logger.debug("Found {} document proofs for applicationId: {}", documentsIdProofList.size(), lendingApplication.getId());

			processIdDocuments(documentsIdProofList, proofList);

			processShopDocuments(merchant, lendingApplication, imageProofRequestDto, proofList);

			// Sort by timestamp
			proofList.sort(Comparator.comparing(ImageProofResponseDto.Proof::getUpdatedAt));
			logger.info("Successfully fetched {} image proofs for applicationId: {}", proofList.size(), lendingApplication.getId());
			return proofList;
		} catch (Exception e) {
			logger.error("Failed to fetch image URLs for applicationId: {}, error: {}",
					lendingApplication.getId(), e.getMessage(), e);
			throw new RuntimeException("Error fetching image URLs: " + e.getMessage());
		}
	}

	private void processIdDocuments(List<DocumentsIdProofMaster> documentsIdProofList, List<ImageProofResponseDto.Proof> proofList) {
		for (DocumentsIdProofMaster documentsIdProof : documentsIdProofList) {
			if (documentsIdProof.getProofType().equalsIgnoreCase("eAadhar")) {
				logger.debug("Skipping eAadhar document with id: {}", documentsIdProof.getId());
				continue;
			}

			if (StringUtils.isEmpty(documentsIdProof.getProofFrontSide())) {
				logger.warn("Empty front URL for documentsIdProof: {}, skipping", documentsIdProof.getId());
				continue;
			}

			try {
				ImageProofResponseDto.Proof proof = new ImageProofResponseDto.Proof();
				proof.setProofType(documentsIdProof.getProofType());
				proof.setSinglePageDocument(documentsIdProof.getSinglePage() == null || documentsIdProof.getSinglePage() == 1);

				List<String> imageURL = new ArrayList<>();

				String frontURL = s3BucketHandler.getTemporaryPublicURL(documentsIdProof.getProofFrontSide(), bucket);
				imageURL.add(frontURL);

				if (!StringUtils.isEmpty(documentsIdProof.getProofBackSide())) {
					String backURL = s3BucketHandler.getTemporaryPublicURL(documentsIdProof.getProofBackSide(), bucket);
					imageURL.add(backURL);
				}

				proof.setProofUrls(imageURL);
				proof.setUpdatedAt(documentsIdProof.getUpdatedAt());
				proofList.add(proof);
				logger.debug("Added {} proof with {} images", proof.getProofType(), imageURL.size());
			} catch (FileNotFoundException e) {
				logger.warn("File not found in S3 bucket for proof: {}, document ID: {}",
						documentsIdProof.getProofType(), documentsIdProof.getId());
			} catch (Exception e) {
				logger.error("Error processing document proof: {}, document ID: {}, error: {}",
						documentsIdProof.getProofType(), documentsIdProof.getId(), e.getMessage());
			}
		}
	}

	private void processShopDocuments(BasicDetailsDto merchant, LendingApplication lendingApplication,
									 ImageProofRequestDto imageProofRequestDto,
									  List<ImageProofResponseDto.Proof> proofList) {

		List<LendingShopDocuments> lendingShopDocumentsList = lendingShopDocumentsDao
				.findByMerchantIdAndApplicationId(merchant.getId(), lendingApplication.getId());
		logger.debug("Found {} shop documents for applicationId: {}",
				lendingShopDocumentsList.size(), lendingApplication.getId());

		if (ObjectUtils.isEmpty(lendingShopDocumentsList)) {
			logger.info("No shop documents found for applicationId: {}", lendingApplication.getId());
			return;
		}

		processSkipDistanceCheck(merchant, lendingApplication, imageProofRequestDto);

		LoanDashboardApiVersion loanDashboardApiVersion = loanDashboardService
				.getLoanDashboardApiVersion(merchant.getId(), lendingApplication);

		for (LendingShopDocuments lendingShopDocuments : lendingShopDocumentsList) {
			try {
				if (StringUtils.isEmpty(lendingShopDocuments.getProofFrontSide()) ||
						(!StringUtils.isEmpty(imageProofRequestDto.getShopDocType()) && imageProofRequestDto.getShopDocType().length > 0 && Arrays.asList(imageProofRequestDto.getShopDocType()).contains(lendingShopDocuments.getProofType()))) {
					continue;
				}

				ImageProofResponseDto.Proof proof = new ImageProofResponseDto.Proof();
				proof.setProofType(lendingShopDocuments.getProofType());
				proof.setSinglePageDocument(true);

				List<String> imageURL = new ArrayList<>();
				String frontURL = s3BucketHandler.getTemporaryPublicURL(lendingShopDocuments.getProofFrontSide(), bucket);
				imageURL.add(frontURL);

				if (!StringUtils.isEmpty(lendingShopDocuments.getProofBackSide())) {
					String backURL = s3BucketHandler.getTemporaryPublicURL(lendingShopDocuments.getProofBackSide(), bucket);
					imageURL.add(backURL);
				}

				submitShopPhotoFunnelEvent(lendingApplication, loanDashboardApiVersion, imageURL);

				proof.setProofUrls(imageURL);
				proof.setUpdatedAt(lendingShopDocuments.getUpdatedAt());
				proofList.add(proof);

				logger.debug("Added shop document of type {} with {} images",
						lendingShopDocuments.getProofType(), imageURL.size());
			} catch (FileNotFoundException e) {
				logger.warn("File not found in S3 for shop document: {}, id: {}",
						lendingShopDocuments.getProofType(), lendingShopDocuments.getId());
			} catch (Exception e) {
				logger.error("Error processing shop document: {}, id: {}, error: {}",
						lendingShopDocuments.getProofType(), lendingShopDocuments.getId(), e.getMessage());
			}
		}
	}


	private void processSkipDistanceCheck(BasicDetailsDto merchant, LendingApplication lendingApplication,
										   	ImageProofRequestDto imageProofRequestDto) {
		Boolean skipDistanceCheck = false;

		LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao
				.findLendingApplicationDetailsByApplicationId(lendingApplication.getId());

		if (ObjectUtils.isEmpty(lendingApplicationDetails)) {
			logger.info("Creating new application details for applicationId: {}", lendingApplication.getId());
			lendingApplicationDetails = new LendingApplicationDetails();
			lendingApplicationDetails.setApplicationId(lendingApplication.getId());
			lendingApplicationDetailsDao.save(lendingApplicationDetails);
		}

		if (!ObjectUtils.isEmpty(lendingApplicationDetails.getSkipDistanceCheck())) {
			skipDistanceCheck = lendingApplicationDetails.getSkipDistanceCheck();
			logger.debug("Using existing skipDistanceCheck value: {} for applicationId: {}",
					skipDistanceCheck, lendingApplication.getId());
		} else if (!ObjectUtils.isEmpty(imageProofRequestDto.getSkipDistanceCheck())) {
			skipDistanceCheck = Boolean.valueOf(imageProofRequestDto.getSkipDistanceCheck().toString());
			lendingApplicationDetails.setSkipDistanceCheck(skipDistanceCheck);
			lendingApplicationDetailsDao.save(lendingApplicationDetails);
			logger.info("Updated skipDistanceCheck to {} for applicationId: {}",
					skipDistanceCheck, lendingApplication.getId());

			LoanDashboardApiVersion loanDashboardApiVersion = loanDashboardService
					.getLoanDashboardApiVersion(merchant.getId());

			if (LoanDetailsConstant.VERSION_V2.equalsIgnoreCase(loanDashboardApiVersion.getApiVersion())) {
				funnelService.submitEventV3(lendingApplication.getMerchantId(), null, lendingApplication.getId(),
						FunnelEnums.StageId.SHOP_PHOTO, FunnelEnums.StageEvent.DISTANCE_CHECK_SKIPPED,
						String.valueOf(skipDistanceCheck), LoanDetailsConstant.FUNNEL_VERSION_TAG);
			} else {
				funnelService.submitEvent(lendingApplication.getMerchantId(), null, lendingApplication.getId(),
						FunnelEnums.StageId.SHOP_PHOTO, FunnelEnums.StageEvent.DISTANCE_CHECK_SKIPPED,
						String.valueOf(skipDistanceCheck));
			}
		}
	}

	private void submitShopPhotoFunnelEvent(LendingApplication lendingApplication,
											LoanDashboardApiVersion loanDashboardApiVersion, List<String> imageURL) {
		if (LoanDetailsConstant.VERSION_V2.equalsIgnoreCase(loanDashboardApiVersion.getApiVersion())) {
			funnelService.submitEventV3(lendingApplication.getMerchantId(), null, lendingApplication.getId(),
					FunnelEnums.StageId.SHOP_PHOTO, FunnelEnums.StageEvent.SHOP_PHOTO_PREFILLED,
					imageURL.toString(), LoanDetailsConstant.FUNNEL_VERSION_TAG);
		} else {
			funnelService.submitEvent(lendingApplication.getMerchantId(), null, lendingApplication.getId(),
					FunnelEnums.StageId.SHOP_PHOTO, FunnelEnums.StageEvent.SHOP_PHOTO_PREFILLED,
					imageURL.toString());
		}
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
