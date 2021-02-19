package com.bharatpe.lending.controller;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.bharatpe.common.dao.*;
import com.bharatpe.common.entities.*;
import com.bharatpe.common.enums.NotificationProvider;
import com.bharatpe.common.handlers.PushNotificationHandler;
import com.bharatpe.common.handlers.SmsServiceHandler;
import com.bharatpe.common.service.WhatsappNotificationService;
import com.bharatpe.common.utils.AesEncryption;
import com.bharatpe.common.utils.HmacCalculator;
import com.bharatpe.lending.dao.ExperianDummyDao;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.service.LoanEligibleService;
import com.bharatpe.lending.util.creditresponse.ResponseUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.bharatpe.lending.dto.IneligibleRequestDTO;
import com.bharatpe.lending.dto.RequestDTO;
import com.bharatpe.lending.service.LoanDetailsService;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

@Service
public class CallLoanDetailService {

	private final Logger logger = LoggerFactory.getLogger(CallLoanDetailService.class);

	List<Integer> derogAccountStatus = Arrays.asList(93, 89, 93, 97, 97, 97, 97, 30, 31, 32, 33, 35, 37, 38, 39, 41, 42,
			43, 44, 45, 47, 49, 50, 51, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 72, 73,
			74, 75, 76, 77, 79, 81, 85, 86, 87, 88, 94, 90, 91);
	List<Integer> derogUnsecuredProducts = Arrays.asList(5, 10, 36, 37, 38, 39, 43, 51, 52, 53, 54, 55, 56, 57, 58, 60,
			61);

	List<Long> internalMerchants = Arrays.asList(722868L,392306L,117516L,581597L,459668L,581600L,1123730L,1123894L,2322353L,501338L,62282L,441631L,3L,809028L,2632113L,460973L,585182L,5254968L,378186L,3948276L,1153132L,3850034L,1010502L,401406L,5376878L,585182L,2400640L,1316093L);

	@Autowired
	ExperianDao experianDao;

	@Autowired
	MerchantDao merchantDao;

	@Autowired
	LoanDetailsService loanDetailsService;

	@Autowired
	LendingApplicationDao lendingApplicationDao;

	@Autowired
	ExperianDummyDao experianDummyDao;

	SimpleDateFormat experianFormat = new SimpleDateFormat("yyyyMMdd");

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	LoanEligibleService loanEligibleService;

	@Autowired
	MerchantBankDetailDao merchantBankDetailDao;

	@Autowired
	SmsServiceHandler smsServiceHandler;

	@Autowired
	PhonebookDao phonebookDao;

	@Autowired
	PushNotificationHandler pushNotificationHandler;

	@Autowired
	MerchantFcmTokenDao merchantFcmTokenDao;

	@Autowired
	WhatsappNotificationService whatsappNotificationService;

	@Autowired
	MerchantStaticVpaDao merchantStaticVpaDao;

	@Autowired
	LendingCitiesDao lendingCitiesDao;

	@Autowired
	HmacCalculator hmacCalculator;

	@Autowired
	AesEncryption aesEncryption;

	@Autowired
	InternalClientDao internalClientDao;

	@Autowired
	RestTemplate restTemplate;

	@Autowired
	EcollectTransactionDao ecollectTransactionDao;

	@Autowired
	KafkaTemplate<String, Object> kafkaTemplate;

	@Autowired
	Environment env;

	public void sendSMS() {
		List<Long> merchantIds = Arrays.asList(129024L);
		Iterable<Merchant> merchants = merchantDao.findAllById(merchantIds);
		for (Merchant merchant : merchants) {
			logger.info("Sending sms to merchant:{}", merchant.getId());
			MerchantBankDetail merchantBankDetail = merchantBankDetailDao
					.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchant.getId(), "ACTIVE");
			String sms = "Hi " + merchantBankDetail.getBeneficiaryName()
					+ "\nWe're unable to process your loan application as we found issues in your credit bureau report.";
			smsServiceHandler.sendSMS(new ArrayList<String>() {
				{
					add(merchant.getMobile());
				}
			}, sms, NotificationProvider.SMS.GUPSHUP);
		}
		logger.info("SMS script ended");
	}

	public void startScript() {
		ExecutorService executorService = Executors.newFixedThreadPool(1);
		executorService.submit(this::callLoanDetail);
	}

	public void callLoanDetail() {
		logger.info("Call Loan Details Script Started");
		try {
//			List<EcollectTransaction> ecollectTransactions = ecollectTransactionDao.getMissedDisbursal();
			List<LendingApplication> lendingApplicationList = lendingApplicationDao.getApplications();
			logger.info("Sending ecollect push to {} merchants", lendingApplicationList.size());
			for (LendingApplication lendingApplication : lendingApplicationList) {
//				if (internalMerchants.contains(merchantId.longValue())) {
//					continue;
//				}
//				sendPush(ecollectTransaction);
//				pushToKafka(ecollectTransaction);
				publishForDisbursal(lendingApplication.getId());
			}
		} catch (Exception e) {
			logger.error("Exception---", e);
		}
		logger.info("Call Loan Details Script Ended");
	}

	public void publishForDisbursal(Long lendingAppId){

		Map<String, String> payloadMap =new HashMap<>();
		try {
			logger.info("Publishing aaplication_id: {} of loan pending for disbursal to kafka", lendingAppId);
			payloadMap.put("lending_application_id",lendingAppId.toString());
			kafkaTemplate.send(Objects.requireNonNull(env.getProperty("kafka.topic.lending.payout")), lendingAppId.toString(), payloadMap);
		}
		catch(Exception e){
			logger.error("Error publishing lending application: {} to kafka for disbursal",lendingAppId);
		}
	}

	private void pushToKafka(EcollectTransaction ecollectTransaction) {
		logger.info("Sending ecollect to account:{} and amount:{}", ecollectTransaction.getVirtualAccountNumber(), ecollectTransaction.getAmount());
		Map<String, String> data = new HashMap<>();
		data.put("transaction_id", ecollectTransaction.getId().toString());
		data.put("bank_reference_no", ecollectTransaction.getBankReferenceNo());
		data.put("account_number", ecollectTransaction.getVirtualAccountNumber());
		data.put("amount", ecollectTransaction.getAmount().toString());
		kafkaTemplate.send("ecollect.loan.disbursal", ecollectTransaction.getMerchant().getId().toString(), data);
	}

	private void sendPush(Experian experian) {
		Optional<Merchant> merchantOptional = merchantDao.findById(experian.getMerchantId());
		Merchant merchant = merchantOptional.get();
		MerchantBankDetail merchantBankDetail = merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchant.getId(), "ACTIVE");
		logger.info("Sending loan survey to merchant:{}", merchant.getId());
		String message = "We saw that you haven't completed your loan application of Rs." + experian.getEligibleAmount() + ". Please fill this survey form to let us know what we can do to serve you better.";
		MerchantFcmToken merchantFcmToken = merchantFcmTokenDao.getByMerchantId(merchant.getId());
		if(merchantFcmToken != null) {
			pushNotificationHandler.sendPushNotification(merchantFcmToken.getFcmToken(), merchantFcmToken.getPlatform(), message, "dynamic?key=survey-merchant-lending");
		}
		String whatsapp = "Dear " + merchantBankDetail.getBeneficiaryName() + ",\nWe saw that you haven't completed your loan application of Rs " + experian.getEligibleAmount() + ". Please fill this survey form to let us know what we can do to serve you better. Click here:https://bharatpe.in/Uy9AX";
		whatsappNotificationService.send(merchant, null, whatsapp, new ArrayList<String>(){{add(merchant.getMobile());}}, null);
	}

	public void orderQrCode(LendingApplication lendingApplication) {
		try {
			logger.info("Order QR code for merchant {}",lendingApplication.getMerchant() );
			List<MerchantStaticVpa> merchantStaticVpaList=merchantStaticVpaDao.findAllByMerchant(lendingApplication.getMerchant().getId());
			LendingCities lendingCities = lendingCitiesDao.findActiveCityByPincode(lendingApplication.getPincode().intValue());
			//Send only to DIY merchants and green pincodes
			if(!merchantStaticVpaList.isEmpty() && lendingCities != null && (lendingApplication.getMerchant().getReferalCode() == null || lendingApplication.getMerchant().getReferalCode().trim().equals(""))) {
				Map<String, String> details=getDetailsToSendQrCodeForLending(lendingApplication);
				String vpa=merchantStaticVpaList.get(0).getFullVpa();
				logger.info("Calling API to order QR");
				Map<String, Object> body = new HashMap<String,Object>() {{
					put("mobile",lendingApplication.getMerchant().getMobile());
					put("vpa",vpa);
					put("businessname",lendingApplication.getMerchant().getBusinessName());
					put("remark", "NTB");
					put("OrderQRAddress",new HashMap<String,Object>(){{
						put("shopAddress",details.get("shopAddress"));
						put("pincode",details.get("pincode"));
						put("state",details.get("state"));
						put("city",details.get("city"));
						put("address",details.get("address"));
						put("landmark",details.get("landmark"));
						put("latitude",details.get("latitude"));
						put("shop_latitude",details.get("shop_latitude"));
						put("shop_longitude",details.get("shop_longitude"));
						put("longitude",details.get("longitude"));
						put("alternateMobile",details.get("alternateMobile"));
					}});
				}};
				String payload = getObjectPayload(body,details);
				logger.info("Order QR payload:{}", payload);
				String hash =hmacCalculator.calculateHMACHexEncoded(payload, getSecret());
				HttpHeaders headers = new HttpHeaders();
				headers.setContentType(MediaType.APPLICATION_JSON);
				headers.set("Hash",hash);
				headers.set("client-Name","LENDING");
				headers.set("merchantId", lendingApplication.getMerchant().getId().toString());
				headers.set("mobileNumber", lendingApplication.getMerchant().getMobile());
				HttpEntity<Map<String, Object>> request  = new HttpEntity<>(body, headers);
				String URL="https://api-merchant.bharatpe.in/merchant/v2/createOrderQR";
				logger.info("Order QR URL {} request {}",URL,request);
				int retryCount=0;
				while(retryCount<3) {
					try {
						ResponseEntity<String> responseBody = restTemplate.exchange(URL, HttpMethod.POST, request, String.class);
						logger.info("Order QR response {}",responseBody);
						break;
					}
					catch(Exception e) {
						logger.error("Error occured while ordering for QR ",e);
					}
					retryCount++;
				}

			}

		}
		catch(Exception e) {
			logger.error("Error occured while ordering QR code",e);
		}
	}

	public Map<String,String> getDetailsToSendQrCodeForLending(LendingApplication lendingApplication){
		return new HashMap<String,String>(){{
			put("shopAddress",lendingApplication.getStreetAddress());
			put("pincode",lendingApplication.getPincode().toString());
			put("state",lendingApplication.getState());
			put("city",lendingApplication.getCity());
			put("address",lendingApplication.getShopNumber()+","+lendingApplication.getStreetAddress()+","+lendingApplication.getCity()+","+lendingApplication.getState()+","+lendingApplication.getPincode());
			put("landmark",lendingApplication.getLandmark());
			put("latitude",lendingApplication.getMerchant().getLatitude().toString());
			put("shop_latitude",lendingApplication.getLatitude());
			put("shop_longitude",lendingApplication.getLongitude());
			put("longitude",lendingApplication.getMerchant().getLongitude().toString());
			put("alternateMobile",lendingApplication.getAlternateMobile());
		}};
	}

	public String getObjectPayload(Map<String, Object> paramMap,Map<String, String> details) {
		Map<String, Object> sortedMap = new TreeMap<>();
		sortedMap.put("mobile",paramMap.get("mobile"));
		sortedMap.put("vpa",paramMap.get("vpa"));
		sortedMap.put("remark", "NTB");
		sortedMap.put("businessname",paramMap.get("businessname"));
		sortedMap.put("shopAddress",details.get("shopAddress"));
		sortedMap.put("pincode",details.get("pincode"));
		sortedMap.put("state",details.get("state"));
		sortedMap.put("city",details.get("city"));
		sortedMap.put("address",details.get("address"));
		sortedMap.put("landmark",details.get("landmark"));
		sortedMap.put("latitude",details.get("latitude"));
		sortedMap.put("shop_latitude",details.get("shop_latitude"));
		sortedMap.put("shop_longitude",details.get("shop_longitude"));
		sortedMap.put("longitude",details.get("longitude"));
		sortedMap.put("alternateMobile",details.get("alternateMobile"));
		sortedMap.values().removeIf(Objects::isNull);
		return StringUtils.collectionToDelimitedString(sortedMap.values(), "|");
	}

	private String getSecret() {
		InternalClient client = internalClientDao.findByClientName("LENDING");
		if (client != null) {
			return aesEncryption.decrypt(client.getSecret());
		}
		return null;
	}

	private void test(Merchant merchant, Double loanAmount) {
		try {
			logger.info("Sending Pending Enach Push to merchant:{}", merchant.getId());
			MerchantBankDetail merchantBankDetail = merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchant.getId(), "ACTIVE");
			String message = "Register eNACH to fast-track your application process of Rs." + loanAmount + " in your " + merchantBankDetail.getBankName() + " A/C.";
			MerchantFcmToken merchantFcmToken = merchantFcmTokenDao.getByMerchantId(merchant.getId());
			if(merchantFcmToken != null) {
				pushNotificationHandler.sendPushNotification(merchantFcmToken.getFcmToken(), merchantFcmToken.getPlatform(), message, "dynamic?key=loan");
			}
			String whatsapp = "Hi " + merchantBankDetail.getBeneficiaryName() + ",\nRegister eNACH to fast-track your application process of Rs." + loanAmount + " in your " + merchantBankDetail.getBankName() + " A/C.\nClick here: bharatpe.in/loan~";
			whatsappNotificationService.send(merchant, null, whatsapp, new ArrayList<String>(){{add(merchant.getMobile());}}, null);
		} catch (Exception e) {
			logger.error("Exception---", e);
		}
	}

//	public void callLoanDetailFunction(Merchant merchant) {
//		try {
//			RequestDTO<IneligibleRequestDTO> requestDTO = new RequestDTO<>();
//			requestDTO.setPayload(new IneligibleRequestDTO());
//			requestDTO.getPayload().setSkip(false);
//			logger.info("Calling loan details for merchant:{}", merchant.getId());
//			loanDetailsService.fetchLoanDetails(merchant, requestDTO, null);
//		} catch (Exception e) {
//			logger.error("Exception---", e);
//		}
//	}
}
