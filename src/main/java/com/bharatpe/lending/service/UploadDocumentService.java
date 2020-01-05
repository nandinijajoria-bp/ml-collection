package com.bharatpe.lending.service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import com.amazonaws.services.dynamodbv2.xspec.M;
import com.bharatpe.lending.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.bharatpe.common.constants.ResponseCode;
import com.bharatpe.common.dao.DocAuthenticationDao;
import com.bharatpe.common.dao.DocKycDetailsDao;
import com.bharatpe.common.dao.DocumentsIdProofDao;
import com.bharatpe.common.entities.DocAuthentication;
import com.bharatpe.common.entities.DocKycDetails;
import com.bharatpe.common.entities.DocumentsIdProof;
import com.bharatpe.common.entities.Merchant;
import com.bharatpe.common.objects.CommonAPIRequest;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.handlers.KarzaHandler;
import com.bharatpe.lending.handlers.S3BucketHandler;
import com.bharatpe.lending.util.LoanUtil;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;

@Service
public class UploadDocumentService {
	Logger logger = LoggerFactory.getLogger(UploadDocumentService.class);
	
	@Autowired
	DocumentsIdProofDao documentsIdProofdao;
	
	@Autowired
	DocKycDetailsDao docKycDetailsDao;
	
	@Autowired
	DocAuthenticationDao docAuthenticationDao;
	
	@Autowired
	LendingApplicationDao lendingApplicationDao;
	
	@Autowired
	S3BucketHandler s3BucketHandler;
	
	@Autowired
	KarzaHandler karzaHandler;

	public UploadDocumentResponse uploadDocument(Merchant merchant, RequestDTO<UploadDocumentRequest> requestDTO) {
		Map<String, Object> finalResponse = new LinkedHashMap<>();
		UploadDocumentResponse uploadDocumentResponse = new UploadDocumentResponse();
		uploadDocumentResponse.setSuccess(false);
		UploadDocumentRequest uploadDocumentRequest = requestDTO.getPayload();
		Long applicationId =  uploadDocumentRequest.getApplicationId();
		if(applicationId == null || applicationId <= 0) {
			logger.info("Invalid Application Id: {} for merchant : {}", applicationId, merchant.getId());
			return uploadDocumentResponse;
		}

		List<UploadDocumentRequest.Document> documents = uploadDocumentRequest.getDocuments();

		Boolean isUpdate = false;
		List<DocumentsIdProof> documentsIdProofList = documentsIdProofdao.findByMerchantIdAndApplicationId(merchant.getId(), applicationId);
		if(documentsIdProofList.size() > 0) {
			isUpdate = true;
		}
		List<UploadDocumentResponse.Document> documentList = processAndUploadDocuments(documents, isUpdate, merchant, applicationId, requestDTO.getMeta(), uploadDocumentResponse);

		if(documentList.size() > 0) {
			finalResponse.put("success", true);
			uploadDocumentResponse.setSuccess(true);
		}
		uploadDocumentResponse.setDocument(documentList);
		uploadDocumentResponse.setSelectedLoan(LoanUtil.prepareSelectedLoanForClient(lendingApplicationDao.findByApplicationId(applicationId)));
		return uploadDocumentResponse;
	}
	
	private List<UploadDocumentResponse.Document> processAndUploadDocuments(List<UploadDocumentRequest.Document> documents, Boolean isUpdate, Merchant merchant, Long applicationId, Meta meta, UploadDocumentResponse uploadDocumentResponse) {
		List<UploadDocumentResponse.Document> documentList = new ArrayList<>();

		for(UploadDocumentRequest.Document document : documents) {
			if(isUpdate && document.getChangeFlag() != 1) {
				continue;
			}

			String proofType = document.getProofType();
			int singlePageDocument = document.getSinglePageDocument() ? 1 : 0;

			Map<String, String>	proofSides = processAndUploadProof(document.getProof(), merchant);

			String frontSide = proofSides.get("frontSide");
			String backSide = proofSides.get("backSide");

			DocumentsIdProof documentsIdProof = null;
			if(isUpdate) {
				documentsIdProof = updateDocumentIdProof(proofType, frontSide, backSide, singlePageDocument, merchant, applicationId, meta);
			} else {
				documentsIdProof = insertDocumentIdProof(proofType, frontSide, backSide, singlePageDocument, merchant, applicationId, meta);
			}

			if(documentsIdProof != null) {
				UploadDocumentResponse.Document documentResponse = uploadDocumentResponse.new Document();
				documentResponse.setProofId(documentsIdProof.getId());
				documentResponse.setProofType(proofType);
				documentResponse.setSinglePageDocument(singlePageDocument);
				documentList.add(documentResponse);
			}
			karzaVerification(proofType, frontSide, backSide, singlePageDocument, documentsIdProof.getId(), merchant, applicationId);
		}
		return documentList;
	}
	
	private Map<String, String> processAndUploadProof(List<String> proof, Merchant merchant) {
		boolean frontSide = true;
		Map<String, String> proofSides = new LinkedHashMap<>();
		proofSides.put("frontSide", "");
		proofSides.put("backSide", "");
		
		for(String base64Encoded : proof) {
			if(!base64Encoded.isEmpty()) {
				base64Encoded = processBase64String(base64Encoded);
				
				if(frontSide) {
					Instant start = Instant.now();
					String frontUrl = s3BucketHandler.uploadToS3Bucket(base64Encoded, merchant.getId());
					Instant end = Instant.now();
					logger.info("Time Taken by AWS S3 upload API : {} miliseconds", Duration.between(start, end).toMillis());
					proofSides.put("frontSide", frontUrl);
					frontSide = false;
				}else {
					Instant start = Instant.now();
					String backUrl = s3BucketHandler.uploadToS3Bucket(base64Encoded, merchant.getId());
					Instant end = Instant.now();
					logger.info("Time Taken by AWS S3 upload API : {} miliseconds", Duration.between(start, end).toMillis());
					proofSides.put("backSide", backUrl);
				}

			}
		}
		return proofSides;
	}
	
	private String processBase64String(String base64EncodedString) {
		base64EncodedString.replace(' ', '+');
		if(base64EncodedString.contains("base64,")) {
			String [] base64EncodedSplit = base64EncodedString.split("base64,");
			base64EncodedString = base64EncodedSplit[1];
		}
		return base64EncodedString;
	}
	
	private DocumentsIdProof insertDocumentIdProof(String proofType, String frontSide, String backSide, int singlePageDocument, Merchant merchant, Long applicationId, Meta meta) {
		DocumentsIdProof documentsIdProof = new DocumentsIdProof();
		documentsIdProof.setMerchantId(merchant.getId());
		documentsIdProof.setApplicationId(applicationId);
		documentsIdProof.setProofType(proofType);
		documentsIdProof.setProofFrontSide(frontSide);
		documentsIdProof.setProofBackSide(backSide);
		documentsIdProof.setStatus("pending_verification");
		documentsIdProof.setSinglePage(singlePageDocument);
		documentsIdProof.setLatitude(meta.getLatitude());
		documentsIdProof.setLongitude(meta.getLongitude());
		documentsIdProof.setIp(meta.getIp());
		documentsIdProofdao.save(documentsIdProof);
		return documentsIdProof;
	}
	
	private DocumentsIdProof updateDocumentIdProof(String proofType, String frontSide, String backSide, int singlePageDocument, Merchant merchant, Long applicationId, Meta meta) {
		int updateId = documentsIdProofdao.updateDocSides(frontSide, backSide, singlePageDocument, meta.getLatitude(), meta.getLongitude(), meta.getIp(), merchant.getId(), applicationId, proofType);

		if(updateId > 0) {
			return documentsIdProofdao.findByMerchantIdAndApplicationIdAndProofType(merchant.getId(), applicationId, proofType);
		}
		return null;
	}
	
	private void karzaVerification(String proofType, String frontSide, String backSide, int singlePageDocument, Long documentId, Merchant merchant, Long applicationId) {
		if(proofType.equals("pancard") || proofType.equals("adhaarcard") || proofType.equals("votercard") || proofType.equals("passport")) {
			Runnable runnable = () -> {
				kycUsingKarzaAPI(proofType, frontSide, documentId, merchant, applicationId);
				if (singlePageDocument == 0) {
					kycUsingKarzaAPI(proofType, backSide, documentId, merchant, applicationId);
				}
			};
		}
	}
	
	private void kycUsingKarzaAPI(String proofType, String fileName, Long documentId, Merchant merchant, Long applicationId) {
		try {
			Instant start = Instant.now();
			String tempPublicURL = s3BucketHandler.getTemporaryPublicURL(fileName);
			Instant end = Instant.now();
			logger.info("Time Taken by AWS S3 ImageUrl API : {} miliseconds", Duration.between(start, end).toMillis());
			if(!tempPublicURL.isEmpty()) {
				start = Instant.now();
				String response = karzaHandler.curlKarzaKycAPI(tempPublicURL);
				end = Instant.now();
				logger.info("Time Taken by Karza kyc API : {} miliseconds", Duration.between(start, end).toMillis());
				if(!response.isEmpty()) {
					ObjectMapper mapper = new ObjectMapper();
	    	        Map<String, Object> responseMap = mapper.readValue(response, new TypeReference<Map<String, Object>>(){});
	    	        Integer status = (Integer) responseMap.get("statusCode");
					
					if(status == 101) {
						Long insertId = processAndSaveKycResponse(response, proofType, documentId, merchant);
						if(proofType.equals("pancard")) {
							start = Instant.now();
							pancardAuthenticationUsingKarzaAPI(responseMap, insertId, documentId, merchant, applicationId);
							end = Instant.now();
							logger.info("Time Taken by Karza Pan Authentication API : {} miliseconds", Duration.between(start, end).toMillis());
						}
					}else {
						String requestId = (String) responseMap.get("requestId");
						String failureResponse = (String) responseMap.get("error"); 
						logger.info("UploadDocumentService karza kyc api failure for documentId : {} and api response : {} and karza requestId : {}",documentId, failureResponse, requestId);
					}
				}else {
					logger.info("UploadDocumentService karza kyc api failure with blank response for documentId : {}",documentId);
				}
			}else {
				logger.info("UploadDocumentService blank tempURL from S3 bucket for key : {}",fileName);
			}
		} catch (FileNotFoundException e) {
			logger.info("UploadDocumentService exception while fetching tempURL from S3 bucket,file not found for key : {}",fileName);
		} catch (Exception e) {
			e.printStackTrace();
			logger.info("UploadDocumentService exception while fetching tempURL from S3 bucket, message : {}",e.getMessage());
		}
	}
	
	private Long processAndSaveKycResponse(String responseString, String proofType, Long documentId, Merchant merchant) {
		Long docKycDetailsInsertId = null;
		ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> response = null;
		try {
			response = mapper.readValue(responseString, new TypeReference<Map<String, Object>>(){});
		} catch (JsonParseException e1) {
			e1.printStackTrace();
		} catch (JsonMappingException e1) {
			e1.printStackTrace();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		List<Map<String, Object>> result = (response != null) ? (List<Map<String, Object>>) response.get("result") : null;
		
		if(result != null && result.size() > 0) {
			String dob = "";
			String doi = "";
			DocKycDetails docKycDetails = new DocKycDetails();
			docKycDetails.setDocId(documentId);
			docKycDetails.setMerchantId(merchant.getId());
			docKycDetails.setCreatedAt(new Date());
			docKycDetails.setUpdatedAt(new Date());
			docKycDetails.setModule("LENDING");
			docKycDetails.setStatus("");
			docKycDetails.setDocType(proofType);
			docKycDetails.setResponse(responseString);
			String type	= (String) result.get(0).get("type");
			Map<String, Map<String, String>> details	= (Map<String, Map<String, String>>) result.get(0).get("details");
			if(proofType.equals("votercard")) {
				if(type.equals("Voterid Front")) {
					docKycDetails.setDocNo(details.get("voterid").get("value"));
					docKycDetails.setFatherName(details.get("relation").get("value"));
					docKycDetails.setPersonName(details.get("name").get("value"));
					dob = details.get("dob").get("value");
					docKycDetails.setDocSide("FRONT");
				}else if(type.equals("Voterid Back")) {
					docKycDetails.setDocNo(details.get("voterid").get("value"));
					dob = details.get("dob").get("value");
					docKycDetails.setAddress(details.get("address").get("value"));
					docKycDetails.setGender(details.get("gender").get("value"));
					docKycDetails.setPincode(Integer.parseInt(details.get("pin").get("value")));
					docKycDetails.setCity(details.get("addressSplit").get("city"));
					docKycDetails.setState(details.get("addressSplit").get("state"));
					docKycDetails.setDocSide("BACK");
				}
			}else if(proofType.equals("pancard")) {
				docKycDetails.setDocNo(details.get("panNo").get("value"));
				dob = details.get("date").get("value");
				docKycDetails.setPersonName(details.get("name").get("value"));
				doi = details.get("dateOfIssue").get("value");
				docKycDetails.setFatherName(details.get("father").get("value"));
			}else if(proofType.equals("passport")) {
				docKycDetails.setDocNo(details.get("passportNum").get("value"));
				dob = details.get("dob").get("value");
				docKycDetails.setPersonName(details.get("givenName").get("value") + " " + details.get("surname").get("value"));
				doi = details.get("doi").get("value");
				docKycDetails.setGender(details.get("gender").get("value"));
				docKycDetails.setCountryCode(details.get("countryCode").get("value"));
			}else if(proofType.equals("adhaarcard")) {
				if(type.equals("Aadhaar Front Bottom")) {
					docKycDetails.setDocNo(details.get("aadhaar").get("value"));
					dob = details.get("dob").get("value");
					docKycDetails.setPersonName(details.get("name").get("value"));
					doi = details.get("yob").get("value");
					docKycDetails.setGender(details.get("gender").get("value"));
					docKycDetails.setQr(details.get("qr").get("value"));
					docKycDetails.setFatherName(details.get("father").get("value"));
					docKycDetails.setDocSide("FRONT");
				}else if(type.equals("Aadhaar Back")) {
					docKycDetails.setDocNo(details.get("aadhaar").get("value"));
					docKycDetails.setQr(details.get("qr").get("value"));
					docKycDetails.setFatherName(details.get("father").get("value"));
					docKycDetails.setPincode(Integer.parseInt(details.get("pin").get("value")));
					docKycDetails.setCity(details.get("addressSplit").get("city"));
					docKycDetails.setState(details.get("addressSplit").get("state"));
					docKycDetails.setAddress(details.get("address").get("value"));
					docKycDetails.setDocSide("BACK");
				}
			}
			Date initDate;
			try {
				SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
				if(!dob.isEmpty()) {
					initDate = new SimpleDateFormat("dd/MM/yyyy").parse(dob);
					docKycDetails.setDob(formatter.format(initDate));
				}
				
				if(!doi.isEmpty()) {
					initDate = new SimpleDateFormat("dd/MM/yyyy").parse(doi);
					formatter = new SimpleDateFormat("yyyy-MM-dd");
					docKycDetails.setDoi(formatter.format(initDate));
				}
				
			} catch (ParseException e) {
				e.printStackTrace();
				logger.info("UploadDocumentService exception while parsing date, message : {}",e.getMessage());
			}
			docKycDetailsDao.save(docKycDetails);
			docKycDetailsInsertId = docKycDetails.getId();
		}
		return docKycDetailsInsertId;
	}
	
	private void pancardAuthenticationUsingKarzaAPI(Map<String, Object> response, Long insertId, Long documentId, Merchant merchant, Long applicationId) {
		List<Map<String, Object>> result = (List<Map<String, Object>>) response.get("result");
		
		if(result != null && result.size() > 0) {
			Map<String, Map<String, String>> details = (Map<String, Map<String, String>>) result.get(0).get("details");
			String panNumber = details.get("panNo").get("value");
			String dob = details.get("date").get("value");
			String name = details.get("name").get("value");
			
			String curlResponse = karzaHandler.curlKarzaPanAuthenticationAPI(panNumber, name, dob);
			if(!curlResponse.isEmpty()) {
				processAndSavePanAuthenticationResponse(curlResponse, insertId, documentId, merchant, applicationId);
			}else {
				logger.info("UploadDocumentService karza pan authentication api failure with blank response for panNumber : {}, dob : {}, name : {}",panNumber, dob, name);
			}
		}
	}
	
	private void processAndSavePanAuthenticationResponse(String response, Long insertId, Long documentId, Merchant merchant, Long applicationId) {
		ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> responseMap = null;
		try {
			responseMap = mapper.readValue(response, new TypeReference<Map<String, Object>>(){});
		} catch (JsonParseException e) {
			e.printStackTrace();
		} catch (JsonMappingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		String status = (responseMap != null) ? (String) responseMap.get("status-code") : "";
		
		DocAuthentication docAuthentication = new DocAuthentication();
		docAuthentication.setDocKycDetailsId(insertId);
		docAuthentication.setMerchantId(merchant.getId());
		docAuthentication.setDocType("pancard");
		docAuthentication.setFullResponse(response);
		docAuthentication.setDocId(documentId);
		docAuthentication.setCreatedAt(new Date());
		docAuthentication.setUpdatedAt(new Date());
		
		if(status.equals("101")) {
			Map<String, String> result = (Map<String, String>) responseMap.get("result");
			docAuthentication.setDocStatus(result.get("status"));
			docAuthentication.setDuplicate(String.valueOf(result.get("duplicate")));
			docAuthentication.setNameMatch(String.valueOf(result.get("nameMatch")));
			docAuthentication.setDobMatch(String.valueOf(result.get("dobMatch")));
			if(String.valueOf(result.get("duplicate")).equals("false") && String.valueOf(result.get("nameMatch")).equals("true") && String.valueOf(result.get("dobMatch")).equals("true") ) {
				docAuthentication.setStatus("ACCEPTED");
				lendingApplicationDao.updateApplicationManualKyc("APPROVED", applicationId);
			}else {
				docAuthentication.setStatus("REJECTED");
			}
			
		}else {
			docAuthentication.setDocStatus("FAILED");
			docAuthentication.setDuplicate("");
			docAuthentication.setNameMatch("");
			docAuthentication.setDobMatch("");
			docAuthentication.setStatus("");
		}
		docAuthenticationDao.save(docAuthentication);
	}
}
