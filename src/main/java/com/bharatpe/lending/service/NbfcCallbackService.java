package com.bharatpe.lending.service;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingResubmitTaskDao;
import com.bharatpe.lending.common.dto.KafkaAudit;
import com.bharatpe.lending.common.entity.LendingResubmitTask;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dto.MamtaDecisionAuditDTO;
import com.bharatpe.lending.dto.NbfcDecisionCallbackRequestDTO;
import com.bharatpe.lending.dto.PostPayoutAuditDto;
import com.bharatpe.lending.loanV2.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;
import java.util.Date;
import java.util.Objects;

import static com.bharatpe.lending.common.enums.LendingEnum.LENDER.MAMTA0;

@Slf4j
@Service
public class NbfcCallbackService {

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LendingResubmitTaskDao lendingResubmitTaskDao;

    @Autowired
    KafkaTemplate<String, Object> kafkaTemplate;

    public ApiResponse<?> processDecision(NbfcDecisionCallbackRequestDTO nbfcDecisionCallbackRequestDTO) {
        final LendingApplication lendingApplication = lendingApplicationDao.findByExternalLoanId(nbfcDecisionCallbackRequestDTO.getPartnerLoanId());

        KafkaAudit<MamtaDecisionAuditDTO> kafkaAudit = new KafkaAudit<>("easy_loan", "lending", "mamta_decision_callback", null);
        MamtaDecisionAuditDTO mamtaDecisionAuditDTO = new MamtaDecisionAuditDTO();
        mamtaDecisionAuditDTO.setNbfcDecisionCallbackRequestDTO(nbfcDecisionCallbackRequestDTO);
        mamtaDecisionAuditDTO.setExternalLoanId(nbfcDecisionCallbackRequestDTO.getPartnerLoanId());

        if (ObjectUtils.isEmpty(lendingApplication)) {
            log.error("Application not found for nbfcDecisionCallbackRequestDTO : {}", nbfcDecisionCallbackRequestDTO);
            final ApiResponse<Object> apiResponse = new ApiResponse<>(false, "Application with this id not found");
            mamtaDecisionAuditDTO.setResponse(apiResponse);
            kafkaAudit.setData(mamtaDecisionAuditDTO);
            pushKafkaAudit(kafkaAudit);
            return apiResponse;
        }

        // no action to be taken if loan is accepted by the lender
        if ("ACCEPT".equalsIgnoreCase(nbfcDecisionCallbackRequestDTO.getStatus())) {
            log.info("offer accepted by lender for nbfcDecisionCallbackRequestDTO: {}", nbfcDecisionCallbackRequestDTO);
            final ApiResponse<Object> apiResponse = new ApiResponse<>(true, "success");
            mamtaDecisionAuditDTO.setResponse(apiResponse);
            kafkaAudit.setData(mamtaDecisionAuditDTO);
            pushKafkaAudit(kafkaAudit);
            return apiResponse;
        } else if ("REJECT".equalsIgnoreCase(nbfcDecisionCallbackRequestDTO.getStatus())) {

            LendingResubmitTask resubmitTask = lendingResubmitTaskDao.findTopByApplicationIdAndMerchantId(lendingApplication.getId()
              , lendingApplication.getMerchantId());

            // if already a re-sign request exists then return success
            if (Objects.nonNull(resubmitTask) && Objects.nonNull(resubmitTask.getResign()) && Objects.nonNull(resubmitTask.getResignReason())) {
                log.info("Already a re-sign task exists for the applicationId : {}", lendingApplication.getId());
                final ApiResponse<Object> apiResponse = new ApiResponse<>(true, "The loan decision has been marked rejected already.");
                mamtaDecisionAuditDTO.setResponse(apiResponse);
                kafkaAudit.setData(mamtaDecisionAuditDTO);
                pushKafkaAudit(kafkaAudit);
                return apiResponse;
            }


            // else if rejected change the lender to mamta0
            if (Objects.isNull(resubmitTask)){
                resubmitTask = new LendingResubmitTask();
                resubmitTask.setMerchantId(lendingApplication.getMerchantId());
                resubmitTask.setApplicationId(lendingApplication.getId());
            }
            resubmitTask.setResign(Boolean.TRUE);
            resubmitTask.setResignDone(Boolean.FALSE);
            resubmitTask.setResignReason("LENDER_CHANGE_FROM_" + lendingApplication.getLender() + "_TO_" + MAMTA0.name());

            log.info("changing lender from : {} to {}", lendingApplication.getLender(), MAMTA0.name());
            lendingApplication.setLender(MAMTA0.name());

            lendingApplicationDao.save(lendingApplication);
            lendingResubmitTaskDao.save(resubmitTask);

            // add code to send notification for the lender and resign to the merchant

            final ApiResponse<Object> apiResponse = new ApiResponse<>(true, "success");
            mamtaDecisionAuditDTO.setResponse(apiResponse);
            kafkaAudit.setData(mamtaDecisionAuditDTO);
            pushKafkaAudit(kafkaAudit);
            return apiResponse;
        }

        final ApiResponse<Object> apiResponse = new ApiResponse<>(false, "Internal Error");
        mamtaDecisionAuditDTO.setResponse(apiResponse);
        kafkaAudit.setData(mamtaDecisionAuditDTO);
        pushKafkaAudit(kafkaAudit);
        return apiResponse;
    }

    public void pushKafkaAudit(KafkaAudit kafkaAudit) {
        try {
            log.info("pushing kafka event for {}", kafkaAudit);
            kafkaTemplate.send("easyloan_audit_data",kafkaAudit);
        } catch (Exception e) {
            log.error("error while sending audit data {} {}", kafkaAudit, Arrays.asList(e.getStackTrace()));
        }
    }

}
