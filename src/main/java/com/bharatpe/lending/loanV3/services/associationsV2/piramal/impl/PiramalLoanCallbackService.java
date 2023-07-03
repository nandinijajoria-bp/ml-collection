package com.bharatpe.lending.loanV3.services.associationsV2.piramal.impl;

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
import com.bharatpe.lending.loanV2.dto.ApiResponse;
import com.bharatpe.lending.loanV3.dto.DrawdownCallbackResponseDto;
import com.bharatpe.lending.loanV3.dto.piramal.CreateLoanCallbackDTO;
import com.bharatpe.lending.loanV3.dto.piramal.NbfcResponseDto;
import com.bharatpe.lending.loanV3.dto.piramal.PiramalDisbursalResponse;
import com.bharatpe.lending.loanV3.enums.DisbursalStatus;
import com.bharatpe.lending.service.LiquiloansService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.io.IOException;
import java.util.*;

@Service
@Slf4j
public class PiramalLoanCallbackService {
    @Autowired
    private LendingApplicationDetailsDao lendingApplicationDetailsDao;
    @Autowired
    private LendingPaymentScheduleDao lendingPaymentScheduleDao;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LiquiloansService liquiloansService;

    @Value("${handle.failed.disbursal:false}")
    Boolean handleFailedDisbursal;


    public ApiResponse createLoanCallback(NbfcResponseDto nbfcResponseDto) {

        Long applicationId = Long.valueOf(nbfcResponseDto.getApplicationId());
        try {
            if (!nbfcResponseDto.getSuccess()) {
                log.info("failure case in create loan callback for applicationId with error: {} {}", applicationId, nbfcResponseDto.getError());
                return new ApiResponse(true, "error callback acknowledged");
            }
            CreateLoanCallbackDTO createLoanCallbackDTO = objectMapper.readValue( objectMapper.writeValueAsString
                    (nbfcResponseDto.getData()), CreateLoanCallbackDTO.class);
            log.info("create loan callback dto for applicationId: {} {}",createLoanCallbackDTO, applicationId);
            LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao
                    .findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusOrderByIdDesc(applicationId, Status.ACTIVE.name());
            Optional<LendingApplication> lendingApplicationOptional = lendingApplicationDao.findById(applicationId);
            if (!lendingApplicationOptional.isPresent()) {
                log.info("lending application not found for applicationId: {}", applicationId);
                return new ApiResponse(false, "application not found");
            }
            updateLANIfMissingInLendingApplication(createLoanCallbackDTO.getLoanAccountNumber(), lendingApplicationOptional);
            LendingApplication lendingApplication = lendingApplicationOptional.get();
            lendingApplicationLenderDetails.setLan(createLoanCallbackDTO.getLoanAccountNumber());
            lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);
            if (Objects.nonNull(createLoanCallbackDTO.getLoanCreationSuccessful()) && createLoanCallbackDTO.getLoanCreationSuccessful()
                    && !Arrays.asList("DISBURSED","PROCESSING").contains(lendingApplication.getLoanDisbursalStatus())
            ) {
                lendingApplication.setLoanDisbursalStatus("PROCESSING");
                lendingApplicationDao.save(lendingApplication);
                return new ApiResponse(true, "callback acknowledged");
            }
        } catch (Exception e) {
            log.error("exception while handling loan creation callback for applicationId: {} {} {}",applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return new ApiResponse(false, "failed to acknowledge callback");
    }

    public ApiResponse disbursalCallback(NbfcResponseDto nbfcResponseDto) throws JsonProcessingException {
        log.info("disbursement callback received {}", objectMapper.writeValueAsString(nbfcResponseDto));
        PiramalDisbursalResponse piramalDisbursalResponse = null;
        try {
            piramalDisbursalResponse = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDto.getData()), PiramalDisbursalResponse.class);
        } catch (Exception e) {
            log.error("json parsing issue while casting response {} {}", nbfcResponseDto , e.getMessage());
        }
        log.info("disbursement callback casted object received {}", piramalDisbursalResponse);
        // 3 cases:
        // success (mark it and open loan)
        // reversed (mark loan as inactive)
        // failed (reject the application)
        LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(Long.valueOf(nbfcResponseDto.getApplicationId()), Status.ACTIVE.name(), Lender.PIRAMAL.name());
        if (!lendingApplicationLenderDetails.getLeadId().equalsIgnoreCase(piramalDisbursalResponse.getLeadId()))
        {
            log.info("lead id mismatch for the callback event {}", nbfcResponseDto);
            return new ApiResponse(false, "lead id mismatch for the callback event");
        }
        if (Arrays.asList(
//                LenderAssociationStatus.DRAWDOWN_HARD_FAILED.name(),
                LenderAssociationStatus.DRAWDOWN_COMPLETED.name()).contains(lendingApplicationLenderDetails.getDrawDownStatus()))
        {
            log.info("callback status {} for application {}", lendingApplicationLenderDetails.getDrawDownStatus(), nbfcResponseDto.getApplicationId());
            return new ApiResponse(false, "callback status is " + lendingApplicationLenderDetails.getDrawDownStatus());
        }
        Optional<LendingApplication> lendingApplication = lendingApplicationDao.findById(Long.valueOf(nbfcResponseDto.getApplicationId()));
        updateLANIfMissingInLendingApplication(piramalDisbursalResponse.getLoanAccountNumber(), lendingApplication);
        LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(lendingApplication.get().getId());
        if (!LenderAssociationStages.COMPLETED.name().equalsIgnoreCase(lendingApplicationDetails.getStage())) {
            lendingApplicationDetails.setStage(LenderAssociationStages.COMPLETED.name());
            lendingApplicationLenderDetails.setStage(LenderAssociationStages.COMPLETED.name());
            lendingApplicationDetailsDao.save(lendingApplicationDetails);
        }
        if (piramalDisbursalResponse.getDisbursementSuccessful()) {
            return handleSuccessCallback(nbfcResponseDto, piramalDisbursalResponse, lendingApplicationLenderDetails, lendingApplication);
        }
        lendingApplicationLenderDetails.setLan(piramalDisbursalResponse.getLoanAccountNumber());
        lendingApplicationLenderDetails.setDrawDownStatus(LenderAssociationStatus.DRAWDOWN_HARD_FAILED.name());
        lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);
        if (handleFailedDisbursal) {
            switch (DisbursalStatus.valueOf(piramalDisbursalResponse.getStatus())) {
                case FAILED:
                    lendingApplication.get().setLoanDisbursalStatus("FAILED");
                    lendingApplicationDao.save(lendingApplication.get());
                case REVERSED:
                    LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.findByApplicationId(lendingApplication.get().getId());
                    if (!ObjectUtils.isEmpty(lendingPaymentSchedule) &&  "ACTIVE".equalsIgnoreCase(lendingPaymentSchedule.getStatus())) {
                        lendingPaymentSchedule.setStatus("INACTIVE");
                        lendingPaymentScheduleDao.save(lendingPaymentSchedule);
                    }
            }
        }
        return new ApiResponse(true, "disbursement event processed");
    }

    private ApiResponse handleSuccessCallback(NbfcResponseDto nbfcResponseDto, PiramalDisbursalResponse piramalDisbursalResponse, LendingApplicationLenderDetails lendingApplicationLenderDetails, Optional<LendingApplication> lendingApplication) {
        PostPayoutResponseDto postPayoutResponseDto =  liquiloansService.populatePostPayoutSchedule(
                PostPayoutRequestDto.builder()
                        .applicationId(String.valueOf(lendingApplication.get().getExternalLoanId()))
                        .disbursalDate(new Date())
                        .lender(nbfcResponseDto.getLender())
                        .loanDisbursalStatus("DISBURSED")
                        .nbfcId(lendingApplication.get().getNbfcId())
                        .disbursedAmount(Optional.ofNullable(piramalDisbursalResponse.getDisbursedAmount()).orElse(lendingApplication.get().getDisbursalAmount()))
                        .utr(piramalDisbursalResponse.getUtrNumber())
                        .build()).getBody();
        if (null == postPayoutResponseDto || !"SUCCESS".equalsIgnoreCase(postPayoutResponseDto.getStatus())) {
            return new ApiResponse(false, "failed to process loan event !");
        }
        lendingApplicationLenderDetails.setUtrNo(piramalDisbursalResponse.getUtrNumber());
        lendingApplicationLenderDetails.setLan(piramalDisbursalResponse.getLoanAccountNumber());
        lendingApplicationLenderDetails.setLoanCreationTimestamp(new Date());
        lendingApplicationLenderDetails.setDrawDownStatus(LenderAssociationStatus.DRAWDOWN_COMPLETED.name());
        lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);
        return new ApiResponse(true, "loan created successfully");
    }

    private void updateLANIfMissingInLendingApplication(String loanAccountNumber, Optional<LendingApplication> lendingApplication) {
        if (ObjectUtils.isEmpty(lendingApplication.get().getNbfcId())
        ) {
            log.info("update forced NBFC id for application id: {}, {}", loanAccountNumber, lendingApplication.get().getId());
            lendingApplication.get().setNbfcId(loanAccountNumber);
            lendingApplication.get().setSendToNbfc("YES");
            lendingApplication.get().setNbfcSendDate(Calendar.getInstance().getTime());
            lendingApplicationDao.save(lendingApplication.get());
        }
    }
}
