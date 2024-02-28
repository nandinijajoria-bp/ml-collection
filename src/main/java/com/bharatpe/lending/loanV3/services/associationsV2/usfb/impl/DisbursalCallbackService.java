package com.bharatpe.lending.loanV3.services.associationsV2.usfb.impl;

import com.bharatpe.lending.loanV3.dto.DisbursalCallbackCommonDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.response.usfb.DisbursalCallbackResponseDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;

@Slf4j
@Service
public class DisbursalCallbackService {

    @Autowired
    ObjectMapper objectMapper;

    public DisbursalCallbackCommonDTO handleCallbackResponse(NBFCResponseDTO nbfcResponseDTO) {
        try {
            if(!ObjectUtils.isEmpty(nbfcResponseDTO) && nbfcResponseDTO.getSuccess() && !ObjectUtils.isEmpty(nbfcResponseDTO.getData())) {
                DisbursalCallbackResponseDTO disbursalCallbackResponse = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDTO.getData()), DisbursalCallbackResponseDTO.class);
                Boolean disbursalStatus = "LOAN_DISBURSED".equalsIgnoreCase(disbursalCallbackResponse.getEventName()) && "disbursed".equalsIgnoreCase(disbursalCallbackResponse.getPayload().getDecisionStatus()) ? Boolean.TRUE : Boolean.FALSE;
                DisbursalCallbackCommonDTO disbursalCallbackCommonDTO = DisbursalCallbackCommonDTO.builder()
                        .leadId(disbursalCallbackResponse.getPayload().getLeadId())
                        .status(disbursalStatus)
                        .lan(disbursalCallbackResponse.getPayload().getLoanAccountNumber())
                        .disbursalAmount(Double.valueOf(disbursalCallbackResponse.getPayload().getDisbursalAmount()))
                        .interestRate(disbursalCallbackResponse.getPayload().getInterestRate())
                        .utr(disbursalCallbackResponse.getPayload().getUtrNo())
                        .build();
                return disbursalCallbackCommonDTO;
            }
            return DisbursalCallbackCommonDTO.builder().status(Boolean.FALSE).build();
        } catch (Exception e) {
            log.info("Exception in disbursal callback response handling of {} for {}, {}, {}", nbfcResponseDTO.getLender(), nbfcResponseDTO.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
            return DisbursalCallbackCommonDTO.builder().status(Boolean.FALSE).build();
        }
    }
}
