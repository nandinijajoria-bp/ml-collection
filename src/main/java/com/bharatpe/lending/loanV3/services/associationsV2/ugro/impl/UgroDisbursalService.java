package com.bharatpe.lending.loanV3.services.associationsV2.ugro.impl;

import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.loanV3.config.UgroConfig;
import com.bharatpe.lending.loanV3.dto.DisbursalCallbackCommonDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.response.ugro.UgroDisbursalResponse;
import com.bharatpe.lending.loanV3.services.associationsV2.ugro.validations.UgroPayloadValidation;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;
import java.util.Date;

@Slf4j
@Service
public class UgroDisbursalService {

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    UgroPayloadValidation ugroPayloadValidation;

    @Autowired
    UgroConfig ugroConfig;


    public DisbursalCallbackCommonDTO parseCallbackResponse(NBFCResponseDTO nbfcResponseDTO) {
        try {
            if (!ObjectUtils.isEmpty(nbfcResponseDTO) && !ObjectUtils.isEmpty(nbfcResponseDTO.getData())) {
                UgroDisbursalResponse disbursalResponse = new ObjectMapper().convertValue(nbfcResponseDTO.getData(), UgroDisbursalResponse.class);
                LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(Long.valueOf(nbfcResponseDTO.getApplicationId()), Status.ACTIVE.name(), nbfcResponseDTO.getLender());

                if (ugroPayloadValidation.isInvalidSuccessDisbursalResponse(disbursalResponse, lendingApplicationLenderDetails) && ugroPayloadValidation.isInvalidRejectedDisbursalResponse(disbursalResponse, lendingApplicationLenderDetails)) {
                    log.error("UGRO: Invalid payload received for DisbursalResponse for applicationId: {}, {}, {}", nbfcResponseDTO.getApplicationId(), disbursalResponse, nbfcResponseDTO);
                    return DisbursalCallbackCommonDTO.builder().status(Boolean.FALSE).build();
                }

                Boolean status = ugroConfig.getSuccessResponse().equalsIgnoreCase(disbursalResponse.getStatus());

                return DisbursalCallbackCommonDTO.builder()
                        .applicationId(Long.valueOf(nbfcResponseDTO.getApplicationId()))
                        .lender(nbfcResponseDTO.getLender())
                        .leadId(lendingApplicationLenderDetails.getLeadId())
                        .status(status)
                        .utr(disbursalResponse.getEvents().get(0).getBankRefNo())
                        .lan(lendingApplicationLenderDetails.getLan())
                        .disbursalAmount(disbursalResponse.getEvents().get(0).getDisbursalAmount())
                        .disbursalDate(new Date(disbursalResponse.getEvents().get(0).getDate()))
                        .build();
            }
            return DisbursalCallbackCommonDTO.builder().status(Boolean.FALSE).build();
        } catch (Exception e) {
            log.error("UGRO: Exception in disbursal callback response handling of {} for {}, {}, {}", nbfcResponseDTO.getLender(), nbfcResponseDTO.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
            return DisbursalCallbackCommonDTO.builder().status(Boolean.FALSE).build();
        }
    }
}
