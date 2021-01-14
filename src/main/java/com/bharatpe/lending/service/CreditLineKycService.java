package com.bharatpe.lending.service;

import java.io.IOException;
import java.util.*;

import com.bharatpe.common.dao.DocKycDetailsDao;
import com.bharatpe.common.dao.DocumentsIdProofDao;
import com.bharatpe.common.dao.MerchantBankDetailDao;
import com.bharatpe.common.entities.MerchantBankDetail;
import com.bharatpe.common.entities.MerchantFcmToken;
import com.bharatpe.common.enums.NotificationProvider;
import com.bharatpe.common.handlers.PushNotificationHandler;
import com.bharatpe.common.handlers.SmsServiceHandler;
import com.bharatpe.common.service.WhatsappNotificationService;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.bharatpe.common.dao.MerchantDao;
import com.bharatpe.common.dao.MerchantFcmTokenDao;
import com.bharatpe.common.entities.DocKycDetails;
import com.bharatpe.common.entities.DocumentsIdProof;
import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.Merchant;
import com.bharatpe.lending.common.dao.CreditApplicationAddressDao;
import com.bharatpe.lending.common.dao.CreditApplicationDao;
import com.bharatpe.lending.common.dao.CreditApplicationTransitionDao;
import com.bharatpe.lending.common.dao.LendingEkycDao;
import com.bharatpe.lending.common.dao.LendingManualKycDao;
import com.bharatpe.lending.common.dao.MerchantDocumentProofDao;
import com.bharatpe.lending.common.dao.MerchantDocumentProofOcrDao;
import com.bharatpe.lending.common.entity.CreditApplication;
import com.bharatpe.lending.common.entity.CreditApplicationTransition;
import com.bharatpe.lending.common.entity.LendingEkyc;
import com.bharatpe.lending.common.entity.LendingManualKyc;
import com.bharatpe.lending.common.entity.MerchantDocumentProof;
import com.bharatpe.lending.common.entity.MerchantDocumentProofOcr;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dto.CreditLineKycResponseDto;
import com.bharatpe.lending.dto.EKycRequestDTO;
import com.bharatpe.lending.dto.EkycManualRequestDTO;
import com.bharatpe.lending.dto.RequestDTO;
import com.bharatpe.lending.handlers.S3BucketHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;


@Service
public class CreditLineKycService {

	@Autowired
	LendingEkycDao  lendingEkycDao;

	@Autowired
	CreditApplicationTransitionDao 	creditApplicationTransitionDao;

	@Autowired
	LendingManualKycDao lendingManualKycDao;
	@Autowired
	MerchantDao merchantDao;
	@Autowired
	CreditApplicationAddressDao creditApplicationAddressDao;
	@Autowired
	CreditApplicationDao creditApplicationDao;
	@Autowired
	S3BucketHandler s3BucketHandler;
 
	@Value("${aws.s3.lending.ekyc.bucket}")
	private String bucket;

	@Autowired
	SmsServiceHandler smsServiceHandler;

	@Autowired
	WhatsappNotificationService whatsappNotificationService;

	@Autowired
	MerchantBankDetailDao merchantBankDetailDao;
	
	@Autowired
	RedisNotificationService redisNotificationService;
	
	@Autowired
	MerchantFcmTokenDao merchantFcmTokenDao;
	
	@Autowired
	PushNotificationHandler pushNotificationHandler;
	
	Logger logger=LoggerFactory.getLogger(CreditLineKycService.class);
	
	@Autowired
	ObjectMapper objectMapper;
	
	@Autowired
	LendingApplicationDao lendingApplicationDao;
	
	@Autowired
	MerchantDocumentProofDao merchantDocumentProofDao;
	
	@Autowired
	MerchantDocumentProofOcrDao merchantDocumentProofOcrDao;
	
	@Autowired
	DocumentsIdProofDao documentsIdProofDao;
	
	@Autowired
	DocKycDetailsDao docKycDetailsDao;
	
	@Autowired
	VerifyOTPService verifyOTPService;
	
	public  CreditLineKycResponseDto fetchAddress(Merchant merchant) {

		CreditLineKycResponseDto creditLineKycResponseDto =new CreditLineKycResponseDto();
		LendingManualKyc lendingManualKyc=lendingManualKycDao.findTop1ByMerchantIdOrderByIdDesc(merchant.getId());
		if(lendingManualKyc==null)
		{
			LendingEkyc lendingEkyc=lendingEkycDao.findTop1ByMerchantIdOrderByIdDesc(merchant.getId());

			if(lendingEkyc==null)return creditLineKycResponseDto;
			else
			{

				creditLineKycResponseDto.setFullAddress(lendingEkyc.getAddress());
				creditLineKycResponseDto.setPincode(Long.valueOf(lendingEkyc.getPincode()));
				creditLineKycResponseDto.setCity(lendingEkyc.getCity());
				creditLineKycResponseDto.setState(lendingEkyc.getState());
			}
		}
		else
		{
			creditLineKycResponseDto.setFullAddress(lendingManualKyc.getAddress());
			creditLineKycResponseDto.setPincode(Long.valueOf(lendingManualKyc.getPincode()));
			creditLineKycResponseDto.setCity(lendingManualKyc.getCity());
			creditLineKycResponseDto.setState(lendingManualKyc.getState());
		}
		return creditLineKycResponseDto;
	}

//	public Boolean isEkycDone(Merchant merchant) {
//		try{
//			LendingEkyc lendingEkyc=lendingEkycDao.findSuccessEkyc(merchant.getId());
//			if(lendingEkyc!=null){
//				return true;
//			}
//			return false;
//		}
//		catch(Exception e) {
//			return null;
//		}
//	}

	public   Object verifyAddress(Merchant merchant,RequestDTO< EkycManualRequestDTO> requestDTO) {

		Map<String,Object>map=new HashMap<>();
		EkycManualRequestDTO eKycManualRequestDTO=requestDTO.getPayload();
		if(eKycManualRequestDTO==null)
		{
			map.put("success", false);
			map.put("message", "empty request");
			return map;
		}
		CreditApplication creditApplication=creditApplicationDao.findTop1ByMerchantIdOrderByIdDesc(merchant.getId());
		if(creditApplication==null)
		{
			map.put("success", false);
			map.put("message", "credit application not found");
			return map;
		}

//		 Boolean eKycSuccess=isEkycDone(merchant);
//		 if(eKycSuccess==null) {
//			 map.put("success", false);
//			 map.put("message", "Error occured while checking ekyc success status");
//			 return map;
//		 }
		if(eKycManualRequestDTO.getIsAddressChanged())
		{
			LendingManualKyc lendingManualKyc=new LendingManualKyc();
			lendingManualKyc.setApplicationId(creditApplication.getId());
			lendingManualKyc.setMerchantId(merchant.getId());
			lendingManualKyc.setmId(merchant.getMid());
			lendingManualKyc.setAddress(eKycManualRequestDTO.getFullAdress());
			lendingManualKyc.setCity(eKycManualRequestDTO.getCity());
			lendingManualKyc.setPincode(String.valueOf(eKycManualRequestDTO.getPincode()));
			lendingManualKyc.setState(eKycManualRequestDTO.getState());
			lendingManualKycDao.save(lendingManualKyc);

			map.put("success", true);
			map.put("message", "address change successfully");
			creditApplication.setStatus("kyc");
			creditApplicationDao.save(creditApplication);
			CreditApplicationTransition creditApplicationTransition =new CreditApplicationTransition ();
			creditApplicationTransition.setApplicationId(creditApplication.getId());
			creditApplicationTransition.setFromStatus("draft");
			creditApplicationTransition.setToStatus("kyc");
			creditApplicationTransition.setComment("");
			creditApplicationTransitionDao.save(creditApplicationTransition);
			return map;
		}
		creditApplication.setStatus("kyc");
		creditApplicationDao.save(creditApplication);
		CreditApplicationTransition creditApplicationTransition =new CreditApplicationTransition ();
		creditApplicationTransition.setApplicationId(creditApplication.getId());
		creditApplicationTransition.setFromStatus("draft");
		creditApplicationTransition.setToStatus("kyc");
		creditApplicationTransition.setComment("");
		creditApplicationTransitionDao.save(creditApplicationTransition);
		map.put("success", true);
		map.put("message", "address same");
		sendNotification(merchant,creditApplication);
		redisNotificationService.sendEnachNotificationForCreditLine(merchant, creditApplication);
		if ((merchant.getId().equals(1141505L) || merchant.getId().equals(3612680L)) && creditApplication.getAmount() <= 50000)
			verifyOTPService.sendDetailsForKycVerification(merchant.getId(),creditApplication.getId(),true);
		return map;
	}

	public Object eKycInitiate(Merchant merchant) {

		Map<String,Object>map=new HashMap<>();

		map.put("success", true);
		map.put("message", "merchant found successfully");
		map.put("mid", merchant.getMid());
		return map;


	}

	public Object eKycSubmit(Merchant merchant, RequestDTO<EKycRequestDTO> requestDTO) {
		logger.info("Ekyc response for merchant:{} is {}", merchant.getId(), requestDTO);
		Map<String,Object>map=new HashMap<>();
		String module="CREDIT_LINE";
		EKycRequestDTO eKycRequestDTO=requestDTO.getPayload();
		if(eKycRequestDTO==null)
		{
			map.put("success", false);
			map.put("message", "empty request");
			return map;
		}
		Long applicationId=null;
		CreditApplication creditApplication=creditApplicationDao.findTop1ByMerchantIdOrderByIdDesc(merchant.getId());
		LendingApplication lendingApplication=null;
		if(creditApplication==null || !creditApplication.getStatus().equalsIgnoreCase("draft"))
		{   
			lendingApplication=lendingApplicationDao.findTop1ByMerchantOrderByIdDesc(merchant);
			if(lendingApplication==null) {
				map.put("success", false);
				map.put("message", "Application not found");
				return map;	
			}
			applicationId=lendingApplication.getId();
			module="LENDING";
		}
		else {
			applicationId=creditApplication.getId();
		}
		LendingEkyc lendingEkyc=new LendingEkyc();
		lendingEkyc.setApplicationId(applicationId);
		lendingEkyc.setMerchantId(merchant.getId());;
		lendingEkyc.setmId(eKycRequestDTO.getmId());
		lendingEkyc.setAddress(eKycRequestDTO.getAddress());
		lendingEkyc.setCity(eKycRequestDTO.getCity());
		lendingEkyc.setCountry(eKycRequestDTO.getCountry());
		lendingEkyc.setDob(eKycRequestDTO.getDob());
		lendingEkyc.setGender(eKycRequestDTO.getGender());
		lendingEkyc.setName(eKycRequestDTO.getName());
		lendingEkyc.setPincode(eKycRequestDTO.getPincode());
		lendingEkyc.setState(eKycRequestDTO.getState());
		lendingEkyc.setStatus(eKycRequestDTO.getStatus());
		lendingEkyc.setStatusMessage(eKycRequestDTO.getStatusMessage());
		lendingEkyc.setResponse(eKycRequestDTO.getXmlResponse() != null ? eKycRequestDTO.getXmlResponse() : eKycRequestDTO.getResponse());
		lendingEkyc.setModule(module);
		lendingEkyc.setMaskedAadhar(getMaskedAadhar(eKycRequestDTO.getResponse()));
		String response=eKycRequestDTO.getResponse();
		JsonNode rootNode=null;
		if (response != null) {
			try {
				rootNode = objectMapper.readTree(response);
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}
		JsonNode uidData = (rootNode != null) ? rootNode.path("UidData") : null;
		if(uidData != null) {
			String pht = uidData.path("Pht").textValue();
			String base64Encoded = processBase64String(pht);
			String fileName = merchant.getId() + "" + ((int) (Math.random() * ((100000 - 1) + 1)) + 1) + ".jpeg";
			String imagePath = s3BucketHandler.uploadToS3Bucket(base64Encoded, fileName, bucket);
			lendingEkyc.setImagePath(imagePath);
			lendingEkycDao.save(lendingEkyc);
		}
		if (lendingEkyc.getStatus() != null && lendingEkyc.getStatus()) {
			if (module.equalsIgnoreCase("CREDIT_LINE")) {
				MerchantDocumentProof merchantDocumentProof = insertInMerchantDocumentProof(merchant, lendingEkyc, applicationId);
				insertInMerchantDocumentProofOcr(merchant, lendingEkyc, applicationId, merchantDocumentProof);
			} else {
				DocumentsIdProof documentIdProof = insertIntoDocumentIdProof(merchant, lendingEkyc, lendingApplication);
				insertIntoDocKycDetails(merchant, lendingEkyc, lendingApplication, documentIdProof);
			}
		}
		map.put("success", true);
		map.put("message", "ekyc created successfully");
		return map;

	}
	
	private MerchantDocumentProof insertInMerchantDocumentProof(Merchant merchant, LendingEkyc lendingEkyc,Long applicationId) {
		
		MerchantDocumentProof merchantDocumentProof=new MerchantDocumentProof();
		merchantDocumentProof.setMerchantId(merchant.getId());
		merchantDocumentProof.setProofType("eAadhar");
		merchantDocumentProof.setProofNumber(lendingEkyc.getMaskedAadhar());
		merchantDocumentProof.setOwnerId(applicationId);
		merchantDocumentProof.setOwnerType("LENDING");
		merchantDocumentProof.setStatus("APPROVED");
		merchantDocumentProof.setApprovedDate(new Date());
		merchantDocumentProof.setIsVerified(true);
		merchantDocumentProof.setProvider("INVOID");
		merchantDocumentProof.setProofFrontSide(lendingEkyc.getImagePath());
		merchantDocumentProofDao.save(merchantDocumentProof);
		return merchantDocumentProof;
	}
	
	private void insertInMerchantDocumentProofOcr(Merchant merchant, LendingEkyc lendingEkyc,Long applicationId,MerchantDocumentProof merchantDocumentProof) {
		
		MerchantDocumentProofOcr merchantDocumentProofOcr=new MerchantDocumentProofOcr();
		merchantDocumentProofOcr.setMerchantId(merchant.getId());
		merchantDocumentProofOcr.setProofType("eAadhar");
		merchantDocumentProofOcr.setProofNumber(lendingEkyc.getMaskedAadhar());
		merchantDocumentProofOcr.setName(lendingEkyc.getName());
		merchantDocumentProofOcr.setProvider("INVOID");
		merchantDocumentProofOcr.setStatus("APPROVED");
		merchantDocumentProofOcr.setIsVerified(true);
		merchantDocumentProofOcr.setDocumentId(merchantDocumentProof.getId());
		merchantDocumentProofOcr.setPincode(lendingEkyc.getPincode());
		merchantDocumentProofOcr.setGender(lendingEkyc.getGender());
		merchantDocumentProofOcr.setDob(lendingEkyc.getDob());
		merchantDocumentProofOcr.setAddress(lendingEkyc.getAddress());
		merchantDocumentProofOcr.setCity(lendingEkyc.getCity());
		merchantDocumentProofOcr.setState(lendingEkyc.getState());
		merchantDocumentProofOcrDao.save(merchantDocumentProofOcr);
	}
	
	public String getMaskedAadhar(String response) {
		try {
			if(response!=null && response.length()>0) {
				Map<String,Object> responseMap=objectMapper.readValue(response, Map.class);
				if(responseMap!=null && responseMap.containsKey("referenceId")) {
					String aadhar=responseMap.get("referenceId").toString();
					if(aadhar.length()>4) {
						return aadhar.substring(0,4);
					}
				}
			}
		}
		catch(Exception e) {
			logger.error("Error occured while getting masked aadhar ",e);
		}
		return null;
	}
	
	public String processBase64String(String base64EncodedString) {
		base64EncodedString.replace(' ', '+');
		if(base64EncodedString.contains("base64,")) {
			String [] base64EncodedSplit = base64EncodedString.split("base64,");
			base64EncodedString = base64EncodedSplit[1];
		}
		return base64EncodedString;
		 
	}

	public void sendNotification(Merchant merchant, CreditApplication creditApplication) {
		List<String> mobiles = new ArrayList<>();
		mobiles.add(merchant.getMobile());
		String message=getNotificationContent(merchant, creditApplication);
		if(message!=null) {
			smsServiceHandler.sendSMS(mobiles, message, NotificationProvider.SMS.GUPSHUP);
			whatsappNotificationService.send(merchant, null, message, mobiles, null);
			
			MerchantFcmToken merchantFcmToken = merchantFcmTokenDao.getByMerchantId(merchant.getId());			
			if(merchantFcmToken != null) {
				pushNotificationHandler.sendPushNotification(merchantFcmToken.getFcmToken(), merchantFcmToken.getPlatform(), message, "dynamic?key=credit-line");
			}
		}
	}

	public String getNotificationContent(Merchant merchant,CreditApplication creditApplication) {
		MerchantBankDetail merchantBankDetail=merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchant.getId(), "ACTIVE");
		if(merchantBankDetail==null) {
			return null;
		}
//		String message = "Hi  " + merchantBankDetail.getBeneficiaryName() + ",\n\n" +
//				"Your loan application for INR " + creditApplication.getAmount().intValue() + " has been received successfully.\n" +
//				"Your Application ID is " + creditApplication.getExternalLoanId() + ".";
		
		String message = "Hi  " + merchantBankDetail.getBeneficiaryName() + ",\n\n" +
				"Your Application (ID - "+creditApplication.getExternalLoanId()+") for Rs. " + creditApplication.getAmount().intValue() + "BharatPe Loan Balance has been registered successfully. Application is under review and limit will be activated within 48 hours";
		return message;
	}
	
	private DocumentsIdProof insertIntoDocumentIdProof(Merchant merchant, LendingEkyc lendingEkyc,LendingApplication lendingApplication) {
		
		DocumentsIdProof documentsIdProof=new DocumentsIdProof();
		documentsIdProof.setMerchant(merchant);
		documentsIdProof.setProofType("eAadhar");
		documentsIdProof.setStatus("APPROVED");
		documentsIdProof.setLendingApplication(lendingApplication);
		documentsIdProof.setLatitude(lendingApplication.getLatitude());
		documentsIdProof.setProofFrontSide(lendingEkyc.getImagePath());
		documentsIdProof.setIp(lendingApplication.getIp());
		documentsIdProof.setLatitude(lendingApplication.getLatitude());
		documentsIdProof.setLongitude(lendingApplication.getLongitude());
		documentsIdProofDao.save(documentsIdProof);
		
		return documentsIdProof;
		
	}
	
	private void insertIntoDocKycDetails(Merchant merchant,LendingEkyc lendingEkyc,LendingApplication lendingApplication,DocumentsIdProof documentIdProof) {
		
		DocKycDetails docKycDetails=new DocKycDetails();
		docKycDetails.setMerchant(merchant);
		docKycDetails.setDocType("eAadhar");
		docKycDetails.setAddress(lendingEkyc.getAddress());
		docKycDetails.setCity(lendingEkyc.getCity());
		docKycDetails.setDob(lendingEkyc.getDob());
		docKycDetails.setDocumentsIdProof(documentIdProof);
		docKycDetails.setPincode(lendingEkyc.getPincode()!=null?Integer.valueOf(lendingEkyc.getPincode()):null);
		docKycDetails.setState(lendingEkyc.getState());
		docKycDetails.setGender(lendingEkyc.getGender());
		docKycDetails.setStatus("APPROVED");
		docKycDetails.setPersonName(StringUtils.substring(lendingEkyc.getName(), 0, 30));
//		docKycDetails.setResponse(lendingEkyc.getResponse());
		docKycDetails.setModule("LENDING");
		docKycDetailsDao.save(docKycDetails);
	}

}
