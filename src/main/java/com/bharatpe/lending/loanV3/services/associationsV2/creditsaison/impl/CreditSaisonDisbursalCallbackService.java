package com.bharatpe.lending.loanV3.services.associationsV2.creditsaison.impl;


import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.loanV3.dto.DisbursalCallbackCommonDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.response.creditsasion.CreditSaisonCallbackResponseDTO;
import com.bharatpe.lending.loanV3.dto.response.creditsasion.CreditSasionCallbackResponseStatuses;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;

@Slf4j
@Service
public class CreditSaisonDisbursalCallbackService {

    @Autowired
    ObjectMapper objectMapper;

    public DisbursalCallbackCommonDTO parseCallbackResponse(NBFCResponseDTO nbfcResponseDTO) {
        try {
            if(!ObjectUtils.isEmpty(nbfcResponseDTO) && nbfcResponseDTO.getSuccess() && !ObjectUtils.isEmpty(nbfcResponseDTO.getData())) {
                CreditSaisonCallbackResponseDTO disbursalCallbackResponse = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDTO.getData()), CreditSaisonCallbackResponseDTO.class);
                Boolean disbursalStatus = CreditSasionCallbackResponseStatuses.DRAWDOWN.getStatusCode().equalsIgnoreCase(disbursalCallbackResponse.getStatus()) ? Boolean.TRUE : Boolean.FALSE;
                DisbursalCallbackCommonDTO disbursalCallbackCommonDTO = DisbursalCallbackCommonDTO.builder()
                        .applicationId(Long.valueOf(nbfcResponseDTO.getApplicationId()))
                        .lender(nbfcResponseDTO.getLender())
                        .disbursalDate(DateTimeUtil.parseDate(disbursalCallbackResponse.getDisbursalDate(), "yyyy-MM-dd'T'HH:mm:ss"))
                        .leadId(disbursalCallbackResponse.getPartnerLoanId())
                        .status(disbursalStatus)
                        .lan(disbursalCallbackResponse.getPartnerLoanId())
                        .disbursalAmount(disbursalCallbackResponse.getDisbursalAmount())
                        .utr(disbursalCallbackResponse.getUtr())
                        .build();
                return disbursalCallbackCommonDTO;
            }
            return DisbursalCallbackCommonDTO.builder().status(Boolean.FALSE).build();
        } catch (Exception e) {
            log.info("CS: Exception in disbursal callback response handling of {} for {}, {}, {}", nbfcResponseDTO.getLender(), nbfcResponseDTO.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
            return DisbursalCallbackCommonDTO.builder().status(Boolean.FALSE).build();
        }
    }
}


