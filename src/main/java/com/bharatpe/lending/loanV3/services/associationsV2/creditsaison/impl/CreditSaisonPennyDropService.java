package com.bharatpe.lending.loanV3.services.associationsV2.creditsaison.impl;


import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.common.service.merchant.dto.BankDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.loanV3.config.CreditSaisonConfig;
import com.bharatpe.lending.loanV3.dto.*;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.dto.request.creditsasion.CreditSasionPennyDropRequestDTO;
import com.bharatpe.lending.loanV3.dto.response.creditsasion.CreditSaisonCallbackResponseDTO;
import com.bharatpe.lending.loanV3.dto.response.creditsasion.CreditSasionCallbackResponseStatuses;
import com.bharatpe.lending.loanV3.dto.response.creditsasion.CreditSasionPennyDropResponseDTO;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.*;

@Slf4j
@Service
public class CreditSaisonPennyDropService  {

    @Autowired
    CreditSaisonConfig csConfig;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    CommonService commonService;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    MerchantService merchantService;

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Value("${lender.change.enabled:false}")
    Boolean enableLenderChange;


    public Boolean invokePennyDrop(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto) {

        LendingApplication lendingApplication = lenderAssociationDetailsDto.getLendingApplication();

        LendingApplicationLenderDetails lendingApplicationLenderDetails = lenderAssociationDetailsDto.getLendingApplicationLenderDetails();

        Optional<BankDetailsDto> merchantBankDetails = merchantService.fetchMerchantBankDetails(lendingApplication.getMerchantId());

        if(!merchantBankDetails.isPresent()){
            log.info("CS: merchantBankDetails is empty returning false for application {} for lender {}", lendingApplication.getId(), lendingApplication.getLender());
            return false;
        }

        Boolean isAccountSame =  merchantBankDetails.get().getAccountNumber().equalsIgnoreCase(lendingApplicationLenderDetails.getPennyDropAccountNumber());

        try {
            MDC.put("requestId", UUID.randomUUID().toString());
            log.info("CS: Received pennydrop request for creditsaison applicationId: {}", lendingApplication.getId());

            NBFCRequestDTO nbfcRequestDTO = createPayload(lendingApplication, merchantBankDetails.get(), isAccountSame);

            if(!ObjectUtils.isEmpty(lendingApplicationLenderDetails) && !nbfcRequestDTO.getLender().equalsIgnoreCase(lendingApplicationLenderDetails.getLender())){
                log.info("CS: lender mismatch while initiating pennydrop for application {}", nbfcRequestDTO.getApplicationId());
                return false;
            } else if(!ObjectUtils.isEmpty(lendingApplicationLenderDetails) && LenderAssociationStages.BRE.name().equalsIgnoreCase(lendingApplicationLenderDetails.getStage())){
                log.info("CS: stage is BRE for CredistSaison so moving forward and initiating pennydrop before agreement for application {}", nbfcRequestDTO.getApplicationId());
            } else if(!ObjectUtils.isEmpty(lendingApplicationLenderDetails) && !ObjectUtils.isEmpty(merchantBankDetails) && !isAccountSame){
                log.info("CS: stage is SanctionWrapper and Account number is changed for CredistSaison so moving forward and initiating pennydrop after verifyOTP for application {}", nbfcRequestDTO.getApplicationId());
            } else if (!ObjectUtils.isEmpty(lendingApplicationLenderDetails) && !ObjectUtils.isEmpty(merchantBankDetails) && isAccountSame) {
                log.info("CS: Account number is same as previous pennydrop so returning true for application {}", nbfcRequestDTO.getApplicationId());
                commonService.manageApplicationStateAndPushToNextStage(lenderAssociationDetailsDto);
                return true;
            } else if (!ObjectUtils.isEmpty(lendingApplicationLenderDetails) && !LenderAssociationStages.PENNY_DROP.name().equalsIgnoreCase(lendingApplicationLenderDetails.getStage())) {
                log.info("CS: lender or stage mismatch while initiating pennydrop for application {}", nbfcRequestDTO.getApplicationId());
                return false;
            }
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStages.PENNY_DROP.name());
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setPennyDropStatus(LenderAssociationStatus.PENNY_DROP_PENDING.name());
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setPennyDropAccountNumber(merchantBankDetails.get().getAccountNumber());
            commonService.manageApplicationState(lenderAssociationDetailsDto);

            NBFCResponseDTO nbfcResponseDTO = lenderAPIGateway.invokeStage(nbfcRequestDTO, LenderAssociationStages.PENNY_DROP);

            CreditSasionPennyDropResponseDTO creditSaisonCallbackResponseDTO = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDTO.getData()), CreditSasionPennyDropResponseDTO.class);

            if (!ObjectUtils.isEmpty(nbfcResponseDTO) && nbfcResponseDTO.getSuccess()
                    && Arrays.asList(csConfig.getPennyDropSyncAlreadyValidatedStatus().toLowerCase(), csConfig.getPennyDropSyncInProgressStatus().toLowerCase()).contains(creditSaisonCallbackResponseDTO.getMessage().toLowerCase())
            ) {
                log.info("CS: successfully placed the penny drop request at lender for {}", nbfcRequestDTO);
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setPennyDropStatus(LenderAssociationStatus.PENNY_DROP_IN_PROGRESS.name());
                commonService.manageApplicationState(lenderAssociationDetailsDto);
                return true;
            } else{
                if(ObjectUtils.isEmpty(lenderAssociationDetailsDto.getLendingApplication().getAgreementAt())){
                    log.info("CS: agreement at is NULL");
                    lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setPennyDropStatus(LenderAssociationStatus.PENNY_DROP_FAILED.name());
                    lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.PENNY_DROP_FAILED.name());
                    lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setPennyDropAccountNumber(null);
                    lenderAssociationDetailsDto.setManageState(true);
                    lenderAssociationDetailsDto.setModifyLender(true);
                    commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, LenderAssociationStatus.PENNY_DROP_FAILED);
                } else {
                    rejectApplication(lendingApplication, lendingApplicationLenderDetails, LenderAssociationStatus.PENNY_DROP_FAILED.name());
                }
            }

        } catch (Exception ex) {
            log.error("CS: exception occurred while processing lender pennydrop request for applicationId: {} exception is: {}", lendingApplication.getId(), ex);
            rejectApplication(lendingApplication, lendingApplicationLenderDetails, LenderAssociationStatus.PENNY_DROP_FAILED.name());
        }
        return false;
    }

    public NBFCRequestDTO createPayload(LendingApplication lendingApplication, BankDetailsDto bankDetailsDto, Boolean isAccountSame) {
        try {
            if (ObjectUtils.isEmpty(lendingApplication)) {
                log.error("CS: application not found !! {}", lendingApplication.getId());
            }

            if (ObjectUtils.isEmpty(bankDetailsDto)) {
                throw new RuntimeException("merchant bank details not found for application");
            }

            LinkedHashMap<String, Object> identifiers = new LinkedHashMap<>();
            identifiers.put("partnerLoanId", lendingApplication.getExternalLoanId());

            NBFCRequestDTO pennyDropRequestDTO = NBFCRequestDTO.builder()
                    .applicationId(lendingApplication.getId())
                    .lender(lendingApplication.getLender())
                    .productName(csConfig.getLendingProduct())
                    .identifier(identifiers)
                    .payload(CreditSasionPennyDropRequestDTO.builder()
                            .bankAccounts(Arrays.asList(CreditSasionPennyDropRequestDTO.BankAccount.builder()
                                    .type(csConfig.getBankTypeCurrent().equalsIgnoreCase(bankDetailsDto.getAccountType()) ? csConfig.getBankTypeCurrent() : csConfig.getBankTypeSaving())
                                    .bankName(bankDetailsDto.getBankName())
                                    .holderName(bankDetailsDto.getBeneficiaryName())
                                    .ifscCode(bankDetailsDto.getIfsc())
                                    .accountNumber(bankDetailsDto.getAccountNumber())
                                    .build()))
                            .partnerLoanId(lendingApplication.getExternalLoanId())
                            .loanProductCode(csConfig.getLoanProduct())
                            .isRetry(isAccountSame)
                            .build())
                    .build();
            log.info("CS: creditSaison pennydrop payload {}", pennyDropRequestDTO);
            return pennyDropRequestDTO;
        } catch (Exception e) {
            log.error("CS: exception occurred while initiating pennyDropRequestDTO workflow for  {}", lendingApplication.getId(), e);
        }
        return null;
    }

    public boolean processCallback(NBFCResponseDTO nbfcResponseDTO){
        try {
            LendingApplication lendingApplication = lendingApplicationDao.findById(Long.valueOf(nbfcResponseDTO.getApplicationId())).orElse(null);
            if(ObjectUtils.isEmpty(lendingApplication)) {
                log.info("CS: No application found for applicationId : {}", nbfcResponseDTO.getApplicationId());
                return false;
            }
            LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(lendingApplication.getId(), Status.ACTIVE.name(), lendingApplication.getLender());
            if(ObjectUtils.isEmpty(lendingApplicationLenderDetails) || !nbfcResponseDTO.getLender().equalsIgnoreCase(lendingApplicationLenderDetails.getLender())) {
                log.info("CS: No LendingApplicationLenderDetails found with lender {} for applicationId {}", lendingApplication.getLender(), lendingApplication.getId());
                return false;
            }
            if(!LenderAssociationStatus.PENNY_DROP_IN_PROGRESS.name().equalsIgnoreCase(lendingApplicationLenderDetails.getPennyDropStatus())) {
                log.info("CS: pennydrop status of {} application is not in progress for applicationId {}", lendingApplication.getLender(), lendingApplication.getId());
                return false;
            }
            LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest = LenderAssociationDetailsRequestDto.builder()
                    .applicationId(lendingApplication.getId())
                    .lendingApplication(lendingApplication)
                    .lendingApplicationLenderDetails(lendingApplicationLenderDetails)
                    .modifyLender(enableLenderChange)
                    .manageState(true)
                    .build();
            if (nbfcResponseDTO.getSuccess() && Objects.nonNull(nbfcResponseDTO.getData())) {
                CreditSaisonCallbackResponseDTO creditSaisonCallbackResponseDTO = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDTO.getData()), CreditSaisonCallbackResponseDTO.class);
                log.info("CS: pennydrop callback Response of CreditSaison for {} {}", nbfcResponseDTO.getApplicationId(), creditSaisonCallbackResponseDTO);
                if (!ObjectUtils.isEmpty(creditSaisonCallbackResponseDTO) && CreditSasionCallbackResponseStatuses.PENNYDROP_SUCCESS.getStatusCode().equalsIgnoreCase(creditSaisonCallbackResponseDTO.getStatus())) {
                    lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setPennyDropStatus(LenderAssociationStatus.PENNY_DROP_SUCCESS.name());
                    commonService.manageApplicationStateAndPushToNextStage(lenderAssociationDetailsRequest);
                    return true;
                } else {
                    if (ObjectUtils.isEmpty(lendingApplication.getAgreementAt())) {
                        lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setPennyDropStatus(LenderAssociationStatus.PENNY_DROP_FAILED.name());
                        lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.PENNY_DROP_FAILED.name());
                        commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequest, LenderAssociationStatus.PENNY_DROP_FAILED);
                        return false;
                    } else {
                        rejectApplication(lendingApplication, lendingApplicationLenderDetails, LenderAssociationStatus.PENNY_DROP_FAILED.name());
                        return false;
                    }
                }
            }
            if (ObjectUtils.isEmpty(lendingApplication.getAgreementAt()) && ObjectUtils.isEmpty(nbfcResponseDTO.getSuccess())) {
                lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setPennyDropStatus(LenderAssociationStatus.PENNY_DROP_FAILED.name());
                lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.PENNY_DROP_FAILED.name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequest, LenderAssociationStatus.PENNY_DROP_FAILED);
                return false;
            } else {
                rejectApplication(lendingApplication, lendingApplicationLenderDetails, LenderAssociationStatus.PENNY_DROP_FAILED.name());
                return false;
            }
        } catch (Exception e) {
            log.error("CS: exception while processing bre callback of CreditSaison for {} {} {}", nbfcResponseDTO.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return false;
    }

    private void rejectApplication(LendingApplication lendingApplication, LendingApplicationLenderDetails lendingApplicationLenderDetails, String status) {
        log.info("CS: rejecting application as pennydrop stage failed of creditSaison for {}", lendingApplication.getId());
        if(!ObjectUtils.isEmpty(lendingApplication)) {
            log.info("CS: lending_application not empty setting status to REJECTED");
            lendingApplication.setStatus("rejected");
            lendingApplicationDao.save(lendingApplication);
        } else {
            log.info("CS: lending application is empty while rejecting application for creditsaison");
        }
        if(!ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
            log.info("CS: lending_application_lender_details not empty setting status to INACTIVE");
            lendingApplicationLenderDetails.setPennyDropStatus(status);
            lendingApplicationLenderDetails.setStatus(Status.INACTIVE.name());
            lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);
        } else {
            log.info("CS: lending_application_lender_details is empty while rejecting application for creditsaison");
        }
    }

}