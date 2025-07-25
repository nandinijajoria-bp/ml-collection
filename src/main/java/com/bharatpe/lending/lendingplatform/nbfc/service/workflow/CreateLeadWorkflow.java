package com.bharatpe.lending.lendingplatform.nbfc.service.workflow;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LeadSubStatus;
import com.bharatpe.lending.common.enums.LenderOffDays;
import com.bharatpe.lending.lendingplatform.nbfc.client.LendingPlatformClient;
import com.bharatpe.lending.lendingplatform.nbfc.dto.request.CreateLeadRequest;
import com.bharatpe.lending.lendingplatform.nbfc.dto.request.LenderBaseRequest;
import com.bharatpe.lending.lendingplatform.nbfc.dto.response.CreateLeadResponse;
import com.bharatpe.lending.lendingplatform.nbfc.dto.response.LenderApiResponse;
import com.bharatpe.lending.lendingplatform.nbfc.enums.Lender;
import com.bharatpe.lending.lendingplatform.nbfc.registry.WorkflowRegistry;
import com.bharatpe.lending.lendingplatform.nbfc.registry.WorkflowRegistryFactory;
import com.bharatpe.lending.lendingplatform.nbfc.service.builder.request.CreateLeadRequestBuilder;
import com.bharatpe.lending.lendingplatform.nbfc.service.database.LendingApplicationDetailsService;
import com.bharatpe.lending.lendingplatform.nbfc.service.database.LendingApplicationLenderDetailsService;
import com.bharatpe.lending.lendingplatform.nbfc.util.WorkflowUtil;
import com.bharatpe.lending.loanV2.service.LendingApplicationServiceV2;
import com.bharatpe.lending.loanV3.utils.NbfcUtils;
import com.bharatpe.lending.util.EdiUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.Arrays;

import static com.bharatpe.lending.common.enums.LenderAssociationStatus.LEAD_CREATION_FAILED;
import static com.bharatpe.lending.common.enums.LenderAssociationStatus.RISK_FAILED;
import static com.bharatpe.lending.common.enums.Status.ACTIVE;
import static com.bharatpe.lending.lendingplatform.nbfc.constants.WorkflowName.CREATE_LEAD_WORKFLOW;
import static com.bharatpe.lending.lendingplatform.nbfc.enums.LeadStatus.CREATE_LEAD;
import static com.bharatpe.lending.lendingplatform.nbfc.enums.LeadStatus.KYC;

@Service
@Slf4j
@RequiredArgsConstructor
public class CreateLeadWorkflow implements Workflow {
    private final LendingPlatformClient lendingPlatformClient;
    private final CreateLeadRequestBuilder createLeadRequestBuilder;
    private final LendingApplicationLenderDetailsService lendingApplicationLenderDetailsService;
    @Lazy
    private final LendingApplicationServiceV2 lendingApplicationServiceV2;
    @Lazy
    private final NbfcUtils nbfcUtils;
    private final WorkflowUtil workflowUtil;
    private final EdiUtil ediUtil;
    private final LendingApplicationDetailsService lendingApplicationDetailsService;
    @Lazy
    private final WorkflowRegistryFactory workflowRegistryFactory;


    @Override
    public boolean invoke(String applicationId) {
        LendingApplication lendingApplication = workflowUtil.getLendingApplication(applicationId);
        LendingApplicationLenderDetails lald = createLald(lendingApplication);
        if (ObjectUtils.isEmpty(lald)) {
            log.warn("Lending application lender details not created for application id: {}", applicationId);
            return false;
        }
        LenderBaseRequest<CreateLeadRequest> createLeadRequest = getCreateLeadRequest(lendingApplication);
        if(ObjectUtils.isEmpty(createLeadRequest)) {
            log.warn("Create lead request is empty for applicationId={}", applicationId);
            lald.setLeadSubStatus(LeadSubStatus.REQUEST_CREATION_FAILED);
            nbfcUtils.modifyLender(lendingApplication, lald, RISK_FAILED);
            return false;
        }
        WorkflowRegistry workflowRegistry = workflowRegistryFactory
                .getWorkflowRegistry(Lender.valueOf(lendingApplication.getLender()));
        updateLad(applicationId, workflowRegistry);
        invokeCreateLead(applicationId, lendingApplication, lald, createLeadRequest);
        return true;
    }

    private void updateLad(String applicationId, WorkflowRegistry workflowRegistry) {
        LendingApplicationDetails lendingApplicationDetails = workflowUtil.getLendingApplicationDetails(applicationId);
        lendingApplicationDetails.setLenderAssc(true);
        lendingApplicationDetails.setStage(workflowRegistry.getAssociationStageForWorkflow(this).name());
        lendingApplicationDetailsService.save(lendingApplicationDetails);
    }

    @Override
    public String getWorkflowName() {
        return CREATE_LEAD_WORKFLOW;
    }

    private void invokeCreateLead(String applicationId, LendingApplication lendingApplication, LendingApplicationLenderDetails lald,
                                  LenderBaseRequest<CreateLeadRequest> createLeadRequest) {
        LenderApiResponse<CreateLeadResponse> response = lendingPlatformClient.initiateCreateLead(createLeadRequest);
        processCreateLeadResponse(applicationId, lendingApplication, lald, response);
    }

    private void processCreateLeadResponse(String applicationId, LendingApplication lendingApplication, LendingApplicationLenderDetails lald,
                                           LenderApiResponse<CreateLeadResponse> response) {
        if (ObjectUtils.isEmpty(response) || !response.isSuccess() || !isCreateLeadResponseDataSuccess(response)) {
            log.info("Create lead failed for application id {}", applicationId);
            lald.setLeadSubStatus(LeadSubStatus.FAILED);
            nbfcUtils.modifyLender(lendingApplication, lald, LEAD_CREATION_FAILED);
            return;
        }

        if (response.getLender() == Lender.CREDITSAISON) {
            CreateLeadResponse data = response.getData();
            boolean isNoExposureAndNoMatch = (data != null && ObjectUtils.isEmpty(data.getAllowableExposure()) && "No Match".equalsIgnoreCase(data.getDedupeStatus()));
            boolean isPositiveExposureAndEntityExists = (data != null && !ObjectUtils.isEmpty(data.getAllowableExposure()) && data.getAllowableExposure().doubleValue() > 0.0 && "Entity Exists".equalsIgnoreCase(data.getDedupeStatus()));

            if (!(isNoExposureAndNoMatch || isPositiveExposureAndEntityExists)) {
                log.info("Create lead failed for application id {} due to Credit Saison dedupe/exposure logic", applicationId);
                lald.setLeadSubStatus(LeadSubStatus.FAILED);
                nbfcUtils.modifyLender(lendingApplication, lald, LEAD_CREATION_FAILED);
                return;
            }
        }

        log.info("Create lead successful for application id {}", applicationId);
        try {
            lald.setLeadSubStatus(LeadSubStatus.SUCCESS);
            lald.setSmbId(response.getData().getSmbId());
            lald.setCccId(response.getData().getClientId());
            lald.setLeadId(response.getData().getLeadId());
            lendingApplicationLenderDetailsService.save(lald);
        }catch (Exception e) {
            log.error("Processing create lead response failed for application id {}", applicationId, e);
        }
    }

    private boolean isCreateLeadResponseDataSuccess(LenderApiResponse<CreateLeadResponse> response) {
        return !ObjectUtils.isEmpty(response.getData());
    }

    public LendingApplicationLenderDetails createLald(LendingApplication lendingApplication) {

        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        lendingApplicationLenderDetails.setApplicationId(lendingApplication.getId());
        lendingApplicationLenderDetails.setLender(lendingApplication.getLender());
        lendingApplicationLenderDetails.setStage(KYC.name());
        lendingApplicationLenderDetails.setStatus(ACTIVE.name());
        lendingApplicationLenderDetails.setLeadStatus(CREATE_LEAD.name());
        lendingApplicationLenderDetails.setLeadSubStatus(LeadSubStatus.PENDING);
        lendingApplicationLenderDetails.setAccountId(lendingApplication.getExternalLoanId());
        DecimalFormat df = new DecimalFormat("#.##");
        df.setRoundingMode(ediUtil.isRoundDownEligibleLender(lendingApplication.getLender()) ? RoundingMode.UP : RoundingMode.DOWN);
        if (com.bharatpe.lending.enums.Lender.UGRO.name().equalsIgnoreCase(lendingApplicationLenderDetails.getLender())) {
            df = new DecimalFormat("#.######");
        }
        lendingApplicationLenderDetails.setAnnualRoi(Double.valueOf(df.format(
                lendingApplicationServiceV2.getApr(lendingApplication.getMerchantId(), lendingApplication.getId(),
                        lendingApplication.getLoanAmount(),
                        LenderOffDays.valueOf(lendingApplication.getLender()).getEdiModel().getNoOfEdiDaysInAWeek(),
                        lendingApplication.getLender()))));
        lendingApplicationLenderDetails.setRearchFlow(true);
        log.info("Creating LendingApplicationLenderDetails for applicationId: {}", lendingApplication.getId());
        return lendingApplicationLenderDetailsService.save(lendingApplicationLenderDetails);
    }

    private LenderBaseRequest<CreateLeadRequest> getCreateLeadRequest(LendingApplication lendingApplication) {
        CreateLeadRequest createLeadRequest;
        try {
            createLeadRequest =  createLeadRequestBuilder.buildRequest(lendingApplication);
        } catch (Exception e) {
            log.error("Error while creating create lead request for applicationId={}, error:{}",
                    lendingApplication.getId(), Arrays.asList(e.getStackTrace()));
            return null;
        }
        return LenderBaseRequest.<CreateLeadRequest>builder()
                .applicationId(String.valueOf(lendingApplication.getId()))
                .customerId(String.valueOf(lendingApplication.getMerchantId()))
                .lender(Lender.valueOf(lendingApplication.getLender()))
                .data(createLeadRequest)
                .build();
    }
}
