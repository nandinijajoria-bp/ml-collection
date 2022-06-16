package com.bharatpe.lending.service;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.slave.dao.MerchantDocumentProofDaoSlave;
import com.bharatpe.lending.common.slave.entity.MerchantDocumentProofSlave;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.bharatpe.common.objects.CommonAPIRequest;
import com.bharatpe.lending.common.dao.CreditApplicationDao;
import com.bharatpe.lending.common.dao.LendingEkycDao;
import com.bharatpe.lending.common.entity.CreditApplication;
import com.bharatpe.lending.common.entity.LendingEkyc;
import com.bharatpe.lending.common.entity.MerchantDocumentProof;
import com.bharatpe.lending.handlers.S3BucketHandler;

@Service
public class CreditImageURLService {

Logger logger = LoggerFactory.getLogger(CreditImageURLService.class);
	
	@Autowired
	MerchantDocumentProofDaoSlave merchantDocumentProofDaoSlave;

	@Autowired
	CreditApplicationDao creditApplicationDao;

	@Autowired
	S3BucketHandler s3BucketHandler;
	
	@Autowired
	LendingEkycDao lendingEkycDao;

	@Value("${aws.s3.creditline.bucket}")
	private String bucket;
	
	public Map<String, Object> fetchAndWrapResult(BasicDetailsDto merchant, CommonAPIRequest commonAPIRequest) {
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
		Boolean eKycDone=isEkycDone(merchant, creditApplication.getId());
		if(eKycDone==null){
			result.put("success", false);
			return result;
		}
		result.put("allow_route", allowRoute(creditApplication, merchant, eKycDone));
		result.put("isEKYC",eKycDone);
		logger.info("Application: {}", creditApplication);
		List<Map<String, Object>> data = fetchImageUrl(merchant, creditApplication, commonAPIRequest);
		result.put("proofs", data);
		result.put("success", true);
		
		return result;
	}

	private boolean allowRoute(CreditApplication creditApplication, BasicDetailsDto merchant, Boolean isEkycDone) {
		boolean selfie = false;
		boolean pancard = false;
		boolean poa = false;
		List<MerchantDocumentProofSlave> documentsIdProofList = merchantDocumentProofDaoSlave.findByMerchantIdAndOwnerIdAndOwnerType(merchant.getId(), creditApplication.getId(), "LENDING");
		for (MerchantDocumentProofSlave merchantDocumentProof : documentsIdProofList) {
			if (merchantDocumentProof.getProofType().equalsIgnoreCase("selfie")) {
				selfie = true;
			} else if (merchantDocumentProof.getProofType().equalsIgnoreCase("pancard")) {
				pancard = true;
			} else {
				poa = true;
			}
		}
		return selfie && pancard && (isEkycDone || poa);
	}
	
	public Boolean isEkycDone(BasicDetailsDto merchant, Long applicationId) {
		try{
			LendingEkyc lendingEkyc=lendingEkycDao.findSuccessEkyc(merchant.getId(), applicationId);
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
	
	public List<Map<String, Object>> fetchImageUrl(BasicDetailsDto merchant,CreditApplication creditApplication, CommonAPIRequest commonAPIRequest) {
		List<Map<String, Object>> finalResponse = new ArrayList<>();
		List<MerchantDocumentProofSlave> documentsIdProofList = merchantDocumentProofDaoSlave.findByMerchantIdAndOwnerIdAndOwnerType(merchant.getId(), creditApplication.getId(), "LENDING");

		for(MerchantDocumentProofSlave documentsIdProof : documentsIdProofList) {
			if (documentsIdProof.getProofType().equalsIgnoreCase("eAadhar")) {
				continue;
			}
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
