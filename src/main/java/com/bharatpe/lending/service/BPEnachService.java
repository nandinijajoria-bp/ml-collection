package com.bharatpe.lending.service;

import com.bharatpe.common.dao.*;
import com.bharatpe.common.entities.*;
import com.bharatpe.lending.common.Handler.EnachHandler;
import com.bharatpe.lending.common.Handler.PartnersApiHandler;
import com.bharatpe.lending.common.dto.BharatPeEnachResponseDTO;
import com.bharatpe.lending.common.dto.LendingNachBankResponseDTO;
import com.bharatpe.lending.common.dto.MerchantNachDetailsResponseDTO;
import com.bharatpe.lending.common.query.dao.LendingPaymentScheduleDaoSlave;
import com.bharatpe.lending.common.query.entity.LendingPaymentScheduleSlave;
import com.bharatpe.lending.common.service.merchant.constants.Constants;
import com.bharatpe.lending.common.service.merchant.dto.BankDetailsDto;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.common.query.dao.InternalClientDaoSlave;
import com.bharatpe.lending.common.query.entity.InternalClientSlave;
import com.bharatpe.lending.common.util.AesEncryptionUtil;
import com.bharatpe.lending.common.util.LendingHmacCalculator;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dto.ENachIntitiationResponseDTO;
import com.bharatpe.lending.dto.ENachSubmitRequestDTO;
import com.bharatpe.lending.dto.EnachInitiateRequestDTO;
import com.bharatpe.lending.enums.EnachMode;
import com.bharatpe.lending.util.LoanUtil;
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

    @Value("${enach.provider:techprocess}")
    String enachProvider;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LendingPaymentScheduleDaoSlave lendingPaymentScheduleDaoSlave;

    @Autowired
    LoanUtil loanUtil;

    @Autowired
    MerchantLoansService merchantLoansService;

    Logger logger = LoggerFactory.getLogger(BPEnachService.class);
    
    private static String drfDeepLinkStr = "drf-onboard";

    public ENachIntitiationResponseDTO eNachInitiate(BasicDetailsDto merchant, String token ,String appVersion,
                                                     String module, String amt,
                                                     String type, String referenceNumber,
                                                     String ownerId, String clientName, String nachMode) {

        ENachIntitiationResponseDTO responseDTO = new ENachIntitiationResponseDTO();
        LendingApplication lendingApplication = lendingApplicationDao.findTop1ByMerchantIdOrderByIdDesc(merchant.getId());

        if(clientName.equalsIgnoreCase("LENDING")) {

            if (loanUtil.reNachEnabledMerchants().contains(merchant.getId())) {
                LendingPaymentScheduleSlave activeLoan =  lendingPaymentScheduleDaoSlave.findByMerchantIdAndStatus(merchant.getId(), "ACTIVE");
                if (!ObjectUtils.isEmpty(activeLoan)) {
                    if (merchantLoansService.showRenachBanner(merchant.getId(), activeLoan.getNbfc(), false)) {
                        return eNachInitiateForRenachMerchants(merchant, token, nachMode, activeLoan);
                    }
                }
            }

            if(lendingApplication == null) {
                responseDTO.setResponse(false);
                responseDTO.setMessage("Loan Application not found");
                logger.error("Unable to find loan application for Merchant - {}", merchant.getId());
                return responseDTO;
            }


            if(loanUtil.isEligibleForNachSkip(lendingApplication, lendingApplication.getLender())){
                responseDTO.setResponse(false);
                responseDTO.setMessage("Nach can be skipped");
                logger.info("nach can be skipped for application:{}", lendingApplication.getId());
                return responseDTO;
            }

            Double nachAmount = (EnachMode.ADHAAR.name().equalsIgnoreCase(nachMode) && lendingApplication.getLoanAmount() > 100000D)
                    ? 100000D : lendingApplication.getLoanAmount();
            String deep_link = apiGatewayService.getEnachProvider(token, lendingApplication.getLender(), merchant.getId());
            String providerName = deep_link.contains("bharatpe://enachdigio")?"DIGIO":"TECHPROCESS";

            return apiGatewayService.initiateEnach(new EnachInitiateRequestDTO(token, merchant.getId(), lendingApplication.getId(),
                    String.valueOf(nachAmount), providerName, lendingApplication.getLender(), nachMode), lendingApplication.getLoanType());

        } else {
            final double LOAN_AMOUNT = Double.parseDouble(amt); ;
            final EnachInitiateRequestDTO enachInitiateRequestDTO = new EnachInitiateRequestDTO(token, merchant.getId(), Long.parseLong(ownerId), String.valueOf(LOAN_AMOUNT), enachProvider);
            enachInitiateRequestDTO.setClientName(clientName);
            return apiGatewayService.initiateEnach(enachInitiateRequestDTO, lendingApplication.getLoanType());
        }
    }

    private ENachIntitiationResponseDTO eNachInitiateForRenachMerchants(BasicDetailsDto merchant, String token, String nachMode, LendingPaymentScheduleSlave activeLoan) {

        ENachIntitiationResponseDTO responseDTO = new ENachIntitiationResponseDTO();

        Optional<LendingApplication> optionalLendingApplication = lendingApplicationDao.findById(activeLoan.getApplicationId());
        if(!optionalLendingApplication.isPresent()) {
            responseDTO.setResponse(false);
            responseDTO.setMessage("Loan Application not found");
            logger.error("Unable to find loan application for Merchant - {}", merchant.getId());
            return responseDTO;
        }

        LendingApplication lendingApplication = optionalLendingApplication.get();


        lendingApplication.setNachStatus(null);
        lendingApplication.setNachReferenceNumber(null);
        lendingApplicationDao.save(lendingApplication);

        logger.info("Initiating nach for applicationId : {} for merchantId : {}", lendingApplication.getId(), lendingApplication.getMerchantId());

        Double nachAmount = (EnachMode.ADHAAR.name().equalsIgnoreCase(nachMode) && lendingApplication.getLoanAmount() > 100000D)
          ? 100000D : lendingApplication.getLoanAmount();
        String deep_link = apiGatewayService.getEnachProvider(token, lendingApplication.getLender(), merchant.getId());
        String providerName = deep_link.contains("bharatpe://enachdigio")?"DIGIO":"TECHPROCESS";

        return apiGatewayService.initiateEnach(new EnachInitiateRequestDTO(token, merchant.getId(), lendingApplication.getId(),
          String.valueOf(nachAmount), providerName, lendingApplication.getLender(), nachMode), lendingApplication.getLoanType());
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


        BharatPeEnachResponseDTO bharatPeEnach = enachHandler.findByMerchantIdAndApplicationIdV2(merchant.getId(), requestDTO.getApplicationId());


        if (ObjectUtils.isEmpty(bharatPeEnach)){
                responseDTO.setResponse(false);
                responseDTO.setMessage("Enach not initiated");
                return responseDTO;
        }

        LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchantId(requestDTO.getApplicationId(), merchant.getId());

        return apiGatewayService.submitEnach(requestDTO, token, merchant.getId(), enachProvider, bharatPeEnach.getClientName(),lendingApplication.getLoanType());
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



