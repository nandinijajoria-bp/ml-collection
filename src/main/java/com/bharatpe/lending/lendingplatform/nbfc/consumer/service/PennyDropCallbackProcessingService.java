package com.bharatpe.lending.lendingplatform.nbfc.consumer.service;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LeadSubStatus;
import com.bharatpe.lending.lendingplatform.nbfc.dto.callback.PennyDropCallback;
import com.bharatpe.lending.lendingplatform.nbfc.dto.response.LenderApiResponse;
import com.bharatpe.lending.lendingplatform.nbfc.enums.PennyDropCallbackStatus;
import com.bharatpe.lending.lendingplatform.nbfc.registry.WorkflowRegistryFactory;
import com.bharatpe.lending.lendingplatform.nbfc.util.WorkflowUtil;
import com.bharatpe.lending.loanV3.utils.NbfcUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Optional;

import static com.bharatpe.lending.common.enums.LenderAssociationStages.ASSC_COMPLETED;
import static com.bharatpe.lending.common.enums.LenderAssociationStatus.PENNY_DROP_FAILED;

@Service
@RequiredArgsConstructor
@Slf4j
public class PennyDropCallbackProcessingService {

    private final LendingApplicationLenderDetailsDao laldDao;
    private final WorkflowRegistryFactory workflowRegistryFactory;
    private final NbfcUtils nbfcUtils;
    private final WorkflowUtil workflowUtil;
    private final ObjectMapper objectMapper;
    private final LendingApplicationDetailsDao lendingApplicationDetailsDao;

    // TODO : Need to improve this class
    public void processPennyDropCallback(String message) {
        LenderApiResponse<PennyDropCallback> pennyDropCallbackResponse = null;
        LendingApplication lendingApplication = null;
        LendingApplicationLenderDetails lald = null;
        try {
            pennyDropCallbackResponse = objectMapper.readValue(message, new TypeReference<LenderApiResponse<PennyDropCallback>>() {
            });
            log.info("Processing PennyDrop callback for applicationId: {}", pennyDropCallbackResponse.getApplicationId());

            if (ObjectUtils.isEmpty(pennyDropCallbackResponse) || ObjectUtils.isEmpty(pennyDropCallbackResponse.getData())) {
                log.warn("Invalid or empty PennyDrop callback received");
                return;
            }

            log.debug("Fetching LendingApplication for applicationId: {}", pennyDropCallbackResponse.getApplicationId());
            lendingApplication = workflowUtil.getLendingApplication(pennyDropCallbackResponse.getApplicationId());

            log.debug("Fetching LendingApplicationLenderDetails for applicationId: {} and lender: {}",
                    lendingApplication.getId(), pennyDropCallbackResponse.getLender());
            lald = workflowUtil.getLendingApplicationLenderDetails(
                    String.valueOf(lendingApplication.getId()), pennyDropCallbackResponse.getLender().toString());


            boolean isPennyDropSuccess = isPennyDropSuccessful(pennyDropCallbackResponse);
            log.info("PennyDrop Callback Status: {} for applicationId: {}",
                    pennyDropCallbackResponse.getData().getStatus(), pennyDropCallbackResponse.getApplicationId());

            if (isPennyDropSuccess) {
                log.info("PennyDrop success for applicationId: {}, proceeding to next workflow stage", pennyDropCallbackResponse.getApplicationId());
                updateLaldForSuccessCallback(pennyDropCallbackResponse, lald);
                updateLad(lendingApplication.getId());

            } else {
                log.warn("PennyDrop failed for applicationId: {}, modifying lender", pennyDropCallbackResponse.getApplicationId());
                lald.setLeadStatus(PENNY_DROP_FAILED.name());
                lald.setLeadSubStatus(LeadSubStatus.FAILED);
                laldDao.save(lald);
                nbfcUtils.modifyLender(lendingApplication, lald, PENNY_DROP_FAILED);
            }
        } catch (Exception e) {
            log.error("Exception in processing PennyDrop callback: {}", e.getMessage(), e);
            log.info("modifying lender !");
            nbfcUtils.modifyLender(lendingApplication, lald, PENNY_DROP_FAILED);
        }
    }

    private boolean isPennyDropSuccessful(LenderApiResponse<PennyDropCallback> pennydropCallback) {
        log.info("Checking if PennyDrop callback is successful for applicationId: {}", pennydropCallback.getApplicationId());
        return !ObjectUtils.isEmpty(pennydropCallback.getData()) &&
                !ObjectUtils.isEmpty(pennydropCallback.getData().getStatus()) &&
                pennydropCallback.getData().getStatus().equalsIgnoreCase(PennyDropCallbackStatus.PENNY_DROP_SUCCESS.getValue());
    }

    private void updateLad(Long id) {
        LendingApplicationDetails lendingApplicationDetails = workflowUtil.getLendingApplicationDetails(String.valueOf(id));
        lendingApplicationDetails.setStage(ASSC_COMPLETED.name());
        lendingApplicationDetailsDao.save(lendingApplicationDetails);
    }

    private void updateLaldForSuccessCallback(LenderApiResponse<PennyDropCallback> pennyDropCallbackResponse, LendingApplicationLenderDetails lald) {
        lald.setLeadSubStatus(LeadSubStatus.SUCCESS);
        lald.setStage(ASSC_COMPLETED.name());
        Optional.ofNullable(pennyDropCallbackResponse.getData().getClientId())
                .ifPresent(lald::setCccId);
        laldDao.save(lald);
    }

}