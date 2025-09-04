package com.bharatpe.lending.loanV3.services.associationsV2.wrapper;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderOffDays;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.loanV2.service.LendingApplicationServiceV2;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import com.bharatpe.lending.util.EdiUtil;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.*;

@Slf4j
@Service
public class InvokeLeadWrapperService {
    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    CommonService commonService;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Lazy
    @Autowired
    LendingApplicationServiceV2 lendingApplicationServiceV2;

    @Autowired
    private EdiUtil ediUtil;

    @Lazy
    @Autowired
    KycUtils kycUtils;

    @Value("${lender.change.enabled:false}")
    Boolean enableLenderChange;

    @Async("lenderPoolTaskExecutor")
    public void invokeCreateLead(Map<String, String> request, Map<String, Object> args) {
        try {
            if (!ObjectUtils.isEmpty(args)) {
                if (args.containsKey("requestId")) {
                    MDC.put("requestId", (String) args.get("requestId"));
                }
            }
            Long applicationId = Long.valueOf(request.get("application_id"));
            LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto = new LenderAssociationDetailsRequestDto();
            lenderAssociationDetailsRequestDto.setApplicationId(applicationId);
            lenderAssociationDetailsRequestDto.setManageState(true);
            runBaseChecksAndCreateRecord(lenderAssociationDetailsRequestDto);
            if (ObjectUtils.isEmpty(lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails())) {
                log.info("no record found in lendingApplicationLenderDetails for applicationId {} with lender {}", applicationId, lenderAssociationDetailsRequestDto.getLendingApplication().getLender());
                MDC.clear();
                return;
            }
            log.info("base checks ran for applicationId {} with lender {}", applicationId, lenderAssociationDetailsRequestDto.getLendingApplication().getLender());
            List<String> stagesToBeInvokedInOrder = getStageToBeInvokedInOrder(lenderAssociationDetailsRequestDto.getLendingApplication().getId(), lenderAssociationDetailsRequestDto.getLendingApplication().getLender());
            LendingApplication application = lenderAssociationDetailsRequestDto.getLendingApplication();
            boolean isTopup = LoanType.TOPUP.name().equalsIgnoreCase(application.getLoanType());
            boolean eligibleForSkipKyc = kycUtils.isEligibleForSkipKyc(application.getId(), Lender.valueOf(application.getLender()), application.getMerchantId(), isTopup);
            Boolean eligibleForLenderKyc = kycUtils.isEligibleForLenderKyc(application.getLender(), application.getMerchantId(), LoanType.TOPUP.name().equalsIgnoreCase(application.getLoanType()));
            stagesToBeInvokedInOrder =  eligibleForSkipKyc ? addStagesForSkipKyc(application.getId(), new ArrayList<>(stagesToBeInvokedInOrder), Lender.valueOf(application.getLender())) : stagesToBeInvokedInOrder;
            Optional<String> failureStage = stagesToBeInvokedInOrder.stream().filter(stage -> !commonService.invokeStage(lenderAssociationDetailsRequestDto, stage)).findFirst();
            if (failureStage.isPresent()) {
                log.info("lender association failed at {} stage for applicationId {}  with lender {}", failureStage.get(), applicationId, lenderAssociationDetailsRequestDto.getLendingApplication().getLender());
                MDC.clear();
                return;
            }
            if(!eligibleForSkipKyc && !eligibleForLenderKyc) {
                commonService.manageApplicationStateAndPushToNextStage(lenderAssociationDetailsRequestDto);
            }
            MDC.clear();
        } catch (Exception e) {
            log.error("Exception in invoking lead wrapper flow for applicationId : {} {}", request.get("application_id"), Arrays.asList(e.getStackTrace()));
            MDC.clear();
        }
    }

    public void runBaseChecksAndCreateRecord(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto) {
        Optional<LendingApplication> lendingApplication = lendingApplicationDao.findById(lenderAssociationDetailsDto.getApplicationId());
        log.info("lending application {}", lendingApplication.get());
        if (ObjectUtils.isEmpty(lendingApplication.get())) {
            log.info("no application found for {}", lenderAssociationDetailsDto.getApplicationId());
            return;
        }
        lenderAssociationDetailsDto.setLendingApplication(lendingApplication.get());
        lenderAssociationDetailsDto.setMerchantId(lendingApplication.get().getMerchantId());
        lenderAssociationDetailsDto.setModifyLender(enableLenderChange);
        lenderAssociationDetailsDto.setManageState(Boolean.TRUE);
        LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao
                .findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(lenderAssociationDetailsDto.getApplicationId(), Status.ACTIVE.name(), lendingApplication.get().getLender());
        if (Objects.nonNull(lendingApplicationLenderDetails) && Objects.nonNull(lendingApplicationLenderDetails.getLeadId())) {
            log.info("lead creation already done for applicationId: {}", lenderAssociationDetailsDto.getApplicationId());
        }
        if (null == lendingApplicationLenderDetails) {
            lendingApplicationLenderDetails = createLenderRecord(lendingApplication.get(), LenderAssociationStages.LEAD_WRAPPER.name(), Status.ACTIVE.name());
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
        df.setRoundingMode(ediUtil.isRoundDownEligibleLender(lendingApplication.getLender()) ? RoundingMode.UP : RoundingMode.DOWN);
        if (Lender.UGRO.name().equalsIgnoreCase(lendingApplicationLenderDetails.getLender())) {
            df = new DecimalFormat("#.######");
        }
        lendingApplicationLenderDetails.setAnnualRoi(Double.valueOf(df.format(
                lendingApplicationServiceV2.getApr(lendingApplication.getMerchantId(), lendingApplication.getId(), lendingApplication.getLoanAmount(),
                        LenderOffDays.valueOf(lendingApplication.getLender()).getEdiModel().getNoOfEdiDaysInAWeek(), lendingApplication.getLender()))));
        return lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);
    }

    public static boolean kycDataNeeded(String stage) {
        switch (stage) {
            case "CREATE_LEAD":
            case "UPDATED_LEAD":
            case "CREATE_CLIENT":
                return true;
            default:
                return false;
        }
    }

    public List<String> getStageToBeInvokedInOrder(Long applicationId, String lender) {
        log.info("Getting stages to be invoked in lead wrapper for applicationId  {} and lender {} ", applicationId, lender);
        if (ObjectUtils.isEmpty(lender)) {
            throw new RuntimeException("Invalid lender " + lender + " for applicationId " + applicationId);
        }
        switch (Lender.valueOf(lender)) {
            case USFB:
            case UGRO:
            case OXYZO:
                return Collections.singletonList(LenderAssociationStages.CREATE_LEAD.name());
            case TRILLIONLOANS:
            case CAPRI:
                return Arrays.asList(LenderAssociationStages.CREATE_CLIENT.name(), LenderAssociationStages.CREATE_LEAD.name());
            case MUTHOOT:
            case PAYU:
                return Arrays.asList(LenderAssociationStages.CREATE_LEAD.name(), LenderAssociationStages.UPDATE_LEAD.name());
            case CREDITSAISON:
                return Collections.singletonList(LenderAssociationStages.CREATE_CLIENT.name());
            default:
                throw new RuntimeException("Invalid lender " + lender + " for applicationId " + applicationId);
        }
    }

    public List<String> addStagesForSkipKyc(Long applicationId, List<String> stageToBeInvokedForLender, Lender lender) {
        switch (lender) {
            case TRILLIONLOANS:
                  stageToBeInvokedForLender.add(LenderAssociationStages.KYC_VALIDITY.name());
                  break;
            default:
                throw new RuntimeException("Invalid lender " + lender + " for applicationId " + applicationId);
        }
        return stageToBeInvokedForLender;
    }
}
