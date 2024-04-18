package com.bharatpe.lending.loanV3.services.associationsV2.capri.impl;

import com.bharatpe.lending.loanV3.dto.DisbursalCallbackCommonDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.response.capri.CapriDisbursalCallbackResponseDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;

@Slf4j
@Service
public class CapriDisbursalCallbackService {
    @Autowired
    ObjectMapper objectMapper;

    public DisbursalCallbackCommonDTO parseCallbackResponse(NBFCResponseDTO nbfcResponseDTO) {
        try {
            if(!ObjectUtils.isEmpty(nbfcResponseDTO) && nbfcResponseDTO.getSuccess() && !ObjectUtils.isEmpty(nbfcResponseDTO.getData())) {
                CapriDisbursalCallbackResponseDTO disbursalCallbackResponse = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDTO.getData()), CapriDisbursalCallbackResponseDTO.class);
                Boolean disbursalStatus = "Disbursed".equalsIgnoreCase(disbursalCallbackResponse.getStatus()) ? Boolean.TRUE : Boolean.FALSE;
                DisbursalCallbackCommonDTO disbursalCallbackCommonDTO = DisbursalCallbackCommonDTO.builder()
                        .applicationId(Long.valueOf(nbfcResponseDTO.getApplicationId()))
                        .lender(nbfcResponseDTO.getLender())
                        .disbursalDate(Date.from(disbursalCallbackResponse.getDisbdate().atZone(ZoneId.systemDefault()).toInstant()))
                        .leadId(String.valueOf(disbursalCallbackResponse.getLoanApplicationId()))
                        .status(disbursalStatus)
                        .lan(String.valueOf(disbursalCallbackResponse.getLoanId()))
                        .disbursalAmount(disbursalCallbackResponse.getNetDisbursement())
                        .utr(disbursalCallbackResponse.getUtr())
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
