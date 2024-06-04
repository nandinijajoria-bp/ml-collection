package com.bharatpe.lending.loanV3.consumer;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.util.ConfigResolver;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dto.PostPayoutRequestDto;
import com.bharatpe.lending.loanV3.dto.DrawdownCallbackResponseDto;
import com.bharatpe.lending.service.LiquiloansService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Component

@Slf4j
public class DrawdownRequestKafka {

    @Autowired
    ConfigResolver configResolver;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    LendingApplicationDao lendingApplicationDao;
    
    @Autowired
    LiquiloansService liquiloansService;


    @KafkaListener(topics = "${abfl.drawdown.callback.topic:drawdown-callback}", concurrency = "5")
    @KafkaListener(
            topics="${abfl.drawdown.callback.topic:drawdown-callback}",
            concurrency = "5",
            autoStartup = "${kafka.confluent.consumer:false}",
            containerFactory = "ConfluentKafkaListenerContainer")
    public void drawdownEventListener(String request) {
        try {
            MDC.put("requestId", UUID.randomUUID().toString());
            log.info("Received drawdown callback request:{}", request);
            DrawdownCallbackResponseDto drawdownCallbackResponseDto = configResolver.getConfig(request, DrawdownCallbackResponseDto.class);
            Optional<LendingApplication> lendingApplication = lendingApplicationDao.findById(Long.valueOf(drawdownCallbackResponseDto.getApplicationId()));
            if (!lendingApplication.isPresent()) {
                log.info("no application found for id {}", drawdownCallbackResponseDto.getData());
                return;
            }
            LendingApplicationLenderDetails existingLendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusOrderByIdDesc(lendingApplication.get().getId(), "ACTIVE");
            if (ObjectUtils.isEmpty(existingLendingApplicationLenderDetails) || !drawdownCallbackResponseDto.getLender().equalsIgnoreCase(existingLendingApplicationLenderDetails.getLender()) ||
                    !ObjectUtils.isEmpty(existingLendingApplicationLenderDetails.getLoanCreationTimestamp())) {
                log.info("lender mismatch while callback ack / callback already received for application {}", drawdownCallbackResponseDto.getApplicationId());
                return;
            }
            if (!drawdownCallbackResponseDto.getSuccess() ||
                    ObjectUtils.isEmpty(drawdownCallbackResponseDto.getData()) ||
                    ObjectUtils.isEmpty(drawdownCallbackResponseDto.getData().getData()) ||
                    ObjectUtils.isEmpty(drawdownCallbackResponseDto.getData().getData().getLan())
            ) {
                log.info("drawdown callback resulted in failure for  {}", lendingApplication.get().getId());
                return;
            }
            Date disbursalDate = Calendar.getInstance().getTime();

            if(!ObjectUtils.isEmpty(drawdownCallbackResponseDto.getData().getData().getDisbursalDate())) {
                try {
                    disbursalDate = new SimpleDateFormat("yyyy-MM-dd").parse(drawdownCallbackResponseDto.getData().getData().getDisbursalDate());
                } catch (Exception e) {
                    log.error("Exception in parsing disbursal date : {}", e.getMessage());
                    disbursalDate = Calendar.getInstance().getTime();
                }
            }

            DrawdownCallbackResponseDto.Data data = drawdownCallbackResponseDto.getData().getData();
            existingLendingApplicationLenderDetails.setUtrNo(data.getUtrNo());
            existingLendingApplicationLenderDetails.setLan(data.getLan());
            existingLendingApplicationLenderDetails.setLoanCreationTimestamp(disbursalDate);
            existingLendingApplicationLenderDetails.setDrawDownStatus(LenderAssociationStatus.DRAWDOWN_COMPLETED.name());
            lendingApplicationLenderDetailsDao.save(existingLendingApplicationLenderDetails);


            if (ObjectUtils.isEmpty(lendingApplication.get().getNbfcId())
            && data.getAccountId().equalsIgnoreCase(lendingApplication.get().getExternalLoanId())
            ) {
                log.info("update forced NBFC id for application id: {}, {}", data.getLan(), lendingApplication.get().getId());
                lendingApplication.get().setNbfcId(data.getLan());
                lendingApplication.get().setSendToNbfc("YES");
                lendingApplication.get().setNbfcSendDate(Calendar.getInstance().getTime());
                lendingApplicationDao.save(lendingApplication.get());
            }

            // TODO: 16/11/22  todo final update edi date and rest in liqui svc
            liquiloansService.populatePostPayoutSchedule(
                PostPayoutRequestDto.builder()
                        .applicationId(String.valueOf(lendingApplication.get().getExternalLoanId()))
                        .disbursalDate(disbursalDate)
                        .lender(drawdownCallbackResponseDto.getLender())
                        .loanDisbursalStatus("DISBURSED")
                        .nbfcId(lendingApplication.get().getNbfcId())
                        .disbursedAmount(Optional.ofNullable(data.getAmount()).orElse(lendingApplication.get().getDisbursalAmount()))
                        .utr(data.getUtrNo())
                        .build());
        } catch (Exception e) {
            log.error("exception occurred while acknowledging drawdown event for app {}", request);
        }
    }
}
