package com.bharatpe.lending.loanV3.services.associationsV2.wrapper;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
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
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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
            LenderAssociationDetailsRequestDto lenderAssociationDetailsDto = new LenderAssociationDetailsRequestDto();
            createRecord(lenderAssociationDetailsDto, applicationId);
            if(ObjectUtils.isEmpty(lenderAssociationDetailsDto.getLendingApplication()) || ObjectUtils.isEmpty(lenderAssociationDetailsDto.getLendingApplicationLenderDetails())) {
                log.info("No application details found for {}", lenderAssociationDetailsDto.getApplicationId());
                MDC.clear();
                return;
            }
            if (Objects.isNull(lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getLeadId())) {
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

    private void createRecord(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto, Long applicationId) {
        lenderAssociationDetailsDto.setApplicationId(applicationId);
        Optional<LendingApplication> lendingApplicationOptional = lendingApplicationDao.findById(applicationId);
        if (!lendingApplicationOptional.isPresent()) {
            log.info("No application found for application id : {}", applicationId);
            return;
        }
        LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(applicationId, Status.ACTIVE.name(), lendingApplicationOptional.get().getLender());
        if (ObjectUtils.isEmpty(lendingApplicationLenderDetails) || Objects.isNull(lendingApplicationLenderDetails.getLeadId())) {
            log.info("No lender association details found for lender {} of application id : {}", lendingApplicationOptional.get().getLender(), applicationId);
            return;
        }
        LendingApplication lendingApplication = lendingApplicationOptional.get();
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
}
