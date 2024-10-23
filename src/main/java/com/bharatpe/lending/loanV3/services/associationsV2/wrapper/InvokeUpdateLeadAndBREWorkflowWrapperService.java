package com.bharatpe.lending.loanV3.services.associationsV2.wrapper;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.LenderOffDays;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.loanV2.service.LendingApplicationServiceV2;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.factory.LenderAssociationStageFactoryV2;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.associationsV2.AssociationServiceUtil;
import com.bharatpe.lending.loanV3.utils.NbfcUtils;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.transaction.Transactional;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
public class InvokeUpdateLeadAndBREWorkflowWrapperService {
    @Autowired
    CommonService commonService;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    NbfcUtils nbfcUtils;

    @Autowired
    AssociationServiceUtil associationServiceUtil;

    @Value("${lender.change.enabled:false}")
    Boolean enableLenderChange;

    @Autowired
    LendingApplicationServiceV2 lendingApplicationServiceV2;

    @Async("lenderPoolTaskExecutor")
    public void invokeUpdateLeadAndBREWorkflow(Map<String,String> request, Map<String, Object> args) {
        try {
            if (null != args && args.containsKey("requestId")) {
                MDC.put("requestId", (String) args.get("requestId"));
            }
            Long applicationId = Long.valueOf(request.get("application_id"));
            if (ObjectUtils.isEmpty(applicationId)) {
                log.info("No application id found");
                MDC.clear();
                return;
            }
            LendingApplication lendingApplication = lendingApplicationDao.findById(applicationId).orElse(null);
            if (ObjectUtils.isEmpty(lendingApplication)) {
                log.info("No application found for application id : {}", applicationId);
                return;
            }

            LenderAssociationDetailsRequestDto lenderAssociationDetailsDto = new LenderAssociationDetailsRequestDto();
            if ("SMFG".equalsIgnoreCase(lendingApplication.getLender()))
                runBaseChecksAndCreateLenderDetailsRecord(lenderAssociationDetailsDto, lendingApplication);
            else
                createRecord(lenderAssociationDetailsDto, lendingApplication);

            if(ObjectUtils.isEmpty(lenderAssociationDetailsDto.getLendingApplication()) || ObjectUtils.isEmpty(lenderAssociationDetailsDto.getLendingApplicationLenderDetails())) {
                log.info("No application details found for {}", lenderAssociationDetailsDto.getApplicationId());
                MDC.clear();
                return;
            }
            if (Objects.isNull(lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getLeadId()) && !"SMFG".equalsIgnoreCase(lendingApplication.getLender())) {
                log.info("lead creation was unsuccessful for {} ", applicationId);
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.UPDATE_LEAD_FAILED.name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, LenderAssociationStatus.UPDATE_LEAD_FAILED);
                MDC.clear();
                return;
            }
            Boolean updateLeadStatus = invokeUpdateLead(lenderAssociationDetailsDto);
            if (updateLeadStatus) {
                log.info("update lead of {} success for {}", lenderAssociationDetailsDto.getLendingApplication().getLender(), applicationId);
                invokeRiskDecision(lenderAssociationDetailsDto);
            }
            MDC.clear();
        } catch (Exception e) {
            log.info("Exception in invoking update lead and BRE flow for applicationId : {} {}", request.get("application_id"), Arrays.asList(e.getStackTrace()));
            MDC.clear();
        }
    }

    private void createRecord(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto, LendingApplication lendingApplication) {
        lenderAssociationDetailsDto.setApplicationId(lendingApplication.getId());
        LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(lendingApplication.getId(), Status.ACTIVE.name(), lendingApplication.getLender());
        if (ObjectUtils.isEmpty(lendingApplicationLenderDetails) || Objects.isNull(lendingApplicationLenderDetails.getLeadId())) {
            log.info("No lender association details found for lender {} of application id : {}",lendingApplication.getLender(), lendingApplication.getId());
            return;
        }
        lenderAssociationDetailsDto.setLendingApplication(lendingApplication);
        lenderAssociationDetailsDto.setLendingApplicationLenderDetails(lendingApplicationLenderDetails);
        lenderAssociationDetailsDto.setMerchantId(lendingApplication.getMerchantId());
        lenderAssociationDetailsDto.setManageState(Boolean.TRUE);
        lenderAssociationDetailsDto.setModifyLender(enableLenderChange);
    }

    @Transactional
    private Boolean invokeUpdateLead(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest) {
        switch (lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().getLender()) {
            case "USFB":
                return associationServiceUtil.invokeLeadUpdateService(lenderAssociationDetailsRequest.getLendingApplication().getLender(), lenderAssociationDetailsRequest);
            case "TRILLIONLOANS":
                return associationServiceUtil.invokeConsentPostingService(lenderAssociationDetailsRequest.getLendingApplication().getLender(), lenderAssociationDetailsRequest);
            case "MUTHOOT":
            case "CAPRI":
            case "PAYU":
            case "CREDITSAISON":
            case "SMFG":
                return true;    // Skipped update lead as its only taking same payload as createLead
            default:
                return false;
        }
    }

    @Transactional
    private void invokeRiskDecision(LenderAssociationDetailsRequestDto lenderAssociationDetailDto) {
        Boolean isBreSuccess = associationServiceUtil.invokeBREService(lenderAssociationDetailDto.getLendingApplication().getLender(), lenderAssociationDetailDto);
        if (isBreSuccess && lenderAssociationDetailDto.getLendingApplication().getLender().equalsIgnoreCase(Lender.TRILLIONLOANS.name())) {
            associationServiceUtil.invokeConsentPostingService(lenderAssociationDetailDto.getLendingApplication().getLender(), lenderAssociationDetailDto);
        }
    }

    private void runBaseChecksAndCreateLenderDetailsRecord(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto, LendingApplication lendingApplication) {
        lenderAssociationDetailsDto.setApplicationId(lendingApplication.getId());
        lenderAssociationDetailsDto.setLendingApplication(lendingApplication);
        lenderAssociationDetailsDto.setMerchantId(lendingApplication.getMerchantId());
        lenderAssociationDetailsDto.setModifyLender(enableLenderChange);
        lenderAssociationDetailsDto.setManageState(Boolean.TRUE);
        LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao
                .findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(lenderAssociationDetailsDto.getApplicationId(), Status.ACTIVE.name(), lendingApplication.getLender());
        if (Objects.nonNull(lendingApplicationLenderDetails) && Objects.nonNull(lendingApplicationLenderDetails.getLeadId())) {
            log.info("lead creation already done for applicationId: {}", lenderAssociationDetailsDto.getApplicationId());
        }
        LenderAssociationStages initialLenderDetailsStage = LenderAssociationStageFactoryV2.getNextStage(Lender.valueOf(lendingApplication.getLender()), LenderAssociationStages.INIT);
        if (null == lendingApplicationLenderDetails) {
            lendingApplicationLenderDetails = createLenderRecord(lendingApplication, initialLenderDetailsStage.name(), Status.ACTIVE.name());
        }
        lenderAssociationDetailsDto.setLendingApplicationLenderDetails(lendingApplicationLenderDetails);
    }

    private LendingApplicationLenderDetails createLenderRecord(LendingApplication lendingApplication, String stage, String recordStatus) {
        LendingApplicationLenderDetails lendingApplicationLenderDetails;
        lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        lendingApplicationLenderDetails.setApplicationId(lendingApplication.getId());
        lendingApplicationLenderDetails.setLender(lendingApplication.getLender());
        lendingApplicationLenderDetails.setStage(stage);
        lendingApplicationLenderDetails.setStatus(recordStatus);
        lendingApplicationLenderDetails.setAccountId(lendingApplication.getExternalLoanId());
        DecimalFormat df = new DecimalFormat("#.##");
        df.setRoundingMode(RoundingMode.DOWN);
        lendingApplicationLenderDetails.setAnnualRoi(Double.valueOf(df.format(
                lendingApplicationServiceV2.getApr(lendingApplication.getMerchantId(), lendingApplication.getId(), lendingApplication.getLoanAmount(),
                        LenderOffDays.valueOf(lendingApplication.getLender()).getEdiModel().getNoOfEdiDaysInAWeek(), lendingApplication.getLender()))));
        return lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);
    }

}
