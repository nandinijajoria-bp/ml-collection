package com.bharatpe.lending.service;

import java.io.FileNotFoundException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.bharatpe.common.constants.ResponseCode;
import com.bharatpe.common.dao.DocumentsIdProofDao;
import com.bharatpe.common.entities.DocumentsIdProof;
import com.bharatpe.common.entities.Merchant;
import com.bharatpe.common.objects.CommonAPIRequest;
import com.bharatpe.lending.handlers.S3BucketHandler;

@Service
public class ImageURLService {
	Logger logger = LoggerFactory.getLogger(ImageURLService.class);
	
	@Autowired
	DocumentsIdProofDao documentsIdProofDao;
	
	@Autowired
	S3BucketHandler s3BucketHandler;
	
	public Map<String, Object> fetchAndWrapResult(Merchant merchant, HttpServletResponse response, CommonAPIRequest commonAPIRequest) {
		Map<String, Object> result = new HashMap<String, Object>();
		List<Map<String, Object>> data = fetchImageUrl(merchant, response, commonAPIRequest);
		result.put("proofs", data);
		result.put("success", data.size() > 0 ? true : false);
		return result;
	}
	
	public List<Map<String, Object>> fetchImageUrl(Merchant merchant, HttpServletResponse response, CommonAPIRequest commonAPIRequest) {
		List<Map<String, Object>> finalResponse = new ArrayList<>();
		
		Long merchantId = merchant.getId();
		Long applicationId =  commonAPIRequest.getPayload().get("application_id") != null ? Long.parseLong(commonAPIRequest.getPayload().get("application_id").toString()) : null;
		
		if(applicationId != null) {
			List<DocumentsIdProof> documentsIdProofList = documentsIdProofDao.findByMerchantIdAndApplicationId(merchantId, applicationId);
			
			for(DocumentsIdProof documentsIdProof : documentsIdProofList) {
				Map<String, Object> proof = new LinkedHashMap<>();
				List<String> imageURL = new ArrayList<>();
				if(documentsIdProof.getProofFrontSide() != null && !documentsIdProof.getProofFrontSide().isEmpty()) {
					try {
						String frontURL = s3BucketHandler.getTemporaryPublicURL(documentsIdProof.getProofFrontSide());
						imageURL.add(frontURL);
					} catch (FileNotFoundException e) {
						e.printStackTrace();
						imageURL.add(null);
						logger.info("ImageURLService file not found in S3 bucket for key : {}", documentsIdProof.getProofFrontSide());
					} catch (Exception e) {
						e.printStackTrace();
						logger.info("ImageURLService exception while fetching S3 bucket for key : {}, message : {}", documentsIdProof.getProofFrontSide(), e.getMessage());
					}
				}
				if(documentsIdProof.getProofBackSide() != null && !documentsIdProof.getProofBackSide().isEmpty()) {
					try {
						Instant start = Instant.now();
						String backURL = s3BucketHandler.getTemporaryPublicURL(documentsIdProof.getProofBackSide());
						Instant end = Instant.now();
						logger.info("Time Taken by AWS S3 tempPublicURL API : {} miliseconds", Duration.between(start, end).toMillis());
						imageURL.add(backURL);
					} catch (FileNotFoundException e) {
						e.printStackTrace();
						logger.info("ImageURLService file not found in S3 bucket for key : {}", documentsIdProof.getProofBackSide());
					} catch (Exception e) {
						e.printStackTrace();
						logger.info("ImageURLService exception while fetching S3 bucket for key : {}, message : {}", documentsIdProof.getProofBackSide(), e.getMessage());
					}
				}
				Boolean singlePageFlag = (documentsIdProof.getSinglePage() == 1) ? true : false;
				proof.put("proof_type",documentsIdProof.getProofType());
				proof.put("proof",imageURL);
				proof.put("single_page_document",singlePageFlag);
				finalResponse.add(proof);
			}
		} else {
			response.setStatus(Integer.parseInt(ResponseCode.BAD_REQUEST));
			logger.info("ImageURLService No applicationId was passed!");
		}
		
		return finalResponse;
	}
}
