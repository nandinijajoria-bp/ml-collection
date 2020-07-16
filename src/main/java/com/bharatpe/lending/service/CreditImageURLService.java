package com.bharatpe.lending.service;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.bharatpe.common.dao.DocumentsIdProofDao;
import com.bharatpe.common.entities.DocumentsIdProof;
import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.Merchant;
import com.bharatpe.common.objects.CommonAPIRequest;
import com.bharatpe.lending.common.dao.CreditApplicationDao;
import com.bharatpe.lending.common.dao.LendingEkycDao;
import com.bharatpe.lending.common.dao.MerchantDocumentProofDao;
import com.bharatpe.lending.common.entity.CreditApplication;
import com.bharatpe.lending.common.entity.LendingEkyc;
import com.bharatpe.lending.common.entity.MerchantDocumentProof;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.handlers.S3BucketHandler;

@Service
public class CreditImageURLService {

Logger logger = LoggerFactory.getLogger(ImageURLService.class);
	
	@Autowired
	MerchantDocumentProofDao merchantDocumentProofDao;

	@Autowired
	CreditApplicationDao creditApplicationDao;

	@Autowired
	S3BucketHandler s3BucketHandler;
	
	@Autowired
	LendingEkycDao lendingEkycDao;

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
		CreditApplication creditApplication = creditApplicationDao.findByIdAndMerchantIdAndStatus(applicationId, merchant.getId(), "draft");
		if(creditApplication == null) {
			logger.info("Application not found for Id: {} for merchant : {}", applicationId, merchant.getId());
			result.put("success", false);
			return result;
		}
		Boolean eKycDone=isEkycDone(merchant);
		if(eKycDone==null){
			result.put("success", false);
			return result;
		}
		result.put("isEKYC",eKycDone);
		logger.info("Application: {}", creditApplication);
		List<Map<String, Object>> data = fetchImageUrl(merchant, creditApplication, commonAPIRequest);
		result.put("proofs", data);
		result.put("success", true);
		
		return result;
	}
	
	public Boolean isEkycDone(Merchant merchant) {
		try{
			LendingEkyc lendingEkyc=lendingEkycDao.findSuccessEkyc(merchant.getId());
			if(lendingEkyc!=null){
				return true;
			}
			return false;
		}
		catch(Exception e) {
			logger.error("Error occured while checking for ekyc status",e);
			return null;
		}
	}
	
	public List<Map<String, Object>> fetchImageUrl(Merchant merchant,CreditApplication creditApplication, CommonAPIRequest commonAPIRequest) {
		List<Map<String, Object>> finalResponse = new ArrayList<>();
		List<MerchantDocumentProof> documentsIdProofList = merchantDocumentProofDao.findAllByMerchantId(merchant.getId());

		for(MerchantDocumentProof documentsIdProof : documentsIdProofList) {
			Map<String, Object> proof = new LinkedHashMap<>();
			proof.put("proof_type",documentsIdProof.getProofType());
			 proof.put("single_page_document",1);

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
					proof.put("single_page_document", (int)proof.get("single_page_document") - 1);
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
