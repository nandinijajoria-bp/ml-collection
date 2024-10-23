package com.bharatpe.lending.loanV3.services.associationsV2.smfg.impl;

import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.loanV3.dto.DisbursalCallbackCommonDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.request.smfg.SmfgCallbackRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;

@Slf4j
@Service
public class SmfgDisbursalCallbackService {
    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    public DisbursalCallbackCommonDTO handleCallbackResponse(NBFCResponseDTO nbfcResponseDTO) {
        try {
            if (!ObjectUtils.isEmpty(nbfcResponseDTO) && nbfcResponseDTO.getSuccess() && !ObjectUtils.isEmpty(nbfcResponseDTO.getData())) {
                SmfgCallbackRequest smfgCallbackRequest = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDTO.getData()), SmfgCallbackRequest.class);
                if (!ObjectUtils.isEmpty(smfgCallbackRequest) && !ObjectUtils.isEmpty(smfgCallbackRequest.getData()) && !ObjectUtils.isEmpty(smfgCallbackRequest.getData().getOutput())) {
                    LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findByApplicationIdAndLender(Long.valueOf(nbfcResponseDTO.getApplicationId()), nbfcResponseDTO.getLender());
                    Boolean status = "SUCCESS".equalsIgnoreCase(smfgCallbackRequest.getStatus()) && "Processed".equalsIgnoreCase(smfgCallbackRequest.getData().getOutput().getDisbursalstatus());
                    DisbursalCallbackCommonDTO disbursalCallbackCommonDTO = DisbursalCallbackCommonDTO.builder()
                            .applicationId(Long.parseLong(nbfcResponseDTO.getApplicationId()))
                            .leadId(lendingApplicationLenderDetails.getLeadId())
                            .lender("SMFG")
                            .disbursalDate(smfgCallbackRequest.getData().getOutput().getDatedisbursal())
                            .disbursalAmount(smfgCallbackRequest.getData().getOutput().getDrawdownamount())
                            .utr(smfgCallbackRequest.getData().getOutput().getUtrno())
                            .lan(smfgCallbackRequest.getData().getOutput().getLanno())
                            .status(status).build();
                    return disbursalCallbackCommonDTO;
                }
            }
            return DisbursalCallbackCommonDTO.builder().status(Boolean.FALSE).build();
        } catch (Exception e) {
            log.error("SMFG : Exception in disbursal callback response handling for {}, {}, {}", nbfcResponseDTO.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
            return DisbursalCallbackCommonDTO.builder().status(Boolean.FALSE).build();
        }
    }
}
