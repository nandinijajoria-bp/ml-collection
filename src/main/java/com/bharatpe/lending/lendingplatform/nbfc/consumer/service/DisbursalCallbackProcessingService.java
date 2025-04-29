package com.bharatpe.lending.lendingplatform.nbfc.consumer.service;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LeadSubStatus;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dto.PostPayoutRequestDto;
import com.bharatpe.lending.dto.PostPayoutResponseDto;
import com.bharatpe.lending.lendingplatform.nbfc.dto.callback.DisbursalCallback;
import com.bharatpe.lending.lendingplatform.nbfc.dto.response.LenderApiResponse;
import com.bharatpe.lending.lendingplatform.nbfc.util.WorkflowUtil;
import com.bharatpe.lending.loanV3.dto.DisbursalCallbackCommonDTO;
import com.bharatpe.lending.service.LiquiloansService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@Service
public class DisbursalCallbackProcessingService {

    private final LendingApplicationDao lendingApplicationDao;
    private final LendingApplicationLenderDetailsDao laldDao;
    private final LendingApplicationDetailsDao lendingApplicationDetailsDao;
    private final WorkflowUtil workflowUtil;
    private final ObjectMapper objectMapper;
    private final LiquiloansService liquiloansService;

    @Transactional
    public void processDisbursalCallback(String message) {
        LenderApiResponse<DisbursalCallback> disbursalCallbackResponse = null;
        try {
            disbursalCallbackResponse = objectMapper.readValue(message, new TypeReference<LenderApiResponse<DisbursalCallback>>() {
            });
            log.info("Processing Disbursal callback for applicationId: {}", disbursalCallbackResponse.getApplicationId());

            if (ObjectUtils.isEmpty(disbursalCallbackResponse) || ObjectUtils.isEmpty(disbursalCallbackResponse.getData())) {
                log.warn("Invalid or empty disbursal callback received");
                return;
            }

            log.debug("Fetching LendingApplication for applicationId: {}", disbursalCallbackResponse.getApplicationId());
            LendingApplication lendingApplication = workflowUtil.getLendingApplication(disbursalCallbackResponse.getApplicationId());

            log.debug("Fetching LendingApplicationLenderDetails for applicationId: {} and lender: {}",
                    lendingApplication.getId(), disbursalCallbackResponse.getLender());
            LendingApplicationLenderDetails lald = workflowUtil.getLendingApplicationLenderDetails(
                    String.valueOf(lendingApplication.getId()), disbursalCallbackResponse.getLender().toString());

            DisbursalCallbackCommonDTO disbursalCallbackDto = DisbursalCallbackCommonDTO.builder()
                    .applicationId(Long.parseLong(disbursalCallbackResponse.getApplicationId()))
                    .leadId(lald.getLeadId())
                    .lender(String.valueOf(disbursalCallbackResponse.getLender()))
                    .disbursalDate(disbursalCallbackResponse.getData().getDisbursalDate())
                    .disbursalAmount(Optional.ofNullable(disbursalCallbackResponse.getData().getDisbursalAmount())
                            .map(BigDecimal::doubleValue)
                            .orElse(lendingApplication.getDisbursalAmount()))
                    .utr(disbursalCallbackResponse.getData().getUtr())
                    .lan(disbursalCallbackResponse.getData().getLoanAccountNumber())
                    .status(disbursalCallbackResponse.getData().isStatus())
                    .build();

            log.info("Disbursal callback received: {}", disbursalCallbackDto);

            if (!lald.getLeadId().equalsIgnoreCase(disbursalCallbackDto.getLeadId())) {
                log.warn("Lead ID mismatch for applicationId: {}, expected: {}, received: {}",
                        disbursalCallbackResponse.getApplicationId(), lald.getLeadId(), disbursalCallbackDto.getLeadId());
                return;
            }

            if (LenderAssociationStatus.DRAWDOWN_COMPLETED.name().equals(lald.getDrawDownStatus())) {
                log.info("Disbursal callback already processed for applicationId: {}", disbursalCallbackResponse.getApplicationId());
                return;
            }

            updateLANIfMissingInLendingApplication(disbursalCallbackDto.getLan(), lendingApplication);

            log.debug("Fetching LendingApplicationDetails for applicationId: {}", lendingApplication.getId());
            LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(lendingApplication.getId());

            if (!LenderAssociationStages.COMPLETED.name().equalsIgnoreCase(lendingApplicationDetails.getStage())) {
                log.info("Updating LendingApplicationDetails stage to COMPLETED for applicationId: {}", lendingApplication.getId());
                lendingApplicationDetails.setStage(LenderAssociationStages.COMPLETED.name());
                lald.setStage(LenderAssociationStages.COMPLETED.name());
                laldDao.save(lald);
                lendingApplicationDetailsDao.save(lendingApplicationDetails);
            }

            if (Boolean.TRUE.equals(disbursalCallbackDto.getStatus())) {
                handleSuccessCallback(disbursalCallbackResponse, disbursalCallbackDto, lald, lendingApplication);
                log.info("Disbursal callback processed successfully for applicationId: {}", disbursalCallbackResponse.getApplicationId());
            } else {
                log.info("Marking disbursal status as failed for applicationId: {}", disbursalCallbackResponse.getApplicationId());
                lald.setLan(disbursalCallbackDto.getLan());
                lald.setDrawDownStatus(LenderAssociationStatus.DRAWDOWN_HARD_FAILED.name());
                lald.setLeadSubStatus(LeadSubStatus.FAILED);
                laldDao.save(lald);
                lendingApplication.setLoanDisbursalStatus("FAILED");
                lendingApplicationDao.save(lendingApplication);
            }
        } catch (Exception e) {
            log.error("Exception while processing disbursal callback: {}", e.getMessage(), e);
        }
    }

    private void updateLANIfMissingInLendingApplication(String loanAccountNumber, LendingApplication lendingApplication) {
        if (ObjectUtils.isEmpty(lendingApplication.getNbfcId())) {
            log.info("Updating missing NBFC ID for applicationId: {}, LAN: {}", lendingApplication.getId(), loanAccountNumber);
            lendingApplication.setNbfcId(loanAccountNumber);
            lendingApplication.setSendToNbfc("YES");
            lendingApplication.setNbfcSendDate(
                    ObjectUtils.isEmpty(lendingApplication.getNbfcSendDate()) ? Calendar.getInstance().getTime() : lendingApplication.getNbfcSendDate()
            );
            lendingApplicationDao.save(lendingApplication);
        }
    }

    private void handleSuccessCallback(LenderApiResponse<DisbursalCallback> disbursalCallbackResponse,
                                       DisbursalCallbackCommonDTO disbursalCallbackDto,
                                       LendingApplicationLenderDetails lald,
                                       LendingApplication lendingApplication) {
        Date disbursalDate = Optional.ofNullable(disbursalCallbackDto.getDisbursalDate()).orElse(new Date());
        PostPayoutResponseDto postPayoutResponseDto =  liquiloansService.populatePostPayoutSchedule(
                PostPayoutRequestDto.builder()
                        .applicationId(String.valueOf(lendingApplication.getExternalLoanId()))
                        .disbursalDate(disbursalDate)
                        .lender(String.valueOf(disbursalCallbackResponse.getLender()))
                        .loanDisbursalStatus("DISBURSED")
                        .nbfcId(lendingApplication.getNbfcId())
                        .disbursedAmount(Optional.ofNullable(disbursalCallbackResponse.getData().getDisbursalAmount())
                                .map(BigDecimal::doubleValue)
                                .orElse(lendingApplication.getDisbursalAmount()))
                        .utr(disbursalCallbackResponse.getData().getUtr())
                        .build()).getBody();

        if (Objects.isNull(postPayoutResponseDto) || !"SUCCESS".equalsIgnoreCase(postPayoutResponseDto.getStatus())) {
            log.info("failed to process loan event of {} for {}", disbursalCallbackResponse.getLender(), disbursalCallbackResponse.getApplicationId());
            return;
        }

        lald.setUtrNo(disbursalCallbackDto.getUtr());
        lald.setLan(disbursalCallbackDto.getLan());
        lald.setLoanCreationTimestamp(disbursalDate);
        lald.setDrawDownStatus(LenderAssociationStatus.DRAWDOWN_COMPLETED.name());
        lald.setLeadSubStatus(LeadSubStatus.SUCCESS);
        laldDao.save(lald);
        log.info("Loan disbursed successfully for applicationId: {} at timestamp: {}",
                disbursalCallbackResponse.getApplicationId(), disbursalDate);
    }
}