package com.bharatpe.lending.service;

//import java.util.*;
//
//import com.bharatpe.common.dao.DocKycDetailsDao;
//import com.bharatpe.common.dao.DocumentsIdProofDao;
//import com.bharatpe.common.entities.MerchantFcmToken;
//import com.bharatpe.common.enums.NotificationProvider;
//import com.bharatpe.common.handlers.PushNotificationHandler;
//import com.bharatpe.common.handlers.SmsServiceHandler;
//import com.bharatpe.common.service.WhatsappNotificationService;
//
//import com.bharatpe.lending.common.service.merchant.dto.BankDetailsDto;
//import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
//import com.bharatpe.lending.common.service.merchant.service.MerchantService;
//import org.apache.commons.lang.StringUtils;
//import org.json.JSONObject;
//import org.json.XML;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

//import com.bharatpe.common.dao.MerchantFcmTokenDao;
//import com.bharatpe.common.entities.DocKycDetails;
//import com.bharatpe.common.entities.DocumentsIdProof;
//import com.bharatpe.common.entities.LendingApplication;
//import com.bharatpe.lending.common.dao.CreditApplicationAddressDao;
//import com.bharatpe.lending.common.dao.CreditApplicationTransitionDao;
//import com.bharatpe.lending.common.dao.LendingEkycDao;
//import com.bharatpe.lending.common.dao.LendingManualKycDao;
//import com.bharatpe.lending.common.dao.MerchantDocumentProofDao;
//import com.bharatpe.lending.common.dao.MerchantDocumentProofOcrDao;
//import com.bharatpe.lending.common.entity.CreditApplication;
//import com.bharatpe.lending.common.entity.CreditApplicationTransition;
//import com.bharatpe.lending.common.entity.LendingEkyc;
//import com.bharatpe.lending.common.entity.LendingManualKyc;
//import com.bharatpe.lending.common.entity.MerchantDocumentProof;
//import com.bharatpe.lending.common.entity.MerchantDocumentProofOcr;
//import com.bharatpe.lending.dao.LendingApplicationDao;
//import com.bharatpe.lending.dto.CreditLineKycResponseDto;
//import com.bharatpe.lending.dto.EKycRequestDTO;
//import com.bharatpe.lending.dto.EkycManualRequestDTO;
//import com.bharatpe.lending.dto.RequestDTO;
//import com.bharatpe.lending.handlers.S3BucketHandler;
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;


@Service
public class CreditLineKycService {

//	@Autowired
//	LendingEkycDao  lendingEkycDao;
//
//	@Autowired
//	CreditApplicationTransitionDao 	creditApplicationTransitionDao;
//
//	@Autowired
//	LendingManualKycDao lendingManualKycDao;
////	@Autowired
////	MerchantDao merchantDao;
//	@Autowired
//	CreditApplicationAddressDao creditApplicationAddressDao;
//	@Autowired
//	CreditApplicationDao creditApplicationDao;
//	@Autowired
//	S3BucketHandler s3BucketHandler;
//
//	@Value("${aws.s3.lending.ekyc.bucket}")
//	private String bucket;
//
//	@Autowired
//	SmsServiceHandler smsServiceHandler;
//
//	@Autowired
//	WhatsappNotificationService whatsappNotificationService;
//
//	@Autowired
//	MerchantService merchantService;
//
//	@Autowired
//	RedisNotificationService redisNotificationService;
//
//	@Autowired
//	MerchantFcmTokenDao merchantFcmTokenDao;
//
//	@Autowired
//	PushNotificationHandler pushNotificationHandler;
//
//	Logger logger=LoggerFactory.getLogger(CreditLineKycService.class);
//
//	@Autowired
//	ObjectMapper objectMapper;
//
//	@Autowired
//	LendingApplicationDao lendingApplicationDao;
//
//	@Autowired
//	MerchantDocumentProofDao merchantDocumentProofDao;
//
//	@Autowired
//	MerchantDocumentProofOcrDao merchantDocumentProofOcrDao;
//
//	@Autowired
//	DocumentsIdProofDao documentsIdProofDao;
//
//	@Autowired
//	DocKycDetailsDao docKycDetailsDao;
//
//	@Autowired
//	VerifyOTPService verifyOTPService;
	
//	public  CreditLineKycResponseDto fetchAddress(BasicDetailsDto merchant) {
//
//		CreditLineKycResponseDto creditLineKycResponseDto =new CreditLineKycResponseDto();
//		LendingManualKyc lendingManualKyc=lendingManualKycDao.findTop1ByMerchantIdOrderByIdDesc(merchant.getId());
//		if(lendingManualKyc==null)
//		{
//			LendingEkyc lendingEkyc=lendingEkycDao.findTop1ByMerchantIdOrderByIdDesc(merchant.getId());
//
//			if(lendingEkyc==null)return creditLineKycResponseDto;
//			else
//			{
//
//				creditLineKycResponseDto.setFullAddress(lendingEkyc.getAddress());
//				creditLineKycResponseDto.setPincode(Long.valueOf(lendingEkyc.getPincode()));
//				creditLineKycResponseDto.setCity(lendingEkyc.getCity());
//				creditLineKycResponseDto.setState(lendingEkyc.getState());
//			}
//		}
//		else
//		{
//			creditLineKycResponseDto.setFullAddress(lendingManualKyc.getAddress());
//			creditLineKycResponseDto.setPincode(Long.valueOf(lendingManualKyc.getPincode()));
//			creditLineKycResponseDto.setCity(lendingManualKyc.getCity());
//			creditLineKycResponseDto.setState(lendingManualKyc.getState());
//		}
//		return creditLineKycResponseDto;
//	}
//
////	public Boolean isEkycDone(Merchant merchant) {
////		try{
////			LendingEkyc lendingEkyc=lendingEkycDao.findSuccessEkyc(merchant.getId());
////			if(lendingEkyc!=null){
////				return true;
////			}
////			return false;
////		}
////		catch(Exception e) {
////			return null;
////		}
////	}
//
//	public   Object verifyAddress(BasicDetailsDto merchantBasicDetails,RequestDTO< EkycManualRequestDTO> requestDTO) {
//
//		Map<String,Object>map=new HashMap<>();
//		EkycManualRequestDTO eKycManualRequestDTO=requestDTO.getPayload();
//		if(eKycManualRequestDTO==null)
//		{
//			map.put("success", false);
//			map.put("message", "empty request");
//			return map;
//		}
//		CreditApplication creditApplication=creditApplicationDao.findTop1ByMerchantIdOrderByIdDesc(merchantBasicDetails.getId());
//		if(creditApplication==null)
//		{
//			map.put("success", false);
//			map.put("message", "credit application not found");
//			return map;
//		}
//
////		 Boolean eKycSuccess=isEkycDone(merchant);
////		 if(eKycSuccess==null) {
////			 map.put("success", false);
////			 map.put("message", "Error occured while checking ekyc success status");
////			 return map;
////		 }
//		if(eKycManualRequestDTO.getIsAddressChanged())
//		{
//			LendingManualKyc lendingManualKyc=new LendingManualKyc();
//			lendingManualKyc.setApplicationId(creditApplication.getId());
//			lendingManualKyc.setMerchantId(merchantBasicDetails.getId());
//			lendingManualKyc.setmId(merchantBasicDetails.getMid());
//			lendingManualKyc.setAddress(eKycManualRequestDTO.getFullAdress());
//			lendingManualKyc.setCity(eKycManualRequestDTO.getCity());
//			lendingManualKyc.setPincode(String.valueOf(eKycManualRequestDTO.getPincode()));
//			lendingManualKyc.setState(eKycManualRequestDTO.getState());
//			lendingManualKycDao.save(lendingManualKyc);
//
//			map.put("success", true);
//			map.put("message", "address change successfully");
//			creditApplication.setStatus("kyc");
//			creditApplicationDao.save(creditApplication);
//			CreditApplicationTransition creditApplicationTransition =new CreditApplicationTransition ();
//			creditApplicationTransition.setApplicationId(creditApplication.getId());
//			creditApplicationTransition.setFromStatus("draft");
//			creditApplicationTransition.setToStatus("kyc");
//			creditApplicationTransition.setComment("");
//			creditApplicationTransitionDao.save(creditApplicationTransition);
//			return map;
//		}
//		creditApplication.setStatus("kyc");
//		creditApplicationDao.save(creditApplication);
//		CreditApplicationTransition creditApplicationTransition =new CreditApplicationTransition ();
//		creditApplicationTransition.setApplicationId(creditApplication.getId());
//		creditApplicationTransition.setFromStatus("draft");
//		creditApplicationTransition.setToStatus("kyc");
//		creditApplicationTransition.setComment("");
//		creditApplicationTransitionDao.save(creditApplicationTransition);
//		map.put("success", true);
//		map.put("message", "address same");
//
//		// TODO : remove this and use api
////		Merchant merchant = merchantDao.getById(merchantBasicDetails.getId());
//		sendNotification(merchantBasicDetails,creditApplication);
//		redisNotificationService.sendEnachNotificationForCreditLine(merchantBasicDetails, creditApplication);
//
//		if ((merchantBasicDetails.getId().equals(1141505L) || merchantBasicDetails.getId().equals(3612680L)) && creditApplication.getAmount() <= 50000)
//			verifyOTPService.sendDetailsForKycVerification(merchantBasicDetails.getId(),creditApplication.getId(),true);
//		return map;
//	}
//
//	public Object eKycInitiate(BasicDetailsDto merchant) {
//
//		Map<String,Object>map=new HashMap<>();
//
//		map.put("success", true);
//		map.put("message", "merchant found successfully");
//		map.put("mid", merchant.getMid());
//		return map;
//
//
//	}
//
//	public Object eKycSubmit(BasicDetailsDto merchantBasicDetails, RequestDTO<EKycRequestDTO> requestDTO) {
//		logger.info("Ekyc response for merchant:{} is {}", merchantBasicDetails.getId(), requestDTO);
//		Map<String,Object>map=new HashMap<>();
//		String module="CREDIT_LINE";
//		EKycRequestDTO eKycRequestDTO=requestDTO.getPayload();
//		if(eKycRequestDTO==null)
//		{
//			map.put("success", false);
//			map.put("message", "empty request");
//			return map;
//		}
//		Long applicationId=null;
//		CreditApplication creditApplication=creditApplicationDao.findTop1ByMerchantIdOrderByIdDesc(merchantBasicDetails.getId());
//		LendingApplication lendingApplication=null;
//		if(creditApplication==null || !creditApplication.getStatus().equalsIgnoreCase("draft"))
//		{
//			lendingApplication=lendingApplicationDao.findTop1ByMerchantIdOrderByIdDesc(merchantBasicDetails.getId());
//			if(lendingApplication==null) {
//				map.put("success", false);
//				map.put("message", "Application not found");
//				return map;
//			}
//			applicationId=lendingApplication.getId();
//			module="LENDING";
//		}
//		else {
//			applicationId=creditApplication.getId();
//		}
//		LendingEkyc lendingEkyc=new LendingEkyc();
//		lendingEkyc.setApplicationId(applicationId);
//		lendingEkyc.setMerchantId(merchantBasicDetails.getId());;
//		lendingEkyc.setmId(eKycRequestDTO.getmId());
//		lendingEkyc.setAddress(eKycRequestDTO.getAddress());
//		lendingEkyc.setCity(eKycRequestDTO.getCity());
//		lendingEkyc.setCountry(eKycRequestDTO.getCountry());
//		lendingEkyc.setDob(eKycRequestDTO.getDob());
//		lendingEkyc.setGender(eKycRequestDTO.getGender());
//		lendingEkyc.setName(eKycRequestDTO.getName());
//		lendingEkyc.setPincode(eKycRequestDTO.getPincode());
//		lendingEkyc.setState(eKycRequestDTO.getState());
//		lendingEkyc.setStatus(eKycRequestDTO.getStatus());
//		lendingEkyc.setStatusMessage(eKycRequestDTO.getStatusMessage());
//		lendingEkyc.setResponse(eKycRequestDTO.getXmlResponse() != null ? eKycRequestDTO.getXmlResponse() : eKycRequestDTO.getResponse());
//		lendingEkyc.setModule(module);
//		if (eKycRequestDTO.getXmlResponse() != null) {
//			lendingEkyc.setMaskedAadhar(getMaskedAadharFromXML(eKycRequestDTO.getXmlResponse()));
//		} else {
//			lendingEkyc.setMaskedAadhar(getMaskedAadhar(eKycRequestDTO.getResponse()));
//		}
//		JsonNode uidData = null;
//		try {
//			if (eKycRequestDTO.getXmlResponse() != null) {
//				JSONObject jsonObject = XML.toJSONObject(eKycRequestDTO.getXmlResponse());
//				JsonNode jsonNode = objectMapper.readTree(jsonObject.toString());
//				if(jsonNode != null && jsonNode.get("OfflinePaperlessKyc") != null) {
//					uidData = jsonNode.get("OfflinePaperlessKyc").get("UidData");
//				}
//			} else if (eKycRequestDTO.getResponse() != null) {
//				JsonNode jsonNode = objectMapper.readTree(eKycRequestDTO.getResponse());
//				uidData = jsonNode.get("UidData");
//			}
//		} catch (Exception e) {
//			logger.error("Exception while parsing ekyc", e);
//		}
//		String imagePath = uploadAdhaarImage(uidData, merchantBasicDetails.getId());
//		lendingEkyc.setImagePath(imagePath);
//		lendingEkycDao.save(lendingEkyc);
//		if (lendingEkyc.getStatus() != null && lendingEkyc.getStatus()) {
//			if (module.equalsIgnoreCase("CREDIT_LINE")) {
//				MerchantDocumentProof merchantDocumentProof = insertInMerchantDocumentProof(merchantBasicDetails, lendingEkyc, applicationId);
//				insertInMerchantDocumentProofOcr(merchantBasicDetails, lendingEkyc, applicationId, merchantDocumentProof);
//			} else {
////				Merchant merchant = merchantDao.getById(merchantBasicDetails.getId());
//				DocumentsIdProof documentIdProof = insertIntoDocumentIdProof(merchantBasicDetails, lendingEkyc, lendingApplication);
//				insertIntoDocKycDetails(merchantBasicDetails, lendingEkyc, lendingApplication, documentIdProof);
//			}
//		}
//		map.put("success", true);
//		map.put("message", "ekyc created successfully");
//		return map;
//
//	}
//
//	private String uploadAdhaarImage(JsonNode uidData, Long merchantId) {
//		if(uidData != null) {
//			String pht = uidData.path("Pht").textValue();
//			String base64Encoded = processBase64String(pht);
//			String fileName = merchantId + "" + ((int) (Math.random() * ((100000 - 1) + 1)) + 1) + ".jpeg";
//			return s3BucketHandler.uploadToS3Bucket(base64Encoded, fileName, bucket);
//		}
//		return null;
//	}
//
//	private MerchantDocumentProof insertInMerchantDocumentProof(BasicDetailsDto merchant, LendingEkyc lendingEkyc,
//																Long applicationId) {
//
//		MerchantDocumentProof merchantDocumentProof=new MerchantDocumentProof();
//		merchantDocumentProof.setMerchantId(merchant.getId());
//		merchantDocumentProof.setProofType("eAadhar");
//		merchantDocumentProof.setProofNumber(lendingEkyc.getMaskedAadhar());
//		merchantDocumentProof.setOwnerId(applicationId);
//		merchantDocumentProof.setOwnerType("LENDING");
//		merchantDocumentProof.setStatus("APPROVED");
//		merchantDocumentProof.setApprovedDate(new Date());
//		merchantDocumentProof.setIsVerified(true);
//		merchantDocumentProof.setProvider("INVOID");
//		merchantDocumentProof.setProofFrontSide(lendingEkyc.getImagePath());
//		merchantDocumentProofDao.save(merchantDocumentProof);
//		return merchantDocumentProof;
//	}
//
//	private void insertInMerchantDocumentProofOcr(BasicDetailsDto merchant, LendingEkyc lendingEkyc,Long applicationId,
//												  MerchantDocumentProof merchantDocumentProof) {
//
//		MerchantDocumentProofOcr merchantDocumentProofOcr=new MerchantDocumentProofOcr();
//		merchantDocumentProofOcr.setMerchantId(merchant.getId());
//		merchantDocumentProofOcr.setProofType("eAadhar");
//		merchantDocumentProofOcr.setProofNumber(lendingEkyc.getMaskedAadhar());
//		merchantDocumentProofOcr.setName(lendingEkyc.getName());
//		merchantDocumentProofOcr.setProvider("INVOID");
//		merchantDocumentProofOcr.setStatus("APPROVED");
//		merchantDocumentProofOcr.setIsVerified(true);
//		merchantDocumentProofOcr.setDocumentId(merchantDocumentProof.getId());
//		merchantDocumentProofOcr.setPincode(lendingEkyc.getPincode());
//		merchantDocumentProofOcr.setGender(lendingEkyc.getGender());
//		merchantDocumentProofOcr.setDob(lendingEkyc.getDob());
//		merchantDocumentProofOcr.setAddress(lendingEkyc.getAddress());
//		merchantDocumentProofOcr.setCity(lendingEkyc.getCity());
//		merchantDocumentProofOcr.setState(lendingEkyc.getState());
//		merchantDocumentProofOcrDao.save(merchantDocumentProofOcr);
//	}
//
//	public String getMaskedAadhar(String response) {
//		try {
//			if(response!=null && response.length()>0) {
//				Map<String,Object> responseMap=objectMapper.readValue(response, Map.class);
//				if(responseMap!=null && responseMap.containsKey("referenceId")) {
//					String aadhar=responseMap.get("referenceId").toString();
//					if(aadhar.length()>4) {
//						return aadhar.substring(0,4);
//					}
//				}
//			}
//		}
//		catch(Exception e) {
//			logger.error("Error occured while getting masked aadhar ",e);
//		}
//		return null;
//	}
//
//	public String getMaskedAadharFromXML(String response) {
//		try {
//			if(response!=null && response.length()>0) {
//				JSONObject jsonObject = XML.toJSONObject(response);
//				JsonNode jsonNode = objectMapper.readTree(jsonObject.toString());
//				if(jsonNode != null && jsonNode.get("OfflinePaperlessKyc") != null && jsonNode.get("OfflinePaperlessKyc").get("referenceId") != null) {
//					String aadhar = jsonNode.get("OfflinePaperlessKyc").get("referenceId").asText();
//					if(aadhar.length() > 4) {
//						return aadhar.substring(0,4);
//					}
//				}
//			}
//		}
//		catch(Exception e) {
//			logger.error("Error occured while getting masked aadhar ",e);
//		}
//		return null;
//	}
//
//	public String processBase64String(String base64EncodedString) {
//		base64EncodedString.replace(' ', '+');
//		if(base64EncodedString.contains("base64,")) {
//			String [] base64EncodedSplit = base64EncodedString.split("base64,");
//			base64EncodedString = base64EncodedSplit[1];
//		}
//		return base64EncodedString;
//
//	}
//
//	public void sendNotification(BasicDetailsDto merchant, CreditApplication creditApplication) {
//		List<String> mobiles = new ArrayList<>();
//		mobiles.add(merchant.getMobile());
//		String message=getNotificationContent(merchant.getId(), creditApplication);
//		if(message!=null) {
//			smsServiceHandler.sendSMS(mobiles, message, NotificationProvider.SMS.GUPSHUP);
//			whatsappNotificationService.send(merchant.getId(), null,merchant.getBeneficiaryName(), message, mobiles, null);
//
//			MerchantFcmToken merchantFcmToken = merchantFcmTokenDao.getByMerchantId(merchant.getId());
//			if(merchantFcmToken != null) {
//				pushNotificationHandler.sendPushNotification(merchantFcmToken.getFcmToken(), merchantFcmToken.getPlatform(), message, "dynamic?key=credit-line");
//			}
//		}
//	}
//
//	public String getNotificationContent(Long merchantId,CreditApplication creditApplication) {
//		final Optional<BankDetailsDto> bankDetailsDtoOptional = merchantService.fetchMerchantBankDetails(merchantId);
//		BankDetailsDto merchantBankDetail = null;
//		if (bankDetailsDtoOptional.isPresent())
//			merchantBankDetail = bankDetailsDtoOptional.get();
//		if(merchantBankDetail==null) {
//			return null;
//		}
////		String message = "Hi  " + merchantBankDetail.getBeneficiaryName() + ",\n\n" +
////				"Your loan application for INR " + creditApplication.getAmount().intValue() + " has been received successfully.\n" +
////				"Your Application ID is " + creditApplication.getExternalLoanId() + ".";
//
//		String message = "Hi  " + merchantBankDetail.getBeneficiaryName() + ",\n\n" +
//				"Your Application (ID - "+creditApplication.getExternalLoanId()+") for Rs. " + creditApplication.getAmount().intValue() + "BharatPe Loan Balance has been registered successfully. Application is under review and limit will be activated within 48 hours";
//		return message;
//	}
//
//	private DocumentsIdProof insertIntoDocumentIdProof(BasicDetailsDto merchant, LendingEkyc lendingEkyc,LendingApplication lendingApplication) {
//
//		DocumentsIdProof documentsProof = documentsIdProofDao.fetchLatestAddressProof(merchant.getId(),lendingApplication.getId(),"LENDING");
//		if(documentsProof != null){
//			documentsProof.setDeletedAt(new Date());
//			documentsIdProofDao.save(documentsProof);
//		}
//		DocumentsIdProof documentsIdProof=new DocumentsIdProof();
//		documentsIdProof.setMerchantId(lendingApplication.getMerchantId());
//		documentsIdProof.setProofType("eAadhar");
//		documentsIdProof.setStatus("APPROVED");
//		documentsIdProof.setLendingApplication(lendingApplication);
//		documentsIdProof.setLatitude(lendingApplication.getLatitude());
//		documentsIdProof.setProofFrontSide(lendingEkyc.getImagePath());
//		documentsIdProof.setIp(lendingApplication.getIp());
//		documentsIdProof.setLatitude(lendingApplication.getLatitude());
//		documentsIdProof.setLongitude(lendingApplication.getLongitude());
//		documentsIdProofDao.save(documentsIdProof);
//
//		return documentsIdProof;
//
//	}
//
//	private void insertIntoDocKycDetails(BasicDetailsDto merchant,LendingEkyc lendingEkyc,LendingApplication lendingApplication,DocumentsIdProof documentIdProof) {
//
//		DocKycDetails docKycDetails=new DocKycDetails();
//		docKycDetails.setMerchantId(lendingApplication.getMerchantId());
//		docKycDetails.setDocType("eAadhar");
//		docKycDetails.setAddress(lendingEkyc.getAddress());
//		docKycDetails.setCity(lendingEkyc.getCity());
//		docKycDetails.setDob(lendingEkyc.getDob());
//		docKycDetails.setDocumentsIdProof(documentIdProof);
//		docKycDetails.setPincode(lendingEkyc.getPincode()!=null?lendingEkyc.getPincode():null);
//		docKycDetails.setState(lendingEkyc.getState());
//		docKycDetails.setGender(lendingEkyc.getGender());
//		docKycDetails.setStatus("APPROVED");
//		docKycDetails.setPersonName(StringUtils.substring(lendingEkyc.getName(), 0, 30));
////		docKycDetails.setResponse(lendingEkyc.getResponse());
//		docKycDetails.setModule("LENDING");
//		docKycDetailsDao.save(docKycDetails);
//	}

}
