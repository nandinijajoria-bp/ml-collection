package com.bharatpe.lending.service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.bharatpe.common.dao.DocAuthenticationDao;
import com.bharatpe.common.dao.DocKycDetailsDao;
import com.bharatpe.common.dao.DocumentsIdProofDao;
import com.bharatpe.common.dao.LoanDetailsDao;
import com.bharatpe.common.dao.MerchantDao;
import com.bharatpe.common.dao.MerchantFcmTokenDao;
import com.bharatpe.common.entities.DocAuthentication;
import com.bharatpe.common.entities.DocKycDetails;
import com.bharatpe.common.entities.DocumentsIdProof;
import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingAuditTrial;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.common.entities.LoanDetails;
import com.bharatpe.common.entities.Merchant;
import com.bharatpe.common.entities.MerchantFcmToken;
import com.bharatpe.common.entities.SettlementSchedule;
import com.bharatpe.common.objects.CommonAPIRequest;
import com.bharatpe.lending.constants.LendingConstants;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingAuditTrialDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dao.SettlementScheduleDao;
import com.bharatpe.lending.dao.ValidateDao;
import com.bharatpe.lending.handlers.KarzaHandler;
import com.bharatpe.lending.handlers.S3BucketHandler;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@Service
public class UpdateLoanInfoFromPanelService {
	Logger logger = LoggerFactory.getLogger(UpdateLoanInfoFromPanelService.class);
	
	@Autowired
	DocumentsIdProofDao documentsIdProofDao;
	
	@Autowired
	DocKycDetailsDao docKycDetailsDao;
	
	@Autowired
	DocAuthenticationDao docAuthenticationDao;
	
	@Autowired
	LendingApplicationDao lendingApplicationDao;
	
	@Autowired
	SettlementScheduleDao settlementScheduleDao;
	
	@Autowired
	MerchantDao merchantDao;
	
	@Autowired
	LoanDetailsDao loanDetailsDao;
	
	@Autowired
	LendingPaymentScheduleDao lendingPaymentScheduleDao;
	
	@Autowired
	ValidateDao validateDao;
	
	@Autowired
	LendingAuditTrialDao lendingAuditTrialDao;
	
	@Autowired
	MerchantFcmTokenDao merchantFcmTokenDao;
	
	@Autowired
	S3BucketHandler s3BucketHandler;
	
	@Autowired
	KarzaHandler karzaHandler;

	public Map<String, String> updateLoanInfoFromPanel(CommonAPIRequest commonAPIRequest) {
		Map<String, String> finalResponse = new LinkedHashMap<>();
		finalResponse.put("response", "success");
		finalResponse.put("message", "loan Application updated");
		
		uploadDocToS3AndValidate(commonAPIRequest);
		
		updateLoanInfo(commonAPIRequest);
		
		return finalResponse;
	}
	
	private void uploadDocToS3AndValidate(CommonAPIRequest commonAPIRequest) {
		List<Map<String, Object>> documents = (List<Map<String, Object>>) commonAPIRequest.getPayload().get("documents");
		Long applicationId = Long.parseLong(commonAPIRequest.getPayload().get("application_id").toString());
		Long merchantId = Long.parseLong(commonAPIRequest.getPayload().get("merchant_id").toString());
		
		if(documents.size() > 0) {
			for(Map<String, Object> document : documents) {
				String frontSide = "";
				String backSide = "";
				Boolean singlePageDocFlag = (document.get("single_page_document") == null) ? (Boolean)document.get("single_page_document") : false;
				int singlePageDoc = (singlePageDocFlag == true) ? 1 : 0;
				Long docId = (document.get("doc_id") != null) ? Long.parseLong(document.get("doc_id").toString()) : null;
				String docType = document.get("proof_type").toString();
				
				if(document.get("front_side") != null && !document.get("front_side").toString().isEmpty()) {
					frontSide = processAndUploadDocToS3(document.get("front_side").toString(), merchantId);
				}
				if(!singlePageDocFlag && document.get("back_side") != null && !document.get("back_side").toString().isEmpty()) {
					backSide = processAndUploadDocToS3(document.get("back_side").toString(), merchantId);
				}

				if(!frontSide.isEmpty() || !backSide.isEmpty()) {
					if(docId == 0 || docId == null){
			            docId = addNewDocument(docType, merchantId, applicationId, frontSide, backSide);
			        }
					if(!docType.equalsIgnoreCase("selfie")) {
						karzaVerification(docType, frontSide, backSide, singlePageDoc, docId, merchantId, applicationId);
					}
					
					Optional<DocumentsIdProof> documentsIdProofOptional = documentsIdProofDao.findById(docId);
					if(documentsIdProofOptional.isPresent()) {
						DocumentsIdProof documentsIdProofToSave = documentsIdProofOptional.get();
						documentsIdProofToSave.setStatus("pending_verification");
						if(!frontSide.isEmpty()) {
							documentsIdProofToSave.setProofFrontSide(frontSide);
						}
						if(!backSide.isEmpty()) {
							documentsIdProofToSave.setProofBackSide(backSide);
						}
						documentsIdProofDao.save(documentsIdProofToSave);
					}
				}
			}
		}
	}
	
	private String processAndUploadDocToS3(String base64Encoded, Long merchantId) {
		String fileName = null;
		base64Encoded = processBase64String(base64Encoded);
		
		Instant start = Instant.now();
		fileName = s3BucketHandler.uploadToS3Bucket(base64Encoded, merchantId);
		Instant end = Instant.now();
		logger.info("Time Taken by AWS S3 upload API : {} miliseconds", Duration.between(start, end).toMillis());
		
		return fileName;
	}
	
	private String processBase64String(String base64EncodedString) {
		base64EncodedString.replace(' ', '+');
		if(base64EncodedString.contains("base64,")) {
			String [] base64EncodedSplit = base64EncodedString.split("base64,");
			base64EncodedString = base64EncodedSplit[1];
		}
		return base64EncodedString;
	}
	
	private Long addNewDocument(String docType, Long merchantId, Long applicationId, String frontSide, String backSide) {
		Long docId = null;
		
		DocumentsIdProof documentsIdProof = documentsIdProofDao.findByMerchantIdAndApplicationIdAndProofType(merchantId, applicationId, docType);
		
		if(documentsIdProof == null || documentsIdProof.getId() == null) {
			DocumentsIdProof documentToSave = new DocumentsIdProof();
			documentToSave.setMerchantId(merchantId);
			documentToSave.setApplicationId(applicationId);
			documentToSave.setProofType(docType);
			documentToSave.setStatus("pending_verification");
			documentToSave.setSinglePage(1);
			if(!frontSide.isEmpty()) {
				documentToSave.setProofFrontSide(frontSide);
			}
			if(!backSide.isEmpty()) {
				documentToSave.setSinglePage(0);
				documentToSave.setProofFrontSide(backSide);
			}
			documentsIdProofDao.save(documentToSave);
			docId = documentToSave.getId();
		}else {
			docId = documentsIdProof.getId();
		}
		
		return docId;
	}
	
	private void karzaVerification(String proofType, String frontSide, String backSide, int singlePageDocument, Long documentId, Long merchantId, Long applicationId) {
		if(!frontSide.isEmpty()) {
			kycUsingKarzaAPI(proofType, frontSide, documentId, merchantId, applicationId);
		}
		if(singlePageDocument == 0 && !backSide.isEmpty()) {
			kycUsingKarzaAPI(proofType, backSide, documentId, merchantId, applicationId);
		}
	}
	
	private void kycUsingKarzaAPI(String proofType, String fileName, Long documentId, Long merchantId, Long applicationId) {
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
						saveKarzaKycFailedResponse(proofType, documentId, response, merchantId);
						
						String requestId = (String) jsonResponseObject.get("requestId");
						String failureResponse = (String) jsonResponseObject.get("error"); 
						logger.info("karza kyc api failure for documentId : {} and api response : {} and karza requestId : {}",documentId, failureResponse, requestId);
					}
				}else {
					logger.info("karza kyc api failure with blank response for documentId : {}",documentId);
				}
			}else {
				logger.info("blank tempURL from S3 bucket for key : {}",fileName);
			}
		} catch (FileNotFoundException e) {
			logger.info("exception while fetching tempURL from S3 bucket,file not found for key : {}",fileName);
		} catch (Exception e) {
			e.printStackTrace();
			logger.info("exception while fetching tempURL from S3 bucket, message : {}",e.getMessage());
		}
	}
	
	private void saveKarzaKycFailedResponse (String proofType, Long docId, String response, Long merchantId) {
		Long docKycId = null;
		DocKycDetails docKycDetails = docKycDetailsDao.findTop1ByMerchantIdAndDocTypeAndDocId(merchantId, proofType, docId);
		
		if(docKycDetails != null) {
			docKycDetails.setResponse(response);
			docKycDetailsDao.save(docKycDetails);
			docKycId = docKycDetails.getId();
		}else {
			DocKycDetails docKycToSave = new DocKycDetails();
			docKycToSave.setMerchantId(merchantId);
			docKycToSave.setDocId(docId);
			docKycToSave.setDocType(proofType);
			docKycToSave.setResponse(response);
			docKycDetailsDao.save(docKycToSave);
			docKycId = docKycToSave.getId();
		}
		if(proofType.equalsIgnoreCase("pancard")) {
			docAuthenticationDao.updateDeletedAt(new Date(), docId, merchantId);
			DocAuthentication docAuthentication = new DocAuthentication();
			docAuthentication.setDocId(docId);
			docAuthentication.setDocKycDetailsId(docKycId);
			docAuthentication.setMerchantId(merchantId);
			docAuthentication.setDocType(proofType);
			docAuthentication.setStatus("FAILED");
			docAuthentication.setFullResponse(response);
			docAuthenticationDao.save(docAuthentication);
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
			
			String curlResponse = karzaHandler.curlKarzaPanAuthenticationAPI(panNumber, name, dob);
			if(!curlResponse.isEmpty()) {
				processAndSavePanAuthenticationResponse(curlResponse, insertId, documentId, merchantId, applicationId);
			}else {
				logger.info("UploadDocumentService karza pan authentication api failure with blank response for panNumber : {}, dob : {}, name : {}",panNumber, dob, name);
			}
		}
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
	
	private void updateLoanInfo(CommonAPIRequest commonAPIRequest) {
		Long applicationId = Long.parseLong(commonAPIRequest.getPayload().get("application_id").toString());
		Long merchantId = Long.parseLong(commonAPIRequest.getPayload().get("merchant_id").toString());
		Long userId = Long.parseLong(commonAPIRequest.getPayload().get("user_id").toString());
		Map<String, Object> loanDetails = (Map<String, Object>) commonAPIRequest.getPayload().get("loan_details");
		
		
		
		if(loanDetails != null) {
			LendingApplication lendingApplication = lendingApplicationDao.findByApplicationIdAndMerchantId(applicationId, merchantId);
			Optional<Merchant> merchantOptional = merchantDao.findById(merchantId);
			DateFormat df = new SimpleDateFormat("dMMY");
			String loanId = "BPL" + df.format(lendingApplication.getAgreementAt()) + lendingApplication.getApplicationId();
			
			DocKycDetails docKycDetails = docKycDetailsDao.findTop1ByMerchantIdAndModule(merchantId, "LENDING");
			String status = null;
			
			//query for details
			
			String manualKyc = (loanDetails.get("manual_kyc") != null && !loanDetails.get("manual_kyc").toString().isEmpty()) ? loanDetails.get("manual_kyc").toString() : null;
			String manualCibil = (loanDetails.get("manual_cibil") != null && !loanDetails.get("manual_cibil").toString().isEmpty()) ? loanDetails.get("manual_cibil").toString() : null;
			String physicalVerificationStatus = (loanDetails.get("physical_verification_status") != null && !loanDetails.get("physical_verification_status").toString().isEmpty()) ? loanDetails.get("physical_verification_status").toString() : null;
			String loanDisbursalStatus = (loanDetails.get("loan_disbursal_status") != null && !loanDetails.get("loan_disbursal_status").toString().isEmpty()) ? loanDetails.get("loan_disbursal_status").toString() : null;
			String nbfcStatus = (loanDetails.get("nbfc_status") != null && !loanDetails.get("nbfc_status").toString().isEmpty()) ? loanDetails.get("nbfc_status").toString() : null;
			
			if((manualKyc != null && manualKyc.equals("REJECTED")) || (manualCibil != null && manualCibil.equals("REJECTED")) || (physicalVerificationStatus != null && physicalVerificationStatus.equals("REJECTED"))){
	            status = "rejected";
	        }else if((manualKyc != null && manualKyc.equals("APPROVED")) && (manualCibil != null && manualCibil.equals("APPROVED")) && (physicalVerificationStatus != null && physicalVerificationStatus.equals("APPROVED"))){
	            status = "approved";
	        }else if ((manualKyc != null && manualKyc.equals("APPROVED")) || (manualCibil != null && manualCibil.equals("APPROVED"))){
	            status = "pending_verification";
	        }
			
			insertAuditTrial(lendingApplication, status, merchantId, applicationId, manualKyc, manualCibil, physicalVerificationStatus, loanDisbursalStatus, nbfcStatus, userId);
			
			updateLoanApplication(loanDetails, applicationId, merchantId, status, lendingApplication.getAgreementAt());
			
			if((manualKyc != null && manualKyc.equals("REJECTED")) || (manualCibil != null && manualCibil.equals("REJECTED")) || (physicalVerificationStatus != null && physicalVerificationStatus.equals("REJECTED"))){
				String message = "We regret to inform you that we are unable to process your application as it does not meet the guidelines for document assessment.";
                sendSmsAndNotification(merchantOptional.get().getMobile(), merchantId, message);
            }else if((manualKyc != null && manualKyc.equals("APPROVED")) && (manualCibil != null && manualCibil.equals("APPROVED")) && (physicalVerificationStatus != null && physicalVerificationStatus.equals("APPROVED"))){
                if(loanDisbursalStatus != null && loanDisbursalStatus.equalsIgnoreCase("DISBURSED")  && lendingApplication.getStatus().equalsIgnoreCase("approved")){
                    String message = "Congrats " + docKycDetails.getPersonName() + "! Your BharatPe Loan of INR " + lendingApplication.getLoanAmount() + " is approved. The amount will reflect in your bank account within 48 hours.";
                    sendSmsAndNotification(merchantOptional.get().getMobile(), merchantId, message);
                }
            }
			
			documentsIdProofDao.updateStatus(merchantId, applicationId, status);
			
			lendingApplication = lendingApplicationDao.findByApplicationIdAndMerchantId(applicationId, merchantId);
			
			if(lendingApplication.getStatus() != null && lendingApplication.getStatus().equalsIgnoreCase("approved") && lendingApplication.getLoanDisbursalStatus() != null && lendingApplication.getLoanDisbursalStatus().equalsIgnoreCase("DISBURSED")) {
				saveActiveLoanDetails(applicationId, merchantId, lendingApplication);
				insertNewLendingPaymentSchedule(applicationId, merchantId, lendingApplication, merchantOptional.get());
				merchantDao.updateSettlementType(merchantId, "DAILY");
				validateDao.updateSettlement(merchantOptional.get().getMobile(), "daily");
				settlementScheduleDao.updateSettlementDateAndMoveDaily(new Date(), "YES", "PENDING", merchantId);
				
				if(loanDisbursalStatus.equalsIgnoreCase("DISBURSED")) {
					String message = "";
					SettlementSchedule settlementSchedule = settlementScheduleDao.findTop1ByMerchantIdAndStatus(merchantId, "PENDING");
					if(settlementSchedule != null) {
						message = "Congrats, your BharatPe loan of Rs."+ lendingApplication.getLoanAmount() +" has been disbursed to your bank a/c. Loan ID - "+ loanId +" . We'll be settling your "+ merchantOptional.get().getSettlementType().toLowerCase() +" Flexi - Plan today & going forward, your transactions will be settled everyday. Once the loan is fully paid, you can restart your Flexi - Plan again. Happy selling!";
					}else {
						message = "Congrats! Your BharatPe Loan of INR "+ lendingApplication.getLoanAmount() +" has been disbursed to your bank a/c. Refer to Loan Id "+ loanId +" for future.";
					}
					sendSmsAndNotification(merchantOptional.get().getMobile(), merchantId, message);
				}
			}
		}	
	}
	
	private void insertAuditTrial(LendingApplication lendingApplication, String toUpdateStatus, Long merchantId, Long applicationId, String manualKyc, String manualCibil, String physicalVerificationStatus, String loanDisbursalStatus, String nbfcStatus, Long userId) {
		DateFormat df = new SimpleDateFormat("dMMY");
		String loanId = "BPL" + df.format(lendingApplication.getAgreementAt()) + lendingApplication.getApplicationId();
		
		if(manualKyc != null && !manualKyc.isEmpty() && (lendingApplication.getManualKyc() == null || !lendingApplication.getManualKyc().equalsIgnoreCase(manualKyc))) {
			saveLendingAuditTrialDetails(applicationId, merchantId, lendingApplication.getManualKyc(), manualKyc, "KYC", loanId, userId);
		}
		if(manualCibil != null && !manualCibil.isEmpty() && (lendingApplication.getManualCibil() == null || !lendingApplication.getManualCibil().equalsIgnoreCase(manualCibil))) {
			saveLendingAuditTrialDetails(applicationId, merchantId, lendingApplication.getManualCibil(), manualCibil, "CIBIL", loanId, userId);
		}
		if(physicalVerificationStatus != null && !physicalVerificationStatus.isEmpty() && (lendingApplication.getPhysicalVerificationStatus() == null || !lendingApplication.getPhysicalVerificationStatus().equalsIgnoreCase(physicalVerificationStatus))) {
			saveLendingAuditTrialDetails(applicationId, merchantId, lendingApplication.getPhysicalVerificationStatus(), physicalVerificationStatus, "PHYSICAL", loanId, userId);
		}
		if(toUpdateStatus != null && !toUpdateStatus.isEmpty() && toUpdateStatus.equalsIgnoreCase("approved") && (lendingApplication.getStatus() == null || !lendingApplication.getStatus().equalsIgnoreCase(toUpdateStatus))) {
			saveLendingAuditTrialDetails(applicationId, merchantId, lendingApplication.getStatus(), toUpdateStatus, "APP_STATUS", loanId, userId);
		}
		if(loanDisbursalStatus != null && !loanDisbursalStatus.isEmpty() && (lendingApplication.getLoanDisbursalStatus() == null || !lendingApplication.getLoanDisbursalStatus().equalsIgnoreCase(loanDisbursalStatus))) {
			saveLendingAuditTrialDetails(applicationId, merchantId, lendingApplication.getStatus(), loanDisbursalStatus, "DISBURSAL", loanId, userId);
		}
		if(nbfcStatus != null && !nbfcStatus.isEmpty() && (lendingApplication.getSendToNbfc() == null || !lendingApplication.getSendToNbfc().equalsIgnoreCase(nbfcStatus))) {
			saveLendingAuditTrialDetails(applicationId, merchantId, lendingApplication.getLoanDisbursalStatus(), nbfcStatus, "NBFC", loanId, userId);
		}
	}
	
	private void updateLoanApplication(Map<String, Object> loanDetails, Long applicationId, Long merchantId, String status, Date agreementAt) {
		String manualKyc = (loanDetails.get("manual_kyc") != null && !loanDetails.get("manual_kyc").toString().isEmpty()) ? loanDetails.get("manual_kyc").toString() : null;
		String manualKycNotes = (loanDetails.get("manual_kyc_notes") != null && !loanDetails.get("manual_kyc_notes").toString().isEmpty()) ? loanDetails.get("manual_kyc_notes").toString() : null;
		String manualKycAdditionalInfo = (loanDetails.get("manual_kyc_additional_info") != null && !loanDetails.get("manual_kyc_additional_info").toString().isEmpty()) ? loanDetails.get("manual_kyc_additional_info").toString() : null;
		String manualCibil = (loanDetails.get("manual_cibil") != null && !loanDetails.get("manual_cibil").toString().isEmpty()) ? loanDetails.get("manual_cibil").toString() : null;
		String manualCibilDeviation = (loanDetails.get("manual_cibil_deviation") != null && !loanDetails.get("manual_cibil_deviation").toString().isEmpty()) ? loanDetails.get("manual_cibil_deviation").toString() : null;
		String manualCibilDeviationOtherReason = (loanDetails.get("manual_cibil_deviation_other_reason") != null && !loanDetails.get("manual_cibil_deviation_other_reason").toString().isEmpty()) ? loanDetails.get("manual_cibil_deviation_other_reason").toString() : null;
		String manualCibilNotes = (loanDetails.get("manual_cibil_notes") != null && !loanDetails.get("manual_cibil_notes").toString().isEmpty()) ? loanDetails.get("manual_cibil_notes").toString() : null;
		String manualCibilAdditionalInfo = (loanDetails.get("manual_cibil_additional_info") != null && !loanDetails.get("manual_cibil_additional_info").toString().isEmpty()) ? loanDetails.get("manual_cibil_additional_info").toString() : null;
		String physicalVerificationStatus = (loanDetails.get("physical_verification_status") != null && !loanDetails.get("physical_verification_status").toString().isEmpty()) ? loanDetails.get("physical_verification_status").toString() : null;
		String physicalVerificationAdditionalInfo = (loanDetails.get("physical_verification_additional_info") != null && !loanDetails.get("physical_verification_additional_info").toString().isEmpty()) ? loanDetails.get("physical_verification_additional_info").toString() : null;
		String physicalVerificationNotes = (loanDetails.get("physical_verification_notes") != null && !loanDetails.get("physical_verification_notes").toString().isEmpty()) ? loanDetails.get("physical_verification_notes").toString() : null;
		String sendToNbfc = (loanDetails.get("send_to_nbfc") != null && !loanDetails.get("send_to_nbfc").toString().isEmpty()) ? loanDetails.get("send_to_nbfc").toString() : null;
		String loanDisbursalStatus = (loanDetails.get("loan_disbursal_status") != null && !loanDetails.get("loan_disbursal_status").toString().isEmpty()) ? loanDetails.get("loan_disbursal_status").toString() : null;
		if(loanDisbursalStatus != null) {
			loanDisbursalStatus = loanDisbursalStatus.equalsIgnoreCase("APPROVED") ? "DISBURSED" : loanDisbursalStatus;
		}
		Long approvedBy = (loanDetails.get("user_id") != null) ? Long.parseLong(loanDetails.get("user_id").toString()) : null;
		String lender = (loanDetails.get("lender") != null && !loanDetails.get("lender").toString().isEmpty()) ? loanDetails.get("lender").toString().toUpperCase() : null;
		SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy");
		String externalLoanId = (loanDetails.get("external_loan_id") != null) ? "BPL"+sdf.format(agreementAt)+loanDetails.get("external_loan_id").toString() : null;
		String addressProofCity = (loanDetails.get("proof_city") != null && !loanDetails.get("proof_city").toString().isEmpty()) ? loanDetails.get("proof_city").toString() : null;
		String addressProofState = (loanDetails.get("proof_state") != null && !loanDetails.get("proof_state").toString().isEmpty()) ? loanDetails.get("proof_state").toString() : null;
		String addressProofZipCode = (loanDetails.get("proof_zip_code") != null && !loanDetails.get("proof_zip_code").toString().isEmpty()) ? loanDetails.get("proof_zip_code").toString() : null;
		String addressProofAddress = (loanDetails.get("proof_address") != null && !loanDetails.get("proof_address").toString().isEmpty()) ? loanDetails.get("proof_address").toString() : null;
		
		LendingApplication applicationToUpdate = lendingApplicationDao.findByApplicationIdAndMerchantId(applicationId, merchantId);
		applicationToUpdate.setManualKyc(manualKyc);
		applicationToUpdate.setManualKycNotes(manualKycNotes);
		applicationToUpdate.setManualKycAdditionalInfo(manualKycAdditionalInfo);
		applicationToUpdate.setManualCibil(manualCibil);
		applicationToUpdate.setManualCibilDeviation(manualCibilDeviation);
		applicationToUpdate.setManualCibilDeviationOtherReason(manualCibilDeviationOtherReason);
		applicationToUpdate.setManualCibilNotes(manualCibilNotes);
		applicationToUpdate.setManualCibilAdditionalInfo(manualCibilAdditionalInfo);
		applicationToUpdate.setPhysicalVerificationStatus(physicalVerificationStatus);
		applicationToUpdate.setPhysicalVerificationNotes(physicalVerificationNotes);
		applicationToUpdate.setPhysicalVerificationAdditionalInfo(physicalVerificationAdditionalInfo);
		applicationToUpdate.setSendToNbfc(sendToNbfc);
		applicationToUpdate.setLoanDisbursalStatus(loanDisbursalStatus);
		applicationToUpdate.setApprovedBy(approvedBy);
		applicationToUpdate.setLender(lender);
		applicationToUpdate.setExternalLoanId(externalLoanId);
		applicationToUpdate.setAddressProofCity(addressProofCity);
		applicationToUpdate.setAddressProofState(addressProofState);
		applicationToUpdate.setAddressProofZipCode(addressProofZipCode);
		applicationToUpdate.setAddressProofAddress(addressProofAddress);
		applicationToUpdate.setStatus(status);
		
		lendingApplicationDao.save(applicationToUpdate);
	}
	
	private void saveActiveLoanDetails(Long applicationId, Long merchantId, LendingApplication lendingApplication) {
		LoanDetails loanDetails = new LoanDetails();
		loanDetails.setApplicationId(applicationId);
		loanDetails.setStartDate(new Date());
		loanDetails.setMerchantId(merchantId);
		loanDetails.setLoanAmount(lendingApplication.getLoanAmount());
		loanDetails.setEdiCount(lendingApplication.getPayableDays());
		loanDetails.setStatus("ACTIVE");
		loanDetailsDao.save(loanDetails);
	}
	
	private void insertNewLendingPaymentSchedule(Long applicationId, Long merchantId, LendingApplication lendingApplication, Merchant merchant) {
		LendingPaymentSchedule toInsert = new LendingPaymentSchedule();
		toInsert.setApplicationId(applicationId);
		toInsert.setMerchant(merchant);
		toInsert.setLoanAmount(lendingApplication.getLoanAmount());
		toInsert.setEdiAmount(lendingApplication.getEdi());
		toInsert.setStartDate(new Date());
		toInsert.setEdiCount(Integer.parseInt(lendingApplication.getPayableDays().toString()));
		toInsert.setEdiRemainingCount(Integer.parseInt(lendingApplication.getPayableDays().toString()));
		toInsert.setStatus("ACTIVE");
		toInsert.setMobile(merchant.getMobile());
		toInsert.setTotalPayableAmount(lendingApplication.getRepayment());
		toInsert.setCreatedAt(new Date());
		toInsert.setUpdatedAt(new Date());
		LocalDate localDate = LocalDate.now().plusDays(1);
		toInsert.setNextEdiDate(Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant()));

		Date tentativeClosingDate = calculateTentativeClosingDate(lendingApplication.getPayableDays());
		toInsert.setTentativeClosingDate(tentativeClosingDate);
		lendingPaymentScheduleDao.save(toInsert);
	}
	
	private Date calculateTentativeClosingDate(Long payableDays) {
		int months = 0;
		int days = 0;
		if(payableDays == 77) {
			months = 3;
		}else if(payableDays == 26) {
			months = 1;
		}else if(payableDays == 12) {
			days = 14;
		}else if(payableDays == 155) {
			months = 6;
		}else if(payableDays == 311) {
			months = 12;
		}
		
		LocalDate localDate = LocalDate.now().plusDays(days).plusMonths(months);
		Date date = Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
		
		return date;
	}
	
	private void saveLendingAuditTrialDetails(Long applicationId, Long merchantId, String oldStatus, String newStatus, String type, String loanId, Long userId) {
		LendingAuditTrial toInsert = new LendingAuditTrial();
		toInsert.setMerchantId(merchantId);
		toInsert.setApplicationId(applicationId);
		toInsert.setOldStatus(oldStatus);
		toInsert.setNewStatus(newStatus);
		toInsert.setType(type);
		toInsert.setLoanId(loanId);
		toInsert.setUserId(userId);
		
		lendingAuditTrialDao.save(toInsert);
	}
	
	private void sendSmsAndNotification(String mobile, Long merchantId, String message) {
		Instant start = Instant.now();
		sendSuccessSMS(mobile, message);
		Instant end = Instant.now();
		logger.info("Time Taken by GUPSHUP sendMessage API : {} miliseconds", Duration.between(start, end).toMillis());
		
		start = Instant.now();
		sendNotification(merchantId, message);
		end = Instant.now();
		logger.info("Time Taken by fcm google API : {} miliseconds", Duration.between(start, end).toMillis());
	}
	
	private void sendSuccessSMS(String mobile, String message) {
		OkHttpClient client = new OkHttpClient();

		Request request = new Request.Builder()
				  .url("http://enterprise.smsgupshup.com/GatewayAPI/rest?method=sendMessage&send_to="+mobile+"&msg="+message+"&msg_type=TEXT&userid="+LendingConstants.GUPSHUP_SENDMESSAGE_API_USERID+"&password="+LendingConstants.GUPSHUP_SENDMESSAGE_API_PASSWORD+"&auth_scheme=PLAIN&format=JSON")
				  .get()
				  .addHeader("Content-Type", "application/json")
				  .addHeader("Accept", "*/*")
				  .addHeader("Cache-Control", "no-cache")
				  .addHeader("Host", "enterprise.smsgupshup.com")
				  .addHeader("Accept-Encoding", "gzip, deflate")
				  .addHeader("Connection", "keep-alive")
				  .addHeader("cache-control", "no-cache")
				  .build();
		logger.info("SendSuccessSMS api request : {}", request);
		try {
			Response response = client.newCall(request).execute();
			String responseBody = response.body().string();
			logger.info("SendSuccessSMS api response : {}", responseBody);
		} catch (IOException e) {
			e.printStackTrace();
			logger.info("SendSuccessSMS api exception : {} ",e.getMessage());
		}
	}
	
	private void sendNotification(Long merchantId, String message) {
		MerchantFcmToken merchantFcmToken = merchantFcmTokenDao.findByMerchantId(merchantId);
		
		if(merchantFcmToken != null) {
			OkHttpClient client = new OkHttpClient();

			MediaType mediaType = MediaType.parse("application/json");
			okhttp3.RequestBody body = okhttp3.RequestBody.create(mediaType, "{\"to\":\""+merchantFcmToken.getFcmToken()+"\",\"data\" : {\"title\":\"BharatPe\",\"body\":\""+message+"\",\"soundname\":\"bharatpenotification\",\"image\":\"icon\",\"image-type\":\"circular\",\"url\":\"loan.html\"}}");
			Request request = new Request.Builder()
			  .url("https://fcm.googleapis.com/fcm/send")
			  .post(body)
			  .addHeader("Content-Type", "application/json")
			  .addHeader("Authorization", "key="+LendingConstants.FCM_GOOGLE_API_KEY)
			  .addHeader("Accept", "*/*")
			  .addHeader("Cache-Control", "no-cache")
			  .addHeader("Host", "fcm.googleapis.com")
			  .addHeader("Accept-Encoding", "gzip, deflate")
			  .addHeader("Content-Length", "254")
			  .addHeader("Connection", "keep-alive")
			  .addHeader("cache-control", "no-cache")
			  .build();
			logger.info("VerifyOTP SendSuccessNotification api request : {}", request);
			try {
				Response response = client.newCall(request).execute();
				String responseBody = response.body().string();
				logger.info("SendSuccessNotification api response : {}", responseBody);
			} catch (IOException e) {
				e.printStackTrace();
				logger.info("SendSuccessNotification api exception : {} ",e.getMessage());
			}
		}
		
	}
}
