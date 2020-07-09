package com.bharatpe.lending.service;

import com.bharatpe.common.dao.ExperianDao;
import com.bharatpe.common.dao.LendingNachBankDao;
import com.bharatpe.common.dao.MerchantBankDetailDao;
import com.bharatpe.common.entities.*;
import com.bharatpe.lending.common.Constants.BPEnachConstant;
import com.bharatpe.lending.common.dao.IfscNewDao;
import com.bharatpe.lending.common.dao.PartnerRetailerDao;
import com.bharatpe.lending.common.entity.IfscNew;
import com.bharatpe.lending.common.entity.PartnerRetailers;
import com.bharatpe.lending.common.enums.BPEnachEnum;
import com.bharatpe.lending.constant.LendingConstants;
import com.bharatpe.lending.dao.BPEnachDao;
import com.bharatpe.lending.common.entity.BpEnach;
import com.bharatpe.lending.dao.BPEnachSkipDao;
import com.bharatpe.lending.dto.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

@Service
public class BPEnachService {

    @Autowired
    MerchantBankDetailDao merchantBankDetailDao;

    @Autowired
    BPEnachDao bpEnachDao;

    @Autowired
    LendingNachBankDao lendingNachBankDao;

    @Autowired
    BPEnachSkipDao bpEnachSkipDao;


    @Value("${enach.digio.authorization}")
    String authorization;

    @Autowired
    RestTemplate restTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    IfscNewDao ifscNewDao;

    @Autowired
    PartnerRetailerDao partnerRetailerDao;

    @Autowired
    ExperianDao experianDao;

    Logger logger = LoggerFactory.getLogger(BPEnachService.class);

    public ENachIntitiationResponseDTO eNachInitiate(Merchant merchant, String appVersion, String module, Double nachAmount, String type, String referenceNumber) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy");
        Date mandateDate = new Date(new Date().getTime() + (1000 * 60 * 60 * 24));
        final double LOAN_AMOUNT = nachAmount;
        ENachIntitiationResponseDTO responseDTO = new ENachIntitiationResponseDTO();
        MerchantBankDetail merchantBankDetail = merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchant.getId(), "ACTIVE");
        if (merchantBankDetail == null) {
            responseDTO.setResponse(false);
            responseDTO.setMessage("Active Bank not found");
            logger.error("No Bank detail found for Merchant - {}", merchant.getId());
            return responseDTO;
        }
        String bankCode;
        if (appVersion != null && Integer.parseInt(appVersion) >= 238) {
            bankCode = fetchBankCode(merchantBankDetail.getIfscCode().substring(0, 4), "BOTH");
        } else {
            bankCode = fetchBankCode(merchantBankDetail.getIfscCode().substring(0, 4), "NET");
        }
        if (bankCode == null) {
            responseDTO.setResponse(false);
            responseDTO.setMessage("Bank not supported for Enach");
            logger.error("Merchant Bank not supported for Enach - {}", merchant);
            return responseDTO;
        }
        String bankBranch = getBranchName(merchantBankDetail.getIfscCode());
        if (bankBranch == null || StringUtils.isEmpty(bankBranch)) {
            responseDTO.setResponse(false);
            responseDTO.setMessage("Bank Branch not found for Enach");
            logger.error("Merchant Bank Branch not found for Enach - {}", merchant);
            return responseDTO;
        }

        BpEnach bpEnach = new BpEnach(merchant.getId(), referenceNumber, type, merchant.getBeneficiaryName(), merchant.getBeneficiaryName(), Long.parseLong(bankCode),
                merchantBankDetail.getBankName(), merchantBankDetail.getAccountNumber(), merchantBankDetail.getIfscCode(), merchantBankDetail.getAccType(),
                BPEnachConstant.NACH_LENDER, BPEnachConstant.INTERNAL_NACH_TYPE, BPEnachConstant.NACH_MODE, LOAN_AMOUNT, mandateDate, BPEnachEnum.applicationStatus.INPROCESS.toString(), bankBranch
        );

        bpEnach = bpEnachDao.save(bpEnach);
        responseDTO.setData(new ENachIntitiationResponseDTO.Data(bpEnach.getId(), bpEnach.getId(), bankCode, LOAN_AMOUNT, sdf.format(mandateDate), bpEnach.getId(), merchantBankDetail.getAccountNumber(), merchantBankDetail.getBeneficiaryName(), merchantBankDetail.getIfscCode(), merchant.getMid()));
        return responseDTO;
    }

    public String fetchBankCode(String ifscCode, String mode) {
        LendingNachBank lendingNachBank = lendingNachBankDao.findByIfscAndMode(ifscCode, mode);
        return lendingNachBank != null ? lendingNachBank.getBankCode() : null;
    }

    public String getBranchName(String ifscCode) {
        String branch = null;
        List<IfscNew> bankList = ifscNewDao.findByIfsc(ifscCode);
        if (bankList != null && bankList.size() > 0) {
            branch = bankList.get(0).getBranch();
        }
        return branch;
    }

    public ENachIntitiationResponseDTO enachInititateForDigio(Merchant merchant, String appVersion, String module, Double nachAmount, String type, String referenceNumber) {
        logger.info("Initiating  Enach for Digio.");
        ENachIntitiationResponseDTO responseDTO = new ENachIntitiationResponseDTO();
        responseDTO.setData(new ENachIntitiationResponseDTO.Data());
        final double LOAN_AMOUNT = nachAmount;
        logger.info("Fetching merchant details.");
        MerchantBankDetail merchantBankDetail = merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchant.getId(), "ACTIVE");
        if (merchantBankDetail == null) {
            logger.error("Error occured fetching bank detils for merchant {}", merchant.getId());
            responseDTO.setResponse(false);
            responseDTO.setMessage("Merchant bank detail not found");
            logger.error("Unable to find bank detail for Merchant - {}", merchant.getId());
            return responseDTO;
        }

        String bankCode;
        if (appVersion != null && Integer.parseInt(appVersion) >= 238) {
            bankCode = fetchBankCode(merchantBankDetail.getIfscCode().substring(0, 4), "BOTH");
        } else {
            bankCode = fetchBankCode(merchantBankDetail.getIfscCode().substring(0, 4), "NET");
        }
        String bankBranch = getBranchName(merchantBankDetail.getIfscCode());
        if (bankBranch == null || StringUtils.isEmpty(bankBranch)) {
            responseDTO.setResponse(false);
            responseDTO.setMessage("Bank Branch not found for Enach");
            logger.error("Merchant Bank Branch not found for Enach - {}", merchant);
            return responseDTO;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("Authorization", authorization);

        //populating the data into request body class for the digio API call
        DigioEnachInitiationRequestDTO digioEnach = new DigioEnachInitiationRequestDTO();
        digioEnach.setMandate_data(new DigioEnachInitiationRequestDTO.Data());

        responseDTO.getData().setMandate_id("");
        responseDTO.getData().setCustomer_identifier("");

        if (merchant.getMobile() == null) {
            responseDTO.setResponse(false);
            responseDTO.setMessage("Merchant mobile number not found");
            logger.error("Unable to find mobile number for Merchant - {}", merchant.getId());
            return responseDTO;
        } else {
            digioEnach.setCustomer_identifier(merchant.getMobile());
            digioEnach.getMandate_data().setCustomer_mobile(merchant.getMobile());
        }

        logger.info("Fetching the bank details for the merchant {}", merchant.getId());

        digioEnach.getMandate_data().setDestination_bank_id(merchantBankDetail.getIfscCode());
        digioEnach.getMandate_data().setDestination_bank_name(merchantBankDetail.getBankName());
        digioEnach.getMandate_data().setCustomer_account_number(merchantBankDetail.getAccountNumber());

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        Date mandateDate = new Date(new Date().getTime() + (1000 * 60 * 60 * 24));
        digioEnach.getMandate_data().setFirst_collection_date(sdf.format(mandateDate));

        logger.info("Fetching the pancard from the Lending_pan_card table");

        PartnerRetailers partnerRetailersList = partnerRetailerDao.findByMerchantIdOrderByIdDesc(merchant.getId());


        if (partnerRetailersList == null || partnerRetailersList.getPanCard() == null) {

            logger.error("Error occured while fetching pancard detail from the partner_retailers table for merchant id {}", merchant.getId());

            logger.info("Fetching the pancard details from experian");
            Experian experian = experianDao.getByMerchantId(merchant.getId());

            if (experian == null || experian.getPancardNumber() == null) {

                logger.error("Error occured while fetching experian details for merchant if {}", merchant.getId());
                responseDTO.setResponse(false);
                responseDTO.setMessage("Pancard details not found");
                logger.error("Unable to find pancard details for Merchant - {}", merchant.getId());
                return responseDTO;
            }
            digioEnach.getMandate_data().setCustomer_pan(experian.getPancardNumber());
            digioEnach.getMandate_data().setCustomer_name(merchantBankDetail.getBeneficiaryName());
        } else {
            digioEnach.getMandate_data().setCustomer_pan(partnerRetailersList.getPanCard());
            digioEnach.getMandate_data().setCustomer_name(merchant.getBusinessName());
        }

        //check for the merchant name, incase name is not present in the lending_pancard table
        if (digioEnach.getMandate_data().getCustomer_name() == null) {
            digioEnach.getMandate_data().setCustomer_name(merchantBankDetail.getBeneficiaryName());
        }

        HttpEntity<DigioEnachInitiationRequestDTO> request = new HttpEntity<>(digioEnach, headers);
        try {
            logger.info("Hitting digio API for the enach initiation for merchant: {}", merchant.getId());
            String response = restTemplate.postForObject(LendingConstants.DIGIO_ENACH_INITIATION_URL, request, String.class);
            JsonNode jsonNode = objectMapper.readTree(response);
            if (jsonNode.has("mandate_id") && !jsonNode.get("mandate_id").isNull()) {
                responseDTO.getData().setMandate_id(jsonNode.get("mandate_id").asText());
                responseDTO.getData().setCustomer_identifier(merchant.getMobile());
            } else {
                logger.error("Mandate not found for merchant: {}", merchant.getId());
                responseDTO.setResponse(false);
                responseDTO.setMessage("Mandate not created");
                return responseDTO;
            }
            BpEnach bpEnach = new BpEnach(merchant.getId(), referenceNumber, type, merchant.getBeneficiaryName(), merchant.getBeneficiaryName(), Long.parseLong(bankCode),
                    merchantBankDetail.getBankName(), merchantBankDetail.getAccountNumber(), merchantBankDetail.getIfscCode(), merchantBankDetail.getAccType(),
                    BPEnachConstant.NACH_LENDER, BPEnachConstant.INTERNAL_NACH_TYPE, BPEnachConstant.NACH_MODE, LOAN_AMOUNT, mandateDate, BPEnachEnum.applicationStatus.INPROCESS.toString(), bankBranch
            );
            bpEnachDao.save(bpEnach);
        } catch (Exception e) {
            logger.error("Error occurred while fetching enach data from digio for merchant: {}", merchant.getId());
            responseDTO.setResponse(false);
            responseDTO.setMessage("Error occured while fetching enach data");
        }
        return responseDTO;
    }


    public ENachIntitiationResponseDTO submitEnach(Merchant merchant, ENachSubmitRequestDTO requestDTO) {
        ENachIntitiationResponseDTO responseDTO = new ENachIntitiationResponseDTO();
        responseDTO.setData(new ENachIntitiationResponseDTO.Data());
        BpEnach bpEnach = bpEnachDao.findByIdAndMerchantIdAndStatus(requestDTO.getApplicationId(), merchant.getId(), BPEnachEnum.applicationStatus.INPROCESS.toString());
        BPEnachEnum.enachDeepLink bpEnachEnum = BPEnachEnum.enachDeepLink.valueOf(bpEnach.getPlatform().toUpperCase());
        responseDTO.getData().setDeep_link("bharatpe://dynamic?key=" + bpEnachEnum.toString().toLowerCase() + "&&wroute=status");

        if (bpEnach == null) {
            responseDTO.setResponse(false);
            responseDTO.setMessage("Enach not initiated");
            return responseDTO;
        }
        bpEnach.setIdentifier(requestDTO.getIdentifier());
        bpEnach.setMandateId(requestDTO.getMandateId());
        bpEnach.setResponse(requestDTO.getResponse());
        bpEnach.setBankMessage(requestDTO.getStatusMessage());

        if (requestDTO.getStatus()) {
            bpEnach.setStatus(BPEnachEnum.applicationStatus.APPROVED.toString());
            bpEnach.setNachStatus(BPEnachEnum.applicationStatus.APPROVED.toString());
        } else {
            responseDTO.setMessage("Enach rejected");
            bpEnach.setStatus(BPEnachEnum.applicationStatus.REJECTED.toString());
            bpEnach.setNachStatus(BPEnachEnum.applicationStatus.REJECTED.toString());
        }
        bpEnachDao.save(bpEnach);
        return responseDTO;
    }


    public ENachIntitiationResponseDTO submitEnachForDigio(Merchant merchant, ENachSubmitRequestDTO requestDTO) {
        ENachIntitiationResponseDTO responseDTO = new ENachIntitiationResponseDTO();
        responseDTO.setData(new ENachIntitiationResponseDTO.Data());

        BpEnach bpEnach = bpEnachDao.findByIdAndMerchantIdAndStatus(requestDTO.getApplicationId(), merchant.getId(), BPEnachEnum.applicationStatus.INPROCESS.toString());
        BPEnachEnum.enachDeepLink bpEnachEnum = BPEnachEnum.enachDeepLink.valueOf(bpEnach.getPlatform().toUpperCase());
        responseDTO.getData().setDeep_link("bharatpe://dynamic?key=" + bpEnachEnum.toString().toLowerCase() + "&&wroute=status");
        if (bpEnach == null) {
            responseDTO.setResponse(false);
            responseDTO.setMessage("Enach not initiated");
            return responseDTO;
        }
        bpEnach.setIdentifier(requestDTO.getIdentifier());
        bpEnach.setMandateId(requestDTO.getMandateId());
        bpEnach.setResponse(requestDTO.getResponse());
        bpEnach.setBankMessage(requestDTO.getStatusMessage());
        bpEnachDao.save(bpEnach);

        //calling digio api to check if enach is success
        JsonNode jsonNode = null;
        try {
            String URL = LendingConstants.DIGIO_ENACH_STATUS_CHECK + requestDTO.getMandateId();
            HttpHeaders header = new HttpHeaders();
            header.add("Authorization", authorization);
            HttpEntity<String> request = new HttpEntity<String>(header);
            ResponseEntity<String> response = restTemplate.exchange(URL, HttpMethod.GET, request, String.class);
            String jsonResonse = response.getBody();
            jsonNode = objectMapper.readTree(jsonResonse);
        } catch (Exception e) {
            logger.error("Error occurred while fetching enach authorization data", e);
        }
        if (jsonNode != null && jsonNode.has("state") && jsonNode.get("state").asText().equals("auth_success") && requestDTO.getStatus()) {
            // Update Lending Application for ENACH
            logger.info("Authorization was successful");
            responseDTO.setResponse(true);
            responseDTO.setMessage("Enach successful");
            bpEnach.setNachStatus(BPEnachEnum.applicationStatus.APPROVED.toString());

        } else {
            responseDTO.setResponse(false);
            responseDTO.setMessage("Enach rejected");
            bpEnach.setNachStatus(BPEnachEnum.applicationStatus.REJECTED.toString());
        }
        bpEnachDao.save(bpEnach);
        return responseDTO;
    }

    public Response checkEnachStatus(Merchant merchant, String referenceNumber) {
        Response finalResponse = new Response();
        List<BpEnach> bpEnaches = bpEnachDao.findByMerchantIdAndReferenceNumber(merchant.getId(), referenceNumber);

        BpEnach bpEnach = bpEnaches.get(0);
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy");
        EnachCheckStatusRequestDTO.Transaction transaction = new EnachCheckStatusRequestDTO.Transaction(sdf.format(bpEnach.getStartDate()));
        EnachCheckStatusRequestDTO.Consumer consumer = new EnachCheckStatusRequestDTO.Consumer(bpEnach.getIdentifier());
        EnachCheckStatusRequestDTO.MerchantInfo merchantInfo = new EnachCheckStatusRequestDTO.MerchantInfo();
        EnachCheckStatusRequestDTO.PaymentInfo.Instruction instruction = new EnachCheckStatusRequestDTO.PaymentInfo.Instruction();
        EnachCheckStatusRequestDTO.PaymentInfo paymentInfo = new EnachCheckStatusRequestDTO.PaymentInfo(instruction);
        EnachCheckStatusRequestDTO enachCheckStatusRequestDTO = new EnachCheckStatusRequestDTO();
        enachCheckStatusRequestDTO.setConsumer(consumer);
        enachCheckStatusRequestDTO.setTransaction(transaction);
        enachCheckStatusRequestDTO.setMerchantInfo(merchantInfo);
        enachCheckStatusRequestDTO.setPaymentInfo(paymentInfo);

        //test only
        logger.info(enachCheckStatusRequestDTO.toString());

        // converting to json
        // for testing only
//        try
//        {
//            ObjectMapper objectMapper = new ObjectMapper();
//            String jsonString = objectMapper.writeValueAsString(enachCheckStatusRequestDTO);
//            System.out.println(jsonString);
//        }
//        catch (Exception e)
//        {
//            logger.error(e.getMessage());
//        }

        try {
            String URL = LendingConstants.ENACH_STATUS_CHECK;
            HttpHeaders header = new HttpHeaders();
            header.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<EnachCheckStatusRequestDTO> request = new HttpEntity<>(enachCheckStatusRequestDTO, header);
            String response = restTemplate.postForObject(URL, request, String.class);
            JsonNode jsonNode = objectMapper.readTree(response);

            if (jsonNode.isNull()) {
                logger.error("No response received for reference_no: {}", referenceNumber);
            } else if (jsonNode.has("error") && jsonNode.hasNonNull("error")) {
                logger.error("Failure response received for reference_no: {}", referenceNumber);
                bpEnach.setNachStatus(BPEnachEnum.applicationStatus.REJECTED.toString());
            } else {
                logger.error("Success response received for reference_no: {}", referenceNumber);
                bpEnach.setNachStatus(BPEnachEnum.applicationStatus.APPROVED.toString());
            }
        } catch (Exception e) {
            logger.error("Error occurred while fetching enach status data for applicationId: {}", bpEnach.getId());
        }
        finalResponse.setStatus("true");
        finalResponse.setResponseMessage("success");
        finalResponse.setResponseCode("200");
        return finalResponse;
    }
}



