package com.bharatpe.lending.loanV3.services.associationsV2.payu.impl;

import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.loanV3.dto.DisbursalCallbackCommonDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.response.payu.PayUDisbursalCallbackResponseDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.text.SimpleDateFormat;
import java.util.Arrays;

@Slf4j
@Service
public class PayUDisbursalCallbackService {

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    public DisbursalCallbackCommonDTO handleDisbursalCallbackResponse(NBFCResponseDTO nbfcResponseDTO) {
        try {
            if(!ObjectUtils.isEmpty(nbfcResponseDTO) && nbfcResponseDTO.getSuccess() && !ObjectUtils.isEmpty(nbfcResponseDTO.getData())) {
                PayUDisbursalCallbackResponseDTO payUDisbursalCallbackResponseDTO = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDTO.getData()), PayUDisbursalCallbackResponseDTO.class);
                LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findByApplicationIdAndLender(Long.valueOf(nbfcResponseDTO.getApplicationId()),nbfcResponseDTO.getLender());
                Boolean status = "DISBURSED".equalsIgnoreCase(payUDisbursalCallbackResponseDTO.getData().getEventDetails().getStatus());

                return  DisbursalCallbackCommonDTO.builder()
                        .applicationId(Long.parseLong(nbfcResponseDTO.getApplicationId()))
                        .leadId(lendingApplicationLenderDetails.getLeadId())
                        .lender("PAYU")
                        .disbursalDate((new SimpleDateFormat("yyyy-MM-dd")).parse(payUDisbursalCallbackResponseDTO.getData().getEventDetails().getDisbursalDate()))
                        .disbursalAmount(Double.valueOf(payUDisbursalCallbackResponseDTO.getData().getEventDetails().getDisbursedAmount()))
                        .utr(payUDisbursalCallbackResponseDTO.getData().getEventDetails().getDisbursalUtrNumber())
                        .lan(payUDisbursalCallbackResponseDTO.getData().getEventDetails().getLoanId())
                        .status(status)
                        .build();
            }
            return DisbursalCallbackCommonDTO.builder().status(Boolean.FALSE).build();
        } catch (Exception e) {
            log.error("Exception in disbursal callback response handling of {} for {}, {}, {}", nbfcResponseDTO.getLender(), nbfcResponseDTO.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
            return DisbursalCallbackCommonDTO.builder().status(Boolean.FALSE).build();
        }
    }

}
