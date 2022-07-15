package com.bharatpe.lending.service;

import com.bharatpe.common.dao.*;
import com.bharatpe.common.entities.*;
import com.bharatpe.lending.common.Handler.EnachHandler;
import com.bharatpe.lending.common.Handler.PartnersApiHandler;
import com.bharatpe.lending.common.dto.LendingNachBankResponseDTO;
import com.bharatpe.lending.common.dto.MerchantNachDetailsResponseDTO;
import com.bharatpe.lending.common.service.merchant.dto.BankDetailsDto;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.common.query.dao.InternalClientDaoSlave;
import com.bharatpe.lending.common.query.entity.InternalClientSlave;
import com.bharatpe.lending.common.util.AesEncryptionUtil;
import com.bharatpe.lending.common.util.LendingHmacCalculator;
import com.bharatpe.lending.dto.ENachIntitiationResponseDTO;
import com.bharatpe.lending.dto.ENachSubmitRequestDTO;
import com.bharatpe.lending.dto.EnachInitiateRequestDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class BPEnachService {

    @Autowired
    MerchantService merchantService;

    @Autowired
    EnachHandler enachHandler;

    @Value("${enach.digio.authorization}")
    String authorization;

    @Autowired
    RestTemplate restTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @Value("${bpnach.register.endpoint}")
    public String BPNACH_REGISTER_URL;

    @Autowired
    AesEncryptionUtil aesEncryptionUtil;

    @Autowired
    InternalClientDaoSlave internalClientDaoSlave;

    @Autowired
    PartnersApiHandler partnersApiHandler;
   
    @Autowired
    LendingHmacCalculator lendingHmacCalculator;

    @Autowired
    APIGatewayService apiGatewayService;

    @Value("${enach.provider}")
    String enachProvider;

    Logger logger = LoggerFactory.getLogger(BPEnachService.class);
    
    private static String drfDeepLinkStr = "drf-onboard";

    public ENachIntitiationResponseDTO eNachInitiate(BasicDetailsDto merchant, String token ,String appVersion,
                                                     String module, Double nachAmount,
                                                     String type, String referenceNumber,
                                                     String ownerId, String clientName) {
        final double LOAN_AMOUNT = nachAmount;
        ENachIntitiationResponseDTO responseDTO = new ENachIntitiationResponseDTO();
        final Optional<BankDetailsDto> bankDetailsDtoOptional = merchantService.fetchMerchantBankDetails(merchant.getId());
        if (!bankDetailsDtoOptional.isPresent()) {
            responseDTO.setResponse(false);
            responseDTO.setMessage("Active Bank not found");
            logger.error("No Bank detail found for Merchant - {}", merchant.getId());
            return responseDTO;
        }
        BankDetailsDto merchantBankDetail = bankDetailsDtoOptional.get();

        String bankCode;
        if (appVersion != null && Integer.parseInt(appVersion) >= 238) {
            bankCode = fetchBankCode(merchantBankDetail.getIfsc().substring(0, 4), "BOTH");
        } else {
            bankCode = fetchBankCode(merchantBankDetail.getIfsc().substring(0, 4), "NET");
        }
        if (bankCode == null) {
            responseDTO.setResponse(false);
            responseDTO.setMessage("Bank not supported for Enach");
            logger.info("Merchant Bank not supported for Enach - {}", merchant);
            return responseDTO;
        }

        final EnachInitiateRequestDTO enachInitiateRequestDTO = new EnachInitiateRequestDTO(token, merchant.getId(), Long.parseLong(ownerId), String.valueOf(LOAN_AMOUNT), enachProvider);

        enachInitiateRequestDTO.setClientName(clientName);

        return apiGatewayService.initiateEnach(enachInitiateRequestDTO);
    }


    public ENachIntitiationResponseDTO submitEnach(BasicDetailsDto merchant, ENachSubmitRequestDTO requestDTO, String token) {
        ENachIntitiationResponseDTO responseDTO = new ENachIntitiationResponseDTO();
        responseDTO.setData(new ENachIntitiationResponseDTO.Data());
//        BpEnach bpEnach = bpEnachDao.findByIdAndMerchantIdAndStatus(requestDTO.getApplicationId(), merchant.getId(), BPEnachEnum.applicationStatus.INPROCESS.toString());
//        BPEnachEnum.enachDeepLink bpEnachEnum = BPEnachEnum.enachDeepLink.valueOf(bpEnach.getPlatform().toUpperCase());
//
//        if(bpEnach.getPlatform().toUpperCase().equals(BPEnachEnum.enachDeepLink.DRF.name())) {
//        	PartnerRetailerDTO retailer = partnersApiHandler.getPartnerRetailerByExternalId(bpEnach.getReferenceNumber());
//
//        	if(!ObjectUtils.isEmpty(retailer))
//        		responseDTO.getData().setDeep_link("bharatpe://dynamic?key="+drfDeepLinkStr+"&wid="+retailer.getToken());
//
//        }else if(bpEnach.getPlatform().toUpperCase().equals(BPEnachEnum.enachDeepLink.LENDING.name())) {
//
//            responseDTO.getData().setDeep_link("bharatpe://dynamic?key=" + BPEnachEnum.enachDeepLink.LOAN.name()
//                + "&&wroute=status&&platform=" + bpEnach.getPlatform().toUpperCase());
//
//        }else if(bpEnach.getPlatform().toUpperCase().equals(BPEnachEnum.enachDeepLink.CREDITCARD.name()) && requestDTO.getNewApp()){
//            responseDTO.getData().setDeep_link("bharatpe://dynamic?key=bharatpe-card-v2&pageRoute=enach");
//        }else if(bpEnach.getPlatform().toUpperCase().equals(BPEnachEnum.enachDeepLink.CREDITCARD.name()) && !requestDTO.getNewApp()){
//            responseDTO.getData().setDeep_link("bharatpe://dynamic?key=bharatpe-card");
//        } else if(bpEnach.getPlatform().toUpperCase().equals(BPEnachEnum.enachDeepLink.GOLD_LOAN.name())){
//            responseDTO.getData().setDeep_link("bharatpe://dynamic?key=gold-loan-lead");
//        } else{
//                responseDTO.getData().setDeep_link("bharatpe://dynamic?key=" + BPEnachEnum.enachDeepLink.RETAILER_FINANCE.name().toLowerCase()
//                    + "&&wroute=status&&platform=" + bpEnach.getPlatform().toUpperCase());
//        }


        final MerchantNachDetailsResponseDTO merchantNachDetailsResponseDTO = enachHandler.findByIdAndMerchantIdAndStatus(requestDTO.getApplicationId(), merchant.getId(),
          "INPROCESS");

        if (ObjectUtils.isEmpty(merchantNachDetailsResponseDTO)){
                responseDTO.setResponse(false);
                responseDTO.setMessage("Enach not initiated");
                return responseDTO;
        }

        return apiGatewayService.submitEnach(requestDTO, token, merchant.getId(), enachProvider, merchantNachDetailsResponseDTO.getPlatform());
    }


    public String fetchBankCode(String ifscCode, String mode) {
        LendingNachBankResponseDTO lendingNachBank = enachHandler.findByIfsc(ifscCode);
        return lendingNachBank != null ? lendingNachBank.getBankCode() : null;
    }


//    public ResponseDTO setEnachSkipStatus(BasicDetailsDto merchant, String referenceNumber) {
//        BpEnachSlave bpEnach = bpEnachDaoSlave.findTop1ByMerchantIdAndReferenceNumber(merchant.getId(), referenceNumber);
//        BpEnachSkip bpEnachSkip = bpEnachSkipDao.findByMerchantIdAndReferenceNumber(merchant.getId(), referenceNumber);
//        if (bpEnachSkip == null) {
//            return new ResponseDTO(false, "Loan Application not found", null,null);
//        }
//
//        bpEnachSkip.setSkip(true);
//        bpEnachSkip.setMerchantId(merchant.getId());
//        bpEnachSkip.setMerchantStoreId(bpEnach.getMerchantStoreId());
//        bpEnachSkip.setReferenceNumber(referenceNumber);
//        bpEnachSkipDao.save(bpEnachSkip);
//        return new ResponseDTO(true, null, null,null);
//
//    }


//    public String getBranchName(String ifscCode) {
//        String branch = null;
//        IfscSlave ifsc = ifscDaoSlave.findTop1ByIfscOrderByIdDesc(ifscCode);
//        if (ifsc != null) {
//            branch = ifsc.getBranch();
//        }
//        return branch;
//    }

//    public void registerNach(Map requestParams, Long merchantId, String clientName) {
//        logger.info("Registering Nach for merchant:{}", merchantId);
//        try {
//            String hash = lendingHmacCalculator.calculateHmac(lendingHmacCalculator.getPayload(requestParams), getSecret(clientName));
//            HttpHeaders headers = new HttpHeaders();
//            headers.setContentType(MediaType.APPLICATION_JSON);
//            headers.setCacheControl(CacheControl.noCache());
//            headers.set("hash", hash);
//            headers.set("clientName", clientName);
//
//            HttpEntity<Map> request = new HttpEntity<>(requestParams, headers);
//            logger.info("URL: {} request: {} ", BPNACH_REGISTER_URL, objectMapper.writeValueAsString(request));
//            ResponseEntity<Object> response = restTemplate.exchange(BPNACH_REGISTER_URL, HttpMethod.POST, request, Object.class);
//            if (response.getStatusCode().equals(HttpStatus.OK) && "200".equalsIgnoreCase((objectMapper.convertValue(response.getBody(), Map.class)).get("statusCode").toString())) {
//                logger.info("Nach register successful for merchant:{}", merchantId);
//            } else {
//                logger.info("Nach register Failed for merchant:{}", merchantId);
//            }
//        } catch (Exception e) {
//            logger.error("Exception in nach register api---", e);
//        }
//    }

    private String getSecret(String clientName) {
        InternalClientSlave client = internalClientDaoSlave.findByClientName(clientName);
        if (client != null) {
            return aesEncryptionUtil.decrypt(client.getSecret());
        }
        return "";
    }
}



