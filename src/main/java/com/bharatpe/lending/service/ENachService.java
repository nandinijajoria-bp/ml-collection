package com.bharatpe.lending.service;

import com.bharatpe.common.dao.LendingEnachDao;
import com.bharatpe.common.dao.MerchantBankDetailDao;
import com.bharatpe.common.entities.*;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dto.ENachIntitiationResponseDTO;
import com.bharatpe.lending.dto.ENachSubmitRequestDTO;
import com.bharatpe.lending.dto.ResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

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

    // fetch loan detail by merchant IFSC [pending verification state]
    // validate bank for mandate support
    // if bank is suported , insert in ENach Detail Table.
    public ENachIntitiationResponseDTO eNachInitiate(Merchant merchant){
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
        String bankCode = fetchBankCode(merchantBankDetail.getIfscCode());
        if(bankCode == null){
            responseDTO.setResponse(false);
            responseDTO.setMessage("Bank not supported for Enach");
            logger.error("Merchant Bank not supported for Enach - {}", merchant);
            return responseDTO;
        }
        LendingEnach lendingEnach = new LendingEnach(merchant.getId(), lendingApplication.getId(), bankCode);
        lendingEnach = lendingEnachDao.save(lendingEnach);

        responseDTO.setData(new ENachIntitiationResponseDTO.Data());
        responseDTO.getData().setBankCode(bankCode);
        responseDTO.getData().setLoanAmount(lendingApplication.getLoanAmount());
        responseDTO.getData().setApplicationId(lendingApplication.getId());
        responseDTO.getData().setLoanStartDate(LocalDate.now().plusDays(1).toString());
        responseDTO.getData().setTransactionIdentifier(lendingEnach.getId());
        responseDTO.getData().setTransactionReferenceNumber(lendingEnach.getId());

        return responseDTO;
    }


    public ENachIntitiationResponseDTO submitEnach(Merchant merchant, ENachSubmitRequestDTO requestDTO){
        ENachIntitiationResponseDTO responseDTO = new ENachIntitiationResponseDTO();
        responseDTO.setData(null);
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
            lendingApplication.setNachStatus("INITIATED");
            lendingApplication.setNachReferenceNumber("BPEN" + merchant.getId() + lendingEnach.getId());
            List<LendingPaymentSchedule> prevLoans = lendingPaymentScheduleDao.findPreviousLoansByMerchant(merchant.getId());
            if (prevLoans != null && prevLoans.size() > 0) {
                lendingApplication.setStatus("approved");
                lendingApplication.setManualKyc("APPROVED");
                lendingApplication.setManualCibil("APPROVED");
                lendingApplication.setPhysicalVerificationStatus("APPROVED");
                lendingApplication.setLender("LIQUILOANS");
            }
            lendingApplicationDao.save(lendingApplication);
        }
        return responseDTO;
    }

    // fetch if bank is supported or not
    private String fetchBankCode(String ifscCode){
        return "9560";
    }
}
