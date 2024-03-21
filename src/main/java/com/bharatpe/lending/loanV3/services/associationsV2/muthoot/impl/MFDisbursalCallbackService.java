package com.bharatpe.lending.loanV3.services.associationsV2.muthoot.impl;

import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.loanV3.dto.DisbursalCallbackCommonDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.response.muthoot.MFDisbursalCallbackDTO;
import com.bharatpe.lending.loanV3.dto.response.muthoot.MFKycCallbackResponseDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;

@Slf4j
@Service
public class MFDisbursalCallbackService {
    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    public DisbursalCallbackCommonDTO handleCallbackResponse(NBFCResponseDTO nbfcResponseDTO) {
        try {
            if(!ObjectUtils.isEmpty(nbfcResponseDTO) && nbfcResponseDTO.getSuccess() && !ObjectUtils.isEmpty(nbfcResponseDTO.getData())) {
                MFDisbursalCallbackDTO mfDisbursalCallbackDTO = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDTO.getData()), MFDisbursalCallbackDTO.class);
                MFDisbursalCallbackDTO.CallbackDTO callbackData = mfDisbursalCallbackDTO.getData();
                LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findByApplicationIdAndLender(Long.valueOf(nbfcResponseDTO.getApplicationId()),nbfcResponseDTO.getLender());
                Boolean status = "SUCCESS".equalsIgnoreCase(callbackData.getStatus());
                DisbursalCallbackCommonDTO disbursalCallbackCommonDTO = DisbursalCallbackCommonDTO.builder()
                        .applicationId(Long.parseLong(nbfcResponseDTO.getApplicationId()))
                        .leadId(lendingApplicationLenderDetails.getLeadId())
                        .lender("MUTHOOT")
                        .disbursalDate(callbackData.getDisbursedAt())
                        .disbursalAmount(callbackData.getDisbursedAmount())
                        .utr(callbackData.getUtrNumber())
                        .lan(callbackData.getLoanAccountNumber())
                        .status(status)
                        .build();
                return disbursalCallbackCommonDTO;
            }
            return DisbursalCallbackCommonDTO.builder().status(Boolean.FALSE).build();
        } catch (Exception e) {
            log.error("Exception in disbursal callback response handling of {} for {}, {}, {}", nbfcResponseDTO.getLender(), nbfcResponseDTO.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
            return DisbursalCallbackCommonDTO.builder().status(Boolean.FALSE).build();
        }
    }
}
