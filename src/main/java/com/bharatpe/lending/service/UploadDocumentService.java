package com.bharatpe.lending.service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import com.amazonaws.services.dynamodbv2.xspec.M;
import com.bharatpe.common.entities.*;
import com.bharatpe.lending.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.bharatpe.common.constants.ResponseCode;
import com.bharatpe.common.dao.DocAuthenticationDao;
import com.bharatpe.common.dao.DocKycDetailsDao;
import com.bharatpe.common.dao.DocumentsIdProofDao;
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

	public UploadDocumentResponseDTO uploadDocument(Merchant merchant, RequestDTO<UploadDocumentRequestDTO> requestDTO) {
		Map<String, Object> finalResponse = new LinkedHashMap<>();
		UploadDocumentResponseDTO uploadDocumentResponse = new UploadDocumentResponseDTO();
		uploadDocumentResponse.setSuccess(false);

		UploadDocumentRequestDTO uploadDocumentRequest = requestDTO.getPayload();
		Long applicationId =  uploadDocumentRequest.getApplicationId();
		List<UploadDocumentRequestDTO.Document> documents = uploadDocumentRequest.getDocuments();

		if(applicationId == null || applicationId <= 0 || documents == null || documents.isEmpty()) {
			logger.info("Invalid Application Id: {} for merchant : {}", applicationId, merchant.getId());
			return uploadDocumentResponse;
		}

		LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchantAndStatus(applicationId, merchant, "draft");
		if(lendingApplication ==  null) {
			logger.info("Invalid Application Id: {} for merchant : {}", applicationId, merchant.getId());
			return uploadDocumentResponse;
		}

		List<DocumentsIdProof> documentsIdProofList = documentsIdProofdao.findByMerchantAndLendingApplication(merchant, lendingApplication);
		Boolean isUpdate = false;
		if(documentsIdProofList.size() > 0) {
			isUpdate = true;
		}
		List<UploadDocumentResponseDTO.Document> documentList = processAndUploadDocuments(documents, isUpdate, merchant, lendingApplication, requestDTO.getMeta(), uploadDocumentResponse);

		if(documentList.size() > 0) {
			finalResponse.put("success", true);
			uploadDocumentResponse.setSuccess(true);
		}
		uploadDocumentResponse.setDocument(documentList);
		uploadDocumentResponse.setSelectedLoan(LoanUtil.prepareSelectedLoanForClient(lendingApplication));
		return uploadDocumentResponse;
	}
	
	private List<UploadDocumentResponseDTO.Document> processAndUploadDocuments(List<UploadDocumentRequestDTO.Document> documents, Boolean isUpdate, Merchant merchant, LendingApplication lendingApplication, MetaDTO meta, UploadDocumentResponseDTO uploadDocumentResponse) {
		List<UploadDocumentResponseDTO.Document> documentList = new ArrayList<>();

		for(UploadDocumentRequestDTO.Document document : documents) {
			if(isUpdate && !document.getChangeFlag()) {
				continue;
			}
			
			if(document.getProof() != null && document.getProof().isEmpty()) {
				logger.error("Empty Documents");
			}
			
			String proofType = document.getProofType();
			int singlePageDocument = document.getSinglePageDocument() ? 1 : 0;

			Map<String, String>	proofSides = processAndUploadProof(document.getProof(), merchant);

			String frontSide = proofSides.get("frontSide");
			String backSide = proofSides.get("backSide");

			DocumentsIdProof documentsIdProof = null;
			if(isUpdate) {
				documentsIdProof = updateDocumentIdProof(proofType, frontSide, backSide, singlePageDocument, merchant, lendingApplication, meta);
			} else {
				documentsIdProof = insertDocumentIdProof(proofType, frontSide, backSide, singlePageDocument, merchant, lendingApplication, meta);
			}

			if(documentsIdProof != null) {
				UploadDocumentResponseDTO.Document documentResponse = uploadDocumentResponse.new Document();
				documentResponse.setProofId(documentsIdProof.getId());
				documentResponse.setProofType(proofType);
				documentResponse.setSinglePageDocument(singlePageDocument);
				documentList.add(documentResponse);
			}
			karzaVerification(proofType, frontSide, backSide, singlePageDocument, documentsIdProof, merchant, lendingApplication);
		}
		return documentList;
	}
	
	private Map<String, String> processAndUploadProof(List<String> proof, Merchant merchant) {
		Map<String, String> proofSides = new LinkedHashMap<>();
		proofSides.put("frontSide", "");
		proofSides.put("backSide", "");

		String frontBase64Encoded = processBase64String(proof.get(0));
		String frontUrl = s3BucketHandler.uploadToS3Bucket(frontBase64Encoded, merchant.getId());
		proofSides.put("frontSide", frontUrl);

		if(proof.size() > 1 && !StringUtils.isEmpty(proof.get(1))) {
			String backBase64Encoded = processBase64String(proof.get(1));
			String backUrl = s3BucketHandler.uploadToS3Bucket(backBase64Encoded, merchant.getId());
			proofSides.put("backSide", backUrl);
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
	
	private DocumentsIdProof insertDocumentIdProof(String proofType, String frontSide, String backSide, int singlePageDocument, Merchant merchant, LendingApplication lendingApplication, MetaDTO meta) {
		DocumentsIdProof documentsIdProof = new DocumentsIdProof();
		documentsIdProof.setMerchant(merchant);
		documentsIdProof.setLendingApplication(lendingApplication);
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
	
	private DocumentsIdProof updateDocumentIdProof(String proofType, String frontSide, String backSide, int singlePageDocument, Merchant merchant, LendingApplication lendingApplication, MetaDTO meta) {
		DocumentsIdProof documentsIdProof = documentsIdProofdao.findByMerchantAndLendingApplicationAndProofType(merchant, lendingApplication, proofType);
		if(documentsIdProof != null) {
			documentsIdProof.setProofFrontSide(frontSide);
			documentsIdProof.setProofBackSide(backSide);
			documentsIdProof.setSinglePage(singlePageDocument);
			documentsIdProof.setLatitude(meta.getLatitude());
			documentsIdProof.setLongitude(meta.getLongitude());
			documentsIdProof.setIp(meta.getIp());
			documentsIdProofdao.save(documentsIdProof);
		} else {
			documentsIdProof = insertDocumentIdProof(proofType, frontSide, backSide, singlePageDocument, merchant, lendingApplication, meta);
		}
		return documentsIdProof;
	}

	private void karzaVerification(String proofType, String frontSide, String backSide, int singlePageDocument, DocumentsIdProof documentsIdProof, Merchant merchant, LendingApplication lendingApplication) {
		if(proofType.equals("pancard") || proofType.equals("adhaarcard") || proofType.equals("aadharcard") || proofType.equals("votercard") || proofType.equals("passport")) {
			new Thread(() -> {
				kycUsingKarzaAPI(proofType, frontSide, documentsIdProof, merchant, lendingApplication);
				if (singlePageDocument == 0) {
					kycUsingKarzaAPI(proofType, backSide, documentsIdProof, merchant, lendingApplication);
				}
			}).start();
		}
	}
	
	private void kycUsingKarzaAPI(String proofType, String fileName, DocumentsIdProof documentsIdProof, Merchant merchant, LendingApplication lendingApplication) {
		try {
			Instant start = Instant.now();
//			String tempPublicURL = s3BucketHandler.getTemporaryPublicURL(fileName);
			String tempPublicURL = "";
			Instant end = Instant.now();
			logger.info("Time Taken by AWS S3 ImageUrl API : {} miliseconds", Duration.between(start, end).toMillis());
			boolean isDocAuthEntryMade = false;
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
						DocKycDetails docKycDetails = processAndSaveKycResponse(response, proofType, documentsIdProof, merchant);
						if(proofType.equals("pancard")) {
							start = Instant.now();
							pancardAuthenticationUsingKarzaAPI(responseMap, docKycDetails, documentsIdProof, merchant, lendingApplication);
							end = Instant.now();
							logger.info("Time Taken by Karza Pan Authentication API : {} miliseconds", Duration.between(start, end).toMillis());
							isDocAuthEntryMade = true;
						}
					}else {
						String requestId = (String) responseMap.get("requestId");
						String failureResponse = (String) responseMap.get("error"); 
						logger.info("UploadDocumentService karza kyc api failure for documentId : {} and api response : {} and karza requestId : {}",documentsIdProof.getId(), failureResponse, requestId);
					}
				}else {
					logger.info("UploadDocumentService karza kyc api failure with blank response for documentId : {}",documentsIdProof.getId());
				}
			}else {
				logger.info("UploadDocumentService blank tempURL from S3 bucket for key : {}",fileName);
			}
			
			// TODO: Need to do entry first and update based on the response update the details
			if(proofType.equals("pancard") && !isDocAuthEntryMade) {
				logger.info("Marking blank entries for pancard in DocKycDetails and DocAuthentication for merchant id {}", merchant.getId());
				DocKycDetails docDetails = createFailedDocKycDetails("pancard", documentsIdProof, merchant);
				createFailedEntryForPancardDocAuthentication(docDetails, documentsIdProof, merchant, lendingApplication);
			}
		} catch (FileNotFoundException e) {
			logger.info("UploadDocumentService exception while fetching tempURL from S3 bucket,file not found for key : {}",fileName);
		} catch (Exception e) {
			e.printStackTrace();
			logger.info("UploadDocumentService exception while fetching tempURL from S3 bucket, message : {}",e.getMessage());
		}
	}
	
	private DocKycDetails processAndSaveKycResponse(String responseString, String proofType, DocumentsIdProof documentsIdProof, Merchant merchant) {
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
			docKycDetails.setDocumentsIdProof(documentsIdProof);
			docKycDetails.setMerchant(merchant);
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
			}else if(proofType.equals("adhaarcard") || proofType.equals("aadharcard")) {
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
				logger.info("UploadDocumentService exception while parsing date, message : {}",e.getMessage());
			}
			docKycDetailsDao.save(docKycDetails);
			return docKycDetails;
		}
		return null;
	}
	
	private void pancardAuthenticationUsingKarzaAPI(Map<String, Object> response, DocKycDetails docKycDetails, DocumentsIdProof documentsIdProof, Merchant merchant, LendingApplication lendingApplication) {
		List<Map<String, Object>> result = (List<Map<String, Object>>) response.get("result");
		
		if(result != null && result.size() > 0) {
			Map<String, Map<String, String>> details = (Map<String, Map<String, String>>) result.get(0).get("details");
			String panNumber = details.get("panNo").get("value");
			String dob = details.get("date").get("value");
			String name = details.get("name").get("value");
			
			String curlResponse = karzaHandler.curlKarzaPanAuthenticationAPI(panNumber, name, dob);
			if(!curlResponse.isEmpty()) {
				processAndSavePanAuthenticationResponse(curlResponse, docKycDetails, documentsIdProof, merchant, lendingApplication);
			}else {
				logger.info("UploadDocumentService karza pan authentication api failure with blank response for panNumber : {}, dob : {}, name : {}",panNumber, dob, name);
				createFailedEntryForPancardDocAuthentication(docKycDetails, documentsIdProof, merchant, lendingApplication);
			}
		}
	}
	
	private void processAndSavePanAuthenticationResponse(String response, DocKycDetails docKycDetails, DocumentsIdProof documentsIdProof, Merchant merchant, LendingApplication lendingApplication) {
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
		docAuthentication.setDocKycDetails(docKycDetails);
		docAuthentication.setMerchant(merchant);
		docAuthentication.setDocType("pancard");
		docAuthentication.setFullResponse(response);
		docAuthentication.setDocumentsIdProof(documentsIdProof);
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
//				lendingApplicationDao.updateApplicationManualKyc("APPROVED", lendingApplication.getId());
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
	
	private void createFailedEntryForPancardDocAuthentication(DocKycDetails docKycDetails, DocumentsIdProof documentsIdProof, Merchant merchant, LendingApplication lendingApplication) {
		DocAuthentication docAuthentication = new DocAuthentication();
		docAuthentication.setDocKycDetails(docKycDetails);
		docAuthentication.setMerchant(merchant);
		docAuthentication.setDocType("pancard");
		docAuthentication.setFullResponse("");
		docAuthentication.setDocumentsIdProof(documentsIdProof);
		docAuthentication.setCreatedAt(new Date());
		docAuthentication.setUpdatedAt(new Date());
		docAuthentication.setDocStatus("FAILED");
		docAuthentication.setDuplicate("");
		docAuthentication.setNameMatch("");
		docAuthentication.setDobMatch("");
		docAuthentication.setStatus("");
		docAuthenticationDao.save(docAuthentication);
	}
	
	private DocKycDetails createFailedDocKycDetails(String docType, DocumentsIdProof documentsIdProof, Merchant merchant) {
		DocKycDetails docKycDetails = new DocKycDetails();
		docKycDetails.setDocumentsIdProof(documentsIdProof);
		docKycDetails.setMerchant(merchant);
		docKycDetails.setCreatedAt(new Date());
		docKycDetails.setUpdatedAt(new Date());
		docKycDetails.setModule("LENDING");
		docKycDetails.setDocSide("FRONT");
		docKycDetails.setStatus("");
		docKycDetails.setDocType(docType);
		docKycDetails.setResponse("");
		docKycDetails.setDocNo("");
		docKycDetails.setDob("1970-01-01");
		docKycDetails.setPersonName("");
		docKycDetails.setFatherName("");
		docKycDetailsDao.save(docKycDetails);
		return docKycDetails;
	}
	
}
