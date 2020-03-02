package com.bharatpe.lending.service;

import com.bharatpe.common.dao.LendingEnachDao;
import com.bharatpe.common.dao.MerchantBankDetailDao;
import com.bharatpe.common.entities.*;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dto.ENachIntitiationResponseDTO;
import com.bharatpe.lending.dto.ENachSubmitRequestDTO;
import com.sun.org.apache.xpath.internal.operations.Bool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.Date;
import java.util.Optional;

@Service
public class ENachService {

    private Logger logger = LoggerFactory.getLogger(ENachService.class);

    private static final String PENDING_VERIFICATION = "pending_verification";
    private static final String ACTIVE = "ACTIVE";

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    MerchantBankDetailDao merchantBankDetailDao;

    @Autowired
    LendingEnachDao lendingEnachDao;

    @Autowired
    RestTemplate restTemplate;


    // fetch loan detail by merchant IFSC [pending verification state]
    // validate bank for mandate support
    // if bank is suported , insert in ENach Detail Table.
    public ENachIntitiationResponseDTO eNachInitiate(Merchant merchant){
        ENachIntitiationResponseDTO responseDTO = new ENachIntitiationResponseDTO();
        Optional<LendingApplication> lendingApplicationOptional = fetchEligibleLoanApplicationByMerchant(merchant);
        if(!lendingApplicationOptional.isPresent()){
            logger.error("Unable to find loan application for Merchant - {}", merchant);
            responseDTO.setResponseStatus("FAILED");
            return responseDTO;
        }

        MerchantBankDetail  merchantBankDetail = merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchant.getId(), ACTIVE);
        if(merchantBankDetail == null){
            logger.error("No Bank detail found for Merchant - {}", merchant);
            responseDTO.setResponseStatus("FAILED");
            return responseDTO;
        }
        String bankCode = fetchBankCode(merchantBankDetail.getIfscCode());
        if(bankCode == null){
            logger.error("Merchant Bank not supported for Enach - {}", merchant);
            responseDTO.setResponseStatus("FAILED");
            return responseDTO;
        }

        responseDTO.setBankCode(bankCode);
        responseDTO.setLoanAmount(lendingApplicationOptional.get().getLoanAmount());
        responseDTO.setApplicationId(lendingApplicationOptional.get().getId());
        responseDTO.setLoanStartDate(LocalDate.now().plusDays(1).toString());

        LendingEnach lendingEnach = new LendingEnach();

        lendingEnach.setBankCode(responseDTO.getBankCode());
        lendingEnach.setApplicationId(responseDTO.getApplicationId());
        lendingEnachDao.save(lendingEnach);


        return responseDTO;
    }


    public Boolean submitEnach(Merchant merchant, ENachSubmitRequestDTO requestDTO){

        LendingEnach lendingEnach = new LendingEnach();

        // Update Lending Application for ENACH
        LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchant(requestDTO.getApplicationId(), merchant);

        lendingEnach.setApplicationId(requestDTO.getApplicationId());
        lendingEnach.setIdentifier(requestDTO.getIdentifier());
        lendingEnach.setMandateId(requestDTO.getMandateId());
        lendingEnach.setResponse(requestDTO.getResponse());
        lendingEnach.setStatus(requestDTO.getStatus());

        try {
            lendingEnachDao.save(lendingEnach);

            lendingApplication.setNachType("ENACH");
            lendingApplication.setNachLender("BHARATPE");
            lendingApplication.setNachStatus("INITIATED");
            lendingApplication.setNachReferenceNumber("BPEN" + merchant.getId() + lendingEnach.getId());

            return Boolean.TRUE;
        } catch (Exception e){
            logger.error("Unable to save enach submit request for merchant-{}", merchant, e);
            return Boolean.FALSE;
        }
    }

    private Optional<LendingApplication> fetchEligibleLoanApplicationByMerchant(Merchant merchant){
        return lendingApplicationDao.findFirstByMerchantAndStatus(merchant, PENDING_VERIFICATION);
    }



    // fetch if bank is supported or not
    private String fetchBankCode(String ifscCode){
        return "9560";
    }
}
