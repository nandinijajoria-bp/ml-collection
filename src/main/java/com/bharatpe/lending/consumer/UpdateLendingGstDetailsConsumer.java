package com.bharatpe.lending.consumer;

import com.bharatpe.lending.dto.UpdateLendingGstDetailsRequestDTO;
import com.bharatpe.lending.service.LoanDetailsService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

@Service
@Slf4j
public class UpdateLendingGstDetailsConsumer {

    @Autowired
    LoanDetailsService loanDetailsService;

    @Autowired
    ObjectMapper objectMapper;

    @KafkaListener(topics = "update_gst_details", autoStartup = "false")
    public void consume(String data) {
        log.info("Update Gst Details : {}", data);
        try {
            Map<String, Object> updateGstDetailsMap = objectMapper.readValue(data, new TypeReference<Map<String,
              Object>>() {
            });

            if (Objects.isNull(updateGstDetailsMap.get("application_id")) || Objects.isNull(updateGstDetailsMap.get(
              "arrived_score"))) {
                log.info("Invalid request to update_gst_details");
                return;
            }
            Long applicationId = Long.parseLong(String.valueOf(updateGstDetailsMap.get("application_id")));
            Double arrivedScore = Double.parseDouble(String.valueOf(updateGstDetailsMap.get("arrived_score")));

            log.info("Update gst details applicationId : {} and arrivedScore : {}", applicationId, arrivedScore);

            loanDetailsService.updateLendingGstDetails(applicationId,
              UpdateLendingGstDetailsRequestDTO.builder().arrivedScore(arrivedScore).build());

        } catch (IOException e) {
            log.error("IO Exception in update gst details ---", e);
        }
    }
}
