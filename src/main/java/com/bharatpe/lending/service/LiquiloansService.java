package com.bharatpe.lending.service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.*;


import com.bharatpe.common.enums.NotificationProvider;
import com.bharatpe.common.handlers.SmsServiceHandler;
import com.bharatpe.common.service.WhatsappNotificationService;
import com.bharatpe.lending.common.entity.LiquiloansDirectDisbursalRawResponse;
import com.bharatpe.lending.constant.LendingConstants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.bharatpe.common.dao.ExternalGatewayDao;
import com.bharatpe.common.dao.LendingPancardDao;
import com.bharatpe.common.dao.MerchantBankDetailDao;
import com.bharatpe.common.dao.MerchantDao;
import com.bharatpe.common.entities.DisbursalSettlement;
import com.bharatpe.common.entities.ExternalGateway;
import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingPancard;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.common.entities.Merchant;
import com.bharatpe.common.entities.MerchantBankDetail;
import com.bharatpe.common.entities.SettlementSchedule;
import com.bharatpe.common.entities.Validate;
import com.bharatpe.common.utils.AesEncryption;
import com.bharatpe.common.utils.HmacCalculator;
import com.bharatpe.lending.dao.DisbursalSettlementDao;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingCategoryDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dao.LoanAgreementDao;
import com.bharatpe.lending.dao.SettlementScheduleDao;
import com.bharatpe.lending.dao.ValidateDao;
import com.bharatpe.lending.dto.LiquidatePostPayoutStatusUpdateRequestDTO;
import com.bharatpe.lending.dto.LiquiloanCallbackRequestDTO;
import com.bharatpe.lending.dto.LiquiloanSettlementRequestDTO;
import com.bharatpe.lending.dto.ResponseDTO;
import com.bharatpe.lending.entity.LoanAgreement;
import com.bharatpe.lending.handlers.S3BucketHandler;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class LiquiloansService {
	
    private Logger logger = LoggerFactory.getLogger(LiquiloansService.class);

    @Autowired
    ExternalGatewayDao externalGatewayDao;

    @Autowired
    HmacCalculator hmacCalculator;
    
    @Autowired
    AesEncryption aesEncryption;
    
    @Autowired
    RestTemplate restTemplate;
    
    @Autowired
	LendingApplicationDao lendingApplicationDao;
    
    @Autowired
    LendingPancardDao lendingPancardDao;
    
    @Autowired
    MerchantDao merchantDao;
    
    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;
    
    @Autowired
    Environment env;
    
    @Autowired
    LendingCategoryDao lendingCategoryDao;
    
    @Autowired
    DisbursalSettlementDao disbursalSettlementDao;

    @Autowired
    ObjectMapper objectMapper;

	@Autowired
	SmsServiceHandler smsServiceHandler;
    @Autowired
	LoanAgreementDao loanAgreementDao;

    @Autowired
	S3BucketHandler s3BucketHandler;

    @Autowired
	MerchantBankDetailDao merchantBankDetailDao;

    @Autowired
	WhatsappNotificationService whatsappNotificationService;
		
	@Value("${aws.s3.loan.agreement.bucket}")
	private String bucket;
	
	@Autowired
	ValidateDao validateDao;
	
	@Autowired
	SettlementScheduleDao settlementScheduleDao;
    
    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;
    
    public LendingPancard fetchNameOnPancard(String pancardNumber, Long merchantId) {
        String name = null;
        String apiResponse = null;
        try {
            ExternalGateway externalGateway = externalGatewayDao.findByGatewayNameAndTypeAndStatus("LIQUILOANS", null, "ACTIVE");
            if (externalGateway != null) {
                Map<String, String> requestParams = new HashMap<>();
                Date currentTime = new Date();
                String payload = pancardNumber + "||" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(currentTime);
                String checksum = hmacCalculator.calculateHMACHexEncoded(payload, aesEncryption.decrypt(externalGateway.getSecret()));
                logger.info("Liquiloans Checksum:{} for payload: {} for merchant:{}, PAN: {}", checksum, payload, merchantId, pancardNumber);
                requestParams.put("MID", externalGateway.getMbid());
                requestParams.put("Pan", pancardNumber);
                requestParams.put("Timestamp", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(currentTime));
                requestParams.put("Checksum", checksum);
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setCacheControl(CacheControl.noCache());
                HttpEntity<Map<String, String>> request = new HttpEntity<>(requestParams, headers);
                try {
                    long startTime = System.currentTimeMillis();
                    Map response = restTemplate.postForObject("https://api.liquiloans.com/api/apiintegration/v3/VerifyPanNumber", request, Map.class);
                    logger.info("Liquloans PAN validation API response: {}, response time: {}ms", response, (System.currentTimeMillis() - startTime));
                    if (response != null && response.containsKey("status")) {
                        apiResponse= response.toString();
                        boolean status = (boolean) response.get("status");
                        Map responseDataMap = (Map) response.get("data");
                        String statusCode = (String) responseDataMap.get("status-code");
                        if (status && statusCode.equals("101")) {
                            Map responseResultMap = (Map) responseDataMap.get("result");
                            name = (String) responseResultMap.get("name");
                            logger.info("Liquiloans Set status success for merchant: {}", merchantId);
                        } else {
                            logger.info("Liquiloans Set status failed Response params status : {}, status code: {} for merchant: {}", status, statusCode.equals("101"), merchantId);
                        }
                    } else {
                        logger.info("Liquiloans Set status failed response not contain status for merchant: {}", merchantId);
                    }
                } catch (RestClientException e) {
                    logger.error("RestClient Exception accrue in Liquiloans API calling", e);
                }
            }
        } catch (Exception e) {
            logger.error("Exception while fetching name from liquiloans for merchant: {}", merchantId);
            logger.error("Exception---", e);
        }
        return lendingPancardDao.save(new LendingPancard(merchantId, pancardNumber, name, apiResponse));
    }

	public ResponseDTO checkLoanStatus(LiquiloanCallbackRequestDTO callbackRequestDto, LiquiloansDirectDisbursalRawResponse liquiloansDirectDisbursalRawResponse) {
		logger.info("Fetching lending application for given liquiloan_loan_id:{} and bp_loan_id:{}", callbackRequestDto.getUrn(),callbackRequestDto.getLoanId());
		liquiloansDirectDisbursalRawResponse.setApiName("APPROVELOAN");
		liquiloansDirectDisbursalRawResponse.setRequest(callbackRequestDto.toString());
		try {
			LendingApplication lendingApplication=lendingApplicationDao.findByExternalLoanIdNbfcIdAndStatus(callbackRequestDto.getUrn(),callbackRequestDto.getLoanId(),"approved");
			if(lendingApplication==null) {
				return new ResponseDTO(false,"loan application not found",null);
			}
			liquiloansDirectDisbursalRawResponse.setMerchantId(lendingApplication.getMerchant().getId());
			liquiloansDirectDisbursalRawResponse.setApplicationId(lendingApplication.getId());
			liquiloansDirectDisbursalRawResponse.setLoanId(lendingApplication.getExternalLoanId());
			liquiloansDirectDisbursalRawResponse.setLiquiloanId(lendingApplication.getNbfcId());
			if (lendingApplication.getLoanDisbursalStatus() != null && !"null".equalsIgnoreCase(lendingApplication.getLoanDisbursalStatus())) {
				return new ResponseDTO(false,"duplicate request",null);
			}
			else if(callbackRequestDto.getStatus().equalsIgnoreCase("approved")){
				lendingApplication.setLoanDisbursalStatus("PENDING");
				lendingApplicationDao.save(lendingApplication);
				publishForDisbursal(lendingApplication.getId());
				return new ResponseDTO(true,null,null);
			}
			else if(callbackRequestDto.getStatus().equalsIgnoreCase("rejected")){
				lendingApplication.setLoanDisbursalStatus("REJECTED");
				lendingApplicationDao.save(lendingApplication);
				return new ResponseDTO(true,null,null);
			}
			else if(callbackRequestDto.getStatus().equalsIgnoreCase("disbursed")){
				lendingApplication.setLoanDisbursalStatus("DISBURSED");
				lendingApplicationDao.save(lendingApplication);
				return new ResponseDTO(true,null,null);
			}
			else {
				return new ResponseDTO(false,"invalid loan status",null);
			}
		}
		catch(Exception e){
			logger.error("Error occured while updating lending application disbursal status",e);
			return new ResponseDTO(false,"Error occurred while updating loan",null);
		}
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
   
    public ResponseEntity<String> populateLendingPaymentSchedule(LiquidatePostPayoutStatusUpdateRequestDTO postPayoutRequestDto){
    	LendingApplication lendingApplication=null;
		LendingPaymentSchedule lendingPaymentSchedule=null;
    	try{
    		logger.info("Fetching merchant for the merchant id {}",postPayoutRequestDto.getMerchantId());
			Optional<Merchant> merchant=merchantDao.findById(Long.parseLong(postPayoutRequestDto.getMerchantId()));
    		if(!merchant.isPresent()){
    			logger.error("Merchant not found for the merchant id {}",postPayoutRequestDto.getMerchantId());
    			return new ResponseEntity<>("Invalid merchantId", HttpStatus.BAD_REQUEST);
    		}
    		logger.info("Fetching loan application on the basis of application id and merchant");
    		lendingApplication=lendingApplicationDao.findByIdAndMerchant(Long.parseLong(postPayoutRequestDto.getApplicationId()), merchant.get());
    		
    		
    		if(lendingApplication==null){
    			logger.error("Loan application for loanId {} and merchantId {} not found.",postPayoutRequestDto.getApplicationId(),merchant);
    			return new ResponseEntity<>("Invalid applicationId", HttpStatus.BAD_REQUEST);
    		}
    		
    		logger.info("Changing loan_disbursal_status to 'DISBURSED'");
    		lendingApplication.setLoanDisbursalStatus("DISBURSED");
			lendingApplication.setDisburseTimestamp(new Date());
			lendingApplication.setAccountType("INVESTOR_FUNDS");
    		lendingApplicationDao.save(lendingApplication);

    		lendingPaymentSchedule = lendingPaymentScheduleDao.findByMerchantIdAndApplicationId(merchant.get().getId(), lendingApplication.getId());
    		if (lendingPaymentSchedule != null) {
				logger.error("Loan payment schedule already exist for loanId {} and merchantId {}.",postPayoutRequestDto.getApplicationId(),merchant);
				return new ResponseEntity<>("Duplicate Request", HttpStatus.BAD_REQUEST);
			}
    		
    		lendingPaymentSchedule=new LendingPaymentSchedule();
    		
    		logger.info("Popualting data into lending_payment_schedule table for applicationId: {}", lendingApplication.getId());
    		
    		lendingPaymentSchedule.setLoanApplication(lendingApplication);
    		lendingPaymentSchedule.setLoanType("NORMAL");
    		lendingPaymentSchedule.setMerchant(merchant.get());
    		lendingPaymentSchedule.setLoanAmount(lendingApplication.getLoanAmount());
    		lendingPaymentSchedule.setMobile(merchant.get().getMobile());
    		lendingPaymentSchedule.setEdiAmount(lendingApplication.getEdi());
    		lendingPaymentSchedule.setStatus("ACTIVE");
    		lendingPaymentSchedule.setNbfc(lendingApplication.getLender());
    		lendingPaymentSchedule.setEdiCount(Integer.parseInt(lendingApplication.getPayableDays().toString()));
    		lendingPaymentSchedule.setOverdueEdiCount(0);
    		lendingPaymentSchedule.setDueAmount(0D);
    		lendingPaymentSchedule.setIncentiveAmount(0D);
    		lendingPaymentSchedule.setEdiRemainingCount(Integer.parseInt(lendingApplication.getPayableDays().toString()));
    		lendingPaymentSchedule.setOverdueAmount(0D);
    		lendingPaymentSchedule.setPaidAmount(0D);
    		lendingPaymentSchedule.setTotalCashbackAmount(0D);
    		lendingPaymentSchedule.setTotalPayableAmount(lendingApplication.getRepayment());
    		lendingPaymentSchedule.setCreatedAt(new Date());
    		lendingPaymentSchedule.setUpdatedAt(new Date());
    		String construct=lendingApplication.getLoanConstruct();
    		lendingPaymentSchedule.setLoanConstruct(construct);
    		
    		Date date=new Date();
    		
    		SimpleDateFormat format=new SimpleDateFormat("yyyy-MM-dd 00:00:00");
    		
    		//getting tommorow's date
    		
    		Date tomorrow = new Date(date.getTime() + (1000 * 60 * 60 * 24));  	
    		//checking if next day is Sunday or not because we don't cut edi on Sunday
    		if(tomorrow.getDay()==0) {
    			tomorrow = new Date(tomorrow.getTime() + (1000 * 60 * 60 * 24));
    		}
    		tomorrow=format.parse(format.format(tomorrow));
    		
    		//getting date after one month
    		Date oneMonthLaterDate=getDateAfterNMonths(date, 1);
    		if(oneMonthLaterDate.getDay()==0) {
    			oneMonthLaterDate = new Date(oneMonthLaterDate.getTime() + (1000 * 60 * 60 * 24));
    		}
    		oneMonthLaterDate=format.parse(format.format(oneMonthLaterDate));
    	    
    	    
    		if(construct.equals("CONSTRUCT_1")) {
        		lendingPaymentSchedule.setStartDate(tomorrow);
    		}
    		else if(construct.equals("CONSTRUCT_2") || construct.equals("CONSTRUCT_3")) {
    			
    			lendingPaymentSchedule.setStartDate(oneMonthLaterDate);
    			lendingPaymentSchedule.setInterestOnlyStartDate(tomorrow);		
    			lendingPaymentSchedule.setInterestOnlyEdiAmount(lendingApplication.getIoEdi());
    			lendingPaymentSchedule.setInterestOnlyEdiCount(lendingApplication.getIoPayableDays());
    		}
    		else {
    			logger.error("Wrong construct type found for applicationId: {}", lendingApplication.getId());
    			return new ResponseEntity<>("Wrong construct type found in application", HttpStatus.BAD_REQUEST);
    		}	
    		
    		lendingPaymentSchedule.setNextEdiDate(tomorrow);
    		
    		Date tenativeLoanEndDate=getDateAfterNMonths(date,lendingApplication.getTenureInMonths()); 
    		if(tenativeLoanEndDate==null){
    			return new ResponseEntity<>("Error occured", HttpStatus.BAD_REQUEST);	
    		}
    		lendingPaymentSchedule.setTentativeClosingDate(tenativeLoanEndDate);
    		lendingPaymentScheduleDao.save(lendingPaymentSchedule);
    		changeDeductionFromInstantToDaily(merchant.get());
    	}
    	catch(Exception e){
    		logger.error("Error occured while populating data into lending_payment_schedule table",e);
    		
    		logger.info("Changing loan_disbursal_status back to 'PENDING'");
    		if(lendingApplication!=null){
    			lendingApplication.setDisburseTimestamp(null);
    			lendingApplication.setLoanDisbursalStatus("PENDING");
    			lendingApplicationDao.save(lendingApplication);
    			lendingPaymentScheduleDao.delete(lendingPaymentSchedule);
    		}		
    		
    		return new ResponseEntity<>("Something went wrong", HttpStatus.INTERNAL_SERVER_ERROR);
    	}
    	try {
			sendSms(lendingApplication, lendingPaymentSchedule);
		} catch (Exception e) {
    		logger.error("Exception while sending disbursal sms---", e);
		}
    	return new ResponseEntity<>("Ok", HttpStatus.OK);
    }
    
    private void changeDeductionFromInstantToDaily(Merchant merchant) {
    		logger.info("Changing settlement from instant to daily for merchant {}",merchant.getId());
    		merchant.setSettlementType("DAILY");
    		merchant.setKycType("LEVEL2");
    		List<Validate> validateList=validateDao.findByMobile(merchant.getMobile());
    		for(Validate validate:validateList){
    			validate.setSettlement("daily");
    		}
    		SettlementSchedule settlementSchedule=settlementScheduleDao.findTop1ByMerchantIdAndStatus(merchant.getId(), "PENDING");
    		if(settlementSchedule!=null) {
    			settlementSchedule.setSettlementDate(new Date());
        		settlementSchedule.setMoveDaily("YES");
        		settlementScheduleDao.save(settlementSchedule);
    		}
    		merchantDao.save(merchant);
    		if(!validateList.isEmpty()){
    			validateDao.saveAll(validateList);
    		}
    		
    }

	private void sendSms(LendingApplication lendingApplication, LendingPaymentSchedule lendingPaymentSchedule) {
		String sms1;String sms2 = null; String shortUrl="";
		LoanAgreement loanAgreement=loanAgreementDao.findByApplicationId(lendingApplication.getId());
		Merchant merchant=lendingApplication.getMerchant();
		MerchantBankDetail merchantBankDetail = merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchant.getId(), "ACTIVE");
		if (merchantBankDetail == null) {
			return;
		}
		if (loanAgreement != null) {
			String fileName = loanAgreement.getAgreementName();
			try {
				shortUrl = getShorturl(fileName, loanAgreement);
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
		}
		if("TOPUP".equalsIgnoreCase(lendingApplication.getLoanType())) {
			sms1= "Hi  "+merchantBankDetail.getBeneficiaryName()+"\n"+
					"Your BharatPe Loan of Rs."+ lendingApplication.getDisbursalAmount()+" is successfully disbursed. " +
					"Here is a copy of the Loan agreement for your reference:"+shortUrl;
		} else {
			sms1="Hi  "+merchantBankDetail.getBeneficiaryName()+"\n"+
					"Your BharatPe Loan of Rs."+lendingApplication.getLoanAmount()+" is successfully disbursed. " +
					"Here is a copy of the Loan agreement for your reference:"+shortUrl;
		}
		if("CONSTRUCT_1".equals(lendingApplication.getLoanConstruct())) {
			sms2 = "Your daily installment for BharatPe Loan is INR "+lendingApplication.getEdi()+". First installment date "+lendingPaymentSchedule.getStartDate()+". Installments will be deducted from your daily settlements.";
		} else if ("CONSTRUCT_2".equals(lendingApplication.getLoanConstruct())) {
			sms2 = "Congrats , you need not pay any installment during the 1st month. Your daily instalments of INR "+lendingApplication.getEdi()+" will start from "+lendingPaymentSchedule.getStartDate()+". Installments will be deducted from your daily settlements.";
		} else if ("CONSTRUCT_3".equals(lendingApplication.getLoanConstruct())) {
			sms2 = "Your daily installment for 1st month is INR "+lendingPaymentSchedule.getInterestOnlyEdiAmount()+" (Only Interest). After that, it will be INR"+lendingApplication.getEdi()+". First installment date is "+lendingPaymentSchedule.getStartDate()+" Installments will be deducted from your daily settlements.";
		}
		smsServiceHandler.sendSMS(new ArrayList<String>(){{add(lendingApplication.getMerchant().getMobile());}}, sms1, NotificationProvider.SMS.GUPSHUP);
		if (sms2 != null) {
			smsServiceHandler.sendSMS(new ArrayList<String>() {{
				add(lendingApplication.getMerchant().getMobile());
			}}, sms2, NotificationProvider.SMS.GUPSHUP);
		}
		List<String> mobiles = new ArrayList<> ();
		mobiles.add(merchant.getMobile());
		whatsappNotificationService.send(merchant, null, sms1, mobiles, null);
	}
         
	 public Date getDateAfterNMonths(Date startDate, int month){
    	
    	try {
    		logger.info("Getting date after {} month",month);
    
        	Calendar myCal = Calendar.getInstance();
            myCal.setTime(startDate);   
            myCal.add(Calendar.MONTH, +month);   
            Date tentativeEndDate = myCal.getTime();
            SimpleDateFormat format=new SimpleDateFormat("yyyy-MM-dd 00:00:00");
            return format.parse(format.format(tentativeEndDate));
    	}
    	catch(Exception e){
    		logger.error("Error occured while catculating date post N month",e);
    		return null;
    	}
    	
    	
    }
    

    public ResponseDTO populateSettlementDetails(LiquiloanSettlementRequestDTO settlementRequest, LiquiloansDirectDisbursalRawResponse liquiloansDirectDisbursalRawResponse){
    	logger.info("Insertng disbursal settlement details");
		liquiloansDirectDisbursalRawResponse.setApiName("SETTLEMENT");
		liquiloansDirectDisbursalRawResponse.setRequest(settlementRequest.toString());
    	try {
    			String requestBody=objectMapper.writeValueAsString(settlementRequest);
    			Date transferDate=new SimpleDateFormat("yyyy-MM-dd").parse(settlementRequest.getTransferDate());

	    	for(LiquiloanSettlementRequestDTO.LoanData loanDetail : settlementRequest.getLoanDetails()){

	    		DisbursalSettlement disbursalSettlement=new DisbursalSettlement();
	    		disbursalSettlement.setAmount(Double.parseDouble(loanDetail.getAmount()));
	    		disbursalSettlement.setLoanId(loanDetail.getLoanId());
	    		disbursalSettlement.setNbfc("LIQUILOANS");
	    		disbursalSettlement.setRequestBody(requestBody);
	    		disbursalSettlement.setTransferDate(transferDate);
	    		disbursalSettlement.setUrn(loanDetail.getUrn());
	    		disbursalSettlement.setUtrNumber(settlementRequest.getUtrNumber());
	    		disbursalSettlementDao.save(disbursalSettlement);
	    		
	    		logger.info("Populating 'disbursal_settlement_id' field in table 'lending_payment_schedule' for loan id {}",loanDetail.getLoanId());
	    		if(!updateDisbursalSettlementIdInLendingPaymentSchedule(loanDetail.getLoanId(),loanDetail.getUrn(),disbursalSettlement.getId(), liquiloansDirectDisbursalRawResponse)){
	    			return new ResponseDTO(false,"Error occured while processing settlemet details",null);
	    		}
	    	
	    	}
	    	
    	}
    	catch(Exception e){
    		logger.error("Error occured while populating disbursal settlement details",e);
    		return new ResponseDTO(false,"Error occured while processing settlemet details",null);
    	}

    	return new ResponseDTO(true,null,null);
    }
    
    public boolean updateDisbursalSettlementIdInLendingPaymentSchedule(String loanId, String urnId, Integer settlementId, LiquiloansDirectDisbursalRawResponse liquiloansDirectDisbursalRawResponse){
    	logger.info("Fetching lending application for the externa loan id {} and nbfc id {}",urnId,loanId);
    	try {
    	LendingApplication lendingApplication=lendingApplicationDao.findByExternalLoanIdNbfcIdAndStatus(urnId, loanId, "approved");
    	
    	if(lendingApplication==null) {
    		logger.error("Lending application not found");
    		return false;
    	}
    	
    	logger.info("Fetching lending payment schedule details for lending appliation {}",lendingApplication.getId());
			liquiloansDirectDisbursalRawResponse.setMerchantId(lendingApplication.getMerchant().getId());
			liquiloansDirectDisbursalRawResponse.setApplicationId(lendingApplication.getId());
			liquiloansDirectDisbursalRawResponse.setLoanId(lendingApplication.getExternalLoanId());
			liquiloansDirectDisbursalRawResponse.setLiquiloanId(lendingApplication.getNbfcId());
    	
    	LendingPaymentSchedule lendingPaymentSchedule =lendingPaymentScheduleDao.findByMerchantIdAndApplicationId(lendingApplication.getMerchant().getId(), lendingApplication.getId());
    	
    	if(lendingPaymentSchedule==null){
    		logger.error("Lending payment schedule not found");
    		return false;
    	}
    	lendingPaymentSchedule.setDisbursalSettlementId(settlementId);
    	lendingPaymentScheduleDao.save(lendingPaymentSchedule);
    	}
    	catch(Exception e) {
    		logger.error("error occured while updating 'disbursal_settlement_id' In lending_payment_schedule table",e);
    		return false;
    	}
    	return true;
    }

	public String getShorturl(String fileName,LoanAgreement loanAgreement) throws UnsupportedEncodingException {
		String tempUrl="";
		try {
			tempUrl=s3BucketHandler.getPreSignedPublicURL(fileName, bucket);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		String url = "https://bharatpe.in/yourls-api.php?signature=a872b1348e&action=shorturl&format=json&keyword=&url="+URLEncoder.encode(tempUrl,"UTF-8");
		String response="";
		try {
			Instant start = Instant.now();
			response = restTemplate.getForObject(url,String.class);
			logger.info("shorturl response : {}", response);
			Instant end = Instant.now();
			logger.info("Time Taken by shorturl API : {} miliseconds", Duration.between(start, end).toMillis());
		}catch(Exception e) {
			logger.error("exception while shorturl API : {}, Exception is {}", url, e);
		}
		JsonNode rootNode=null;
		try {
			rootNode = objectMapper.readTree(response);
		} catch (Exception e) {
			logger.error("Exception while parsing short url---", e);
		}
		if(rootNode != null && rootNode.path("status") != null && rootNode.path("status").textValue().equals("success")){
			String shortUrl=rootNode.path("shorturl").textValue();
			loanAgreement.setShortUrl(shortUrl);
			loanAgreementDao.save(loanAgreement);
			return shortUrl;
		}
		return " ";
	}


}
