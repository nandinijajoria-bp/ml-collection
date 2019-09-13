package com.bharatpe.lending.service;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.bharatpe.common.constants.ResponseCode;
import com.bharatpe.common.dao.DocumentsIdProofDao;
import com.bharatpe.common.entities.DocumentsIdProof;
import com.bharatpe.common.objects.CommonAPIRequest;

@Service
public class ImageURLService {
	Logger logger = LoggerFactory.getLogger(ImageURLService.class);
	
	@Autowired
	DocumentsIdProofDao documentsIdProofDao;
	
	private Long merchantId;
	private Long applicationId;
	final String S3_BUCKET_NAME = "bharatpe-staging/lending-document";
	
	public List<Map<String, Object>> runService(HttpServletRequest request, HttpServletResponse response, CommonAPIRequest commonAPIRequest) {
		List<Map<String, Object>> finalResponse = new ArrayList<>();
		
		this.merchantId = Long.parseLong(request.getAttribute("merchantId").toString());
		this.applicationId =  commonAPIRequest.getPayload().get("application_id") != null ? Long.parseLong(commonAPIRequest.getPayload().get("application_id").toString()) : null;
		
		if(this.applicationId != null) {
			List<DocumentsIdProof> documentsIdProofList = documentsIdProofDao.findByMerchantIdAndApplicationId(this.merchantId, this.applicationId);
			
			for(DocumentsIdProof documentsIdProof : documentsIdProofList) {
				Map<String, Object> proof = new LinkedHashMap<>();
				List<String> imageURL = new ArrayList<>();
				if(documentsIdProof.getProofFrontSide() != null) {
					try {
						String frontURL = getTemporaryPublicURL(documentsIdProof.getProofFrontSide());
						imageURL.add(frontURL);
					} catch (FileNotFoundException e) {
						imageURL.add(null);
						logger.info("ImageURLService file not found in S3 bucket for key : {}", documentsIdProof.getProofFrontSide());
					} catch (Exception e) {
						logger.info("ImageURLService exception while fetching S3 bucket for key : {}, message : {}", documentsIdProof.getProofFrontSide(), e.getMessage());
					}
				}
				if(documentsIdProof.getProofBackSide() != null) {
					try {
						String backURL = getTemporaryPublicURL(documentsIdProof.getProofBackSide());
						imageURL.add(backURL);
					} catch (FileNotFoundException e) {
						logger.info("ImageURLService file not found in S3 bucket for key : {}", documentsIdProof.getProofBackSide());
					} catch (Exception e) {
						logger.info("ImageURLService exception while fetching S3 bucket for key : {}, message : {}", documentsIdProof.getProofBackSide(), e.getMessage());
					}
				}
				Boolean singlePageFlag = (documentsIdProof.getSinglePage() == 1) ? true : false;
				proof.put("proof_type",documentsIdProof.getProofType());
				proof.put("proof",imageURL);
				proof.put("single_page_document",singlePageFlag);
				finalResponse.add(proof);
			}
		}else {
			response.setStatus(Integer.parseInt(ResponseCode.BAD_REQUEST));
			logger.info("ImageURLService No applicationId was passed!");
		}
		
		return finalResponse;
	}
	
	private AmazonS3 createS3BucketConnection() {
		AmazonS3 s3client = null;
		try {
			//create connection
			AWSCredentials credentials = new BasicAWSCredentials(
					  "AKIAJ7OQDZMK2M2BTWMA", 
					  "WPtGiG3l/HuLf5WwLFj1pOaFBR5wNBj+NvzGUvoE"
					);
			s3client = AmazonS3ClientBuilder
					  .standard()
					  .withCredentials(new AWSStaticCredentialsProvider(credentials))
					  .withRegion(Regions.AP_SOUTH_1)
					  .build();
		}catch(Exception e) {
			logger.info("UploadDocumentService exception while creating connection to S3 bucket message : {}",e.getMessage());
		}
		return s3client;
	}

	private String getTemporaryPublicURL(String key) throws FileNotFoundException {
	    try {
	    	AmazonS3 s3client = createS3BucketConnection();
	        return s3client.generatePresignedUrl(S3_BUCKET_NAME, key, new DateTime().plusMinutes(15).toDate()).toString();
	    }
	    catch (AmazonS3Exception exception){
	        if(exception.getStatusCode() == 404){
	            throw new FileNotFoundException(key);
	        }
	        else{
	            throw exception;
	        }
	    }
	}
}
