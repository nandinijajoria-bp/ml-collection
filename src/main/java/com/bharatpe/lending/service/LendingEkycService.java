package com.bharatpe.lending.service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.Merchant;
import com.bharatpe.lending.common.dao.LendingEkycDao;
import com.bharatpe.lending.common.entity.LendingEkyc;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dto.EKycRequestDTO;
import com.bharatpe.lending.dto.RequestDTO;
import com.bharatpe.lending.handlers.S3BucketHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class LendingEkycService {
	
	@Autowired
	LendingApplicationDao lendingApplicationDao;
	
	@Autowired
	S3BucketHandler s3BucketHandler;
	
	@Autowired
	LendingEkycDao lendingEkycDao;
	
	@Value("${aws.s3.lending.ekyc.bucket}")
	private String bucket;
	
	Logger logger=LoggerFactory.getLogger(LendingEkycService.class);
	
	@Autowired
	ObjectMapper objectMapper;
	
	public Object eKycInitiate(Merchant merchant) {

		Map<String,Object>map=new HashMap<>();

		map.put("success", true);
		map.put("message", "merchant found successfully");
		map.put("mid", merchant.getMid());
		return map;


	}
	
	public Object eKycSubmit(Merchant merchant, RequestDTO<EKycRequestDTO> requestDTO) {
		Map<String,Object>map=new HashMap<>();


		EKycRequestDTO eKycRequestDTO=requestDTO.getPayload();
		if(eKycRequestDTO==null)
		{
			map.put("success", false);
			map.put("message", "empty request");
			return map;
		}
		else if(eKycRequestDTO.getmId().equals(""))
		{
			map.put("success", false);
			map.put("message", "invalid mid");
			return map;
		}
		LendingApplication lendingApplication=lendingApplicationDao.findTop1ByMerchantOrderByIdDesc(merchant);
		if(lendingApplication==null)
		{    
			map.put("success", false);
			map.put("message", "credit application not found");
			return map;
		}
		LendingEkyc lendingEkyc=new LendingEkyc();
		lendingEkyc.setApplicationId(lendingApplication.getId());
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
		lendingEkyc.setResponse(eKycRequestDTO.getResponse());
		lendingEkyc.setModule("LENDING");
		lendingEkyc.setMaskedAadhar(getMaskedAadhar(eKycRequestDTO.getResponse()));
		String response=eKycRequestDTO.getResponse();
		ObjectMapper mapper = new ObjectMapper();
		JsonNode rootNode=null;
		try {
			rootNode = mapper.readTree(response);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		JsonNode uidData = (rootNode != null) ? rootNode.path("UidData") : null;
		if(uidData==null)
			return map;
		String pht=uidData.path("Pht").textValue();
		String  base64Encoded = processBase64String(pht);
		String fileName = merchant.getId() + "" + ((int)(Math.random() * ((100000 - 1) + 1)) + 1) + ".jpeg";
		String imagePath=s3BucketHandler.uploadToS3Bucket(base64Encoded, fileName, bucket);
		lendingEkyc.setImagePath(imagePath); 
		lendingEkycDao.save(lendingEkyc);
		map.put("success", true);
		map.put("message", "ekyc created successfully");
		return map;

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
	
}
