package com.bharatpe.lending.loanV3.services.associationsV2.oxyzo.impl;

import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.loanV3.dto.DisbursalCallbackCommonDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.response.oxyzo.OxyzoCommonResponseDTO;
import com.bharatpe.lending.loanV3.dto.response.oxyzo.OxyzoDisbursalCallbackResponseDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Date;

@Slf4j
@Service
public class OxyzoDisbursalCallbackService {

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    public DisbursalCallbackCommonDTO handleDisbursalCallbackResponse(NBFCResponseDTO nbfcResponseDTO) {
        try {
            if(!ObjectUtils.isEmpty(nbfcResponseDTO) && nbfcResponseDTO.getSuccess() && !ObjectUtils.isEmpty(nbfcResponseDTO.getData())) {

                OxyzoCommonResponseDTO<OxyzoDisbursalCallbackResponseDTO> commonResponseDTO = objectMapper.convertValue(nbfcResponseDTO.getData(), OxyzoCommonResponseDTO.class);

                OxyzoDisbursalCallbackResponseDTO oxyzoDisbursalCallbackResponseDTO = objectMapper.convertValue(commonResponseDTO.getData(), OxyzoDisbursalCallbackResponseDTO.class);

                LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findByApplicationIdAndLender(Long.valueOf(nbfcResponseDTO.getApplicationId()),nbfcResponseDTO.getLender());
                Boolean status = "DISBURSED".equalsIgnoreCase(oxyzoDisbursalCallbackResponseDTO.getDisbursalStatus());


                long disbursementDate = oxyzoDisbursalCallbackResponseDTO.getPaymentDate();

                ZonedDateTime zonedDateTime = Instant.ofEpochMilli(disbursementDate)
                        .atZone(ZoneId.of("Asia/Kolkata"));

                Date date = Date.from(zonedDateTime.toInstant());


                return  DisbursalCallbackCommonDTO.builder()
                        .applicationId(Long.parseLong(nbfcResponseDTO.getApplicationId()))
                        .leadId(lendingApplicationLenderDetails.getLeadId())
                        .lender(nbfcResponseDTO.getLender())
                        .disbursalDate(date)
                        .disbursalAmount((oxyzoDisbursalCallbackResponseDTO.getDisbursalAmount()).doubleValue())
                        .utr(oxyzoDisbursalCallbackResponseDTO.getUtr())
                        .lan(oxyzoDisbursalCallbackResponseDTO.getLoanId())
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
