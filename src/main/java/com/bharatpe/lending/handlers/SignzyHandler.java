//package com.bharatpe.lending.handlers;
//
//import java.io.IOException;
//import java.time.Duration;
//import java.time.Instant;
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.LinkedHashMap;
//import java.util.List;
//import java.util.Map;
//
//import com.bharatpe.lending.common.slave.dao.SignzyCredentialDaoSlave;
//import com.bharatpe.lending.common.slave.entity.SignzyCredentialSlave;
//import com.bharatpe.lending.service.APIGatewayService;
//import com.fasterxml.jackson.core.JsonProcessingException;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.http.HttpEntity;
//import org.springframework.http.HttpHeaders;
//import org.springframework.stereotype.Component;
//import org.springframework.web.client.RestTemplate;
//
//import com.bharatpe.lending.constant.CreditConstants;
//import com.fasterxml.jackson.core.JsonParseException;
//import com.fasterxml.jackson.core.type.TypeReference;
//import com.fasterxml.jackson.databind.JsonMappingException;
//import com.fasterxml.jackson.databind.ObjectMapper;
//
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//@Component
//public class SignzyHandler {
//
//	Map<String,Object> category=new HashMap<>();
//
//	void populate() {
//		category.put("pancard","individualPan");
//		category.put("adhaarcard","aadhaar");
//		category.put("votarcard","voterid");
//		category.put("passport","passport");
//		category.put("driving_license","drivingLicence");
//	}
//
//
//	private Logger logger = LoggerFactory.getLogger(KarzaHandler.class);
//
//
//	@Autowired
//	RestTemplate restTemplate;
//
//	@Value("${signzy.url}")
//	public String SIGNZY_URL;
//
//	@Autowired
//	SignzyCredentialDaoSlave signzyCredentialDaoSlave;
//
//	@Autowired
//	APIGatewayService apiGatewayService;
//
//	@Autowired
//	ObjectMapper objectMapper;
//
//	String MODULE = "LENDING";
//
////	public Map<String,String>  curlSignzyKycAPI(String frontURL,String backURL,String proofType, Long merchantId) throws IOException {
////		populate();
////		SignzyCredentialSlave signzyCredential = signzyCredentialDaoSlave.findByModule(MODULE);
////		if (signzyCredential == null) {
////			logger.info("Signzy credentials not found for Lending");
////			return new HashMap<>();
////		}
////		return  signzyIdentityCreate( frontURL,backURL, signzyCredential.getAccessId(), signzyCredential.getUserId(), proofType, merchantId);
////	}
//
////	public  Map<String,String> signzyIdentityCreate(String frontURL,String backURL,String accessToken,String patronId,String proofType, Long merchantId) throws IOException
////	{
////
////		String response= null;
////
////		Map<String, Object> body = new HashMap<>();
////
////
////		body.put("type", category.get(proofType));
////		body.put( "email", "admin@signzy.com");
////
////		body.put( "callbackUrl", "https://your-domain.com/your-callback-system");
////		List<String> list =new ArrayList<>();
////		list.add(frontURL);
////		if(!backURL.equals(""))
////			list.add(backURL);
////		body.put("images", list);
////		logger.info("signzy KYC request : {}", body);
////		String url= SIGNZY_URL + CreditConstants.SIGNZY_IDENTITY_URL+"/"+patronId+"/identities";
////
////		HttpHeaders headers = new HttpHeaders();
////		headers.set("Content-Type", "application/json");
////		headers.set("Authorization", accessToken);
////		HttpEntity<Map<String, Object>> request  = new HttpEntity<>(body, headers);
////		try {
////			Instant start = Instant.now();
////			logger.info("Karza KYC request : {}", request);
////			response = restTemplate.postForObject(url, request, String.class);
////			logger.info("Karza KYC response : {}", response);
////			Instant end = Instant.now();
////			logger.info("Time Taken by Karza KYC API : {} miliseconds", Duration.between(start, end).toMillis());
////			apiGatewayService.insertIntoSignzyReqRes(merchantId, null, "IDENTITY", "SUCCESS", objectMapper.writeValueAsString(request), response, MODULE);
////		}catch(Exception e) {
////			logger.info("exception while Karza KYC API Identity, signedURL : {}, Exception is {}", frontURL, e);
////			logger.info("identity signzy KYC API  url: ", url);
////			apiGatewayService.insertIntoSignzyReqRes(merchantId, null, "IDENTITY", "FAILED", objectMapper.writeValueAsString(request), response, MODULE);
////		}
////		Map<String, Object> responseMap = objectMapper.readValue(response, new TypeReference<Map<String, Object>>(){});
////		String accessTokenSnoop =(String)responseMap.get("accessToken");
////		String itemId =(String)responseMap.get("id");
////		Map<String,String> res = docsDatabySnoop(itemId,accessTokenSnoop, merchantId);
////		res.put("identity_id", itemId);
////		res.put("access_token", accessTokenSnoop);
////		return res;
////	}
//
////	public  Map<String,String> docsDatabySnoop(String itemId,String accessTokenSnoop, Long merchantId) throws JsonProcessingException {
////		Map<String,String> m=new HashMap<>();
////
////		String response= null;
////
////		Map<String, Object> body = new HashMap<>();
////		Map<String, Object> essentials = new HashMap<>();
////
////		body.put("service","Identity");
////		body.put("itemId",itemId);
////		body.put("task","autoRecognition");
////		body.put("accessToken",accessTokenSnoop);
////		body.put("essentials",essentials);
////
////		HttpHeaders headers = new HttpHeaders();
////		headers.set("Content-Type", "application/json");
////		HttpEntity<Map<String, Object>> request  = new HttpEntity<>(body,headers);
////		try {
////			Instant start = Instant.now();
////			m.put("request",request.toString());
////			logger.info("Karza KYC request : {}", request);
////			response = restTemplate.postForObject(SIGNZY_URL + CreditConstants.SIGNZY_SNOOP_URL, request, String.class);
////			logger.info("Karza KYC response : {}", response);
////			m.put("response",response);
////			Instant end = Instant.now();
////			logger.info("Time Taken by Karza KYC API : {} miliseconds", Duration.between(start, end).toMillis());
////			apiGatewayService.insertIntoSignzyReqRes(merchantId, null, "OCR", "SUCCESS", objectMapper.writeValueAsString(request), response, MODULE);
////		}catch(Exception e) {
////			logger.info("exception while Karza KYC API Snoop request, signedURL : {}, Exception is {}", itemId, e);
////			apiGatewayService.insertIntoSignzyReqRes(merchantId, null, "OCR", "FAILED", objectMapper.writeValueAsString(request), response, MODULE);
////		}
////
////		return m;
////
////	}
////
////	public Map<String,String> curlSignzyPanAuthenticationAPI(String panNumber, String name, String dob,String Id,String accessToken) {
////		Map<String,String> m=new HashMap<>();
////
////		Map<String,Object> paramMap = new HashMap<>();
////
////		Map<String, String> Essentials = new HashMap<>();
////		Essentials.put("number",panNumber);
////		Essentials.put("name",name);
////		Essentials.put("fuzzy","true");
////		paramMap.put("service","Identity");
////		paramMap.put("itemId",Id);
////		paramMap.put("task","verification");
////		paramMap.put("accessToken",accessToken);
////		paramMap.put("essentials",Essentials);
////
////		HttpHeaders headers = new HttpHeaders();
////		headers.set("Content-Type", "application/json");
////
////		String response= null;
////
////		try {
////			Instant start = Instant.now();
////			HttpEntity<Map<String, Object>> request = new HttpEntity<>(paramMap, headers);
////			m.put("request",request.toString());
////
////			logger.info("Signzy Pan Authentication request : {}", request);
////			response = restTemplate.postForObject(SIGNZY_URL + CreditConstants.SIGNZY_SNOOP_URL , request, String.class);
////			logger.info("Signzy Pan Authentication response : {}", response);
////			Instant end = Instant.now();
////			m.put("response",response);
////			logger.info("Time Taken by Karza Pan Authentication API : {} miliseconds", Duration.between(start, end).toMillis());
////		}catch(Exception e) {
////			logger.info("Exception while Karza Pan Authentication API, Exception is {}", e);
////		}
////
////		return m;
////	}
////
////	public String getTemporaryPublicURL(String fileName) throws JsonParseException, JsonMappingException, IOException {
////		String response= null;
////		Map<String, Object> body = new HashMap<>();
////		body.put( "base64String",fileName);
////		body.put("mimetype","image/jpeg");
////		body.put("ttl","7 days");
////
////		HttpHeaders headers = new HttpHeaders();
////		headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
////
////		try {
////			Instant start = Instant.now();
////			HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
////
////			//logger.info("signzy KYC request : {}", request);
////			response = restTemplate.postForObject("https://persist.signzy.tech/api/base64", request, String.class);
////			logger.info("signzy KYC response : {}", response);
////			Instant end = Instant.now();
////			logger.info("Time Taken by signzy KYC API : {} miliseconds", Duration.between(start, end).toMillis());
////		}catch(Exception e) {
////			logger.info("exception while signzy KYC API, file exchange : {}, Exception is {}", fileName, e);
////		}
////
////		ObjectMapper mapper = new ObjectMapper();
////		Map<String, Object> responseMap = mapper.readValue(response, new TypeReference<Map<String, Object>>(){});
////		Map<String,Object>res= ( Map<String,Object>)responseMap.get("file");
////		String url=(String)res.get("directURL");
////		return url;
////	}
//
//}
//
