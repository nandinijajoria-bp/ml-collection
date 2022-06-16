package com.bharatpe.lending.service;

import com.bharatpe.common.dao.LendingNachBankDao;
import com.bharatpe.common.entities.*;
import com.bharatpe.lending.common.service.merchant.dto.BankDetailsDto;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.common.slave.dao.PincodeCityStateMappingDaoSlave;
import com.bharatpe.lending.common.slave.entity.BharatPeEnachSlave;
import com.bharatpe.lending.common.slave.entity.PincodeCityStateMappingSlave;
import com.bharatpe.lending.constant.ErrorMessages;
import com.bharatpe.lending.constant.LendingConstants;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dto.ENachSubmitRequestDTO;
import com.bharatpe.lending.dto.EnachErrorMessageDTO;
import com.bharatpe.lending.enums.ApplicationStatus;
import com.bharatpe.lending.util.LoanUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class EnachErrorHandingService {

    Logger logger = LoggerFactory.getLogger(EnachErrorHandingService.class);

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LendingNachBankDao lendingNachBankDao;

    @Autowired
    PincodeCityStateMappingDaoSlave pincodeCityStateMappingDaoSlave;

    @Autowired
    APIGatewayService apiGatewayService;

    @Autowired
    MerchantService merchantService;

    ExecutorService executorService = Executors.newFixedThreadPool(10);

    public EnachErrorMessageDTO initiatePopup(){
        EnachErrorMessageDTO enachErrorMessageDTO = new EnachErrorMessageDTO();
        enachErrorMessageDTO.setShowPopup(true);
        enachErrorMessageDTO.setHeader("e-NACH Unsuccessful!");
        enachErrorMessageDTO.setSkipEnach(true);
        return enachErrorMessageDTO;
    }

    public EnachErrorMessageDTO initiateDebitPage(){
        EnachErrorMessageDTO enachErrorMessageDTO = new EnachErrorMessageDTO();
        enachErrorMessageDTO.setHeader("Net Banking not supported");
        enachErrorMessageDTO.setMessage("Please try again using debit card");
        enachErrorMessageDTO.setIcon("not_supported");
        enachErrorMessageDTO.setSkipEnach(false);
        return enachErrorMessageDTO;
    }

    public EnachErrorMessageDTO initiateRetryPage(){
        EnachErrorMessageDTO enachErrorMessageDTO = new EnachErrorMessageDTO();
        enachErrorMessageDTO.setHeader("e-NACH couldn't be completed");
        enachErrorMessageDTO.setMessage("We faced an issue. Please try again.");
        enachErrorMessageDTO.setIcon("failed");
        enachErrorMessageDTO.setSkipEnach(false);
        return enachErrorMessageDTO;
    }

    public Map<String, String> enachApplicableMap(){
        Map<String, String> cpbApplicable = new HashMap<>();

        cpbApplicable.put(ErrorMessages.MANDATE_REGISTRATION_FAILED.toLowerCase(), "initiateRetry");
        cpbApplicable.put(ErrorMessages.EMPTY_RESPONSE.toLowerCase(), "initiateRetry");
        cpbApplicable.put(ErrorMessages.AUTHENTICATION_FAILED.toLowerCase(), "initiateRetry");
        cpbApplicable.put(ErrorMessages.INVALID_CREDENTIAL.toLowerCase(), "initiateRetry");
        cpbApplicable.put(ErrorMessages.REJECT_CONFIRMATION.toLowerCase(), "initiateRetry");
        cpbApplicable.put(ErrorMessages.MERCHANT_SIGNATURE_VALIDATION_FAILED.toLowerCase(), "initiateRetry");
        cpbApplicable.put(ErrorMessages.NO_RESPONSE_ON_MANDATE.toLowerCase(), "initiateRetry");
        cpbApplicable.put(ErrorMessages.MULTIPLE_ERROR.toLowerCase(), "initiateRetry");
        cpbApplicable.put(ErrorMessages.CHECKSUM_VALIDATION_FAILED.toLowerCase(), "initiateRetry");
        cpbApplicable.put(ErrorMessages.NO_RESPONSE_FROM_CUSTOMER.toLowerCase(), "initiateRetry");
        cpbApplicable.put(ErrorMessages.MENDATE_NOT_REGISTERED.toLowerCase(), "initiateRetry");
        cpbApplicable.put(ErrorMessages.DUPICATE_BANK_MANDATE_ID.toLowerCase(), "initiateRetry");
        cpbApplicable.put(ErrorMessages.CARD_VALIDATION_FAILED.toLowerCase(), "initiateRetry");
        cpbApplicable.put(ErrorMessages.DUPLICATE_BANK_MSGID.toLowerCase(), "initiateRetry");
        cpbApplicable.put(ErrorMessages.TECH_ERROR_OR_ISSUE_AT_BANK.toLowerCase(), "initiateRetry");
        cpbApplicable.put(ErrorMessages.REGS_FAILED.toLowerCase(), "initiateRetry");
        cpbApplicable.put(ErrorMessages.BANK_ERROR_XML.toLowerCase(), "initiateRetry");
        cpbApplicable.put(ErrorMessages.TXN_CNACALLED_AT_BANK.toLowerCase(), "initiateRetry");
        cpbApplicable.put(ErrorMessages.INVALIED_CREDENTIAL.toLowerCase(), "initiateRetry");

        cpbApplicable.put(ErrorMessages.MENDATE_NOT_REGISTERED_REQ_BALANC.toLowerCase(), "initiatePopup");
        cpbApplicable.put(ErrorMessages.BRANCH_KYC_NOT_COMPLETED.toLowerCase(), "initiatePopup");
        cpbApplicable.put(ErrorMessages.NO_ACCONT.toLowerCase(), "initiatePopup");
        cpbApplicable.put(ErrorMessages.INORRECT_MERCHANT_DEBITOR.toLowerCase(), "initiatePopup");
        cpbApplicable.put(ErrorMessages.MENDATE_DIFF_FROM_CBS.toLowerCase(), "initiatePopup");
        cpbApplicable.put(ErrorMessages.ACCOUNT_INOPERATIVE.toLowerCase(), "initiatePopup");
        cpbApplicable.put(ErrorMessages.MD_REGS_NOT_ALLOWED.toLowerCase(), "initiatePopup");
        cpbApplicable.put(ErrorMessages.BACK_BUTTON.toLowerCase(), "initiatePopup");

        cpbApplicable.put(ErrorMessages.AC_NOT_REGISTERED.toLowerCase(), "initiatePopupOrDebit");
        cpbApplicable.put(ErrorMessages.AC_NUMBER_NOT_REGISTERED_WITH_NET_BANKING.toLowerCase(), "initiatePopupOrDebit");
        cpbApplicable.put(ErrorMessages.VIEW_RIGHTS_ACCOUNT.toLowerCase(), "initiatePopupOrDebit");

        return cpbApplicable;
    }

    public EnachErrorMessageDTO enachErrorResponse(BharatPeEnachSlave bharatPeEnach, Long merchantId,
                                                   LendingApplication lendingApplication, Experian experian){
        Map<String, String> applicable = enachApplicableMap();
        EnachErrorMessageDTO initiateRetry = initiateRetryPage();
        EnachErrorMessageDTO initiateDebit = initiateDebitPage();
        EnachErrorMessageDTO initPopup = initiatePopup();

        if(Objects.nonNull(bharatPeEnach.getMessage()) && Objects.nonNull(applicable.get(bharatPeEnach.getMessage().toLowerCase())) && applicable.get(bharatPeEnach.getMessage().toLowerCase()).equals("initiateRetry")){
            initiateRetry.setSkipEnach(checkForCpv(lendingApplication, experian, false));

            return initiateRetry;
        }else if(Objects.nonNull(bharatPeEnach.getMessage()) && Objects.nonNull(applicable.get(bharatPeEnach.getMessage().toLowerCase())) && applicable.get(bharatPeEnach.getMessage().toLowerCase()).equals("initiatePopup")){

            initiateRetry.setSkipEnach(checkForCpv(lendingApplication, experian, false));
            return initiateRetry;
        }else if(Objects.nonNull(bharatPeEnach.getMessage()) && Objects.nonNull(applicable.get(bharatPeEnach.getMessage().toLowerCase())) && applicable.get(bharatPeEnach.getMessage().toLowerCase()).equals("initiatePopupOrDebit")){
            final Optional<BankDetailsDto> bankDetailsDtoOptional = merchantService.fetchMerchantBankDetails(merchantId);
            BankDetailsDto merchantBankDetail = null;
            if (bankDetailsDtoOptional.isPresent())
                merchantBankDetail = bankDetailsDtoOptional.get();
            if(Objects.isNull(merchantBankDetail)) {
                return initiateRetry;
            }
            String ifsc = merchantBankDetail.getIfsc().substring(0, 4);
            LendingNachBank lendingNachBank = lendingNachBankDao.findByIfscAndModeIs(ifsc, "BOTH");

            if(Objects.nonNull(lendingNachBank)){
                initiateDebit.setSkipEnach(checkForCpv(lendingApplication, experian, false));

                return initiateDebit;
            }

            initiateRetry.setSkipEnach(checkForCpv(lendingApplication, experian, true));
            return initiateRetry;
        }

        initiateRetry.setSkipEnach(checkForCpv(lendingApplication, experian, false));
        return initiateRetry;
    }

    public Boolean checkForCpv(LendingApplication lendingApplication, Experian experian, Boolean skipNow){

        if (experian != null && experian.getPincode() != null) {
            PincodeCityStateMappingSlave pincodeCityStateMapping = pincodeCityStateMappingDaoSlave.findByPincode(experian.getPincode());
            Boolean cpvCity = (pincodeCityStateMapping != null && LendingConstants.CPV_CITIES.contains(pincodeCityStateMapping.getCity()));

            return cpvCity && lendingApplication.getLoanAmount() >= 50000 && (LoanUtil.getDateDiffInDays(lendingApplication.getAgreementAt(), new Date()) > 3 || skipNow);
        }

        return false;
    }


    public String retryPage(ENachSubmitRequestDTO response, LendingApplication lendingApplication){
        logger.info("Enach failed, retry page initiated");

        return "retry page";
    }

    public String sendForCpvOrReject(ENachSubmitRequestDTO response, LendingApplication lendingApplication){
        logger.info("check for Application need to reject or start cpv, Application - {}", lendingApplication.getId());
        try{
            if(lendingApplication.getLoanAmount() < 50000){
                lendingApplication.setStatus(ApplicationStatus.REJECTED.name().toLowerCase());
                lendingApplication.setResponseCode(response.getStatusMessage());
                lendingApplication.setManualKyc(ApplicationStatus.REJECTED.name());
                lendingApplication.setKycApprovedDate(new Date());
                lendingApplication.setManualKycReason("eNACH Failure");

                lendingApplicationDao.save(lendingApplication);
                executorService.execute(() -> apiGatewayService.globalLimitTxn(lendingApplication.getMerchantId(), "CREDIT",lendingApplication.getLoanAmount()));
            }
        }catch (Exception ex){
            logger.error("Error Occurred in sendForCpvOrReject, Error - {}", ex);
            return null;
        }
        return "success";
    }

    public String sendForCpvOrRejectOrDebitScreenOnBankSupport(BasicDetailsDto merchant, ENachSubmitRequestDTO response, LendingApplication lendingApplication){
        logger.info("check for Application need to reject or start cpv or show debit screen, Application - {}", lendingApplication.getId());

        try{
            final Optional<BankDetailsDto> bankDetailsDtoOptional = merchantService.fetchMerchantBankDetails(merchant.getId());
            BankDetailsDto merchantBankDetail = null;
            if (bankDetailsDtoOptional.isPresent())
                merchantBankDetail = bankDetailsDtoOptional.get();
            if(Objects.isNull(merchantBankDetail)) {
                return null;
            }

            String ifsc = merchantBankDetail.getIfsc().substring(0, 4);
            LendingNachBank lendingNachBank = lendingNachBankDao.findByIfscAndModeIs(ifsc, "BOTH");
            if(Objects.isNull(lendingNachBank) && lendingApplication.getLoanAmount() < 50000){
                lendingApplication.setStatus(ApplicationStatus.REJECTED.name().toLowerCase());
                lendingApplication.setResponseCode(response.getStatusMessage());
                lendingApplication.setManualKyc(ApplicationStatus.REJECTED.name());
                lendingApplication.setKycApprovedDate(new Date());
                lendingApplication.setManualKycReason("eNACH Failure");

                lendingApplicationDao.save(lendingApplication);
                executorService.execute(() -> apiGatewayService.globalLimitTxn(lendingApplication.getMerchantId(), "CREDIT",lendingApplication.getLoanAmount()));
            }
        }catch (Exception ex){
            logger.error("Error Occurred in sendForCpvOrRejectOrDebitScreenOnBankSupport, Error - {}", ex);
            return null;
        }

        return null;
    }
}



