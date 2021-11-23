package com.bharatpe.lending.service;

import java.io.FileNotFoundException;
import java.util.*;

import com.bharatpe.common.dao.DocKycDetailsDao;
import com.bharatpe.common.dao.DocumentsIdProofDao;
import com.bharatpe.common.dao.MerchantBankDetailDao;
import com.bharatpe.common.dao.PhonebookDao;
import com.bharatpe.common.entities.*;
import com.bharatpe.lending.common.dao.LendingEkycDao;
import com.bharatpe.lending.common.dao.LendingResubmitTaskDao;
import com.bharatpe.lending.common.dao.LendingShopDocumentsDao;
import com.bharatpe.lending.common.entity.LendingEkyc;
import com.bharatpe.lending.common.entity.LendingResubmitTask;
import com.bharatpe.lending.common.entity.LendingShopDocuments;
import com.bharatpe.lending.dao.LendingApplicationDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.bharatpe.common.objects.CommonAPIRequest;
import com.bharatpe.lending.handlers.S3BucketHandler;
import org.springframework.util.StringUtils;

@Service
public class ImageURLService {
	Logger logger = LoggerFactory.getLogger(ImageURLService.class);
	
	@Autowired
	DocumentsIdProofDao documentsIdProofDao;

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
	PhonebookDao phonebookDao;

	@Autowired
	RedisNotificationService redisNotificationService;

	@Autowired
	MerchantBankDetailDao merchantBankDetailDao;

	@Autowired
	DocKycDetailsDao docKycDetailsDao;

	@Value("${aws.s3.bucket}")
	private String bucket;
	
	public Map<String, Object> fetchAndWrapResult(Merchant merchant, CommonAPIRequest commonAPIRequest) {
		Map<String, Object> result = new HashMap<String, Object>();
		Map<String, String> panNameCheck = new HashMap<>();
		Long applicationId =  commonAPIRequest.getPayload().get("application_id") != null ? Long.parseLong(commonAPIRequest.getPayload().get("application_id").toString()) : null;
		if(applicationId == null || applicationId <= 0) {
			logger.info("Invalid Application Id: {} for merchant : {}", applicationId, merchant.getId());
			result.put("success", false);
			return result;
		}
		LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchantAndStatus(applicationId, merchant, "draft");
		LendingResubmitTask lendingResubmitTask =lendingResubmitTaskDao.findTopByApplicationId(applicationId);
		if(lendingApplication == null  && Objects.nonNull(lendingResubmitTask) && lendingResubmitTask.getResubmit() && (lendingResubmitTask.getResubmitDone() == null || !lendingResubmitTask.getResubmitDone())) {
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

		Boolean ekycDone=isEkycDone(merchant, lendingApplication.getId());
		if(ekycDone==null){
			result.put("success", false);
			return result;
		}
		boolean finalCall = commonAPIRequest.getPayload().get("finalCall") != null && (boolean) commonAPIRequest.getPayload().get("finalCall");
		if (finalCall) {
			Optional<Phonebook> phonebook = phonebookDao.findTop1ByMerchantIdOrderByIdDesc(merchant.getId());
			if (!phonebook.isPresent() || phonebook.get().getContactsCount() == null) {
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

	public Map<String, String> getBanificaryAndPanName(Merchant merchant, LendingApplication lendingApplication){
		Map<String, String> result = new HashMap<>();
		DocumentsIdProof documentsIdProof = documentsIdProofDao.findByMerchantIdApplicationIdAndProofType(merchant.getId(), lendingApplication.getId(), "pancard");
		if(Objects.nonNull(documentsIdProof) && Objects.nonNull(documentsIdProof.getPanNameMatch()) && !documentsIdProof.getPanNameMatch().isEmpty() && documentsIdProof.getPanNameMatch().equals("NO")){
			DocKycDetails docKycDetails = docKycDetailsDao.fetchLatestPanCardDetails(merchant.getId(), lendingApplication.getId());

			MerchantBankDetail merchantBankDetail = merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchant.getId(), "ACTIVE");
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

	private boolean allowRoute(LendingApplication lendingApplication, Merchant merchant, Boolean isEkycDone) {
		boolean selfie = false;
		boolean pancard = false;
		boolean poa = false;
		List<DocumentsIdProof> documentsIdProofList = documentsIdProofDao.findByMerchantAndLendingApplication(merchant, lendingApplication);
		for (DocumentsIdProof documentsIdProof : documentsIdProofList) {
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
	
	public Boolean isEkycDone(Merchant merchant, Long applicationId) {
		try{
			LendingEkyc lendingEkyc = lendingEkycDao.findSuccessEkyc(merchant.getId(), applicationId);
			DocumentsIdProof ekycDoc = documentsIdProofDao.findByMerchantIdApplicationIdAndProofType(merchant.getId(), applicationId, "eAadhar");
			return lendingEkyc != null && ekycDoc != null;
		}
		catch(Exception e) {
			logger.error("Error occured while checking for ekyc status",e);
			return false;
		}
	}
	
	public List<Map<String, Object>> fetchImageUrl(Merchant merchant, LendingApplication lendingApplication, CommonAPIRequest commonAPIRequest) {
		List<Map<String, Object>> finalResponse = new ArrayList<>();
		List<DocumentsIdProof> documentsIdProofList = documentsIdProofDao.findByMerchantAndLendingApplication(merchant.getId(), lendingApplication.getId());
		List<LendingShopDocuments> lendingShopDocumentsList  = lendingShopDocumentsDao.findByMerchantIdAndApplicationId(merchant.getId(), lendingApplication.getId());

		for(DocumentsIdProof documentsIdProof : documentsIdProofList) {
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

		if(lendingShopDocumentsList != null){
			for(LendingShopDocuments lendingShopDocuments : lendingShopDocumentsList){
				Map<String, Object> moreDocument = new LinkedHashMap<>();
				moreDocument.put("proof_type",lendingShopDocuments.getProofType());
				moreDocument.put("single_page_document",Boolean.TRUE);

				List<String> imageURL = new ArrayList<>();
				try {
					if(StringUtils.isEmpty(lendingShopDocuments.getProofFrontSide())) {
						logger.error("Empty front Url for Shop Documents: {}", lendingShopDocuments.getId());
						continue;
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
}
