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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.bharatpe.common.constants.ResponseCode;
import com.bharatpe.common.dao.DocAuthenticationDao;
import com.bharatpe.common.dao.DocKycDetailsDao;
import com.bharatpe.common.dao.DocumentsIdProofDao;
import com.bharatpe.common.objects.CommonAPIRequest;
import com.bharatpe.lending.common.dao.CreditApplicationAddressDao;
import com.bharatpe.lending.common.dao.CreditApplicationDao;
import com.bharatpe.lending.common.dao.MerchantDocumentProofDao;
import com.bharatpe.lending.common.dao.MerchantDocumentProofOcrDao;
import com.bharatpe.lending.common.dao.MerchantDocumentProofRequestDao;
import com.bharatpe.lending.common.entity.CreditApplication;
import com.bharatpe.lending.common.entity.CreditApplicationAddress;
import com.bharatpe.lending.common.entity.MerchantDocumentProof;
import com.bharatpe.lending.common.entity.MerchantDocumentProofOcr;
import com.bharatpe.lending.common.entity.MerchantDocumentProofRequest;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.handlers.KarzaHandler;
import com.bharatpe.lending.handlers.S3BucketHandler;
import com.bharatpe.lending.handlers.SignzyHandler;
import com.bharatpe.lending.util.CreditUtil;
import com.bharatpe.lending.util.LoanUtil;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.Iterator;

@Service

public class UploadDocumentCreditService {
	Logger logger = LoggerFactory.getLogger(UploadDocumentService.class);
	
	@Autowired
	MerchantDocumentProofDao merchantDocumentProofDao;
	
	@Autowired
	CreditUtil creditUtil;
	
	@Autowired
	MerchantDocumentProofOcrDao merchantDocumentProofOcrDao;
	
	@Autowired
	MerchantDocumentProofRequestDao merchantDocumentProofRequestDao;
	 
	
	@Autowired
	CreditApplicationDao creditApplicationDao;
	

	@Autowired
	CreditApplicationAddressDao creditApplicationAddressDao;
	
	@Autowired
	S3BucketHandler s3BucketHandler;
	
	 @Autowired
	SignzyHandler signzyHandler;
	 @Autowired
	 DocumentsIdProofDao  documentsIdProofDao;

	@Value("${aws.s3.creditline.bucket}")
	private String bucket;

	public UploadDocumentResponseDTO uploadDocument(Merchant merchant, RequestDTO<CreditUploadDocumentRequestDTO> requestDTO) {
		Map<String, Object> finalResponse = new LinkedHashMap<>();
		UploadDocumentResponseDTO uploadDocumentResponse = new UploadDocumentResponseDTO();
		uploadDocumentResponse.setSuccess(false);

		CreditUploadDocumentRequestDTO uploadDocumentRequest = requestDTO.getPayload();
		Long applicationId =  uploadDocumentRequest.getApplicationId();
		List<CreditUploadDocumentRequestDTO.Document> documents = uploadDocumentRequest.getDocuments();

		if(applicationId == null || applicationId <= 0 || documents == null || documents.isEmpty()) {
			logger.info("Invalid Application Id: {} for merchant : {}", applicationId, merchant.getId());
			return uploadDocumentResponse;
		}

		CreditApplication creditApplication = creditApplicationDao.findByIdAndMerchantIdAndStatus(applicationId, merchant.getId(), "draft");
		if(creditApplication ==  null) {
			logger.info("Invalid Application Id: {} for merchant : {}", applicationId, merchant.getId());
			return uploadDocumentResponse;
		}

		List<MerchantDocumentProof> merchantDocumentProofList = merchantDocumentProofDao.findByMerchantIdAndOwnerIdAndOwnerType(merchant.getId(), creditApplication.getId(), "LENDING");
		Boolean isUpdate = false;
		if(merchantDocumentProofList.size() > 0) {
			isUpdate = true;
		}
		for (MerchantDocumentProof merchantDocumentProof : merchantDocumentProofList) {
			if (!merchantDocumentProof.getProofType().equalsIgnoreCase("selfie") && !merchantDocumentProof.getProofType().equalsIgnoreCase("pancard") && !merchantDocumentProof.getProofType().equalsIgnoreCase("eAadhar")) {
				merchantDocumentProof.setStatus("DELETED");
				merchantDocumentProofDao.save(merchantDocumentProof);
			}
		}

		List<UploadDocumentResponseDTO.Document> documentList = processAndUploadDocuments(documents, isUpdate, merchant, creditApplication, requestDTO.getMeta(), uploadDocumentResponse);

		if(documentList.size() > 0) {
			finalResponse.put("success", true);
			uploadDocumentResponse.setSuccess(true);
		}
		uploadDocumentResponse.setDocument(documentList);
		uploadDocumentResponse.setSelectedLoan(creditUtil.prepareSelectedLoanForClient(creditApplication));
		 
		 return uploadDocumentResponse;
	}
	
	private List<UploadDocumentResponseDTO.Document> processAndUploadDocuments(List<CreditUploadDocumentRequestDTO.Document> documents, Boolean isUpdate, Merchant merchant, CreditApplication creditApplication, MetaDTO meta, UploadDocumentResponseDTO uploadDocumentResponse) {
		List<UploadDocumentResponseDTO.Document> documentList = new ArrayList<>();

		for(CreditUploadDocumentRequestDTO.Document document : documents) {
			if(isUpdate && !document.getChangeFlag()) {
				continue;
			}
			
			if(document.getProof() == null || document.getProof().isEmpty()) {
				logger.error("Empty Documents");
			}
			
			String proofType = document.getProofType();
			MerchantDocumentProof merchantDocumentProof = null;
			int singlePageDocument=1;
			String frontSide = "";
			String backSide = "";
			 
			  singlePageDocument = document.getSinglePageDocument() ? 1 : 0;

			Map<String, String>	proofSides = processAndUploadProof(document.getProof(), merchant);

			  frontSide = proofSides.get("frontSide");
			  backSide = proofSides.get("backSide");
			  String front64=proofSides.get("front64");
			  String back64=proofSides.get("back64");
			 

		
			if(isUpdate) {
				merchantDocumentProof = updateDocumentIdProof(proofType, frontSide, backSide, singlePageDocument, merchant, creditApplication, meta);
			} else {
				merchantDocumentProof = insertDocumentIdProof(proofType, frontSide, backSide, singlePageDocument, merchant, creditApplication, meta);
			}
			
			
			if(merchantDocumentProof != null) {
				UploadDocumentResponseDTO.Document documentResponse = uploadDocumentResponse.new Document();
				documentResponse.setProofId(merchantDocumentProof.getId());
				documentResponse.setProofType(proofType);
				documentResponse.setSinglePageDocument(singlePageDocument);
				documentList.add(documentResponse);
			}
		 
		 signzyVerification(proofType,front64,back64, singlePageDocument, merchantDocumentProof, merchant, creditApplication);
		}
		return documentList;
	}
	
	private Map<String, String> processAndUploadProof(List<String> proof, Merchant merchant) {
		Map<String, String> proofSides = new LinkedHashMap<>();
		proofSides.put("frontSide", "");
		proofSides.put("backSide", "");
		proofSides.put("front64", "");
		proofSides.put("back64", "");

		String frontBase64Encoded = processBase64String(proof.get(0));
		String fileName = merchant.getId() + "" + ((int)(Math.random() * ((100000 - 1) + 1)) + 1) + ".jpeg";
		String frontUrl = s3BucketHandler.uploadToS3Bucket(frontBase64Encoded, fileName, bucket);
		proofSides.put("frontSide", frontUrl);
		proofSides.put("front64",proof.get(0));
		logger.info("s3 file name", fileName);
		if(proof.size() > 1 && !StringUtils.isEmpty(proof.get(1))) {
			String backBase64Encoded = processBase64String(proof.get(1));
			fileName = merchant.getId() + "" + ((int)(Math.random() * ((100000 - 1) + 1)) + 1) + ".jpeg";
			String backUrl = s3BucketHandler.uploadToS3Bucket(backBase64Encoded, fileName, bucket);
			proofSides.put("backSide", backUrl);
			proofSides.put("back64",proof.get(1));
		}
		return proofSides;
	}
	
	public String processBase64String(String base64EncodedString) {
		base64EncodedString.replace(' ', '+');
		if(base64EncodedString.contains("base64,")) {
			String [] base64EncodedSplit = base64EncodedString.split("base64,");
			base64EncodedString = base64EncodedSplit[1];
		}
		return base64EncodedString;
		 
	}
	
	
	private MerchantDocumentProof insertDocumentIdProof(String proofType, String frontSide, String backSide, int singlePageDocument, Merchant merchant, CreditApplication creditApplication, MetaDTO meta) {
		
		MerchantDocumentProof merchantDocumentProof =new MerchantDocumentProof();
		merchantDocumentProof.setMerchantId(merchant.getId());
		merchantDocumentProof.setProofType(proofType);
		merchantDocumentProof.setProofFrontSide(frontSide);
		merchantDocumentProof.setProofBackSide(backSide);
		merchantDocumentProof.setStatus("PENDING");
		merchantDocumentProof.setOwnerType("LENDING");
		merchantDocumentProof.setProvider("SIGNZY");
		merchantDocumentProof.setOwnerId(creditApplication.getId());
		merchantDocumentProofDao.save(merchantDocumentProof);
		return merchantDocumentProof;
	}
	
	private MerchantDocumentProof updateDocumentIdProof(String proofType, String frontSide, String backSide, int singlePageDocument, Merchant merchant, CreditApplication creditApplication, MetaDTO meta) {
		MerchantDocumentProof merchantDocumentProof = merchantDocumentProofDao.findByMerchantIdAndOwnerIdAndOwnerTypeAndProofType(merchant.getId(), creditApplication.getId(), "LENDING",proofType);
		if(merchantDocumentProof != null) {
			merchantDocumentProof.setStatus("PENDING");
			merchantDocumentProof.setProofFrontSide(frontSide);
			merchantDocumentProof.setProofBackSide(backSide); 
			merchantDocumentProofDao.save(merchantDocumentProof);
		} else {
			merchantDocumentProof = insertDocumentIdProof(proofType, frontSide, backSide, singlePageDocument, merchant, creditApplication, meta);
		}
		return merchantDocumentProof;
	}

	private void signzyVerification(String proofType, String frontSide, String backSide, int singlePageDocument, MerchantDocumentProof merchantDocumentProof, Merchant merchant, CreditApplication creditApplication) {
		if(proofType.equals("pancard")  || proofType.equals("votercard") || proofType.equals("passport")||proofType.equals("adhaarcard")||proofType.equals("driving_license")) {
			new Thread(() -> {
				kycUsingSignzyAPI(proofType, frontSide,backSide, merchantDocumentProof, merchant, creditApplication);
				 
			}).start();
		}
	}

	private void kycUsingSignzyAPI(String proofType, String frontSide,String backSide, MerchantDocumentProof merchantDocumentProof, Merchant merchant, CreditApplication creditApplication) {
		try {

			Instant start = Instant.now();
			String tempPublicURL = signzyHandler.getTemporaryPublicURL(frontSide);
			String tempPublicBackURL = "";
			if(!backSide.equals(""))
			{
				tempPublicBackURL = signzyHandler.getTemporaryPublicURL(backSide);
			}
			Instant end = Instant.now();
			logger.info("Time Taken by signzy ImageUrl API : {} miliseconds", Duration.between(start, end).toMillis());
			String response="";
			String request="";
			if(!tempPublicURL.isEmpty()) {
				start = Instant.now();
				Map<String,String> res = signzyHandler.curlSignzyKycAPI(tempPublicURL,tempPublicBackURL,proofType);
				end = Instant.now();
				logger.info("Time Taken by Signzy kyc API : {} miliseconds", Duration.between(start, end).toMillis());
				response=res.get("response");
				request=res.get("request");
				if(!response.isEmpty()) {
					ObjectMapper mapper = new ObjectMapper();
					JsonNode rootNode=null;
					try {
						rootNode = mapper.readTree(response);
					} catch (IOException e1) {
						e1.printStackTrace();
					}
					String accessToken   = (rootNode != null) ? rootNode.path("accessToken").textValue() : null;
					String id   = (rootNode != null) ? rootNode.path("id").textValue() : null;
					merchantDocumentProof.setAccesstoken(res.get("access_token"));
					merchantDocumentProof.setIdentityId(res.get("identity_id"));
					merchantDocumentProofDao.save(merchantDocumentProof);
					MerchantDocumentProofOcr merchantDocumentProofOcr = processAndSaveKycResponse(response,request, proofType, merchantDocumentProof, merchant);
					if(proofType.equals("pancard")) {
						start = Instant.now();
						//isDocAuthEntryMade=	pancardAuthenticationUsingSignzyAPI(response, merchantDocumentProofOcr, merchantDocumentProof, merchant, creditApplication);
						end = Instant.now();
						logger.info("Time Taken by Signzy Pan Authentication API : {} miliseconds", Duration.between(start, end).toMillis());
//							if(!isDocAuthEntryMade)
//							createFailedEntryForPancardDocAuthentication(response,request,merchantDocumentProofOcr, merchantDocumentProof, merchant, creditApplication);
					}
				}
				else {
					MerchantDocumentProofOcr merchantDocumentProofOcr = createFailedDocKycDetails(proofType, merchantDocumentProof, merchant);
					logger.info("UploadDocumentService Signzy kyc api failure for documentId : {} and api response : {} and signzy requestId : {}",merchantDocumentProof.getId());

				}

			}

			else {
				logger.info("UploadDocumentService blank tempURL , merchant: {} for key : {}",merchant.getId(), frontSide);
			}

			// TODO: Need to do entry first and update based on the response update the details
//			if(proofType.equals("pancard") && !isDocAuthEntryMade) {
//				logger.info("Marking blank entries for pancard in merchantDocumentProofOcr and merchantDocumentProofRequest for merchant id {}", merchant.getId());
//				MerchantDocumentProofOcr merchantDocumentProofOcr = createFailedDocKycDetails("pancard", merchantDocumentProof, merchant);
//				
//			}
		}
		catch (FileNotFoundException e) {
			logger.info("UploadDocumentService exception while fetching tempURL File not found from S3 bucket, merchantId: {},file not found for key : {}",merchant.getId(), frontSide);
		} catch (Exception e) {
			e.printStackTrace();
			logger.info("UploadDocumentService exception while fetching tempURL from S3 bucket, merchant: {}, message : {}",merchant.getId(), e.getMessage());
		}

	}
	
	private MerchantDocumentProofOcr processAndSaveKycResponse(String responseString,String request, String proofType, MerchantDocumentProof merchantDocumentProof, Merchant merchant) {
		ObjectMapper mapper = new ObjectMapper();
		JsonNode rootNode=null;
		try {
			rootNode = mapper.readTree(responseString);
		} catch (JsonParseException e1) {
			e1.printStackTrace();
		} catch (JsonMappingException e1) {
			e1.printStackTrace();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		 		JsonNode response = (rootNode != null) ? rootNode.path("response") : null;
		JsonNode result=response.path("result");
		 
		if(result != null) {
			String dob = "";
			String doi = "";
	 /* merrchant document proof ocr table entry*/
			MerchantDocumentProofOcr merchantDocumentProofOcr = new MerchantDocumentProofOcr();
			merchantDocumentProofOcr.setDocumentId(merchantDocumentProof.getId());
			merchantDocumentProofOcr.setMerchantId(merchant.getId());
			 
			merchantDocumentProofOcr.setProvider("SIGNZY");
			merchantDocumentProofOcr.setStatus("SUCCESS");
			merchantDocumentProofOcr.setProofType(proofType);
			  
			/* merrchant document proof  request table entry*/
			
			MerchantDocumentProofRequest merchantDocumentProofRequest = new MerchantDocumentProofRequest();
			merchantDocumentProofRequest.setDocumentId(merchantDocumentProof.getId());
			merchantDocumentProofRequest.setMerchantId(merchant.getId());
			merchantDocumentProofRequest.setProvider("SIGNZY");
			merchantDocumentProofRequest.setStatus("SUCCESS");
			merchantDocumentProofRequest.setRequestType("OCR");
			merchantDocumentProofRequest.setRequest(request);
			merchantDocumentProofRequest.setResponse(responseString);
			 
			if(proofType.equals("votercard")) {
				 
					merchantDocumentProofOcr.setProofNumber(result.path("epicNumber").textValue());
					merchantDocumentProof.setProofNumber(result.path("epicNumber").textValue());
					merchantDocumentProofOcr.setFatherName(result.path("fatherName").textValue());
					merchantDocumentProofOcr.setName(result.path("name").textValue());
					merchantDocumentProofOcr.setAge(result.path("ageAsOn").textValue());
					dob = result.path("dob").textValue();
					merchantDocumentProofOcr.setDob(dob);
					merchantDocumentProofOcr.setAddress(result.path("address").textValue());
					
					JsonNode splitAddress=  result.path("splitAddress");
					 Iterator<JsonNode> iterator =  splitAddress.path("city").elements();
			   

			         while (iterator.hasNext()) {
			            JsonNode cityname = iterator.next();
			            merchantDocumentProofOcr.setCity(cityname.textValue()); 
			            break;
			         }
			         
				 
					merchantDocumentProofOcr.setPincode( splitAddress.path("pincode").textValue());
					 
				
					 
			 }
			
			else if(proofType.equals("pancard")) {

				merchantDocumentProofOcr.setProofNumber(result.path("number").textValue());
				merchantDocumentProof.setProofNumber(result.path("number").textValue());
				merchantDocumentProofOcr.setFatherName(result.path("fatherName").textValue());
				merchantDocumentProofOcr.setName(result.path("name").textValue());
				merchantDocumentProofOcr.setGender(result.path("gender").textValue());
				merchantDocumentProofOcr.setAddress(result.path("address").textValue());
				dob = result.path("dob").textValue();
				merchantDocumentProofOcr.setDob(dob);
			 }
			else if(proofType.equals("passport")) {
				  
				merchantDocumentProofOcr.setFatherName(result.path("parentsGuardianName").textValue());
				JsonNode summary=  result.path("summary");
				merchantDocumentProofOcr.setProofNumber(summary.path("number").textValue());
				merchantDocumentProof.setProofNumber(summary.path("number").textValue());
				merchantDocumentProofOcr.setName(summary.path("name").textValue());
				merchantDocumentProofOcr.setGender(summary.path("gender").textValue());
				merchantDocumentProofOcr.setAddress(summary.path("address").textValue());
	            merchantDocumentProofOcr.setDob(summary.path("dob").textValue());
				merchantDocumentProofOcr.setGender(summary.path("gender").textValue());
				merchantDocumentProofOcr.setAddress(summary.path("address").textValue());
				JsonNode splitAddress=  summary.path("splitAddress");

				 Iterator<JsonNode> iterator =  splitAddress.path("city").elements();
				   

		         while (iterator.hasNext()) {
		            JsonNode cityname = iterator.next();
		            merchantDocumentProofOcr.setCity(cityname.textValue()); 
		            break;
		         }
				  merchantDocumentProofOcr.setPincode( splitAddress.path("pincode").textValue());
				 
			}
			else if(proofType.equals("adhaarcard"))
			{

				merchantDocumentProofOcr.setProofNumber(result.path("uid").textValue());
				merchantDocumentProof.setProofNumber(result.path("uid").textValue());
				merchantDocumentProofOcr.setAddress(result.path("address").textValue());
				merchantDocumentProofOcr.setName(result.path("name").textValue());
				merchantDocumentProofOcr.setAge(result.path("ageAsOn").textValue());
				dob = result.path("dob").textValue();
				merchantDocumentProofOcr.setDob(dob);
				merchantDocumentProofOcr.setAddress(result.path("address").textValue());
				merchantDocumentProofOcr.setGender(result.path("gender").textValue());
				JsonNode splitAddress=  result.path("splitAddress");
				 
				 Iterator<JsonNode> iterator =  splitAddress.path("city").elements();
				   

		         while (iterator.hasNext()) {
		            JsonNode cityname = iterator.next();
		            merchantDocumentProofOcr.setCity(cityname.textValue()); 
		            break;
		         }
				merchantDocumentProofOcr.setPincode( result.path("pincode").textValue());
				 
			}
			else if(proofType.equals("driving_license"))
			{
				JsonNode summary=  result.path("summary");
          merchantDocumentProofOcr.setFatherName(summary.path("GuardianName").textValue());
				
				merchantDocumentProofOcr.setProofNumber(summary.path("number").textValue());
				merchantDocumentProof.setProofNumber(summary.path("number").textValue() );
				merchantDocumentProofOcr.setName(summary.path("name").textValue());
				merchantDocumentProofOcr.setGender(summary.path("gender").textValue());
				merchantDocumentProofOcr.setAddress(summary.path("address").textValue());
	            merchantDocumentProofOcr.setDob(summary.path("dob").textValue());
				merchantDocumentProofOcr.setGender(summary.path("gender").textValue());
				merchantDocumentProofOcr.setAddress(summary.path("address").textValue());
				JsonNode splitAddress=  summary.path("splitAddress");
				 Iterator<JsonNode> iterator =  splitAddress.path("city").elements();
				   

		         while (iterator.hasNext()) {
		            JsonNode cityname = iterator.next();
		            merchantDocumentProofOcr.setCity(cityname.textValue()); 
		            break;
		         } 
			 
					merchantDocumentProofOcr.setPincode( splitAddress.path("pincode").textValue());
					 
			}
			
			
	  logger.info("detail --merchantDocumentProofOcr {}",merchantDocumentProofOcr);
			merchantDocumentProofOcr=merchantDocumentProofOcrDao.save(merchantDocumentProofOcr);
			merchantDocumentProofRequestDao.save(merchantDocumentProofRequest);
			merchantDocumentProofDao.save(merchantDocumentProof);
			return merchantDocumentProofOcr;
		}
		return null;
	}
	
	private Boolean pancardAuthenticationUsingSignzyAPI(String responseString, MerchantDocumentProofOcr merchantDocumentProofOcr, MerchantDocumentProof merchantDocumentProof, Merchant merchant, CreditApplication creditApplication) {
 
		ObjectMapper mapper = new ObjectMapper();
		JsonNode rootNode=null;
		try {
			rootNode = mapper.readTree(responseString);
		} catch (JsonParseException e1) {
			e1.printStackTrace();
		} catch (JsonMappingException e1) {
			e1.printStackTrace();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		 
		JsonNode response = (rootNode != null) ? rootNode.path("response") : null;
		JsonNode result=response.path("result");
		 
		if(result != null ) {
			String panNumber =result.path("number").textValue();
			 
			String name=result.path("name").textValue();
			 
			String dob =  result.path("dob").textValue();
			 
			  String accessTokenSnoop = rootNode.path("accessToken").textValue();
		       String itemId = rootNode.path("itemId").textValue();
		    if(panNumber.equals("")||name.equals("")||dob.equals("")||accessTokenSnoop.equals("")||itemId.equals(""))
		    	return false;
		   
		    Map<String,String> res= signzyHandler.curlSignzyPanAuthenticationAPI(panNumber, name, dob,itemId,accessTokenSnoop);
			
		    String curlResponse=res.get("response");
		    String request=res.get("request");
		    
		    if(!curlResponse.isEmpty()) {
				processAndSavePanAuthenticationResponse(curlResponse,request, merchantDocumentProofOcr, merchantDocumentProof, merchant, creditApplication);
			}else {
				logger.info("UploadDocumentService karza pan authentication api failure with blank response for panNumber : {}, dob : {}, name : {}",panNumber, dob, name);
				createFailedEntryForPancardDocAuthentication(curlResponse,request,merchantDocumentProofOcr, merchantDocumentProof, merchant, creditApplication);
			}
			return true;
		}
		return false;
	}
	
	private void processAndSavePanAuthenticationResponse(String responseString,String request, MerchantDocumentProofOcr merchantDocumentProofOcr, MerchantDocumentProof merchantDocumentProof, Merchant merchant, CreditApplication creditApplication) {
		ObjectMapper mapper = new ObjectMapper();
		JsonNode rootNode=null;
		try {
			rootNode = mapper.readTree(responseString);
		} catch (JsonParseException e1) {
			e1.printStackTrace();
		} catch (JsonMappingException e1) {
			e1.printStackTrace();
		} catch (IOException e1) {
			e1.printStackTrace();
		}

		JsonNode response = (rootNode != null) ? rootNode.path("response") : null;
		JsonNode result=response.path("result");
		
		MerchantDocumentProofRequest merchantDocumentProofRequest  = new MerchantDocumentProofRequest();
		 
		merchantDocumentProofRequest.setMerchantId(merchant.getId());
        merchantDocumentProofRequest.setDocumentId(merchantDocumentProof.getId());
		merchantDocumentProofRequest.setProvider("SIGNZY");
		merchantDocumentProofRequest.setRequestType("VALIDATION");
		merchantDocumentProofRequest.setResponse(responseString);
		merchantDocumentProofRequest.setRequest(request);
		if(result.path("verified").asBoolean())
		{
			merchantDocumentProofRequest.setStatus("SUCCESS");
			//merchantDocumentProofRequest.setResponse(result.path("message").textValue());
		}
		else
		{
			merchantDocumentProofRequest.setStatus("FAILED");
			//merchantDocumentProofRequest.setResponse(result.path("message").textValue());
		}
		
		 
		merchantDocumentProofRequestDao.save(merchantDocumentProofRequest);
	}
	
	private void createFailedEntryForPancardDocAuthentication(String curlResponse,String request,MerchantDocumentProofOcr merchantDocumentProofOcr, MerchantDocumentProof merchantDocumentProof, Merchant merchant,CreditApplication creditApplication) {
		MerchantDocumentProofRequest merchantDocumentProofRequest  = new MerchantDocumentProofRequest();
		 
		merchantDocumentProofRequest.setMerchantId(merchant.getId());
		merchantDocumentProofRequest.setRequestType("VALIDATION");
		merchantDocumentProofRequest.setResponse(curlResponse);
		merchantDocumentProofRequest.setDocumentId(merchantDocumentProof.getId());
		merchantDocumentProofRequest.setStatus("FAILED");
		merchantDocumentProofRequest.setRequest(request);
		merchantDocumentProofRequest.setProvider("SIGNZY");
		merchantDocumentProofRequestDao.save(merchantDocumentProofRequest);
	}
	
	private MerchantDocumentProofOcr  createFailedDocKycDetails(String docType,  MerchantDocumentProof merchantDocumentProof, Merchant merchant) {
		MerchantDocumentProofOcr merchantDocumentProofOcr = new MerchantDocumentProofOcr();
		merchantDocumentProofOcr.setDocumentId(merchantDocumentProof.getId());
		merchantDocumentProofOcr.setMerchantId(merchant.getId());
		 
		merchantDocumentProofOcr.setProvider("SIGNZY");
		 
		merchantDocumentProofOcr.setStatus("FAILED");
		merchantDocumentProofOcr.setProofType(docType);
		//merchantDocumentProofOcr.setResponse("");
		merchantDocumentProofOcr.setProofNumber("");
		merchantDocumentProofOcr.setDob("1970-01-01");
		merchantDocumentProofOcr.setName("");
		merchantDocumentProofOcr.setFatherName("");
		 merchantDocumentProofOcrDao.save(merchantDocumentProofOcr);
		return merchantDocumentProofOcr;
	}
	
}
