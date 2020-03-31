package com.bharatpe.lending.service;

import com.bharatpe.common.dao.ExperianDao;
import com.bharatpe.common.dao.LendingEnachDao;
import com.bharatpe.common.dao.LendingNachBankDao;
import com.bharatpe.common.dao.LendingPancardDao;
import com.bharatpe.common.dao.MerchantBankDetailDao;
import com.bharatpe.common.entities.*;
import com.bharatpe.lending.constant.LendingConstants;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dto.DigioEnachInitiationRequestDTO;
import com.bharatpe.lending.dto.ENachIntitiationResponseDTO;
import com.bharatpe.lending.dto.ENachSubmitRequestDTO;
import com.bharatpe.lending.dto.ResponseDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

@Service
public class ENachService {

    private Logger logger = LoggerFactory.getLogger(ENachService.class);

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    MerchantBankDetailDao merchantBankDetailDao;

    @Autowired
    LendingEnachDao lendingEnachDao;

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

    // fetch loan detail by merchant IFSC [pending verification state]
    // validate bank for mandate support
    // if bank is suported , insert in ENach Detail Table.
    public ENachIntitiationResponseDTO eNachInitiate(Merchant merchant, String appVersion){
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy");
        String mandateDate = sdf.format(new Date(new Date().getTime() + (1000 * 60 * 60 * 24)));
        final double LOAN_AMOUNT = 100000d;
        ENachIntitiationResponseDTO responseDTO = new ENachIntitiationResponseDTO();
        LendingApplication lendingApplication = lendingApplicationDao.getLatestPendingApplication(merchant.getId());
        if(lendingApplication == null) {
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
        LendingEnach lendingEnach = new LendingEnach(merchant.getId(), lendingApplication.getId(), bankCode, LOAN_AMOUNT, mandateDate, merchant.getMid());
        lendingEnach = lendingEnachDao.save(lendingEnach);
        responseDTO.setData(new ENachIntitiationResponseDTO.Data(lendingEnach.getId(), lendingEnach.getId(), bankCode, LOAN_AMOUNT, mandateDate, lendingApplication.getId(), merchantBankDetail.getAccountNumber(), merchantBankDetail.getBeneficiaryName(), merchantBankDetail.getIfscCode(), merchant.getMid()));
        return responseDTO;
    }

    

    //Submit enach for digio

    public ENachIntitiationResponseDTO submitEnachForDigio(Merchant merchant, ENachSubmitRequestDTO requestDTO){
        ENachIntitiationResponseDTO responseDTO = new ENachIntitiationResponseDTO();
        responseDTO.setData(new ENachIntitiationResponseDTO.Data());

        responseDTO.getData().setDeep_link("bharatpe://dynamic?key=loan");
        LendingEnach lendingEnach = lendingEnachDao.findByMerchantIdAndApplicationId(merchant.getId(), requestDTO.getApplicationId());
        if (lendingEnach == null) {
            responseDTO.setResponse(false);
            responseDTO.setMessage("Enach not initiated");
            return responseDTO;
        }
        lendingEnach.setIdentifier(requestDTO.getIdentifier());
        lendingEnach.setMandateId(requestDTO.getMandateId());
        lendingEnach.setResponse(requestDTO.getResponse());
        lendingEnach.setStatus(requestDTO.getStatus());
        lendingEnach.setStatusMessage(requestDTO.getStatusMessage());
        lendingEnachDao.save(lendingEnach);

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
            LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchant(requestDTO.getApplicationId(), merchant);
            if (lendingApplication == null) {
                responseDTO.setResponse(false);
                responseDTO.setMessage("Loan Application not found");
                return responseDTO;
            }
            lendingApplication.setNachType("ENACH");
            lendingApplication.setNachLender("BHARATPE");
            lendingApplication.setNachStatus("APPROVED");
            lendingApplication.setNachReferenceNumber(requestDTO.getMandateId().toString());
            List<LendingPaymentSchedule> prevLoans = lendingPaymentScheduleDao.findPreviousLoansByMerchant(merchant.getId());
            if (prevLoans != null && prevLoans.size() > 0) {
                lendingApplication.setStatus("approved");
                lendingApplication.setManualKyc("APPROVED");
                lendingApplication.setManualCibil("APPROVED");
                lendingApplication.setPhysicalVerificationStatus("APPROVED");
            }
            lendingApplicationDao.save(lendingApplication);
        }
        return responseDTO;
    }

    public ENachIntitiationResponseDTO submitEnach(Merchant merchant, ENachSubmitRequestDTO requestDTO){
        ENachIntitiationResponseDTO responseDTO = new ENachIntitiationResponseDTO();
        responseDTO.setData(new ENachIntitiationResponseDTO.Data());
        
        responseDTO.getData().setDeep_link("bharatpe://dynamic?key=loan");
        LendingEnach lendingEnach = lendingEnachDao.findByMerchantIdAndApplicationId(merchant.getId(), requestDTO.getApplicationId());
        if (lendingEnach == null) {
            responseDTO.setResponse(false);
            responseDTO.setMessage("Enach not initiated");
            return responseDTO;
        }
        lendingEnach.setIdentifier(requestDTO.getIdentifier());
        lendingEnach.setMandateId(requestDTO.getMandateId());
        lendingEnach.setResponse(requestDTO.getResponse());
        lendingEnach.setStatus(requestDTO.getStatus());
        lendingEnach.setStatusMessage(requestDTO.getStatusMessage());
        lendingEnachDao.save(lendingEnach);

        if (requestDTO.getStatus()) {
            // Update Lending Application for ENACH
            LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchant(requestDTO.getApplicationId(), merchant);
            if (lendingApplication == null) {
                responseDTO.setResponse(false);
                responseDTO.setMessage("Loan Application not found");
                return responseDTO;
            }
            lendingApplication.setNachType("ENACH");
            lendingApplication.setNachLender("BHARATPE");
            lendingApplication.setNachStatus("APPROVED");
            lendingApplication.setNachReferenceNumber(requestDTO.getMandateId().toString());
//            List<LendingPaymentSchedule> prevLoans = lendingPaymentScheduleDao.findPreviousLoansByMerchant(merchant.getId());
//            if (prevLoans != null && prevLoans.size() > 0) {
//                lendingApplication.setStatus("approved");
//                lendingApplication.setManualKyc("APPROVED");
//                lendingApplication.setManualCibil("APPROVED");
//                lendingApplication.setPhysicalVerificationStatus("APPROVED");
//            }
            lendingApplicationDao.save(lendingApplication);
        }
        return responseDTO;
    }

    //changing skip status to true
    public ResponseDTO setEnachSkipStatus(Merchant merchant){
        LendingApplication lendingApplication = lendingApplicationDao.getLatestPendingApplication(merchant.getId());
        if (lendingApplication == null) {
            return new ResponseDTO(false, "Loan Application not found", null);
        }
        LendingEnach lendingEnach= lendingEnachDao.findByMerchantIdAndApplicationId(merchant.getId(), lendingApplication.getId());
        if(lendingEnach == null) {
            lendingEnach = new LendingEnach();
            lendingEnach.setApplicationId(lendingApplication.getId());
            lendingEnach.setMerchantId(merchant.getId());
        }
        lendingEnach.setSkip(true);
        lendingEnachDao.save(lendingEnach);
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
        LendingApplication lendingApplication=lendingApplicationDao.getLatestPendingApplication(merchant.getId());
        if(lendingApplication==null){
        	enachInitiationResponseDto.setResponse(false);
    		enachInitiationResponseDto.setMessage("Lending application not found");
            return enachInitiationResponseDto;
        }
        enachInitiationResponseDto.getData().setApplicationId(lendingApplication.getId());;
        
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
        LendingEnach lendingEnach = new LendingEnach(merchant.getId(), lendingApplication.getId(), null, (double)digioEnach.getMandate_data().getMaximum_amount(), mandateDate, merchant.getMid());
    	lendingEnachDao.save(lendingEnach);
    	return enachInitiationResponseDto;
    }
    
    
}
