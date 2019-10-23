package com.bharatpe.lending.service;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.bharatpe.common.constants.ResponseCode;
import com.bharatpe.common.dao.DocAuthenticationDao;
import com.bharatpe.common.dao.DocKycDetailsDao;
import com.bharatpe.common.dao.DocumentsIdProofDao;
import com.bharatpe.common.entities.DocAuthentication;
import com.bharatpe.common.entities.DocKycDetails;
import com.bharatpe.common.entities.DocumentsIdProof;
import com.bharatpe.common.entities.Merchant;
import com.bharatpe.common.objects.CommonAPIRequest;
import com.bharatpe.lending.constants.LendingConstants;
import com.bharatpe.lending.dao.LendingApplicationDao;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.json.simple.JSONObject;  
import org.json.simple.JSONValue;

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
	
	private Map<String, Object> finalResponse = new LinkedHashMap<>();
	private List<Map<String, Object>> documentList = new ArrayList<Map<String, Object>>();

	public Map<String, Object> runService(Merchant merchant, HttpServletResponse response, CommonAPIRequest commonAPIRequest) {
		this.finalResponse.put("success", false);
		this.documentList = new ArrayList<Map<String, Object>>();
		
		Long merchantId = merchant.getId();
		Long applicationId =  commonAPIRequest.getPayload().get("application_id") != null ? Long.parseLong(commonAPIRequest.getPayload().get("application_id").toString()) : null;
		String latitude = commonAPIRequest.getMeta().getLatitude();
		String longitude = commonAPIRequest.getMeta().getLongitude();
		String ip = commonAPIRequest.getMeta().getIp();
		
		List<Map<String, Object>> documents = (List<Map<String, Object>>) commonAPIRequest.getPayload().get("documents");
		
		Object selectedLoan = commonAPIRequest.getPayload().get("selected_loan");
		if(selectedLoan != null) {
			this.finalResponse.put("selected_loan", selectedLoan);
		}
		
		if(applicationId != null) {
			String dbOperation = "";
			List<DocumentsIdProof> documentsIdProofList = documentsIdProofdao.findByMerchantIdAndApplicationId(merchantId, applicationId);
			if(documentsIdProofList.size() > 0) {
				dbOperation = "update";
			}else {
				dbOperation = "insert";
			}
			processAndUploadDocuments(documents, dbOperation, merchantId, applicationId, latitude, longitude, ip);
		}else {
			logger.info("UploadDocumentService No application Id for merchant : {}", merchantId);
			response.setStatus(Integer.parseInt(ResponseCode.BAD_REQUEST));
		}
		
		if(this.documentList.size() > 0) {
			this.finalResponse.put("success", true);
		}
		this.finalResponse.put("document", this.documentList);
		
		return this.finalResponse;
	}
	
	private void processAndUploadDocuments(List<Map<String, Object>> documents, String dbOperation, Long merchantId, Long applicationId, String latitude, String longitude, String ip) {
		if(documents.size() > 0) {
			for(Map<String, Object> document : documents) {
				Map<String, Object> documentResponse = new LinkedHashMap<>();
				Long documentIdProofId = null;
				int changeFlag = (int) document.get("change_flag");
				String proofType = (String) document.get("proof_type");
				int singlePageDocument = ((Boolean) document.get("single_page_document") == true) ? 1 : 0;
				List<String> proof = (List<String>) document.get("proof");
				Map<String, String> proofSides = new LinkedHashMap<>();
				
				if(dbOperation.equals("update")) {
					if(changeFlag == 1) {
						proofSides = processAndUploadProof(proof, merchantId);
					}else {
						proofSides.put("frontSide", "");
						proofSides.put("backSide", "");
					}
				}else {
					proofSides = processAndUploadProof(proof, merchantId);
				}
				
				String frontSide = proofSides.get("frontSide");
				String backSide = proofSides.get("backSide");
				
				if((!frontSide.isEmpty() && !backSide.isEmpty()) || !frontSide.isEmpty()) {
					if(dbOperation.equals("insert")) {
						documentIdProofId = insertDocumentIdProof(proofType, frontSide, backSide, "pending_verification", singlePageDocument, merchantId, applicationId, latitude, longitude, ip);
					}else {
						documentIdProofId = updateDocumentIdProof(proofType, frontSide, backSide, "pending_verification", singlePageDocument, merchantId, applicationId, latitude, longitude, ip);
					}
					
					if(proofType.equals("pancard") || proofType.equals("adhaarcard") || proofType.equals("votercard") || proofType.equals("passport")) {
						karzaVerification(proofType, frontSide, backSide, singlePageDocument, documentIdProofId, merchantId, applicationId);
					}
				}
				if(documentIdProofId != null) {
					documentResponse.put("proof_id", documentIdProofId);
					documentResponse.put("proof_type", proofType);
					documentResponse.put("single_page_document", singlePageDocument);
					this.documentList.add(documentResponse);
				}
			}
		}
	}
	
	private Map<String, String> processAndUploadProof(List<String> proof, Long merchantId) {
		int docSide = 1;
		Map<String, String> proofSides = new LinkedHashMap<>();
		proofSides.put("frontSide", "");
		proofSides.put("backSide", "");
		
		for(String base64Encoded : proof) {
			if(!base64Encoded.isEmpty()) {
				
				base64Encoded = processBase64String(base64Encoded);
				
				if(docSide == 1) {//front side
					Instant start = Instant.now();
					String frontSide = uploadToS3Bucket(base64Encoded, merchantId);
					Instant end = Instant.now();
					logger.info("Time Taken by AWS S3 upload API : {} miliseconds", Duration.between(start, end).toMillis());
					proofSides.put("frontSide", frontSide);
				}else {//back side
					Instant start = Instant.now();
					String backSide = uploadToS3Bucket(base64Encoded, merchantId);
					Instant end = Instant.now();
					logger.info("Time Taken by AWS S3 upload API : {} miliseconds", Duration.between(start, end).toMillis());
					proofSides.put("backSide", backSide);
				}
				docSide++;
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
	
	private Long insertDocumentIdProof(String proofType, String frontSide, String backSide, String status, int singlePageDocument, Long merchantId, Long applicationId, String latitude, String longitude, String ip) {
		DocumentsIdProof documentsIdProof = new DocumentsIdProof();
		documentsIdProof.setMerchantId(merchantId);
		documentsIdProof.setApplicationId(applicationId);
		documentsIdProof.setProofType(proofType);
		documentsIdProof.setProofFrontSide(frontSide);
		documentsIdProof.setProofBackSide(backSide);
		documentsIdProof.setStatus(status);
		documentsIdProof.setSinglePage(singlePageDocument);
		documentsIdProof.setLatitude(latitude);
		documentsIdProof.setLongitude(longitude);
		documentsIdProof.setIp(ip);
		documentsIdProofdao.save(documentsIdProof);
		return documentsIdProof.getId();
	}
	
	private Long updateDocumentIdProof(String proofType, String frontSide, String backSide, String status, int singlePageDocument, Long merchantId, Long applicationId, String latitude, String longitude, String ip) {
		Long id = null;
		
		if(backSide.isEmpty()) {
			backSide = null;
		}
		int updateId = documentsIdProofdao.updateDocSides(frontSide, backSide, singlePageDocument, latitude, longitude, ip, merchantId, applicationId, proofType);

		if(updateId > 0) {
			DocumentsIdProof documentsIdProof = documentsIdProofdao.findByMerchantIdAndApplicationIdAndProofType(merchantId, applicationId, proofType);
			id = documentsIdProof.getId();
		}

		return id;
	}
	
	private void karzaVerification(String proofType, String frontSide, String backSide, int singlePageDocument, Long documentId, Long merchantId, Long applicationId) {
		kycUsingKarzaAPI(proofType, frontSide, documentId, merchantId, applicationId);
		if(singlePageDocument == 0) {
			kycUsingKarzaAPI(proofType, backSide, documentId, merchantId, applicationId);
		}
	}
	
	private AmazonS3 createS3BucketConnection() {
		AmazonS3 s3client = null;
		try {
			//create connection
			AWSCredentials credentials = new BasicAWSCredentials(
						LendingConstants.AWS_S3_ACCESS_KEY, 
						LendingConstants.AWS_S3_SECRET_KEY
					);
			s3client = AmazonS3ClientBuilder
					  .standard()
					  .withCredentials(new AWSStaticCredentialsProvider(credentials))
					  .withRegion(Regions.AP_SOUTH_1)
					  .build();
		}catch(Exception e) {
			e.printStackTrace();
			logger.info("UploadDocumentService exception while creating connection to S3 bucket message : {}",e.getMessage());
		}
		return s3client;
	}
	
	private String uploadToS3Bucket(String base64Encoded, Long merchantId) {
		String fileName = "";
		//decode and convert into byte stream
		byte[] bI = org.apache.commons.codec.binary.Base64.decodeBase64(base64Encoded.getBytes());
		InputStream fis = new ByteArrayInputStream(bI);
		
		AmazonS3 s3client = createS3BucketConnection();
		try {
			if(s3client != null) {
				//set meta data
				ObjectMetadata metadata = new ObjectMetadata();
				metadata.setContentLength(bI.length);
				metadata.setContentType("image/png");
				metadata.setCacheControl("public, max-age=31536000");
				
				fileName = merchantId + "" + ((int)(Math.random() * ((100000 - 1) + 1)) + 1) + ".jpeg";
				
				//put object to s3 bucket
				s3client.putObject(
							LendingConstants.AWS_S3_BUCKET_NAME, 
							fileName,
							fis,
							metadata
						);
			}
		}catch(Exception e) {
			e.printStackTrace();
			logger.info("UploadDocumentService exception while Uploading doc to S3 bucket message : {}",e.getMessage());
		}
		return fileName;
	}
	
	private String getTemporaryPublicURL(String key) throws FileNotFoundException {
	    try {
	    	AmazonS3 s3client = createS3BucketConnection();
	        return s3client.generatePresignedUrl(LendingConstants.AWS_S3_BUCKET_NAME, key, new DateTime().plusMinutes(15).toDate()).toString();
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
	
	private String curlKarzaKycAPI(String signedURL) {
		String response = null;
		OkHttpClient client = new OkHttpClient();

		MediaType mediaType = MediaType.parse("multipart/form-data; boundary=----WebKitFormBoundary7MA4YWxkTrZu0gW");
		RequestBody body = RequestBody.create(mediaType, "------WebKitFormBoundary7MA4YWxkTrZu0gW\r\nContent-Disposition: form-data; name=\"url\"\r\n\r\n"+signedURL+"\r\n------WebKitFormBoundary7MA4YWxkTrZu0gW--");
		Request request = new Request.Builder()
		  .url("https://api.karza.in/v3/ocr/kyc")
		  .post(body)
		  .addHeader("content-type", "multipart/form-data; boundary=----WebKitFormBoundary7MA4YWxkTrZu0gW")
		  .addHeader("x-karza-key", LendingConstants.X_KARZA_KEY)
		  .addHeader("Accept", "*/*")
		  .addHeader("Cache-Control", "no-cache")
		  .addHeader("Host", "api.karza.in")
		  .addHeader("Content-Type", "multipart/form-data; boundary=--------------------------240133356117902270274178")
		  .addHeader("Accept-Encoding", "gzip, deflate")
		  .addHeader("Content-Length", "510")
		  .addHeader("Connection", "keep-alive")
		  .addHeader("cache-control", "no-cache")
		  .build();
		logger.info("UploadDocumentService karza kyc api request : {}", request);
		try {
			Response curlResponse = client.newCall(request).execute();
			response = curlResponse.body().string();
			logger.info("UploadDocumentService karza kyc api response : {}", response);
		} catch (IOException e) {
			e.printStackTrace();
			logger.info("UploadDocumentService exception while karza kyc api, signedURL : {}",signedURL);
		}
		return response;
	}
	
	private void kycUsingKarzaAPI(String proofType, String fileName, Long documentId, Long merchantId, Long applicationId) {
		try {
			Instant start = Instant.now();
			String tempPublicURL = getTemporaryPublicURL(fileName);
			Instant end = Instant.now();
			logger.info("Time Taken by AWS S3 ImageUrl API : {} miliseconds", Duration.between(start, end).toMillis());
			if(!tempPublicURL.isEmpty()) {
				start = Instant.now();
				String response = curlKarzaKycAPI(tempPublicURL);
				end = Instant.now();
				logger.info("Time Taken by Karza kyc API : {} miliseconds", Duration.between(start, end).toMillis());
				if(!response.isEmpty()) {
					JSONObject jsonResponseObject = (JSONObject) JSONValue.parse(response);
					Long status = (Long) jsonResponseObject.get("statusCode");
					
					if(status == 101) {
						Long insertId = processAndSaveKycResponse(response, proofType, documentId, merchantId);
						if(proofType.equals("pancard")) {
							start = Instant.now();
							pancardAuthenticationUsingKarzaAPI(jsonResponseObject, insertId, documentId, merchantId, applicationId);
							end = Instant.now();
							logger.info("Time Taken by Karza Pan Authentication API : {} miliseconds", Duration.between(start, end).toMillis());
						}
					}else {
						String requestId = (String) jsonResponseObject.get("requestId");
						String failureResponse = (String) jsonResponseObject.get("error"); 
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
	
	private Long processAndSaveKycResponse(String responseString, String proofType, Long documentId, Long merchantId) {
		Long docKycDetailsInsertId = null;
		JSONObject response = (JSONObject) JSONValue.parse(responseString);
		List<Map<String, Object>> result = (List<Map<String, Object>>) response.get("result");
		
		if(result != null && result.size() > 0) {
			String dob = "";
			String doi = "";
			DocKycDetails docKycDetails = new DocKycDetails();
			docKycDetails.setDocId(documentId);
			docKycDetails.setMerchantId(merchantId);
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
	
	private void pancardAuthenticationUsingKarzaAPI(JSONObject response, Long insertId, Long documentId, Long merchantId, Long applicationId) {
		List<Map<String, Object>> result = (List<Map<String, Object>>) response.get("result");
		
		if(result != null && result.size() > 0) {
			Map<String, Map<String, String>> details = (Map<String, Map<String, String>>) result.get(0).get("details");
			String panNumber = details.get("panNo").get("value");
			String dob = details.get("date").get("value");
			String name = details.get("name").get("value");
			
			String curlResponse = curlKarzaPanAuthenticationAPI(panNumber, name, dob);
			if(!curlResponse.isEmpty()) {
				processAndSavePanAuthenticationResponse(curlResponse, insertId, documentId, merchantId, applicationId);
			}else {
				logger.info("UploadDocumentService karza pan authentication api failure with blank response for panNumber : {}, dob : {}, name : {}",panNumber, dob, name);
			}
		}
	}
	
	private String curlKarzaPanAuthenticationAPI(String panNumber, String name, String dob) {
		String response = null;
		OkHttpClient client = new OkHttpClient();
		
		MediaType mediaType = MediaType.parse("application/json");
		RequestBody body = RequestBody.create(mediaType, "{\n    \"pan\": \""+ panNumber +"\",\n    \"name\": \""+ name +"\",\n    \"dob\": \"" + dob + "\",\n    \"consent\": \"Y\"\n}");
		Request request = new Request.Builder()
		  .url("https://api.karza.in/v2/pan-authentication")
		  .post(body)
		  .addHeader("Content-Type", "application/json")
		  .addHeader("x-karza-key", LendingConstants.X_KARZA_KEY)
		  .addHeader("Accept", "*/*")
		  .addHeader("Cache-Control", "no-cache")
		  .addHeader("Host", "api.karza.in")
		  .addHeader("Accept-Encoding", "gzip, deflate")
		  .addHeader("Content-Length", "99")
		  .addHeader("Connection", "keep-alive")
		  .addHeader("cache-control", "no-cache")
		  .build();
		logger.info("UploadDocumentService karza pan authentication api request : {}", request);
		try {
			Response curlResponse = client.newCall(request).execute();
			response = curlResponse.body().string();
			logger.info("UploadDocumentService karza pan authentication api response : {}", response);
		} catch (IOException e) {
			e.printStackTrace();
			logger.info("UploadDocumentService exception while karza pan authentication api, panNumber : {}, name : {}, documentId : {}",panNumber, name, dob);
		}
		return response;
	}
	
	private void processAndSavePanAuthenticationResponse(String response, Long insertId, Long documentId, Long merchantId, Long applicationId) {
		JSONObject jsonResponseObject = (JSONObject) JSONValue.parse(response);
		String status = (String) jsonResponseObject.get("status-code");
		
		DocAuthentication docAuthentication = new DocAuthentication();
		docAuthentication.setDocKycDetailsId(insertId);
		docAuthentication.setMerchantId(merchantId);
		docAuthentication.setDocType("pancard");
		docAuthentication.setFullResponse(response);
		docAuthentication.setDocId(documentId);
		docAuthentication.setCreatedAt(new Date());
		docAuthentication.setUpdatedAt(new Date());
		
		if(status.equals("101")) {
			Map<String, String> result = (Map<String, String>) jsonResponseObject.get("result");
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
