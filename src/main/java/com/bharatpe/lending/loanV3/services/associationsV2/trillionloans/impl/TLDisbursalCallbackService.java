package com.bharatpe.lending.loanV3.services.associationsV2.trillionloans.impl;

import com.bharatpe.lending.loanV3.dto.DisbursalCallbackCommonDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.response.trillionloans.TLDisbursalCallbackResponseDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.text.SimpleDateFormat;
import java.util.Arrays;

@Slf4j
@Service
public class TLDisbursalCallbackService {
    @Autowired
    ObjectMapper objectMapper;

    public DisbursalCallbackCommonDTO parseCallbackResponse(NBFCResponseDTO nbfcResponseDTO) {
        try {
            if(!ObjectUtils.isEmpty(nbfcResponseDTO) && nbfcResponseDTO.getSuccess() && !ObjectUtils.isEmpty(nbfcResponseDTO.getData())) {
                TLDisbursalCallbackResponseDto disbursalCallbackResponse = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDTO.getData()), TLDisbursalCallbackResponseDto.class);
                Boolean disbursalStatus = "Disbursed".equalsIgnoreCase(disbursalCallbackResponse.getStatus()) ? Boolean.TRUE : Boolean.FALSE;
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat("MMM dd, yyyy");
                return DisbursalCallbackCommonDTO.builder()
                        .applicationId(Long.valueOf(nbfcResponseDTO.getApplicationId()))
                        .lender(nbfcResponseDTO.getLender())
                        .disbursalDate(disbursalCallbackResponse.formatDisbursementDate(simpleDateFormat))
                        .leadId(disbursalCallbackResponse.getLoanApplicationId())
                        .status(disbursalStatus)
                        .lan(disbursalCallbackResponse.getLanID())
                        .disbursalAmount(disbursalCallbackResponse.getNetDisbursement())
                        .utr(disbursalCallbackResponse.getReceiptNumber())
                        .build();
            }
            return DisbursalCallbackCommonDTO.builder().status(Boolean.FALSE).build();
        } catch (Exception e) {
            log.info("Exception in disbursal callback response handling of {} for {}, {}, {}", nbfcResponseDTO.getLender(), nbfcResponseDTO.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
            return DisbursalCallbackCommonDTO.builder().status(Boolean.FALSE).build();
        }
    }
}
