package com.bharatpe.lending.loanV3.services.associationsV2.piramal.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.Handler.EnachHandler;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.common.service.merchant.constants.Constants;
import com.bharatpe.lending.common.service.merchant.dto.BankDetailsDto;
import com.bharatpe.lending.common.service.merchant.dto.MerchantDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.loanV3.dto.CKycResponseDto;
import com.bharatpe.lending.loanV3.dto.piramal.*;
import com.bharatpe.lending.loanV3.factory.LenderAssociationStageFactory;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.gateway.piramal.ILenderGateway;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import com.bharatpe.lending.loanV3.utils.NbfcUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.transaction.Transactional;
import java.util.*;

@Service
@Slf4j
public class PiramalPennyDropServiceV2 {
    @Autowired
    private LendingApplicationDetailsDao lendingApplicationDetailsDao;

    @Autowired
    private EnachHandler enachHandler;

    @Autowired
    private ILenderGateway lenderGateway;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NbfcUtils nbfcUtils;

    @Autowired
    private LendingApplicationDao lendingApplicationDao;

    @Autowired
    private LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    private KycUtils kycUtils;

    @Autowired
    private MerchantService merchantService;

    @Value("${lender.change.enabled:false}")
    private Boolean enableLenderChange;

    @Autowired
    private CommonService commonService;

    @Async
    @Transactional
    public void invokePennyDrop(Map<String,String> request) {

        Optional<LendingApplication> lendingApplication = Optional.empty();
        LendingApplicationLenderDetails lendingApplicationLenderDetails = null;

        lendingApplication = lendingApplicationDao.findById(Long.valueOf(request.get("application_id")));
        if (!lendingApplication.isPresent()) {
            log.info("no application found for id {}", request.get("application_id"));
            return;
        }
        lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(lendingApplication.get().getId(), Status.ACTIVE.name(), Lender.PIRAMAL.name());

        LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto = LenderAssociationDetailsRequestDto.builder()
                .applicationId(lendingApplication.get().getId())
                .merchantId(lendingApplication.get().getMerchantId())
                .lendingApplication(lendingApplication.get())
                .lendingApplicationLenderDetails(lendingApplicationLenderDetails)
                .modifyLender(enableLenderChange)
                .manageState(Boolean.TRUE)
                .build();

        try {
            MDC.put("requestId", UUID.randomUUID().toString());
            log.info("Received pennydrop request:{}", request);

            NbfcRequestDto nbfcRequestDto = getPayload(lendingApplication, lendingApplicationLenderDetails);
            if (Objects.isNull(nbfcRequestDto)) {
                log.info("error in penny drop payload for applicationId: {}", lendingApplication.get().getId());
                return;
            }

            NbfcResponseDto nbfcResponseDto = lenderGateway.invokeStage(nbfcRequestDto, LenderAssociationStages.PiramalAssociationStages.PENNY_DROP);
            log.info("pennyDrop response from nbfc: {} with applicationId: {}", nbfcResponseDto, request.get("application_id"));
            if (Objects.nonNull(nbfcResponseDto) && nbfcResponseDto.getSuccess() && Objects.nonNull(nbfcResponseDto.getData())) {
                PiramalPennyDropResponseDTO piramalPennyDropResponseDTO = objectMapper.readValue( objectMapper.writeValueAsString(nbfcResponseDto.getData()), PiramalPennyDropResponseDTO.class);
                if(!ObjectUtils.isEmpty(piramalPennyDropResponseDTO) && "APPROVED".equalsIgnoreCase(piramalPennyDropResponseDTO.getStatus())) {
                    log.info("successfully placed the penny drop request at lender for {}", request.get("application_id"));
                    lendingApplicationLenderDetails.setPennyDropStatus(LenderAssociationStatus.PENNY_DROP_SUCCESS.name());

                    String currStage =  lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().getStage();
                    LenderAssociationStages nextStage =
                            LenderAssociationStageFactory.getNextStage(Lender.valueOf(lenderAssociationDetailsRequestDto.getLendingApplication().getLender()),
                                    LenderAssociationStages.valueOf(lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().getStage()));
                    log.info("curr stage: {} and next stage: {} in risk async service for applicationId: {}",currStage, nextStage, lendingApplication.get().getId());
                    lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setStage(nextStage.name());

                    lendingApplicationLenderDetails.setStage(nextStage.name());
                    commonService.manageApplicationState(lenderAssociationDetailsRequestDto);

                    log.info("Pushing application to next stage after successful penny drop for applicationId: {}", lendingApplication.get().getId());

                    nbfcUtils.pushApplicationToNextStage(lenderAssociationDetailsRequestDto.getApplicationId(),
                            lenderAssociationDetailsRequestDto.getLendingApplication().getLender(),
                            currStage,
                            LenderAssociationStageFactory.autoInvokeNextStage(Lender.valueOf(lenderAssociationDetailsRequestDto.getLendingApplication().getLender()), LenderAssociationStages.valueOf(currStage)));
                    return;
                }
            }
        } catch (Exception ex) {
            log.error("exception occurred while processing pennydrop request {} {}", ex.getMessage(), Arrays.asList(ex.getStackTrace()));
        }
        lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setPennyDropStatus(LenderAssociationStatus.PENNY_DROP_FAILED.name());
        commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequestDto, LenderAssociationStatus.PENNY_DROP_FAILED);
    }

    private NbfcRequestDto getPayload(Optional<LendingApplication> lendingApplication, LendingApplicationLenderDetails lendingApplicationLenderDetails) {
        try {
            log.info("creating piramal bank details verification payload for applicationId {}", lendingApplication.get().getId());
            MerchantDetailsDto merchantDetailsDto = merchantService.fetchMerchantDetails(lendingApplication.get().getMerchantId(), Arrays.asList(
                    Constants.MerchantUtil.Scope.BANK_DETAIL
            ));

            BankDetailsDto merchantBankDetail = merchantDetailsDto.getBankDetail();

            NbfcRequestDto nbfcRequestDto = NbfcRequestDto.builder()
                    .applicationId(lendingApplication.get().getId())
                    .lender(lendingApplication.get().getLender())
                    .productName("LENDING")
                    .topup(LoanType.TOPUP.name().equals(lendingApplication.get().getLoanType()))
                    .payload(PiramalPennyDropRequestDTO.builder()
                            .leadId(lendingApplicationLenderDetails.getLeadId())
                            .ifsc(merchantBankDetail.getIfsc())
                            .accountNumber(merchantBankDetail.getAccountNumber())
                            .accountType("CURRENT".equalsIgnoreCase(merchantBankDetail.getAccountType())
                                    ? merchantBankDetail.getAccountType() : getBankAccountType(lendingApplication.get().getMerchantId()))
                            .productId(LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.get().getLoanType()) ? "BPETU" : "BRTPE")
                            .build())
                    .build();
            return nbfcRequestDto;
        } catch (Exception e) {
            log.info("Exception in creating piramal bank details verification payload for applicationId {} {}", lendingApplication.get().getId(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    private String getBankAccountType(Long merchantId) {
        log.info("fetching kyc data for bene name match percentage for merchantId {}", merchantId);
        try {
            CKycResponseDto cKycResponseDto = kycUtils.getKycData(merchantId);
            if (!ObjectUtils.isEmpty(cKycResponseDto) && !ObjectUtils.isEmpty(cKycResponseDto.getBankBenePanNameMatchPer())) {
                if (cKycResponseDto.getBankBenePanNameMatchPer() < 0.6) {
                    log.info("bene name match percentage is less than 60% for merchantId {} bene name {}", merchantId, cKycResponseDto.getBankBenePanNameMatchPer());
                    return "CURRENT";
                } else {
                    log.info("bene name match percentage is greater than 60% for merchantId {} bene name {}", merchantId, cKycResponseDto.getBankBenePanNameMatchPer());
                    return "SAVINGS";
                }
             }
        } catch (Exception e) {
            log.error("Exception in fetching kyc data for merchantId {}", merchantId, e);
        }
        return "SAVINGS";
    }
}
