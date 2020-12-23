package com.bharatpe.lending.service;

import java.io.FileNotFoundException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import javax.servlet.http.HttpServletResponse;

import com.bharatpe.common.dao.PhonebookDao;
import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.Phonebook;
import com.bharatpe.lending.common.dao.LendingEkycDao;
import com.bharatpe.lending.common.entity.CreditApplication;
import com.bharatpe.lending.common.entity.LendingEkyc;
import com.bharatpe.lending.common.entity.MerchantDocumentProof;
import com.bharatpe.lending.dao.LendingApplicationDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.bharatpe.common.constants.ResponseCode;
import com.bharatpe.common.dao.DocumentsIdProofDao;
import com.bharatpe.common.entities.DocumentsIdProof;
import com.bharatpe.common.entities.Merchant;
import com.bharatpe.common.objects.CommonAPIRequest;
import com.bharatpe.lending.handlers.S3BucketHandler;
import org.springframework.util.StringUtils;

@Service
public class ImageURLService {
	Logger logger = LoggerFactory.getLogger(ImageURLService.class);
	
	@Autowired
	DocumentsIdProofDao documentsIdProofDao;

	@Autowired
	LendingApplicationDao lendingApplicationDao;

	@Autowired
	S3BucketHandler s3BucketHandler;
	
	@Autowired
	LendingEkycDao lendingEkycDao;

	@Autowired
	PhonebookDao phonebookDao;

	@Autowired
	RedisNotificationService redisNotificationService;

	@Value("${aws.s3.bucket}")
	private String bucket;
	
	public Map<String, Object> fetchAndWrapResult(Merchant merchant, CommonAPIRequest commonAPIRequest) {
		Map<String, Object> result = new HashMap<String, Object>();
		Long applicationId =  commonAPIRequest.getPayload().get("application_id") != null ? Long.parseLong(commonAPIRequest.getPayload().get("application_id").toString()) : null;
		if(applicationId == null || applicationId <= 0) {
			logger.info("Invalid Application Id: {} for merchant : {}", applicationId, merchant.getId());
			result.put("success", false);
			return result;
		}
		LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchantAndStatus(applicationId, merchant, "draft");
		if(lendingApplication == null) {
			logger.info("Application not found for Id: {} for merchant : {}", applicationId, merchant.getId());
			result.put("success", false);
			return result;
		}

		logger.info("Application: {}", lendingApplication);
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
		if (finalCall) {
			redisNotificationService.sendNotificationForAppliedApplication(merchant.getId(), lendingApplication);
		}
		return result;
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
		List<DocumentsIdProof> documentsIdProofList = documentsIdProofDao.findByMerchantAndLendingApplication(merchant, lendingApplication);

		for(DocumentsIdProof documentsIdProof : documentsIdProofList) {
			if (documentsIdProof.getProofType().equalsIgnoreCase("eAadhar")) {
				continue;
			}
			Map<String, Object> proof = new LinkedHashMap<>();
			proof.put("proof_type",documentsIdProof.getProofType());
			proof.put("single_page_document",documentsIdProof.getSinglePage() == 1 ? true : false);

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
			finalResponse.add(proof);
		}
		return finalResponse;
	}
}
