package com.bharatpe.lending.service;

import com.bharatpe.common.dao.LendingEnachDao;
import com.bharatpe.common.dao.LendingNachBankDao;
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

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Date;
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

    @Autowired
    LendingNachBankDao lendingNachBankDao;

    SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy");

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
        String bankCode = fetchBankCode(merchantBankDetail.getIfscCode().substring(0,4), "NET");
        if(bankCode == null) {
            responseDTO.setResponse(false);
            responseDTO.setMessage("Bank not supported for Enach");
            logger.error("Merchant Bank not supported for Enach - {}", merchant);
            return responseDTO;
        }
        LendingEnach lendingEnach = new LendingEnach(merchant.getId(), lendingApplication.getId(), bankCode);
        lendingEnach = lendingEnachDao.save(lendingEnach);

        responseDTO.setData(new ENachIntitiationResponseDTO.Data());
        responseDTO.getData().setBankCode(bankCode);
        responseDTO.getData().setLoanAmount(100000D);
        responseDTO.getData().setApplicationId(lendingApplication.getId());
        responseDTO.getData().setLoanStartDate(sdf.format(new Date(new Date().getTime() + (1000 * 60 * 60 * 24))));
        responseDTO.getData().setTransactionIdentifier(lendingEnach.getId());
        responseDTO.getData().setTransactionReferenceNumber(lendingEnach.getId());
        responseDTO.getData().setAccountNumber(merchantBankDetail.getAccountNumber());
        responseDTO.getData().setBeneficiaryName(merchantBankDetail.getBeneficiaryName());
        responseDTO.getData().setIfscCode(merchantBankDetail.getIfscCode());

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

    // fetch if bank is supported or not
    public String fetchBankCode(String ifscCode, String mode){
        LendingNachBank lendingNachBank = lendingNachBankDao.findByIfscAndMode(ifscCode, mode);
        return lendingNachBank != null ? lendingNachBank.getBankCode() : null;
    }
}
