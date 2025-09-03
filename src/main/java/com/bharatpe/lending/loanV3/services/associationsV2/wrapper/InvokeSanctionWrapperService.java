package com.bharatpe.lending.loanV3.services.associationsV2.wrapper;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.config.VkycConfig;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.utils.NbfcUtils;
import com.bharatpe.lending.util.LoanUtil;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class InvokeSanctionWrapperService {
    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    CommonService commonService;

    @Lazy
    @Autowired
    NbfcUtils nbfcUtils;

    @Value("${lender.change.enabled:false}")
    Boolean enableLenderChange;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Lazy
    @Autowired
    LoanUtil loanUtil;

    @Autowired
    EasyLoanUtil easyLoanUtil;

    @Autowired
    VkycConfig vkycConfig;

    @Async("lenderPoolTaskExecutor")
    public void invokeSanctionFlow(Map<String, String> request, Map<String, Object> args) {
        try {
            List<String> stagesToBeInvokedInOrder = getStageToBeInvokedInOrder(Long.parseLong(request.get("application_id")));
            if (!ObjectUtils.isEmpty(args)) {
                if (args.containsKey("stages")) {
                    stagesToBeInvokedInOrder = Arrays.stream(((String) args.get("stages")).split(",")).collect(Collectors.toList());
                }
                if (args.containsKey("requestId")) {
                    MDC.put("requestId", (String) args.get("requestId"));
                }
            }
            Long applicationId = Long.valueOf(request.get("application_id"));
            LenderAssociationDetailsRequestDto lenderAssociationDetailsDto = new LenderAssociationDetailsRequestDto();
            createRecord(lenderAssociationDetailsDto, applicationId);
            if(ObjectUtils.isEmpty(lenderAssociationDetailsDto.getLendingApplication()) || ObjectUtils.isEmpty(lenderAssociationDetailsDto.getLendingApplicationLenderDetails())) {
                log.info("No application details found for {}", lenderAssociationDetailsDto.getApplicationId());
                MDC.clear();
                return;
            }
            if (Objects.isNull(lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getLeadId())) {
                log.info("lead creation was unsuccessful for {} ", applicationId);
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setSanctionStatus(LenderAssociationStatus.SANCTION_FAILED.name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, LenderAssociationStatus.SANCTION_FAILED);
                MDC.clear();
                return;
            }

            if(Arrays.asList(Lender.TRILLIONLOANS.name(), Lender.UGRO.name(), Lender.PAYU.name()).contains(lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getLender())) {
                stagesToBeInvokedInOrder = checkRetryAndGetStageToBeInvokedInOrderList(lenderAssociationDetailsDto, stagesToBeInvokedInOrder);
            }

            Optional<String> failureStage = stagesToBeInvokedInOrder.stream().filter(stage -> !nbfcUtils.invokeSpecificStage(lenderAssociationDetailsDto.getLendingApplication().getLender(), lenderAssociationDetailsDto, stage)).findFirst();
            if (failureStage.isPresent()) {
                log.info("lender association failed at {} stage for applicationId {} with lender {}", failureStage.get(), applicationId, lenderAssociationDetailsDto.getLendingApplication().getLender());
                MDC.clear();
                return;
            }
            if(Arrays.asList(Lender.MUTHOOT.name(), Lender.PAYU.name()).contains(lenderAssociationDetailsDto.getLendingApplication().getLender())) {
                commonService.manageApplicationStateAndPushToNextStage(lenderAssociationDetailsDto);
            }
            MDC.clear();
        } catch (Exception e) {
            log.error("Exception in invoking sanction wrapper flow for applicationId : {} {}", request.get("application_id"), Arrays.asList(e.getStackTrace()));
            MDC.clear();
        }
    }

    private List<String> checkRetryAndGetStageToBeInvokedInOrderList(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto, List<String> stagesToBeInvokedInOrder) {
        boolean isRetry = !ObjectUtils.isEmpty(lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getSanctionStatus());

        if (isRetry) {
            int retryApiIndex = stagesToBeInvokedInOrder.indexOf(lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getSanctionStatus());
            if (retryApiIndex >= 0) {
                return stagesToBeInvokedInOrder.subList(retryApiIndex, stagesToBeInvokedInOrder.size());
            }
        }
        return stagesToBeInvokedInOrder;
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

    public List<String> getStageToBeInvokedInOrder(Long applicationId) {
        Optional<LendingApplication> lendingApplication = lendingApplicationDao.findById(applicationId);
        if (!lendingApplication.isPresent()) {
            return new ArrayList<>();
        }
        switch (Lender.valueOf(lendingApplication.get().getLender())) {
            case TRILLIONLOANS: {
                if (loanUtil.isNonTLToTLTopup(lendingApplication.get()))
                    return Arrays.asList(LenderAssociationStages.TOPUP_UNDO_APPROVE.name(), LenderAssociationStages.TOPUP_DATA.name(),
                            LenderAssociationStages.ADD_CHARGE.name(), LenderAssociationStages.TOPUP_APPROVE.name(), LenderAssociationStages.UPDATE_LEAD.name(), LenderAssociationStages.NACH_MANDATE.name());
                else
                    return Arrays.asList(LenderAssociationStages.UPDATE_LEAD.name(), LenderAssociationStages.NACH_MANDATE.name());
            }
            case PAYU:
                ArrayList<String> stages = new ArrayList<>(Arrays.asList(LenderAssociationStages.UPDATE_ADDRESS.name(),LenderAssociationStages.UPDATE_BANK_DETAILS.name(),LenderAssociationStages.NACH_MANDATE.name()));
                if(!easyLoanUtil.percentScaleUp(lendingApplication.get().getMerchantId(), vkycConfig.getRolloutPercentage())) {
                    stages.add(LenderAssociationStages.SKIP_VKYC.name()); // when merchant is ineligible mark vkyc skipped.
                }
                return stages;
            case CAPRI:
            case SMFG:
                return Collections.singletonList(LenderAssociationStages.NACH_MANDATE.name());
            case MUTHOOT:
                return Collections.singletonList(LenderAssociationStages.UPDATE_LEAD.name());
            case CREDITSAISON:
                return Collections.singletonList(LenderAssociationStages.PENNY_DROP.name());
            case UGRO:
                return Arrays.asList(LenderAssociationStages.NACH_MANDATE.name(), LenderAssociationStages.PENNY_DROP.name(), LenderAssociationStages.GET_LEAD.name());
            default:
                return new ArrayList<>();
        }
    }
}
