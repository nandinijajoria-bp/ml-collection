package com.bharatpe.lending.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.bharatpe.common.dao.ExperianDao;
import com.bharatpe.common.dao.LendingNachBankDao;
import com.bharatpe.common.dao.LendingPancardDao;
import com.bharatpe.common.dao.MerchantBankDetailDao;
import com.bharatpe.common.dao.MerchantSummaryLendingDao;
import com.bharatpe.common.entities.Experian;
import com.bharatpe.common.entities.LendingEnach;
import com.bharatpe.common.entities.LendingNachBank;
import com.bharatpe.common.entities.LendingPancard;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.common.entities.Merchant;
import com.bharatpe.common.entities.MerchantBankDetail;
import com.bharatpe.common.entities.MerchantSummaryLending;
import com.bharatpe.lending.common.dao.CreditApplicationDao;
import com.bharatpe.lending.common.dao.CreditApplicationNachDao;
import com.bharatpe.lending.common.dao.LendingClEnachDao;
import com.bharatpe.lending.common.entity.CreditApplication;
import com.bharatpe.lending.common.entity.CreditApplicationNach;
import com.bharatpe.lending.common.entity.LendingClEnach;
import com.bharatpe.lending.constant.ExperianConstants;
import com.bharatpe.lending.constant.LendingConstants;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dto.DigioEnachInitiationRequestDTO;
import com.bharatpe.lending.dto.ENachIntitiationResponseDTO;
import com.bharatpe.lending.dto.ENachSubmitRequestDTO;
import com.bharatpe.lending.dto.ResponseDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@Service
public class CreditENachService {

	


    private Logger logger = LoggerFactory.getLogger(ENachService.class);

    @Autowired
    CreditApplicationDao creditApplicationDao;
    @Autowired
    CreditApplicationNachDao creditApplicationNachDao;
    @Autowired
    MerchantBankDetailDao merchantBankDetailDao;

    @Autowired
    LendingClEnachDao lendingClEnachDao;

    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;

    @Autowired
    LendingNachBankDao lendingNachBankDao;
    
    @Autowired
    ExperianDao experianDao;
    
    @Autowired
    LendingPancardDao lendingPanCardDao;
    
    @Autowired
    RestTemplate restTemplate;
    
    @Autowired
    ObjectMapper objectMapper;
    
    @Value("${enach.digio.authorization}")
    String authorization;

    @Autowired
    MerchantSummaryLendingDao merchantSummaryLendingDao;

    @Autowired
    BPEnachService bpEnachService;

    ExecutorService executorService = Executors.newFixedThreadPool(5);

    // fetch loan detail by merchant IFSC [pending verification state]
    // validate bank for mandate support
    // if bank is suported , insert in ENach Detail Table.
    public ENachIntitiationResponseDTO eNachInitiate(Merchant merchant, String appVersion){
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy");
        String mandateDate = sdf.format(new Date(new Date().getTime() + (1000 * 60 * 60 * 24)));
        final double LOAN_AMOUNT = 100000d;
        ENachIntitiationResponseDTO responseDTO = new ENachIntitiationResponseDTO();
        CreditApplication creditApplication = creditApplicationDao.findTop1ByMerchantIdOrderByIdDesc(merchant.getId());
        if(creditApplication == null) {
            responseDTO.setResponse(false);
            responseDTO.setMessage("Loan Application not found");
            logger.error("Unable to find loan application for Merchant - {}", merchant.getId());
            return responseDTO;
        }
        MerchantBankDetail  merchantBankDetail = merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchant.getId(), "ACTIVE");
        if(merchantBankDetail == null) {
            responseDTO.setResponse(false);
            responseDTO.setMessage("Active Bank not found");
            logger.error("No Bank detail found for Merchant - {}", merchant.getId());
            return responseDTO;
        }
        String bankCode;
        if (appVersion != null && Integer.parseInt(appVersion) >= 238) {
            bankCode = fetchBankCode(merchantBankDetail.getIfscCode().substring(0,4), "BOTH");
        } else {
            bankCode = fetchBankCode(merchantBankDetail.getIfscCode().substring(0,4), "NET");
        }
        if(bankCode == null) {
            responseDTO.setResponse(false);
            responseDTO.setMessage("Bank not supported for Enach");
            logger.error("Merchant Bank not supported for Enach - {}", merchant);
            return responseDTO;
        }
        LendingClEnach lendingClEnach = new LendingClEnach();
        
        lendingClEnach.setMerchantId(merchant.getId());
        lendingClEnach.setApplicationId(creditApplication.getId());
        lendingClEnach.setBankCode(bankCode);
         lendingClEnach.setAmount(LOAN_AMOUNT);
         lendingClEnach.setMandateDate(mandateDate);
         lendingClEnach.setmId(merchant.getMid());
         lendingClEnach.setSkip(false);
        lendingClEnach = lendingClEnachDao.save(lendingClEnach);
        responseDTO.setData(new ENachIntitiationResponseDTO.Data(lendingClEnach.getId(),lendingClEnach.getId(), bankCode, LOAN_AMOUNT, mandateDate, creditApplication.getId(), merchantBankDetail.getAccountNumber(), merchantBankDetail.getBeneficiaryName(), merchantBankDetail.getIfscCode(), merchant.getMid()));
        return responseDTO;
    }

    

    //Submit enach for digio

    public ENachIntitiationResponseDTO submitEnachForDigio(Merchant merchant, ENachSubmitRequestDTO requestDTO){
        ENachIntitiationResponseDTO responseDTO = new ENachIntitiationResponseDTO();
        responseDTO.setData(new ENachIntitiationResponseDTO.Data());

        LendingClEnach lendingClEnach = lendingClEnachDao.findByMerchantIdAndApplicationId(merchant.getId(), requestDTO.getApplicationId());
        if (lendingClEnach == null) {
            responseDTO.setResponse(false);
            responseDTO.setMessage("Enach not initiated");
            return responseDTO;
        }
        lendingClEnach.setIdentifier(requestDTO.getIdentifier());
        lendingClEnach.setMandateId(requestDTO.getMandateId());
        lendingClEnach.setResponse(requestDTO.getResponse());
        lendingClEnach.setStatus(requestDTO.getStatus());
        lendingClEnach.setStatusMessage(requestDTO.getStatusMessage());
        lendingClEnachDao.save(lendingClEnach);

        //calling digio api to check if enach is success
        JsonNode jsonNode=null;
        try {
            String URL=LendingConstants.DIGIO_ENACH_STATUS_CHECK+requestDTO.getMandateId();
            HttpHeaders header=new HttpHeaders();
            header.add("Authorization",authorization);
            HttpEntity<String> request=new HttpEntity<String>(header);
            ResponseEntity<String> response=restTemplate.exchange(URL, HttpMethod.GET,request,String.class);
            String jsonResonse=response.getBody();
            jsonNode=objectMapper.readTree(jsonResonse);
        }
        catch(Exception e){
            logger.error("Error occured while fetching enach autherization data",e);
        }
        if (jsonNode!=null && jsonNode.has("state") && jsonNode.get("state").asText().equals("auth_success") && requestDTO.getStatus()) {
            // Update Lending Application for ENACH
            logger.info("Autherization was successful");
            CreditApplication creditApplication = creditApplicationDao.findByIdAndMerchantId(requestDTO.getApplicationId(), merchant.getId());
            if (creditApplication == null) {
                responseDTO.setResponse(false);
                responseDTO.setMessage("Loan Application not found");
                return responseDTO;
            }
            CreditApplicationNach creditApplicationNach =new CreditApplicationNach();
            creditApplicationNach.setApplicationId(requestDTO.getApplicationId());
            creditApplicationNach.setMerchantId(merchant.getId());
            creditApplicationNach.setNachType("ENACH");
            creditApplicationNach.setNachLender("BHARATPE");
            creditApplicationNach.setNachStatus("APPROVED");
            creditApplicationNach.setNachReferenceNumber(lendingClEnach.getmId());
            List<LendingPaymentSchedule> prevLoans = lendingPaymentScheduleDao.findPreviousLoansByMerchantAndCreditLoan(merchant.getId(),true);
            if (prevLoans != null && prevLoans.size() > 0) {
                creditApplication.setStatus("approved");
               
            }
            creditApplicationNachDao.save(creditApplicationNach);
           creditApplicationDao.save(creditApplication);
        }
        return responseDTO;
    }

    public ENachIntitiationResponseDTO submitEnach(Merchant merchant, ENachSubmitRequestDTO requestDTO){
        ENachIntitiationResponseDTO responseDTO = new ENachIntitiationResponseDTO();
        responseDTO.setData(new ENachIntitiationResponseDTO.Data());
        LendingClEnach lendingClEnach = lendingClEnachDao.findByMerchantIdAndApplicationId(merchant.getId(), requestDTO.getApplicationId());
        if (lendingClEnach == null) {
            responseDTO.setResponse(false);
            responseDTO.setMessage("Enach not initiated");
            return responseDTO;
        }
        lendingClEnach.setIdentifier(requestDTO.getIdentifier());
        lendingClEnach.setMandateId(requestDTO.getMandateId());
        lendingClEnach.setResponse(requestDTO.getResponse());
        lendingClEnach.setStatus(requestDTO.getStatus());
        lendingClEnach.setStatusMessage(requestDTO.getStatusMessage());
        lendingClEnachDao.save(lendingClEnach);

        if (requestDTO.getStatus()) {
            // Update Lending Application for ENACH
        	CreditApplication creditApplication = creditApplicationDao.findByIdAndMerchantId(requestDTO.getApplicationId(), merchant.getId());
            if (creditApplication == null) {
                responseDTO.setResponse(false);
                responseDTO.setMessage("Loan Application not found");
                return responseDTO;
            }
            CreditApplicationNach creditApplicationNach = new CreditApplicationNach();
            creditApplicationNach.setMerchantId(merchant.getId());
            creditApplicationNach.setApplicationId(creditApplication.getId());
            creditApplicationNach.setNachType("ENACH");
            creditApplicationNach.setNachLender("BHARATPE");
            creditApplicationNach.setNachStatus("APPROVED");
            creditApplicationNach.setNachReferenceNumber(lendingClEnach.getmId());
            creditApplicationNachDao.save(creditApplicationNach);
            if (merchant.getId().equals(1141505L) || merchant.getId().equals(3612680L))
                executorService.submit(() -> bpEnachService.registerNach(createNachRegReq(lendingClEnach), merchant.getId()));
        }
        return responseDTO;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Map createNachRegReq(LendingClEnach lendingClEnach) {
        MerchantBankDetail merchantBankDetail = merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(lendingClEnach.getMerchantId(), "ACTIVE");
        Map request = new HashMap();
        request.put("merchantId", lendingClEnach.getMerchantId());
        request.put("referenceNumber", lendingClEnach.getmId());
        Date startDate = new Date();
        try {
            startDate = new SimpleDateFormat("dd-MM-yyyy").parse(lendingClEnach.getMandateDate());
        } catch (ParseException e) {
            logger.error("Exception while parsing date", e);
        }
        request.put("startDate", new SimpleDateFormat("yyyy-MM-dd").format(startDate));
        request.put("nachAmount", lendingClEnach.getAmount());
        request.put("ownerId", lendingClEnach.getApplicationId());
        request.put("nachType", "ENACH");
        request.put("status", "APPROVED");
        request.put("applicantName", merchantBankDetail.getBeneficiaryName());
        request.put("nachMode", "ADHO");
        request.put("identifier", lendingClEnach.getIdentifier());
        request.put("mendateId", lendingClEnach.getMandateId());
        request.put("bankResponse", lendingClEnach.getResponse());
        request.put("txnIdentifier", lendingClEnach.getId());
        return request;
    }

    //changing skip status to true
    public ResponseDTO setEnachSkipStatus(Merchant merchant){
    	CreditApplication creditApplication = creditApplicationDao.findTop1ByMerchantIdOrderByIdDesc(merchant.getId());
        if (creditApplication == null) {
            return new ResponseDTO(false, "Loan Application not found", null);
        }
        LendingClEnach lendingClEnach= lendingClEnachDao.findByMerchantIdAndApplicationId(merchant.getId(), creditApplication.getId());
        if(lendingClEnach == null) {
        	lendingClEnach = new LendingClEnach();
        	lendingClEnach.setApplicationId(creditApplication.getId());
        	lendingClEnach.setMerchantId(merchant.getId());
        }
        lendingClEnach.setSkip(true);
        lendingClEnachDao.save(lendingClEnach);
        return new ResponseDTO(true, null, null);
    }

    // check if bank is supported or not
    public String fetchBankCode(String ifscCode, String mode){
        LendingNachBank lendingNachBank = lendingNachBankDao.findByIfscAndMode(ifscCode, mode);
        return lendingNachBank != null ? lendingNachBank.getBankCode() : null;
    }
    
    public ENachIntitiationResponseDTO enachInititateForDigio(Merchant merchant){
    	ENachIntitiationResponseDTO enachInitiationResponseDto=new ENachIntitiationResponseDTO();
    	enachInitiationResponseDto.setData(new ENachIntitiationResponseDTO.Data());
    	//populating lending application
    	logger.info("Fetching lending application");
    	CreditApplication creditApplication=creditApplicationDao.findTop1ByMerchantIdOrderByIdDesc(merchant.getId());
        if(creditApplication==null){
        	enachInitiationResponseDto.setResponse(false);
    		enachInitiationResponseDto.setMessage("Lending application not found");
            return enachInitiationResponseDto;
        }
        enachInitiationResponseDto.getData().setApplicationId(creditApplication.getId());;
        
    	HttpHeaders headers = new HttpHeaders();
    	headers.setContentType(MediaType.APPLICATION_JSON);
    	headers.add("Authorization",authorization);
    	
    	//populating the data into request body class for the digio API call
    	DigioEnachInitiationRequestDTO digioEnach=new DigioEnachInitiationRequestDTO();
    	digioEnach.setMandate_data(new DigioEnachInitiationRequestDTO.Data());
    	
    	enachInitiationResponseDto.getData().setMandate_id("");
		enachInitiationResponseDto.getData().setCustomer_identifier("");
    	
    	if(merchant.getMobile()==null){
    		enachInitiationResponseDto.setResponse(false);
    		enachInitiationResponseDto.setMessage("Merchant mobile number not found");
            logger.error("Unable to find mobile number for Merchant - {}", merchant.getId());
            return enachInitiationResponseDto;
    	}
    	else {
    		digioEnach.setCustomer_identifier(merchant.getMobile());
    		digioEnach.getMandate_data().setCustomer_mobile(merchant.getMobile());
    	}
    	
    	logger.info("Fetching the bank details for the merchant {}",merchant.getId());
    	MerchantBankDetail merchantBankDetail=merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchant.getId(),"ACTIVE");
    	if(merchantBankDetail==null){
    		logger.error("Error occured fetching bank detils for merchant {}",merchant.getId());
    		enachInitiationResponseDto.setResponse(false);
    		enachInitiationResponseDto.setMessage("Merchant bank detail not found");
            logger.error("Unable to find bank detail for Merchant - {}", merchant.getId());
            return enachInitiationResponseDto;
    	}
    	digioEnach.getMandate_data().setDestination_bank_id(merchantBankDetail.getIfscCode());
    	digioEnach.getMandate_data().setDestination_bank_name(merchantBankDetail.getBankName());
    	digioEnach.getMandate_data().setCustomer_account_number(merchantBankDetail.getAccountNumber());


        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    	String mandateDate = sdf.format(new Date(new Date().getTime() + (1000 * 60 * 60 * 24)));
    	digioEnach.getMandate_data().setFirst_collection_date(mandateDate);
    	
    	logger.info("Fetching the pancard from the Lending_pan_card table");
    	LendingPancard lendingPancard=lendingPanCardDao.findByMerchantId(merchant.getId());
    	if(lendingPancard==null || lendingPancard.getPancardNumber()==null) {
    		
    		logger.error("Error occured while fetching pancard detail from the Lending_pan_card table for merchant id {}",merchant.getId());
    		
    		logger.info("Fetching the pancard details from experian");
    		Experian experian=experianDao.getByMerchantId(merchant.getId());
    		
    		if(experian==null || experian.getPancardNumber()==null){
    			
    			logger.error("Error occured while fetching experian details for merchant if {}",merchant.getId());
    			enachInitiationResponseDto.setResponse(false);
        		enachInitiationResponseDto.setMessage("Pancard detail not found");
                logger.error("Unable to find pancard detail for Merchant - {}", merchant.getId());
                return enachInitiationResponseDto;
    		}
    		digioEnach.getMandate_data().setCustomer_pan(experian.getPancardNumber());
    		digioEnach.getMandate_data().setCustomer_name(merchantBankDetail.getBeneficiaryName());
    	}
    	else {
	    	digioEnach.getMandate_data().setCustomer_pan(lendingPancard.getPancardNumber());
			digioEnach.getMandate_data().setCustomer_name(lendingPancard.getName());
    	}
    	
    	//check for the merchant name, incase name is not present in the lending_pancard table
    	if(digioEnach.getMandate_data().getCustomer_name()==null) {
    		digioEnach.getMandate_data().setCustomer_name(merchantBankDetail.getBeneficiaryName());
    	}
    	
    	
    	HttpEntity<DigioEnachInitiationRequestDTO> request = new HttpEntity<>(digioEnach, headers);
    	try {
    		logger.info("Hitting digio API for the enach initiation for merchant: {}", merchant.getId());
    		String response = restTemplate.postForObject(LendingConstants.DIGIO_ENACH_INITIATION_URL, request, String.class);
    		JsonNode jsonNode=objectMapper.readTree(response);
    		if(jsonNode.has("mandate_id") && !jsonNode.get("mandate_id").isNull()) {
    			enachInitiationResponseDto.getData().setMandate_id(jsonNode.get("mandate_id").asText());
    			enachInitiationResponseDto.getData().setCustomer_identifier(merchant.getMobile());
    		}
    		else {
    			logger.error("Mandate not found for merchant: {}", merchant.getId());
    			enachInitiationResponseDto.setResponse(false);
        		enachInitiationResponseDto.setMessage("Mandate not created");
                return enachInitiationResponseDto;
    		}
    	}
    	catch(Exception e) {
    		logger.error("Error occured while fetching enach data from digio for merchant: {}", merchant.getId());
    		enachInitiationResponseDto.setResponse(false);
    		enachInitiationResponseDto.setMessage("Error occured while fetching enach data");
    	}
    	 
    	 
        LendingClEnach lendingClEnach = new LendingClEnach();
        
        lendingClEnach.setMerchantId(merchant.getId());
        lendingClEnach.setApplicationId(creditApplication.getId());
        lendingClEnach.setBankCode(" ");
         lendingClEnach.setAmount(Double.valueOf(digioEnach.getMandate_data().getMaximum_amount()));
         lendingClEnach.setMandateDate(mandateDate);
         lendingClEnach.setmId(merchant.getMid());
        
        lendingClEnachDao.save(lendingClEnach);
    	return enachInitiationResponseDto;
    }
    

}


