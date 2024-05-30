package com.bharatpe.lending.loanV3.services;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingGstDetail;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.enums.*;
import com.bharatpe.lending.common.service.SherlocLoanStatusChangeService;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingGstDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.loanV2.dto.ApiResponse;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.loanV2.service.LendingApplicationServiceV2;
import com.bharatpe.lending.loanV3.dto.InvokeLenderAssociationRequest;
import com.bharatpe.lending.loanV3.dto.LenderAssociationStatusResponse;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.loanV3.dto.ModifyAppRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.util.ObjectUtils;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
public abstract class LendingApplicationServiceV3Base {

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LendingApplicationDetailsDao lendingApplicationDetailsDao;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    LendingGstDao lendingGstDao;

    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;

    @Autowired
    LendingApplicationServiceV2 lendingApplicationServiceV2;

    @Autowired
    @Qualifier("ConfluentKafkaTemplate")
    KafkaTemplate confluentKafkaTemplate;

    @Autowired
    SherlocLoanStatusChangeService sherlocLoanStatusChangeService;

    public abstract void initLenderAssociation(InvokeLenderAssociationRequest invokeLenderAssociationRequest);

    public ApiResponse<?> fetchApplicationStatus(Long merchantId) {
        LendingApplication currentDraftApplication =  lendingApplicationDao.findByMerchantIdAndStatus(merchantId, "draft");
        if (ObjectUtils.isEmpty(currentDraftApplication)) {
            LendingApplication currentRejectApplication =  lendingApplicationDao.findByMerchantIdAndStatus(merchantId, "rejected");
            if(!ObjectUtils.isEmpty(currentRejectApplication)) {
                return new ApiResponse<>(LenderAssociationStatusResponse.builder()
                        .status(LenderAssociationStatus.LENDER_ASSOCIATION_FAILED)
                        .stage(LenderAssociationStages.FAILED)
                        .ediModelModified(false)
                        .lender(currentRejectApplication.getLender())
                        .build());
            }
            return new ApiResponse<>(false,"open draft lending application not found");
        }
        LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(currentDraftApplication.getId());
        if (ObjectUtils.isEmpty(lendingApplicationDetails)) {
            return new ApiResponse<>(false,"lending application details not found");
        }
        LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusOrderByIdDesc(currentDraftApplication.getId(), Status.ACTIVE.name());
        if (ObjectUtils.isEmpty(lendingApplicationLenderDetails) && LendingEnum.LENDER.ABFL.name().equalsIgnoreCase(currentDraftApplication.getLender())) {
            InvokeLenderAssociationRequest invokeLenderAssociationRequest = new InvokeLenderAssociationRequest();
            invokeLenderAssociationRequest.setApplicationId(currentDraftApplication.getId());
            invokeLenderAssociationRequest.setStage(LenderAssociationStages.INIT.name());
            invokeLenderAssociationRequest.setForceEnable(false);
            initLenderAssociation(invokeLenderAssociationRequest);
        }
        if (ObjectUtils.isEmpty(lendingApplicationLenderDetails) && !LendingEnum.LENDER.ABFL.name().equalsIgnoreCase(currentDraftApplication.getLender())) {
            return new ApiResponse<>(LenderAssociationStatusResponse.builder()
                    .status(LenderAssociationStatus.LENDER_ASSOCIATION_COMPLETED)
                    .stage(LenderAssociationStages.COMPLETED)
                    .ediModelModified(false)
                    .lender(currentDraftApplication.getLender())
                    .build());
        }
        else if (ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
            return new ApiResponse<>(false,"lead creation triggered ! Please retry for status in few minutes");
        } else {
            if (LenderAssociationStages.LENDER_CHANGE.name().equalsIgnoreCase(lendingApplicationDetails.getStage())) {
                return new ApiResponse<>(LenderAssociationStatusResponse.builder()
                        .status(LenderAssociationStatus.LENDER_CHANGE_IN_PROGRESS)
                        .stage(LenderAssociationStages.LENDER_CHANGE)
                        .ediModelModified(lendingApplicationDetails.getEdiModelModified())
                        .lender(currentDraftApplication.getLender())
                        .build());
            } else if (LenderAssociationStages.COMPLETED.name().equalsIgnoreCase(getWrapperStage(lendingApplicationLenderDetails.getStage()))) {
                return new ApiResponse<>(LenderAssociationStatusResponse.builder()
                        .status(LenderAssociationStatus.LENDER_ASSOCIATION_COMPLETED)
                        .stage(LenderAssociationStages.COMPLETED)
                        .ediModelModified(lendingApplicationDetails.getEdiModelModified())
                        .lender(currentDraftApplication.getLender())
                        .build());
            } else if (LenderAssociationStages.BRE.name().equalsIgnoreCase(lendingApplicationLenderDetails.getStage())) {
                return new ApiResponse<>(LenderAssociationStatusResponse.builder()
                        .status(LenderAssociationStatus.valueOf(Optional.ofNullable(lendingApplicationLenderDetails.getBreStatus()).orElse(LenderAssociationStatus.BRE_PENDING.name())))
                        .stage(LenderAssociationStages.BRE)
                        .ediModelModified(lendingApplicationDetails.getEdiModelModified())
                        .lender(currentDraftApplication.getLender())
                        .build());
            } else if (LenderAssociationStages.KYC.name().equalsIgnoreCase(lendingApplicationLenderDetails.getStage())) {
                return new ApiResponse<>(LenderAssociationStatusResponse.builder()
                        .status(LenderAssociationStatus.valueOf(Optional.ofNullable(lendingApplicationLenderDetails.getKycStatus()).orElse(LenderAssociationStatus.KYC_PENDING.name())))
                        .stage(LenderAssociationStages.KYC)
                        .ediModelModified(lendingApplicationDetails.getEdiModelModified())
                        .lender(currentDraftApplication.getLender())
                        .build());
            }
        }
        return new ApiResponse<>(false,"something went wrong");
    }
    private String getWrapperStage(String stage) {
        switch (stage) {
            case "ASSC_COMPLETED":
            case "SANCTION_WRAPPER":
            case "DRAWDOWN":
            case "DOCUMENT_UPLOAD":
            case "COMPLETED":
                return "COMPLETED";
        }
        return stage;
    }

    public  ApiResponse<?> modifyAppDetails(ModifyAppRequest modifyAppRequest) {
        Optional<LendingApplication> lendingApplication = lendingApplicationDao.findById(modifyAppRequest.getApplicationId());
        boolean loanStatusFlag = false;
        if (!lendingApplication.isPresent()) {
            return new ApiResponse<>(false, "no app exists");
        }
        lendingApplication.get().setLender(!ObjectUtils.isEmpty(modifyAppRequest.getLender()) ? modifyAppRequest.getLender() : lendingApplication.get().getLender());
        lendingApplication.get().setLoanAmount(!ObjectUtils.isEmpty(modifyAppRequest.getLoanAmount()) ? modifyAppRequest.getLoanAmount() : lendingApplication.get().getLoanAmount());
        lendingApplication.get().setStatus(!ObjectUtils.isEmpty(modifyAppRequest.getAppStatus()) ? modifyAppRequest.getAppStatus() : lendingApplication.get().getStatus());
        lendingApplication.get().setExternalLoanId(!ObjectUtils.isEmpty(modifyAppRequest.getExternalLoanId()) ? modifyAppRequest.getExternalLoanId() : lendingApplication.get().getExternalLoanId());
        lendingApplication.get().setSendToNbfc(!ObjectUtils.isEmpty(modifyAppRequest.getSendToNbfc()) ? ("SET_NULL".equalsIgnoreCase(modifyAppRequest.getSendToNbfc()) ? null: modifyAppRequest.getSendToNbfc()) : lendingApplication.get().getSendToNbfc());
        lendingApplication.get().setLmsStage(!ObjectUtils.isEmpty(modifyAppRequest.getLmsStage()) ? ("SET_NULL".equalsIgnoreCase(modifyAppRequest.getLmsStage()) ? null : modifyAppRequest.getLmsStage()) : lendingApplication.get().getLmsStage());
        lendingApplication.get().setNbfcId(!ObjectUtils.isEmpty(modifyAppRequest.getNbfcId()) ? ("SET_NULL".equalsIgnoreCase(modifyAppRequest.getNbfcId()) ? null : modifyAppRequest.getNbfcId()) : lendingApplication.get().getNbfcId());
        lendingApplication.get().setEdi(!ObjectUtils.isEmpty(modifyAppRequest.getEdi()) ? modifyAppRequest.getEdi() : lendingApplication.get().getEdi());
        lendingApplication.get().setRepayment(!ObjectUtils.isEmpty(modifyAppRequest.getRepaymentAmount()) ? modifyAppRequest.getRepaymentAmount() : lendingApplication.get().getRepayment());
        lendingApplication.get().setPayableDays(!ObjectUtils.isEmpty(modifyAppRequest.getPayableDays()) ? modifyAppRequest.getPayableDays() : lendingApplication.get().getPayableDays());
        lendingApplication.get().setNbfcSendDate(!ObjectUtils.isEmpty(modifyAppRequest.getNbfcSendDate()) ? modifyAppRequest.getNbfcSendDate() : lendingApplication.get().getNbfcSendDate());
        lendingApplication.get().setDisbursalPartner(!ObjectUtils.isEmpty(modifyAppRequest.getDisbursalPartner()) ? modifyAppRequest.getDisbursalPartner() : lendingApplication.get().getDisbursalPartner());
        lendingApplication.get().setLoanDisbursalStatus(!ObjectUtils.isEmpty(modifyAppRequest.getLoanDisbursalStatus()) ? modifyAppRequest.getLoanDisbursalStatus() : lendingApplication.get().getLoanDisbursalStatus());
        lendingApplication.get().setTenure(!ObjectUtils.isEmpty(modifyAppRequest.getTenure()) ? modifyAppRequest.getTenure() + " months" : lendingApplication.get().getTenure());
        lendingApplication.get().setTenureInMonths(!ObjectUtils.isEmpty(modifyAppRequest.getTenure()) ? modifyAppRequest.getTenure() : lendingApplication.get().getTenureInMonths());
        lendingApplication.get().setDisbursalAmount(!ObjectUtils.isEmpty(modifyAppRequest.getDisbursalAmt()) ? modifyAppRequest.getDisbursalAmt() : lendingApplication.get().getDisbursalAmount());
        lendingApplication.get().setProcessingFee(!ObjectUtils.isEmpty(modifyAppRequest.getProcessingFee()) ? modifyAppRequest.getProcessingFee() : lendingApplication.get().getProcessingFee());
        lendingApplication.get().setDisburseTimestamp(!ObjectUtils.isEmpty(modifyAppRequest.getDisbursalDate()) ? modifyAppRequest.getDisbursalDate() : lendingApplication.get().getDisburseTimestamp());
        lendingApplicationDao.save(lendingApplication.get());
        log.info("successfully updated lending app  {}", modifyAppRequest.getApplicationId());
        if (!ObjectUtils.isEmpty(modifyAppRequest.getLenderDetailsId())) {
            Optional<LendingApplicationLenderDetails> lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findById(modifyAppRequest.getLenderDetailsId());
            if (lendingApplicationLenderDetails.isPresent()) {
                lendingApplicationLenderDetails.get().setStage(!ObjectUtils.isEmpty(modifyAppRequest.getStage()) ? modifyAppRequest.getStage() : lendingApplicationLenderDetails.get().getStage());
                lendingApplicationLenderDetails.get().setStatus(!ObjectUtils.isEmpty(modifyAppRequest.getLenderDetailStatus()) ? modifyAppRequest.getLenderDetailStatus() : lendingApplicationLenderDetails.get().getStatus());
                lendingApplicationLenderDetails.get().setBreStatus(!ObjectUtils.isEmpty(modifyAppRequest.getBreStatus()) ? modifyAppRequest.getBreStatus() : lendingApplicationLenderDetails.get().getBreStatus());
                lendingApplicationLenderDetails.get().setKycStatus(!ObjectUtils.isEmpty(modifyAppRequest.getKycStatus()) ? modifyAppRequest.getKycStatus() : lendingApplicationLenderDetails.get().getKycStatus());
                lendingApplicationLenderDetails.get().setSanctionStatus(!ObjectUtils.isEmpty(modifyAppRequest.getSancStatus()) ? modifyAppRequest.getSancStatus() : lendingApplicationLenderDetails.get().getSanctionStatus());
                lendingApplicationLenderDetails.get().setDrawDownStatus(!ObjectUtils.isEmpty(modifyAppRequest.getDrawdownStatus()) ? modifyAppRequest.getDrawdownStatus() : lendingApplicationLenderDetails.get().getDrawDownStatus());
                lendingApplicationLenderDetails.get().setLan(!ObjectUtils.isEmpty(modifyAppRequest.getLan()) ? modifyAppRequest.getLan() : lendingApplicationLenderDetails.get().getLan());
                lendingApplicationLenderDetails.get().setAccountId(!ObjectUtils.isEmpty(modifyAppRequest.getExternalLoanId()) ? modifyAppRequest.getExternalLoanId() : lendingApplicationLenderDetails.get().getAccountId());
                lendingApplicationLenderDetails.get().setUtrNo((modifyAppRequest.getUtr() != null) ? modifyAppRequest.getUtr() : lendingApplicationLenderDetails.get().getUtrNo());
                lendingApplicationLenderDetails.get().setLeadId((modifyAppRequest.getLeadId() != null) ? modifyAppRequest.getLeadId() : lendingApplicationLenderDetails.get().getLeadId());
                lendingApplicationLenderDetails.get().setLender((modifyAppRequest.getLaldLender() != null) ? modifyAppRequest.getLaldLender() : lendingApplicationLenderDetails.get().getLender());
                lendingApplicationLenderDetails.get().setLoanCreationTimestamp(!ObjectUtils.isEmpty(modifyAppRequest.getDisbursalDate()) ? modifyAppRequest.getDisbursalDate() : lendingApplicationLenderDetails.get().getLoanCreationTimestamp());
                if (modifyAppRequest.getDocStatusUpdate()) {
                    lendingApplicationLenderDetails.get().setDocUploadStatus(modifyAppRequest.getDocUploadStatus());
                    lendingApplicationLenderDetails.get().setFailedUpload(modifyAppRequest.getFailedUpload());
                }
                if (modifyAppRequest.getUpdateApr()) {
                    lendingApplicationLenderDetails.get().setAnnualRoi(null);
                    lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails.get());
                    DecimalFormat df = new DecimalFormat("#.##");
                    df.setRoundingMode(RoundingMode.DOWN);
                    lendingApplicationLenderDetails.get().setAnnualRoi(Double.valueOf(df.format(
                            lendingApplicationServiceV2.getApr(lendingApplication.get().getMerchantId(), lendingApplication.get().getId(), lendingApplication.get().getLoanAmount(),
                                    LenderOffDays.valueOf(lendingApplication.get().getLender()).getEdiModel().getNoOfEdiDaysInAWeek(), lendingApplication.get().getLender()))));
                }
                lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails.get());
                log.info("successfully updated lending lender details for {}", modifyAppRequest.getApplicationId());
            }
        }
        if (!ObjectUtils.isEmpty(modifyAppRequest.getLendingAppDetailsId())) {
            Optional<LendingApplicationDetails> lendingApplicationDetails = lendingApplicationDetailsDao.findById(modifyAppRequest.getLendingAppDetailsId());
            if (lendingApplicationDetails.isPresent()) {
                lendingApplicationDetails.get().setStage(!ObjectUtils.isEmpty(modifyAppRequest.getStage()) ? modifyAppRequest.getStage(): lendingApplicationDetails.get().getStage());
                lendingApplicationDetails.get().setEdiModel(!ObjectUtils.isEmpty(modifyAppRequest.getEdiModel()) ? modifyAppRequest.getEdiModel(): lendingApplicationDetails.get().getEdiModel());
                lendingApplicationDetails.get().setEdiModelModified(!ObjectUtils.isEmpty(modifyAppRequest.getEdiModelModified()) ? modifyAppRequest.getEdiModelModified(): lendingApplicationDetails.get().getEdiModelModified());
                lendingApplicationDetails.get().setLenderAssc(!ObjectUtils.isEmpty(modifyAppRequest.getLenderAssc()) ? modifyAppRequest.getLenderAssc(): lendingApplicationDetails.get().getLenderAssc());
                lendingApplicationDetailsDao.save(lendingApplicationDetails.get());
                log.info("successfully updated lending app details for {}", modifyAppRequest.getApplicationId());
            }
        }
        LendingGstDetail lendingGstDetail = lendingGstDao.findByApplicationId(modifyAppRequest.getApplicationId());
        if (!ObjectUtils.isEmpty(lendingGstDetail)) {
            lendingGstDetail.setDisbursedAccountPersonal(true);
            lendingGstDao.save(lendingGstDetail);
        }
        if (!ObjectUtils.isEmpty(modifyAppRequest.getLpsId())) {
            Optional<LendingPaymentSchedule> lendingPaymentSchedule = lendingPaymentScheduleDao.findById(modifyAppRequest.getLpsId());
            if (lendingPaymentSchedule.isPresent() && "CLOSED".equalsIgnoreCase(modifyAppRequest.getLpsStatus())) {
                if(!"CLOSED".equalsIgnoreCase(lendingPaymentSchedule.get().getStatus())){
                    loanStatusFlag = true;
                    log.info("setting loan flag as true in modifyAppDetails for merchantId :{}",lendingPaymentSchedule.get().getMerchantId());
                }
                lendingPaymentSchedule.get().setClosingDate(new Date());
                lendingPaymentSchedule.get().setStatus("CLOSED");
                log.info("closed loan {}", modifyAppRequest.getLpsId());
            }
            else if (lendingPaymentSchedule.isPresent() && "ACTIVE".equalsIgnoreCase(modifyAppRequest.getLpsStatus())) {
                lendingPaymentSchedule.get().setClosingDate(null);
                if(!"ACTIVE".equalsIgnoreCase(lendingPaymentSchedule.get().getStatus())){
                    loanStatusFlag = true;
                    log.info("setting loan flag as true in modifyAppDetails for merchantId :{}",lendingPaymentSchedule.get().getMerchantId());
                }
                lendingPaymentSchedule.get().setStatus("ACTIVE");
                log.info("active marked loan {}", modifyAppRequest.getLpsId());
            }
            if(lendingPaymentSchedule.isPresent() && !ObjectUtils.isEmpty(modifyAppRequest.getLpsStartDate())) {
                log.info("setting loan start date as {} for lpsId : {} ", modifyAppRequest.getLpsStartDate(), lendingPaymentSchedule.get().getId());
                lendingPaymentSchedule.get().setStartDate(modifyAppRequest.getLpsStartDate());
                lendingPaymentSchedule.get().setNextEdiDate(modifyAppRequest.getLpsStartDate());
            }
            lendingPaymentScheduleDao.save(lendingPaymentSchedule.get());

            if(loanStatusFlag) {
                Long merchantId = lendingPaymentSchedule.get().getMerchantId();
                log.info("sending loan flag status in modifyAppDetails for merchantId {}:",merchantId);
                sherlocLoanStatusChangeService.pushLoanStatusChangeEventToKafka(merchantId, lendingPaymentSchedule.get().getStatus());
            }
        }
        return new ApiResponse<>(true,"successfully updated application details");
    }

    public  ApiResponse<?> modifyAppDetailsV2(ModifyAppRequest modifyAppRequest) {
        for (String apps : modifyAppRequest.getApplicationList().split(";")) {
            Map<String, String> request = new HashMap() {{
                put("application_id", apps);
                put("documents", modifyAppRequest.getDocs());
                put("systemManagedState", false);
            }};
            confluentKafkaTemplate.send("invoke_data_upload", request);
        }
        return new ApiResponse<>(true,"success");
    }
    }
