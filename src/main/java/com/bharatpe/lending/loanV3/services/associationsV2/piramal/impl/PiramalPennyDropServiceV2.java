package com.bharatpe.lending.loanV3.services.associationsV2.piramal.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.Handler.EnachHandler;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.dto.MerchantNachDetailsResponseDTO;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.loanV3.dto.piramal.*;
import com.bharatpe.lending.loanV3.factory.LenderAssociationStageFactory;
import com.bharatpe.lending.loanV3.services.gateway.piramal.ILenderGateway;
import com.bharatpe.lending.loanV3.utils.NbfcUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Async
    @Transactional
    public void invokePennyDrop(Map<String,String> request) {

        Optional<LendingApplication> lendingApplication = Optional.empty();
        LendingApplicationLenderDetails lendingApplicationLenderDetails = null;
        try {

            MDC.put("requestId", UUID.randomUUID().toString());
            log.info("Received pennydrop request:{}", request);

            lendingApplication = lendingApplicationDao.findById(Long.valueOf(request.get("application_id")));
            if (!lendingApplication.isPresent()) {
                log.info("no application found for id {}", request.get("application_id"));
                return;
            }
            lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(lendingApplication.get().getId(), Status.ACTIVE.name(), Lender.PIRAMAL.name());


            NbfcRequestDto nbfcRequestDto = getPayload(lendingApplication, lendingApplicationLenderDetails);
            if (Objects.isNull(nbfcRequestDto)) {
                log.info("error in penny drop payload for applicationId: {}", lendingApplication.get().getId());
                return;
            }

            lendingApplicationLenderDetails.setPennyDropStatus(LenderAssociationStatus.PENNY_DROP_IN_PROGRESS.name());

            NbfcResponseDto nbfcResponseDto = lenderGateway.invokeStage(nbfcRequestDto, LenderAssociationStages.PiramalAssociationStages.PENNY_DROP);
            log.info("pennyDrop response from nbfc: {} with applicationId: {}", nbfcResponseDto, request.get("application_id"));
            if (Objects.nonNull(nbfcResponseDto) && nbfcResponseDto.getSuccess() && Objects.nonNull(nbfcResponseDto.getData())) {
                PiramalPennyDropResponseDTO piramalPennyDropResponseDTO = objectMapper.readValue( objectMapper.writeValueAsString(nbfcResponseDto.getData()), PiramalPennyDropResponseDTO.class);
                if(!ObjectUtils.isEmpty(piramalPennyDropResponseDTO) && "APPROVED".equalsIgnoreCase(piramalPennyDropResponseDTO.getStatus())) {
                    log.info("successfully placed the penny drop request at lender for {}", request.get("application_id"));
                    lendingApplicationLenderDetails.setPennyDropStatus(LenderAssociationStatus.PENNY_DROP_SUCCESS.name());
                    LenderAssociationStages nextStage = LenderAssociationStageFactory.getNextStage(Lender.PIRAMAL,LenderAssociationStages.PENNY_DROP);
                    lendingApplicationLenderDetails.setStage(nextStage.name());
                    lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);

                    log.info("Pushing application to next stage after successful penny drop for applicationId: {}", lendingApplication.get().getId());
                    nbfcUtils.pushApplicationToNextStage(lendingApplication.get().getId(),
                            lendingApplication.get().getLender(), LenderAssociationStages.PENNY_DROP.name(),
                            LenderAssociationStageFactory.autoInvokeNextStage(Lender.valueOf(lendingApplication.get().getLender()), LenderAssociationStages.PENNY_DROP));
                } else if(!ObjectUtils.isEmpty(piramalPennyDropResponseDTO) && ObjectUtils.isEmpty(piramalPennyDropResponseDTO.getStatus()) && !ObjectUtils.isEmpty(piramalPennyDropResponseDTO.getErrorDescription())) {
                    //reject application
                    log.info("rejecting the application as penny drop failed at lender for {} with reason {}",lendingApplication.get(), piramalPennyDropResponseDTO.getErrorDescription());
                    rejectApplication(lendingApplication, lendingApplicationLenderDetails, LenderAssociationStatus.PENNY_DROP_FAILED.name());
                }
            }

        } catch (Exception ex) {
            log.error("exception occurred while processing pennydrop request {} {}", ex.getMessage(), Arrays.asList(ex.getStackTrace()));
        }
    }

    private NbfcRequestDto getPayload(Optional<LendingApplication> lendingApplication, LendingApplicationLenderDetails lendingApplicationLenderDetails) {
        try {
            LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(lendingApplication.get().getId());
            Long ownerId = Boolean.TRUE.equals(lendingApplicationDetails.getIsNachSkip()) ? null : lendingApplication.get().getId();

            log.info("creating piramal penny drop payload for applicationId {} and ownerId {}", lendingApplication.get().getId(), ownerId);
            MerchantNachDetailsResponseDTO merchantNachDetailsResponseDTO = enachHandler.findByMerchantIdAndApplicationIdAndLender(lendingApplication.get().getMerchantId(), ownerId, lendingApplication.get().getLender());
            NbfcRequestDto nbfcRequestDto = NbfcRequestDto.builder()
                    .applicationId(lendingApplication.get().getId())
                    .lender(lendingApplication.get().getLender())
                    .productName("LENDING")
                    .topup(LoanType.TOPUP.name().equals(lendingApplication.get().getLoanType()))
                    .payload(PiramalPennyDropRequestDTO.builder()
                            .leadId(lendingApplicationLenderDetails.getLeadId())
                            .ifsc(merchantNachDetailsResponseDTO.getIfscCode())
                            .accountNumber(merchantNachDetailsResponseDTO.getAccountNumber())
                            .bankAccountType(merchantNachDetailsResponseDTO.getAccountType())
                            .productId(LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.get().getLoanType()) ? "BPETU" : "BRTPE")
                            .build())
                    .build();
            return nbfcRequestDto;
        } catch (Exception e) {
            log.info("Exception in creating piramal bank details verification payload for applicationId {} {}", lendingApplication.get().getId(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    private void rejectApplication(Optional<LendingApplication> lendingApplication, LendingApplicationLenderDetails lendingApplicationLenderDetails, String status) {
        log.info("rejecting application as pennydrop stage failed of piramal for {}", lendingApplication.get().getId());
        if(!ObjectUtils.isEmpty(lendingApplication)) {
            log.info("lending_application not empty setting status to REJECTED for applicationId {}", lendingApplication.get().getId());
            lendingApplication.get().setStatus("rejected");
            lendingApplicationDao.save(lendingApplication.get());
        } else {
            log.info("lending application is empty for applicationId {}", lendingApplication.get().getId());
        }
        if(!ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
            log.info("lending_application_lender_details not empty setting status to INACTIVE for applicationId {}", lendingApplication.get().getId());
            lendingApplicationLenderDetails.setPennyDropStatus(status);
            lendingApplicationLenderDetails.setStatus(Status.INACTIVE.name());
            lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);
        } else {
            log.info("lending_application_lender_details is empty for applicationId {}", lendingApplication.get().getId());
        }
    }
}
