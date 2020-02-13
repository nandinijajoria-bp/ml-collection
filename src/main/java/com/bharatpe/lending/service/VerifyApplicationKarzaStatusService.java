package com.bharatpe.lending.service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.bharatpe.common.constants.ResponseCode;
import com.bharatpe.common.dao.DocAuthenticationDao;
import com.bharatpe.common.dao.DocKycDetailsDao;
import com.bharatpe.common.dao.DocumentsIdProofDao;
import com.bharatpe.common.dao.MerchantDao;
import com.bharatpe.common.entities.DocAuthentication;
import com.bharatpe.common.entities.DocKycDetails;
import com.bharatpe.common.entities.DocumentsIdProof;
import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.Merchant;
import com.bharatpe.common.objects.CommonAPIRequest;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.handlers.KarzaHandler;
import com.bharatpe.lending.handlers.S3BucketHandler;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class VerifyApplicationKarzaStatusService {
	private Logger logger = LoggerFactory.getLogger(VerifyApplicationKarzaStatusService.class);
	
	@Autowired
	MerchantDao merchantDao;
	
	@Autowired
	LendingApplicationDao lendingApplicationDao;
	
	@Autowired
	DocKycDetailsDao docKycDetailsDao;
	
	@Autowired
	DocAuthenticationDao docAuthenticationDao;
	
	@Autowired
	DocumentsIdProofDao documentsIdProofDao;
	
	@Autowired
	S3BucketHandler s3BucketHandler;
	
	@Autowired
	KarzaHandler karzaHandler;

	@Value("${aws.s3.bucket}")
	private String bucket;
	
	public Map<String, String> verifyApplicationStatusUsingKarza(HttpServletResponse response, CommonAPIRequest commonAPIRequest) {
		Map<String, String> finalResponse = new LinkedHashMap<>();
		
		Long applicationId =  Long.parseLong(commonAPIRequest.getPayload().get("application_id").toString());
		Long merchantId = Long.parseLong(commonAPIRequest.getPayload().get("merchant_id").toString());
		Long docId = Long.parseLong(commonAPIRequest.getPayload().get("doc_id").toString());
		
		Boolean validMerchantFlag = isValidMerchant(merchantId);
		
		if(validMerchantFlag) {
			Boolean validApplicationFlag = isValidApplication(merchantId, applicationId);
			if(validApplicationFlag) {
				finalResponse = verifyApplicationStatusUsingKarza(merchantId, applicationId, docId);
			}else {
				logger.info("SaveApplicationAddressService invalid Application Id", applicationId);
				response.setStatus(Integer.parseInt(ResponseCode.BAD_REQUEST));
				finalResponse.put("response","failed");
				finalResponse.put("message","Invalid Application Id");
			}
		}else {
			logger.info("SaveApplicationAddressService invalid Merchant Id", merchantId);
			response.setStatus(Integer.parseInt(ResponseCode.BAD_REQUEST));
			finalResponse.put("response","failed");
			finalResponse.put("message","Invalid Merchant Id");
		}
		
		
		return finalResponse;
	}
	
	private Boolean isValidMerchant(Long merchantId) {
		Boolean response = false;
		
		Optional<Merchant> merchant = merchantDao.findById(merchantId);
		if(merchant.isPresent()) {
			response = true;
		}
		
		return response;
	}
	
	private Boolean isValidApplication(Long merchantId, Long applicationId) {
		Boolean response = false;
		
//		LendingApplication application = lendingApplicationDao.findByApplicationIdAndMerchantId(applicationId, merchantId);
//		if(application != null) {
//			response = true;
//		}
		
		return response;
	}
	
	private Map<String, String> verifyApplicationStatusUsingKarza(Long merchantId, Long applicationId, Long docId) {
		Map<String, String> response = new LinkedHashMap<>();
		
		List<Object[]> docDetails = docKycDetailsDao.findPancardDetails(merchantId, applicationId, docId);
		if(docDetails.size() > 0) {
			response = validatePancardUsingKarzaAndSaveDetails(docDetails, applicationId);
		}else {
			response = fetchAndVaildateDocUsingKarzaAndSaveDetails(docId, merchantId, applicationId);
		}
		
		return response;
	}
	
	private Map<String, String> validatePancardUsingKarzaAndSaveDetails(List<Object[]> docDetails, Long applicationId) {
		Map<String, String> response = new LinkedHashMap<>();
		String docNumber = null;
		String personName = null;
		String dob = null;
		Long docAuthId = null;
		for(Object[] obj : docDetails) {
			docNumber = obj[0].toString();
			personName = obj[1].toString();
			dob = obj[2].toString();
			docAuthId = Long.parseLong(obj[3].toString());
		}
		
		String panAuthResponse = karzaHandler.curlKarzaPanAuthenticationAPI(docNumber, personName, dob);
		
		if(!panAuthResponse.isEmpty()) {
			response = processAndSavePanAuthenticationResponse(panAuthResponse, docAuthId, applicationId);
		}else {
			logger.info("VerifyApplicationKarzaStatusService karza pan authentication api failure with blank response for panNumber : {}, dob : {}, name : {}",docNumber, personName, dob);
			response.put("response","failed");
			response.put("message","Karza Authentication for this document is failed.");
			response.put("doc_status","FAILED");
		}
		
		return response;
	}
	
	private Map<String, String> processAndSavePanAuthenticationResponse(String response, Long docAuthId, Long applicationId) {
		Map<String, String> finalResponse = new LinkedHashMap<>();
		
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
		
		String docStatus = "FAILED";
		String duplicate = "";
		String nameMatch = "";
		String dobMatch = "";
		String authStatus = "";
		
		if(status.equals("101")) {
			Map<String, String> result = (Map<String, String>) responseMap.get("result");
			
			docStatus = result.get("status");
			duplicate = String.valueOf(result.get("duplicate"));
			nameMatch = String.valueOf(result.get("nameMatch"));
			dobMatch = String.valueOf(result.get("dobMatch"));
			
			if(String.valueOf(result.get("duplicate")).equals("false") && String.valueOf(result.get("nameMatch")).equals("true") && String.valueOf(result.get("dobMatch")).equals("true") ) {
				authStatus = "ACCEPTED";
				lendingApplicationDao.updateApplicationManualKyc("APPROVED", applicationId);
			}else {
				authStatus = "REJECTED";
			}
		}
		docAuthenticationDao.updateDocAuthenticationDetails(authStatus, docStatus, nameMatch, dobMatch, response, duplicate, docAuthId);
		
		finalResponse.put("response","success");
		finalResponse.put("message","Verfication Done.");
		finalResponse.put("doc_status",authStatus);
		
		return finalResponse;
	}
	
	private Map<String, String> fetchAndVaildateDocUsingKarzaAndSaveDetails(Long docId, Long merchantId, Long applicationId) {
		Map<String, String> finalResponse = new LinkedHashMap<>();
		
		String docStatus = getPanCardKycDetails(docId, merchantId, applicationId);
		
		if(docStatus == null) {
			finalResponse.put("response","failed");
			finalResponse.put("message","Problam in getting OCR data from Karza.");
			finalResponse.put("doc_status","FAILED");
		}else {
			finalResponse.put("response","success");
			finalResponse.put("message","Verfication Done.");
			finalResponse.put("doc_status", docStatus);
		}
		
		return finalResponse;
	}
	
	private String getPanCardKycDetails(Long docId, Long merchantId, Long applicationId) {
		String docStatus = null;
		
		Optional<DocumentsIdProof> documentsIdProof = documentsIdProofDao.findById(docId);
		if(documentsIdProof.isPresent()) {
			String fileName = documentsIdProof.get().getProofFrontSide();
			String docType = documentsIdProof.get().getProofType();
			docId = documentsIdProof.get().getId();
			try {
				String tempPublicURL = s3BucketHandler.getTemporaryPublicURL(fileName, bucket);
				Instant start = Instant.now();
				String response = karzaHandler.curlKarzaKycAPI(tempPublicURL);
				Instant end = Instant.now();
				logger.info("Time Taken by Karza kyc API : {} miliseconds", Duration.between(start, end).toMillis());
				if(!response.isEmpty()) {
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
					Integer status = (responseMap != null) ? (Integer) responseMap.get("statusCode") : null;
					
					if(status == 101) {
						docStatus = savePanCardSuccessAuthData(response, docType, docId, merchantId, applicationId);
					}else {
						savePanCardFailedAuthData(response, docType, docId, merchantId);
						
						String requestId = (String) responseMap.get("requestId");
						String failureResponse = (String) responseMap.get("error"); 
						logger.info("UploadDocumentService karza kyc api failure for documentId : {} and api response : {} and karza requestId : {}",docId, failureResponse, requestId);
					}
				}else {
					logger.info("UploadDocumentService karza kyc api failure with blank response for documentId : {}",docId);
				}
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
		}
		
		return docStatus;
	}
	
	private Long savePanCardFailedAuthData(String response, String docType, Long docId, Long merchantId) {
		Long docKycId = saveAddressProofLogData(response, docType, docId, merchantId);
		
		docAuthenticationDao.updateDeletedAt(new Date(), docId, merchantId);
		
		DocAuthentication docAuthToInsert = new DocAuthentication();
//		docAuthToInsert.setDocId(docId);
//		docAuthToInsert.setDocKycDetailsId(docKycId);
//		docAuthToInsert.setMerchantId(merchantId);
		docAuthToInsert.setDocType("pancard");
		docAuthToInsert.setStatus("FAILED");
		docAuthToInsert.setFullResponse(response);
		docAuthenticationDao.save(docAuthToInsert);
		
		return docAuthToInsert.getId();
	}
	
	private Long saveAddressProofLogData(String response, String docType, Long docId, Long merchantId) {
		Long docKycId = null;
		
//		DocKycDetails docKycDetails = docKycDetailsDao.findTop1ByMerchantIdAndDocTypeAndDocId(merchantId, docType, docId);
		DocKycDetails docKycDetails = null;
		if(docKycDetails == null) {
			DocKycDetails kycDetailsToSave = new DocKycDetails();
//			kycDetailsToSave.setMerchantId(merchantId);
//			kycDetailsToSave.setDocId(docId);
			kycDetailsToSave.setDocType(docType);
			kycDetailsToSave.setResponse(response);
			docKycDetailsDao.save(kycDetailsToSave);
			docKycId = kycDetailsToSave.getId();
		}else {
			docKycDetails.setResponse(response);
			docKycDetailsDao.save(docKycDetails);
			docKycId = docKycDetails.getId();
		}
		
		return docKycId;
	}
	
	private String savePanCardSuccessAuthData(String responseString, String docType, Long docId, Long merchantId, Long applicationId) {
		String dob = "";
		String doi = "";
		
		ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> response = null;
		try {
			response = mapper.readValue(responseString, new TypeReference<Map<String, Object>>(){});
		} catch (JsonParseException e) {
			e.printStackTrace();
		} catch (JsonMappingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		List<Map<String, Object>> result = (List<Map<String, Object>>) response.get("result");
		Map<String, Map<String, String>> details = (Map<String, Map<String, String>>) result.get(0).get("details");
		DocKycDetails docKycDetails = null;
//		DocKycDetails docKycDetails = docKycDetailsDao.findTop1ByMerchantIdAndDocTypeAndDocId(merchantId, "pancard", docId);
		docKycDetails.setDocNo(details.get("panNo").get("value"));
		dob = details.get("date").get("value");
		docKycDetails.setPersonName(details.get("name").get("value"));
		doi = details.get("dateOfIssue").get("value");
		docKycDetails.setFatherName(details.get("father").get("value"));
		docKycDetails.setDocType("pancard");
//		docKycDetails.setDocId(docId);
//		docKycDetails.setMerchantId(merchantId);
		
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
		
		Long docKycId = docKycDetails.getId();
		String docStatus = verifyPanCardDetails(docKycId, docId, merchantId, applicationId);
		
		return docStatus;
	}
	
	private String verifyPanCardDetails(Long docKycId, Long docId, Long merchantId, Long applicationId) {
		String docStatus = null;
		Optional<DocKycDetails> docKycDetailsOptional = docKycDetailsDao.findById(docKycId);
		if(docKycDetailsOptional.isPresent()) {
			SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy");
			String dob = docKycDetailsOptional.get().getDob();
			try {
				Date initDate = new SimpleDateFormat("yyyy-MM-dd").parse(dob);
				dob = formatter.format(initDate);
			} catch (ParseException e) {
				e.printStackTrace();
			}
			
			String panAuthResponse = karzaHandler.curlKarzaPanAuthenticationAPI(docKycDetailsOptional.get().getDocNo(), docKycDetailsOptional.get().getPersonName(), dob);
			if(!panAuthResponse.isEmpty()) {
				docStatus = processAndSavePanAuthenticationResponse(panAuthResponse, docKycId, docId, merchantId, applicationId);
			}else {
				docStatus = "FAILED";
				docAuthenticationDao.updateDeletedAt(new Date(), docId, merchantId);
				
				DocAuthentication docAuthToSave = new DocAuthentication();
//				docAuthToSave.setDocId(docId);
//				docAuthToSave.setDocKycDetailsId(docKycId);
//				docAuthToSave.setMerchantId(merchantId);
				docAuthToSave.setDocType("pancard");
				docAuthToSave.setStatus(docStatus);
				docAuthToSave.setFullResponse(panAuthResponse);
				docAuthenticationDao.save(docAuthToSave);
				logger.info("VerifyApplicationKarzaStatusService karza pan authentication api failure with blank response for panNumber : {}, dob : {}, name : {}",docKycDetailsOptional.get().getDocNo(), docKycDetailsOptional.get().getPersonName(), dob);
			}
		}
		
		return docStatus;
	}
	
	private String processAndSavePanAuthenticationResponse(String response, Long docKycId, Long docId, Long merchantId, Long applicationId) {
		String docStatus = null;
		
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
//		docAuthentication.setDocKycDetailsId(docKycId);
//		docAuthentication.setMerchantId(merchantId);
//		docAuthentication.setDocType("pancard");
//		docAuthentication.setFullResponse(response);
//		docAuthentication.setDocId(docId);
		docAuthentication.setCreatedAt(new Date());
		docAuthentication.setUpdatedAt(new Date());
		
		if(status.equals("101")) {
			Map<String, String> result = (Map<String, String>) responseMap.get("result");
			docAuthentication.setDocStatus(result.get("status"));
			docAuthentication.setDuplicate(String.valueOf(result.get("duplicate")));
			docAuthentication.setNameMatch(String.valueOf(result.get("nameMatch")));
			docAuthentication.setDobMatch(String.valueOf(result.get("dobMatch")));
			if(String.valueOf(result.get("duplicate")).equals("false") && String.valueOf(result.get("nameMatch")).equals("true") && String.valueOf(result.get("dobMatch")).equals("true") ) {
				docStatus = "ACCEPTED";
				docAuthentication.setStatus(docStatus);
				lendingApplicationDao.updateApplicationManualKyc("APPROVED", applicationId);
			}else {
				docStatus = "REJECTED";
				docAuthentication.setStatus(docStatus);
			}
			
		}else {
			docStatus = "FAILED";
			docAuthentication.setDocStatus(docStatus);
			docAuthentication.setDuplicate(null);
			docAuthentication.setNameMatch(null);
			docAuthentication.setDobMatch(null);
			docAuthentication.setStatus(null);
		}
		docAuthenticationDao.updateDeletedAt(new Date(), docId, merchantId);
		docAuthenticationDao.save(docAuthentication);
		return docStatus;
	}
}
