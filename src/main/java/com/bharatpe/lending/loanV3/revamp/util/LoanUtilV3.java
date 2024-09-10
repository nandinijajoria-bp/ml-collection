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
import com.bharatpe.lending.dto.KycDoc;
import com.bharatpe.lending.dto.PanFetchKYCResponseDto;
import com.bharatpe.lending.entity.LendingPancardDetails;
import com.bharatpe.lending.enums.CleverTapEvents;
import com.bharatpe.lending.enums.KycDocStatus;
import com.bharatpe.lending.enums.KycDocType;
import com.bharatpe.lending.handlers.KycHandler;
import com.bharatpe.lending.loanV3.revamp.constants.LoanDetailsConstant;
import com.bharatpe.lending.loanV3.revamp.dto.ResubmitDoneDTO;
import com.bharatpe.lending.loanV3.revamp.response.LoanDashboardApiVersion;
import com.bharatpe.lending.loanV3.revamp.services.LoanDashboardService;
import com.bharatpe.lending.service.CleverTapEventService;
import com.bharatpe.lending.util.LoanUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
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

    public boolean isPanNsdlVerified(String token, String panNumber, Long merchantId) {
        log.info("Checking if pan is nsdl verified for merchantId: {}", merchantId);
        try {
            LendingPancardDetails lendingPancardDetails = lendingPancardDetailsDao.findTop1ByMerchantIdOrderByIdDesc(merchantId);
            if(!ObjectUtils.isEmpty(lendingPancardDetails) && LendingConstants.PAN_VERIFICATION_VERSION.equals(lendingPancardDetails.getVersion())){
                log.info("PAN previously verifies for merchant:{}", merchantId);
                return true;
            }
            PanFetchKYCResponseDto responseDto = kycHandler.panFetch(token, panNumber, merchantId);
            if (responseDto != null && responseDto.getStatus())  {
                PanFetchKYCResponseDto.Data data = responseDto.getData();
                if (data != null && data.getIsPanNsdlVerified() != null && data.getIsPanNsdlVerified()) {
                    log.info("Pan is nsdl verified for {}",merchantId);
                    saveLendingPancardData(data, lendingPancardDetails, merchantId);
                    return true;
                }
            }
        }catch (Exception e) {
            log.info("Error while checking if pan is nsdl verified for merchantId: {} {}", merchantId, Arrays.asList(e.getStackTrace()));
        }
        return false;
    }

    private void saveLendingPancardData(PanFetchKYCResponseDto.Data panFetchResponseData, LendingPancardDetails lendingPancard, Long merchantId){
        if (!ObjectUtils.isEmpty(lendingPancard)) {
            lendingPancard.setName(panFetchResponseData.getName());
            lendingPancard.setPancardNumber(panFetchResponseData.getPanNumber());
            lendingPancard.setVersion(LendingConstants.PAN_VERIFICATION_VERSION);
            lendingPancard.setDob(panFetchResponseData.getDateOfBirth());
            lendingPancardDetailsDao.save(lendingPancard);
        } else {
            lendingPancardDetailsDao.save(new LendingPancardDetails(merchantId, panFetchResponseData.getPanNumber(), panFetchResponseData.getName(), null, LendingConstants.PAN_VERIFICATION_VERSION, panFetchResponseData.getDateOfBirth()));
        }
    }

    private void saveLendingPancardData(LendingPancardDetails lendingPancard, KycDoc kycPan, Long merchantId){
        if (!ObjectUtils.isEmpty(lendingPancard)) {
            lendingPancard.setName(kycPan.getName());
            lendingPancard.setPancardNumber(kycPan.getDocIdentifier());
            lendingPancard.setVersion(LendingConstants.PAN_VERIFICATION_VERSION);
            lendingPancard.setAadhaarSeedingStatus(kycPan.getAadhaarSeedingStatus());
            lendingPancard.setDob(kycPan.getDob());
            lendingPancardDetailsDao.save(lendingPancard);
        } else {
            lendingPancardDetailsDao.save(new LendingPancardDetails(merchantId, kycPan.getDocIdentifier(), kycPan.getName(), null, LendingConstants.PAN_VERIFICATION_VERSION, kycPan.getAadhaarSeedingStatus(), kycPan.getDob()));
        }
    }

    public boolean isReferenceNotRequired(Long applicationId) {
        LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = lendingRiskVariablesSnapshotDao.findByApplicationId(applicationId);
        return !ObjectUtils.isEmpty(lendingRiskVariablesSnapshot)
                && !ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getReferenceCount())
                && lendingRiskVariablesSnapshot.getReferenceCount() == 0;
    }
}
