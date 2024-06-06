package com.bharatpe.lending.loanV3.revamp.util;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingAuditTrial;
import com.bharatpe.lending.common.dao.LendingApplicationPriorityDao;
import com.bharatpe.lending.common.dao.LendingResubmitReasonCountDao;
import com.bharatpe.lending.common.dao.LendingResubmitTaskDao;
import com.bharatpe.lending.common.dao.LendingRiskVariablesSnapshotDao;
import com.bharatpe.lending.common.entity.LendingApplicationPriority;
import com.bharatpe.lending.common.entity.LendingResubmitReasonCount;
import com.bharatpe.lending.common.entity.LendingResubmitTask;
import com.bharatpe.lending.common.entity.LendingRiskVariablesSnapshot;
import com.bharatpe.lending.common.enums.FunnelEnums;
import com.bharatpe.lending.common.service.FunnelService;
import com.bharatpe.lending.constant.LendingConstants;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingAuditTrialDao;
import com.bharatpe.lending.dao.LendingPancardDetailsDao;
import com.bharatpe.lending.dto.PanFetchKYCResponseDto;
import com.bharatpe.lending.entity.LendingPancardDetails;
import com.bharatpe.lending.enums.CleverTapEvents;
import com.bharatpe.lending.handlers.KycHandler;
import com.bharatpe.lending.loanV2.dto.ApiResponse;
import com.bharatpe.lending.loanV3.revamp.constants.LoanDetailsConstant;
import com.bharatpe.lending.loanV3.revamp.dto.AgreementStateDTO;
import com.bharatpe.lending.loanV3.revamp.dto.ResubmitDoneDTO;
import com.bharatpe.lending.loanV3.revamp.response.LoanDashboardApiVersion;
import com.bharatpe.lending.loanV3.revamp.services.LoanDashboardService;
import com.bharatpe.lending.service.CleverTapEventService;
import com.bharatpe.lending.util.LoanUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@Slf4j
public class LoanUtilV3 {

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LendingResubmitTaskDao lendingResubmitTaskDao;

    @Autowired
    LendingResubmitReasonCountDao lendingResubmitReasonCountDao;

    @Autowired
    FunnelService funnelService;

    @Autowired
    LendingApplicationPriorityDao lendingApplicationPriorityDao;

    @Autowired
    CleverTapEventService cleverTapEventService;

    @Autowired
    LendingAuditTrialDao lendingAuditTrialDao;

    @Autowired
    LoanUtil loanUtil;

    @Autowired
    private LoanDashboardService loanDashboardService;

    @Autowired
    private LendingRiskVariablesSnapshotDao lendingRiskVariablesSnapshotDao;

    @Autowired
    private KycHandler kycHandler;

    @Autowired
    LendingPancardDetailsDao lendingPancardDetailsDao;

    ExecutorService executorService = Executors.newFixedThreadPool(10);

    public ResubmitDoneDTO resubmitDone(Long merchantId, Long applicationId, String resubmitReasons, String mid){
        ResubmitDoneDTO resubmitDoneDTO = new ResubmitDoneDTO();
        try{
            if(Objects.isNull(merchantId) || Objects.isNull(applicationId)){
                resubmitDoneDTO.setErrorString("Request is Invalid");
                return resubmitDoneDTO;
            }

            LendingApplication lendingApplication = lendingApplicationDao.findByMerchantIdAndApplicationIdAndStatus(merchantId,applicationId,"pending_verification");
            if(Objects.isNull(lendingApplication)){
                resubmitDoneDTO.setErrorString("application not eligible for resubmit");
                return resubmitDoneDTO;
            }

            LendingResubmitTask lendingResubmitTask = lendingResubmitTaskDao.findTopByApplicationIdAndMerchantId(applicationId,merchantId);
            if(Objects.isNull(lendingResubmitTask) || lendingResubmitTask.getResubmitDone()){
                resubmitDoneDTO.setErrorString("Already Resubmit Done For ApplicationId");
                return resubmitDoneDTO;
            }

            List<LendingResubmitReasonCount> lendingResubmitReasonCountList = lendingResubmitReasonCountDao.findByApplicationIdAndMerchantId(applicationId, merchantId);
            if(ObjectUtils.isEmpty(lendingResubmitReasonCountList)){
                resubmitDoneDTO.setErrorString("Unable to fetch resubmit reason entry.");
                return resubmitDoneDTO;
            }
            Boolean resubmitCompleted = true;
            List<String> resubmitReasonList = Arrays.asList(resubmitReasons.split("\\s*,\\s*"));
            Integer maxCount = -1;
            LoanDashboardApiVersion loanDashboardApiVersion = loanDashboardService.getLoanDashboardApiVersion(merchantId, lendingApplication);
            for(LendingResubmitReasonCount lendingResubmitReasonCount : lendingResubmitReasonCountList){
                if(lendingResubmitReasonCount.getResubmitCount() > maxCount)maxCount = lendingResubmitReasonCount.getResubmitCount();
            }
            for(LendingResubmitReasonCount lendingResubmitReasonCount : lendingResubmitReasonCountList){
                if(lendingResubmitReasonCount.getResubmitCount() != maxCount)continue;
                for(String resubmitReason : resubmitReasonList){
                    if(resubmitReason.equalsIgnoreCase(lendingResubmitReasonCount.getResubmitReason())){
                        lendingResubmitReasonCount.setResubmitDone(Boolean.TRUE);
                        lendingResubmitReasonCount.setResubmittedAt(new Date());
                        lendingResubmitReasonCountDao.save(lendingResubmitReasonCount);
                        if("v2".equalsIgnoreCase(loanDashboardApiVersion.getApiVersion())){
                            funnelService.submitEventV3(merchantId, null, applicationId, FunnelEnums.StageId.RESUBMIT,
                                    FunnelEnums.StageEvent.COMPLETED, resubmitReason,"v3");
                        }
                        else{
                            funnelService.submitEvent(merchantId, null, applicationId, FunnelEnums.StageId.RESUBMIT,
                                    FunnelEnums.StageEvent.COMPLETED, resubmitReason);
                        }
                    }
                }
                resubmitCompleted = resubmitCompleted && lendingResubmitReasonCount.getResubmitDone();
            }

            if(resubmitCompleted){
                lendingResubmitTask.setResubmitDone(Boolean.TRUE);
                lendingResubmitTask.setResubmittedAt(new Date());
                lendingResubmitTaskDao.save(lendingResubmitTask);

                lendingApplication.setLmsStage("PENDING_KYC_ASSIGNMENT");
                lendingApplicationDao.save(lendingApplication);

                // update tat start time on resubmit
                LendingApplicationPriority lendingApplicationPriority = lendingApplicationPriorityDao.findByApplicationId(lendingApplication.getId());
                if (!ObjectUtils.isEmpty(lendingApplicationPriority)) {
                    lendingApplicationPriority.setTatStartTime(new Date());
                    lendingApplicationPriorityDao.save(lendingApplicationPriority);
                }

                if("v2".equalsIgnoreCase(loanDashboardApiVersion.getApiVersion())){
                    funnelService.submitEventV3(merchantId, null, applicationId, FunnelEnums.StageId.RESUBMIT,
                            FunnelEnums.StageEvent.COMPLETED, LocalDateTime.now().toString(), "v3");
                }
                else{
                    funnelService.submitEvent(merchantId, null, applicationId, FunnelEnums.StageId.RESUBMIT,
                            FunnelEnums.StageEvent.COMPLETED, LocalDateTime.now().toString());
                }
                Integer finalMaxCount = maxCount;
                HashMap<String, String> cleverTapEvtData = new HashMap<String, String>() {{
                    put("resubmitReason", lendingResubmitTask.getResubmitReason());
                    put("resubmitCount", finalMaxCount.toString());
                }};
                executorService.execute(() -> cleverTapEventService.sendClevertapEvent(CleverTapEvents.LOAN_RESUBMIT_COMPLETED.name(), cleverTapEvtData, mid));
                LendingAuditTrial lendingAuditTrial = new LendingAuditTrial();
                lendingAuditTrial.setMerchantId(lendingApplication.getMerchantId());
                lendingAuditTrial.setApplicationId(lendingApplication.getId());
                lendingAuditTrial.setLoanId(lendingApplication.getExternalLoanId());
                lendingAuditTrial.setType("APP_STATUS");
                lendingAuditTrial.setNewStatus("RESUBMIT_DONE");
                lendingAuditTrial.setOldStatus(lendingApplication.getStatus());
                lendingAuditTrial.setUserId(0L);
                lendingAuditTrialDao.save(lendingAuditTrial);
                loanUtil.publishDSData(lendingApplication);
            }
            resubmitDoneDTO.setSuccess(true);
            resubmitDoneDTO.setResubmitDone(true);
            log.info("Resubmit Done Succesfully for {}", lendingApplication.getId());
        }catch (Exception e){
            log.error("Exception in resubmit Done for application:{}, {}, {}", applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
            resubmitDoneDTO.setErrorString("Something Went Wrong");
        }
        return resubmitDoneDTO;
    }

    public boolean isPreapprovedRepeatLoan(Long applicationId){
        LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = lendingRiskVariablesSnapshotDao.findByApplicationId(applicationId);
        if(ObjectUtils.isEmpty(lendingRiskVariablesSnapshot))return false;
        String pilotIdentifier = lendingRiskVariablesSnapshot.getPilotIdentifier();
        if(!ObjectUtils.isEmpty(pilotIdentifier) && pilotIdentifier.contains(LoanDetailsConstant.PREAPPROVED_REPEAT_LOAN_IDENTIFIER)){
            return true;
        }
        return false;
    }

    public boolean isPanNsdlVerified(String token, Long merchantId){
        try{
            LendingPancardDetails lendingPancardDetails = lendingPancardDetailsDao.findTop1ByMerchantIdOrderByIdDesc(merchantId);
            if(!ObjectUtils.isEmpty(lendingPancardDetails) && LendingConstants.PAN_VERIFICATION_VERSION.equals(lendingPancardDetails.getVersion())){
                log.info("PAN previously verifies for merchant:{}", merchantId);
                return true;
            }
            else {
                String kycPancard = kycHandler.getPanNumber(merchantId);
                log.info("pancard fetched from kyc : {}", kycPancard);
                PanFetchKYCResponseDto response = kycHandler.panFetch(token, kycPancard, merchantId);
                if (!ObjectUtils.isEmpty(response) && Objects.nonNull(response.getStatus()) && response.getStatus()) {
                    PanFetchKYCResponseDto.Data data = response.getData();
                    if (!ObjectUtils.isEmpty(data)) {
                        if (Objects.nonNull(data.getIsPanNsdlVerified()) && data.getIsPanNsdlVerified()) {
                            log.info("pan is nsdl verified for {}", merchantId);
                            return true;
                        }
                    }
                }
            }
            log.info("nsdl verified pan not available for {}", merchantId);
        }catch (Exception e) {
            log.error("error while fetching pan nsdl validity for {} {}", merchantId, Arrays.asList(e.getStackTrace()));
        }
        return false;
    }
}
