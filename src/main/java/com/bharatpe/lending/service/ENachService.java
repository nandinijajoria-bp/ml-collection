package com.bharatpe.lending.service;

import com.bharatpe.common.dao.LendingNachBankDao;
import com.bharatpe.common.dao.MerchantBankDetailDao;
import com.bharatpe.common.entities.*;
import com.bharatpe.lending.common.dao.BharatPeEnachDao;
import com.bharatpe.lending.common.dao.LendingPennydropDao;
import com.bharatpe.lending.common.entity.BharatPeEnach;
import com.bharatpe.lending.common.entity.LendingPennydrop;
import com.bharatpe.lending.constant.ErrorMessages;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dto.ENachIntitiationResponseDTO;
import com.bharatpe.lending.dto.ENachSubmitRequestDTO;
import com.bharatpe.lending.dto.EnachInitiateRequestDTO;
import com.bharatpe.lending.dto.ResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class ENachService {

    private final Logger logger = LoggerFactory.getLogger(ENachService.class);

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LendingNachBankDao lendingNachBankDao;
    
    @Autowired
    VerifyOTPService verifyOTPService;

    @Autowired
    APIGatewayService apiGatewayService;

    @Autowired
    BharatPeEnachDao bharatPeEnachDao;

    @Autowired
    MerchantBankDetailDao merchantBankDetailDao;

    @Autowired
    EnachErrorHandingService enachErrorHandingService;

    @Autowired
    LendingPennydropDao lendingPennydropDao;

    public ENachIntitiationResponseDTO eNachInitiate(Merchant merchant, String token, String provider){
        ENachIntitiationResponseDTO responseDTO = new ENachIntitiationResponseDTO();
        LendingApplication lendingApplication = lendingApplicationDao.findTop1ByMerchantOrderByIdDesc(merchant);
        if(lendingApplication == null) {
            responseDTO.setResponse(false);
            responseDTO.setMessage("Loan Application not found");
            logger.error("Unable to find loan application for Merchant - {}", merchant.getId());
            return responseDTO;
        }
        if (provider != null && !"DIGIO".equalsIgnoreCase(provider)) {
            MerchantBankDetail merchantBankDetail = merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchant.getId(), "ACTIVE");
            if (fetchBankCode(merchantBankDetail.getIfscCode().substring(0, 4), "BOTH") == null) {
                responseDTO.setResponse(false);
                responseDTO.setMessage("Bank not supported for enach");
                logger.error("Bank not supported for enach for Merchant - {}", merchant.getId());
                return responseDTO;
            }
        }

        return apiGatewayService.initiateEnach(new EnachInitiateRequestDTO(token, merchant.getId(), lendingApplication.getId(), String.valueOf(lendingApplication.getLoanAmount()), provider));
    }

    public ENachIntitiationResponseDTO submitEnach(Merchant merchant, ENachSubmitRequestDTO requestDTO, String token){
        ENachIntitiationResponseDTO responseDTO = new ENachIntitiationResponseDTO();
        responseDTO.setData(new ENachIntitiationResponseDTO.Data());
        responseDTO.getData().setDeep_link("bharatpe://dynamic?key=loan");
        BharatPeEnach bharatPeEnach = bharatPeEnachDao.findByMerchantIdAndApplicationId(merchant.getId(), requestDTO.getApplicationId());
        LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchant(requestDTO.getApplicationId(), merchant);
        if (bharatPeEnach == null) {
            responseDTO.setResponse(false);
            responseDTO.setMessage("Enach not initiated");
            return responseDTO;
        }
        if (requestDTO.getStatus()) {
            logger.info("Enach success for merchant:{}", merchant.getId());
            responseDTO.getData().setDeep_link("bharatpe://dynamic?key=loan&wroute=enachSuccess");
            // Update Lending Application for ENACH
            if (lendingApplication == null) {
                responseDTO.setResponse(false);
                responseDTO.setMessage("Loan Application not found");
                return responseDTO;
            }
            lendingApplication.setNachType("ENACH");
            lendingApplication.setNachLender("BHARATPE");
            lendingApplication.setNachStatus("APPROVED");  
            lendingApplication.setNachReferenceNumber(bharatPeEnach.getProviderUmrn());
            lendingApplicationDao.save(lendingApplication);
            if (lendingApplication.getLoanAmount() <= 200000) {
                verifyOTPService.sendDetailsForKycVerification(merchant.getId(), lendingApplication.getId(), false);
            }
            LendingPennydrop lendingPennydrop = lendingPennydropDao.isFailed(lendingApplication.getMerchant().getId(), lendingApplication.getId());
            if (lendingPennydrop == null) {
                apiGatewayService.updateApplicationPriority(lendingApplication.getMerchant().getId(), lendingApplication.getId());
            }
        }

        apiGatewayService.submitEnach(requestDTO, token, merchant.getId(), bharatPeEnach.getEnachProvider());

        if(Objects.nonNull(requestDTO)){
            checkForApplicationRejection(merchant, requestDTO, lendingApplication);
        }
        return responseDTO;
    }

    public String checkForApplicationRejection(Merchant merchant, ENachSubmitRequestDTO response, LendingApplication lendingApplication){
        logger.info("check for Application need to reject on Enach failure for Merchant - {}", merchant.getId());

        try{
            if(!response.getStatus()){
                switch (response.getStatusMessage()){
                    case ErrorMessages.MANDATE_REGISTRATION_FAILED:
                    case ErrorMessages.EMPTY_RESPONSE:
                    case ErrorMessages.AUTHENTICATION_FAILED:
                    case ErrorMessages.INVALID_CREDENTIAL:
                    case ErrorMessages.REJECT_CONFIRMATION:
                    case ErrorMessages.MERCHANT_SIGNATURE_VALIDATION_FAILED:
                    case ErrorMessages.MENDATE_VERIFICATION_FAILED:
                    case ErrorMessages.NO_RESPONSE_ON_MANDATE:
                    case ErrorMessages.MULTIPLE_ERROR:
                    case ErrorMessages.CHECKSUM_VALIDATION_FAILED:
                    case ErrorMessages.NO_RESPONSE_FROM_CUSTOMER:
                    case ErrorMessages.MENDATE_NOT_REGISTERED:
                    case ErrorMessages.DUPICATE_BANK_MANDATE_ID:
                    case ErrorMessages.CARD_VALIDATION_FAILED:
                    case ErrorMessages.DUPLICATE_BANK_MSGID:
                    case ErrorMessages.TECH_ERROR_OR_ISSUE_AT_BANK:
                    case ErrorMessages.REGS_FAILED:
                    case ErrorMessages.BANK_ERROR_XML:
                    case ErrorMessages.TXN_CNACALLED_AT_BANK:
                    case ErrorMessages.INVALIED_CREDENTIAL:
                        return enachErrorHandingService.retryPage(response, lendingApplication);
                    case ErrorMessages.MENDATE_NOT_REGISTERED_REQ_BALANC:
                    case ErrorMessages.BRANCH_KYC_NOT_COMPLETED:
                    case ErrorMessages.NO_ACCONT:
                    case ErrorMessages.INORRECT_MERCHANT_DEBITOR:
                    case ErrorMessages.MENDATE_DIFF_FROM_CBS:
                    case ErrorMessages.ACCOUNT_INOPERATIVE:
                    case ErrorMessages.MD_REGS_NOT_ALLOWED:
                        return enachErrorHandingService.sendForCpvOrReject(response, lendingApplication);
                    case ErrorMessages.AC_NOT_REGISTERED:
                    case ErrorMessages.AC_NUMBER_NOT_REGISTERED_WITH_NET_BANKING:
                    case ErrorMessages.VIEW_RIGHTS_ACCOUNT:
                        return enachErrorHandingService.sendForCpvOrRejectOrDebitScreenOnBankSupport(merchant, response, lendingApplication);
                    default:
                        return null;
                }
        }
        }catch(Exception ex){
            logger.error("Error Orrocured while Checking Application Rejection , Error - {}", ex);
            return null;
        }

        return "Success";
    }

    //changing skip status to true
    public ResponseDTO setEnachSkipStatus(Merchant merchant){
        LendingApplication lendingApplication = lendingApplicationDao.findTop1ByMerchantOrderByIdDesc(merchant);
        if (lendingApplication == null) {
            return new ResponseDTO(false, "Loan Application not found", null,null);
        }
        BharatPeEnach lendingEnach = bharatPeEnachDao.findByMerchantIdAndApplicationId(merchant.getId(), lendingApplication.getId());
        if(lendingEnach == null) {
            lendingEnach = new BharatPeEnach();
            lendingEnach.setApplicationId(lendingApplication.getId());
            lendingEnach.setMerchantId(merchant.getId());
            lendingEnach.setSuccess(false);
        }
        lendingEnach.setSkip(true);
        bharatPeEnachDao.save(lendingEnach);
        LendingPennydrop lendingPennydrop = lendingPennydropDao.isFailed(lendingApplication.getMerchant().getId(), lendingApplication.getId());
        if (lendingPennydrop == null) {
            apiGatewayService.updateApplicationPriority(lendingApplication.getMerchant().getId(), lendingApplication.getId());
        }
        return new ResponseDTO(true, null, null, null);
    }

    // check if bank is supported or not
    public String fetchBankCode(String ifscCode, String mode){
        LendingNachBank lendingNachBank = lendingNachBankDao.findByIfscAndMode(ifscCode);
        return lendingNachBank != null ? lendingNachBank.getBankCode() : null;
    }
}
