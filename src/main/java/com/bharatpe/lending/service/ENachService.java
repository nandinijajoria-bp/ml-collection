package com.bharatpe.lending.service;

import com.bharatpe.cache.service.LendingCache;
import com.bharatpe.common.entities.*;
import com.bharatpe.lending.common.Handler.EnachHandler;
import com.bharatpe.lending.common.dao.LendingBulkDisbursalDao;
import com.bharatpe.lending.common.dao.LendingBulkNachDao;
import com.bharatpe.lending.common.dao.LendingPennydropDao;
import com.bharatpe.lending.common.dto.BharatPeEnachResponseDTO;
import com.bharatpe.lending.common.dto.LendingNachBankResponseDTO;
import com.bharatpe.lending.common.dto.MerchantNachDetailsResponseDTO;
import com.bharatpe.lending.common.entity.*;
import com.bharatpe.lending.common.service.merchant.dto.BankDetailsDto;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.constant.ErrorMessages;
import com.bharatpe.lending.constant.LendingConstants;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.handlers.S3BucketHandler;
import com.bharatpe.lending.util.LoanUtil;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class ENachService {

    private final Logger logger = LoggerFactory.getLogger(ENachService.class);

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    VerifyOTPService verifyOTPService;

    @Autowired
    APIGatewayService apiGatewayService;

    @Autowired
    EnachErrorHandingService enachErrorHandingService;

    @Autowired
    LendingPennydropDao lendingPennydropDao;

    @Autowired
    EnachHandler enachHandler;

    @Autowired
    S3BucketHandler s3BucketHandler;

    @Autowired
    LendingBulkDisbursalDao lendingBulkDisbursalDao;

    @Autowired
    LendingBulkNachDao lendingBulkNachDao;

    @Autowired
    Environment env;

    @Autowired
    LendingCache lendingCache;

    @Autowired
    MerchantService merchantService;

    ExecutorService executorService = Executors.newFixedThreadPool(50);

    public ENachIntitiationResponseDTO eNachInitiate(BasicDetailsDto merchant, String token, String provider){
        ENachIntitiationResponseDTO responseDTO = new ENachIntitiationResponseDTO();
        LendingApplication lendingApplication = lendingApplicationDao.findTop1ByMerchantIdOrderByIdDesc(merchant.getId());
        if(lendingApplication == null) {
            responseDTO.setResponse(false);
            responseDTO.setMessage("Loan Application not found");
            logger.error("Unable to find loan application for Merchant - {}", merchant.getId());
            return responseDTO;
        }
        if (provider != null && !"DIGIO".equalsIgnoreCase(provider)) {
            final Optional<BankDetailsDto> bankDetailsDtoOptional = merchantService.fetchMerchantBankDetails(merchant.getId());
            BankDetailsDto merchantBankDetail = null;
            if (bankDetailsDtoOptional.isPresent())
                merchantBankDetail = bankDetailsDtoOptional.get();
            if (fetchBankCode(merchantBankDetail.getIfsc().substring(0, 4), "BOTH") == null) {
                responseDTO.setResponse(false);
                responseDTO.setMessage("Bank not supported for enach");
                logger.error("Bank not supported for enach for Merchant - {}", merchant.getId());
                return responseDTO;
            }
        }

        String deep_link = apiGatewayService.getEnachProvider(token, lendingApplication.getLender(), merchant.getId());
        String providerName = deep_link.equals("bharatpe://enachdigio")?"DIGIO":"TECHPROCESS";
        return apiGatewayService.initiateEnach(new EnachInitiateRequestDTO(token, merchant.getId(), lendingApplication.getId(), String.valueOf(lendingApplication.getLoanAmount()), providerName, lendingApplication.getLender()));
    }

    public ENachIntitiationResponseDTO submitEnach(BasicDetailsDto merchant, ENachSubmitRequestDTO requestDTO, String token){
        String loanDetailsCacheKey = "LENDING_LOAN_DETAILS_" + merchant.getId();
        logger.info("deleting cached key of loan details where nach is done for merchant: {}",merchant.getId());
        if(Objects.nonNull(lendingCache.get(loanDetailsCacheKey))) {
            lendingCache.delete(loanDetailsCacheKey);
        }
        ENachIntitiationResponseDTO responseDTO = new ENachIntitiationResponseDTO();
        responseDTO.setData(new ENachIntitiationResponseDTO.Data());
        responseDTO.getData().setDeep_link("bharatpe://dynamic?key=loan");
        BharatPeEnachResponseDTO bharatPeEnach = enachHandler.findByMerchantIdAndApplicationId(merchant.getId(), requestDTO.getApplicationId());
        LendingApplication lendingApplication =
                lendingApplicationDao.findByIdAndMerchantId(requestDTO.getApplicationId(), merchant.getId());
        if (bharatPeEnach == null) {
            responseDTO.setResponse(false);
            responseDTO.setMessage("Enach not initiated");
            return responseDTO;
        }
        if (requestDTO.getStatus()) {
            logger.info("Enach success for merchant:{}", merchant.getId());
            if(Objects.nonNull(lendingApplication) && !StringUtils.isEmpty(lendingApplication.getCkycId())) {
                responseDTO.getData().setDeep_link("bharatpe://dynamic?key=easy-loans&wroute=enachSuccess");
            } else {
                responseDTO.getData().setDeep_link("bharatpe://dynamic?key=loan&wroute=enachSuccess");
            }
            // Update Lending Application for ENACH
            if (lendingApplication == null) {
                responseDTO.setResponse(false);
                responseDTO.setMessage("Loan Application not found");
                return responseDTO;
            }
            lendingApplication.setNachType("ENACH");
//            lendingApplication.setNachLender("BHARATPE");
            lendingApplication.setNachStatus("APPROVED");
            lendingApplication.setNachReferenceNumber(bharatPeEnach.getProviderUmrn());
//            lendingApplicationDao.save(lendingApplication);

            if("NTB".equalsIgnoreCase(lendingApplication.getLoanType()) || "NTB_SMS_1".equalsIgnoreCase(lendingApplication.getLoanType())){
                apiGatewayService.fosAttribution(merchant.getId(),"NTB_LOAN","CLOSED");
            }

            if (lendingApplication.getLoanAmount() <= 200000) {
                verifyOTPService.sendDetailsForKycVerification(merchant.getId(), lendingApplication.getId(), false);
            }

//            LendingPennydrop lendingPennydrop = lendingPennydropDao.isFailed(merchant.getId(), lendingApplication.getId());
//            if (lendingPennydrop == null) {
//                apiGatewayService.updateApplicationPriority(merchant.getId(), lendingApplication.getId());
//            }
        }
        
        requestDTO.setLender(lendingApplication.getLender());
        ENachIntitiationResponseDTO eNachIntitiationResponseDTO = apiGatewayService.submitEnach(requestDTO, token, merchant.getId(), bharatPeEnach.getEnachProvider(), "LENDING");

        if(!ObjectUtils.isEmpty(eNachIntitiationResponseDTO) && !ObjectUtils.isEmpty(eNachIntitiationResponseDTO.getData())){
            if(!ObjectUtils.isEmpty(eNachIntitiationResponseDTO.getData().getLender())){
                lendingApplication.setNachLender(eNachIntitiationResponseDTO.getData().getLender());
            } else{
                lendingApplication.setNachLender("BHARATPE");
            }
        }

        lendingApplicationDao.save(lendingApplication);
        if(Objects.nonNull(requestDTO)){
            checkForApplicationRejection(merchant, requestDTO, lendingApplication);
        }
        if (!requestDTO.getStatus() && lendingApplication != null && !StringUtils.isEmpty(lendingApplication.getCkycId())) {
            responseDTO.getData().setDeep_link(env.getProperty("new.loan.deeplink"));
        }
        return responseDTO;
    }

    public String checkForApplicationRejection(BasicDetailsDto merchant, ENachSubmitRequestDTO response, LendingApplication lendingApplication){
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
    public ResponseDTO setEnachSkipStatus(BasicDetailsDto merchant){
        LendingApplication lendingApplication = lendingApplicationDao.findTop1ByMerchantIdOrderByIdDesc(merchant.getId());
        if (lendingApplication == null) {
            return new ResponseDTO(false, "Loan Application not found", null,null);
        }
        final boolean skipNach = enachHandler.skipNach(lendingApplication.getId(), merchant.getId());
//        LendingPennydrop lendingPennydrop = lendingPennydropDao.isFailed(merchant.getId(), lendingApplication.getId());
//        if (lendingPennydrop == null) {
//            apiGatewayService.updateApplicationPriority(merchant.getId(), lendingApplication.getId());
//        }
        return new ResponseDTO(skipNach, null, null, null);
    }

    // check if bank is supported or not
    public String fetchBankCode(String ifscCode, String mode){
        LendingNachBankResponseDTO lendingNachBank = enachHandler.findByIfsc(ifscCode);
        return lendingNachBank != null ? lendingNachBank.getBankCode() : null;
    }

    public CommonResponse cancelEnach(BasicDetailsDto merchant) {
        MerchantNachDetailsResponseDTO bpEnach = enachHandler.findSuccessEnach(merchant.getId());
        if (bpEnach == null) {
            logger.info("Enach not found for merchant:{}", merchant.getId());
        } else {
            apiGatewayService.cancelEnach(merchant.getId());
        }
        return new CommonResponse(true, "success");
    }

    public void uploadBulkEnach(EnachUploadRequestDTO enachUploadRequestDTO) {
        Long fileId = enachUploadRequestDTO.getFileId();
        Long userId = enachUploadRequestDTO.getUserId();
        LendingBulkDisbursal lendingBulkDisbursal = lendingBulkDisbursalDao.findByIdAndType(fileId,"BULK_NACH");
        if(lendingBulkDisbursal != null){
            try {
                String fileName = lendingBulkDisbursal.getFileName();
                logger.info("Getting file : {} from s3", fileName);
                InputStream lenderFile = s3BucketHandler.getObject(fileName, "loan-document");
                BufferedReader lenderFileReader = new BufferedReader(new InputStreamReader(lenderFile));
                String readLine = lenderFileReader.readLine();
                readLine = lenderFileReader.readLine();
                while (readLine != null) {
                    logger.info("readline: {}",readLine);
                    String[] arr = readLine.split(",");
                    Long merchantId = Long.valueOf(arr[1]);
                    Long applicationId = Long.valueOf(arr[2]);
                    String loanId = arr[3];
                    Double debitAmount = Double.valueOf(arr[4]);
                    String referenceNo = arr[5];
                    executorService.execute(() -> {
                        insertNachData(merchantId,applicationId,debitAmount,loanId,userId,referenceNo);
                    });
                    readLine = lenderFileReader.readLine();
                }
            }
            catch (Exception exception) {
                logger.error("Error occured while uploading nach file : {}",exception);
            }
        }
    }

    public void insertNachData(Long merchantId,Long applicationId,Double debitAmount,String loanId,Long userId,String referenceNo){
        logger.info("Creating bulk nach entry for merchantId: {},applicationId : {}",merchantId,applicationId);
        SimpleDateFormat formatter = new SimpleDateFormat("yy/MM/dd");
        BulkNach bulkNach = new BulkNach();
        bulkNach.setMerchantId(merchantId);
        bulkNach.setApplicationId(applicationId);
        bulkNach.setLoanId(loanId);
        bulkNach.setAmount(debitAmount);
        bulkNach.setRefNumber(referenceNo);
        bulkNach.setStatus("STARTED");
        bulkNach.setDebitDate(getCurrenntDate());
        bulkNach.setUserId(userId);
        lendingBulkNachDao.save(bulkNach);
    }

    private Date getCurrenntDate() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        return cal.getTime();
    }
}
