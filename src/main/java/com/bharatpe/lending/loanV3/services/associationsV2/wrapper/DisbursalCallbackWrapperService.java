package com.bharatpe.lending.loanV3.services.associationsV2.wrapper;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dto.PostPayoutRequestDto;
import com.bharatpe.lending.dto.PostPayoutResponseDto;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.loanV3.dto.DisbursalCallbackCommonDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.associationsV2.AssociationServiceUtil;
import com.bharatpe.lending.service.LiquiloansService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Optional;

@Slf4j
@Service
public class DisbursalCallbackWrapperService {
    @Autowired
    ObjectMapper objectMapper;
    
    @Autowired
    AssociationServiceUtil associationServiceUtil;

    @Autowired
    LendingApplicationDao lendingApplicationDao;
    
    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    LendingApplicationDetailsDao lendingApplicationDetailsDao;

    @Autowired
    LiquiloansService liquiloansService;

    @Autowired
    CommonService commonService;

    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;

    public void disbursalCallback(NBFCResponseDTO nbfcResponse) {
        try {
            log.info("disbursement callback received {}", objectMapper.writeValueAsString(nbfcResponse));
            DisbursalCallbackCommonDTO disbursalCallbackResponse = associationServiceUtil.handleDisbursalCallbackResponse(nbfcResponse.getLender(), nbfcResponse);
            LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(Long.valueOf(nbfcResponse.getApplicationId()), Status.ACTIVE.name(), nbfcResponse.getLender());
            log.info("disbursalCallbackResponse is {} from callback event of {} for {}", disbursalCallbackResponse, nbfcResponse.getLender(), nbfcResponse);
            if(ObjectUtils.isEmpty(disbursalCallbackResponse)) {
                return;
            }
            if (!lendingApplicationLenderDetails.getLeadId().equalsIgnoreCase(disbursalCallbackResponse.getLeadId())) {
                log.info("lead id mismatch for the callback event of {} for {}", nbfcResponse.getLender(), nbfcResponse);
                return;
            }
            if (Arrays.asList(LenderAssociationStatus.DRAWDOWN_COMPLETED.name()).contains(lendingApplicationLenderDetails.getDrawDownStatus())) {
                log.info("disbursal callback already consumed for {}", nbfcResponse.getApplicationId());
                return;
            }
            Optional<LendingApplication> lendingApplication = lendingApplicationDao.findById(Long.valueOf(nbfcResponse.getApplicationId()));
            updateLANIfMissingInLendingApplication(disbursalCallbackResponse.getLan(), lendingApplication.get());
            LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(lendingApplication.get().getId());
            if (!LenderAssociationStages.COMPLETED.name().equalsIgnoreCase(lendingApplicationDetails.getStage())) {
                lendingApplicationDetails.setStage(LenderAssociationStages.COMPLETED.name());
                lendingApplicationLenderDetails.setStage(LenderAssociationStages.COMPLETED.name());
                lendingApplicationDetailsDao.save(lendingApplicationDetails);
            }
            if (disbursalCallbackResponse.getStatus()) {
                handleSuccessCallback(nbfcResponse, disbursalCallbackResponse, lendingApplicationLenderDetails, lendingApplication);
                log.info("Disbursal callback consumed successfully of lender {} for {}", nbfcResponse.getLender(), nbfcResponse.getApplicationId());
                return;
            }
            log.info("marking disbursal status as failed for {} for {}", nbfcResponse.getLender(), nbfcResponse.getApplicationId());
            lendingApplicationLenderDetails.setLan(disbursalCallbackResponse.getLan());
            lendingApplicationLenderDetails.setDrawDownStatus(LenderAssociationStatus.DRAWDOWN_HARD_FAILED.name());
            lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);
            lendingApplication.get().setLoanDisbursalStatus("FAILED");
            if (nbfcResponse.getLender().equalsIgnoreCase(Lender.TRILLIONLOANS.name()) && lendingApplication.get().getLoanType().equalsIgnoreCase(LoanType.TOPUP.name())) {
                LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto = LenderAssociationDetailsRequestDto.builder()
                        .applicationId(lendingApplication.get().getId()).merchantId(lendingApplication.get().getMerchantId())
                        .lendingApplication(lendingApplication.get()).lendingApplicationLenderDetails(lendingApplicationLenderDetails).build();
                commonService.manageApplicationStateAndRejectApplication(lenderAssociationDetailsRequestDto);
                markPreviousLoanActive(lendingApplication.get());
            }
            lendingApplicationDao.save(lendingApplication.get());
            log.info("Disbursal callback consumed successfully of lender {} for {}", nbfcResponse.getLender(), nbfcResponse.getApplicationId());
        } catch (Exception e) {
          log.info("Exception in consuming disbursal callback of lender {} for {}, {}, {}", nbfcResponse.getLender(), nbfcResponse.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
    }

    private void handleSuccessCallback(NBFCResponseDTO nbfcResponse, DisbursalCallbackCommonDTO disbursalCallbackResponse, LendingApplicationLenderDetails lendingApplicationLenderDetails, Optional<LendingApplication> lendingApplication) {
        Date disbursalDate = ObjectUtils.isEmpty(disbursalCallbackResponse.getDisbursalDate()) ? new Date() : disbursalCallbackResponse.getDisbursalDate();
        PostPayoutResponseDto postPayoutResponseDto =  liquiloansService.populatePostPayoutSchedule(
                PostPayoutRequestDto.builder()
                        .applicationId(String.valueOf(lendingApplication.get().getExternalLoanId()))
                        .disbursalDate(disbursalDate)
                        .lender(nbfcResponse.getLender())
                        .loanDisbursalStatus("DISBURSED")
                        .nbfcId(lendingApplication.get().getNbfcId())
                        .disbursedAmount(Optional.ofNullable(disbursalCallbackResponse.getDisbursalAmount()).orElse(lendingApplication.get().getDisbursalAmount()))
                        .utr(disbursalCallbackResponse.getUtr())
                        .build()).getBody();
        if (null == postPayoutResponseDto || !"SUCCESS".equalsIgnoreCase(postPayoutResponseDto.getStatus())) {
            log.info("failed to process loan event of {} for {}", nbfcResponse.getLender(), nbfcResponse.getApplicationId());
            return;
        }
        lendingApplicationLenderDetails.setUtrNo(disbursalCallbackResponse.getUtr());
        lendingApplicationLenderDetails.setLan(disbursalCallbackResponse.getLan());
        lendingApplicationLenderDetails.setLoanCreationTimestamp(disbursalDate);
        lendingApplicationLenderDetails.setDrawDownStatus(LenderAssociationStatus.DRAWDOWN_COMPLETED.name());
        lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);
        log.info("loan created successfully of lender {} for {}", nbfcResponse.getLender(), nbfcResponse.getApplicationId());
    }

    private void updateLANIfMissingInLendingApplication(String loanAccountNumber, LendingApplication lendingApplication) {
        if (ObjectUtils.isEmpty(lendingApplication.getNbfcId())
        ) {
            log.info("update forced NBFC id for application id: {}, {}", loanAccountNumber, lendingApplication.getId());
            lendingApplication.setNbfcId(loanAccountNumber);
            lendingApplication.setSendToNbfc("YES");
            lendingApplication.setNbfcSendDate(ObjectUtils.isEmpty(lendingApplication.getNbfcSendDate()) ? Calendar.getInstance().getTime() : lendingApplication.getNbfcSendDate());
            lendingApplicationDao.save(lendingApplication);
        }
    }

    private void markPreviousLoanActive(LendingApplication lendingApplication) {
        LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findByApplicationId(lendingApplication.getId());
        LendingPaymentSchedule prevLendingPaymentSchedule = null;
        if (!ObjectUtils.isEmpty(lendingApplicationDetails) && !ObjectUtils.isEmpty(lendingApplicationDetails.getPrevAppId())) {
            prevLendingPaymentSchedule = lendingPaymentScheduleDao.findByApplicationId(lendingApplicationDetails.getPrevAppId());
        }
        prevLendingPaymentSchedule = ObjectUtils.isEmpty(prevLendingPaymentSchedule) ? liquiloansService.updatePreviousLoan(lendingApplication) : prevLendingPaymentSchedule;
        if (ObjectUtils.isEmpty(prevLendingPaymentSchedule)) {
            log.error("Previous LPS not found for application id : {}", lendingApplication.getId());
            throw new RuntimeException("Previous LPS not found for application id: " + lendingApplication.getId());
        }
        if ("INACTIVE_TOPUP".equalsIgnoreCase(prevLendingPaymentSchedule.getStatus()) && "TOPUP".equalsIgnoreCase(lendingApplication.getLoanType())) {
            log.info("Marking previous loan as active for application id: {}, prev application id: {}", lendingApplication.getId(), prevLendingPaymentSchedule.getApplicationId());
            prevLendingPaymentSchedule.setStatus("ACTIVE");
            lendingPaymentScheduleDao.save(prevLendingPaymentSchedule);
        } else {
            log.info("Prev application id: {} is in {} status", prevLendingPaymentSchedule.getApplicationId(), prevLendingPaymentSchedule.getStatus());
        }
    }
}
