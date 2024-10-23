package com.bharatpe.lending.loanV3.services.associationsV2.smfg.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.loanV3.config.SmfgConfig;
import com.bharatpe.lending.loanV3.dto.LenderEdIScheduleResponseDTO;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.request.smfg.SmfgRpsRequest;
import com.bharatpe.lending.loanV3.dto.response.smfg.SmfgRpsResponse;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;


@Service
@Slf4j
public class SmfgRpsService{

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    SmfgConfig smfgConfig;


    public LenderEdIScheduleResponseDTO invokeRpsGenerate(Long applicationId) {
        LendingApplication lendingApplication = lendingApplicationDao.findById(applicationId).orElse(null);
        if (ObjectUtils.isEmpty(lendingApplication)) {
            log.info("application record not exist of SMFG for {}", applicationId);
            return null;
        }
        NBFCRequestDTO nbfcRequestDto = NBFCRequestDTO.builder()
                .productName("LENDING")
                .lender(Lender.SMFG.name())
                .applicationId(applicationId)
                .payload(SmfgRpsRequest.builder()
                        .authentication(SmfgRpsRequest.Authentication.builder()
                                .appName(smfgConfig.getLmsAppName())
                                .appPass(smfgConfig.getLmsAppPassword())
                                .deviceId(smfgConfig.getLmsDeviceRpsId())
                                .ipAddress(smfgConfig.getLmsStaticIpAddress())
                                .latitude(smfgConfig.getLmsLatitude())
                                .longitude(smfgConfig.getLmsLongitude()).build())
                        .basicInfo(SmfgRpsRequest.BasicInfo.builder()
                                .prospectId(lendingApplication.getNbfcId())
                                .build())
                        .build())
                .build();

        NBFCResponseDTO nbfcResponseDto = lenderAPIGateway.invokeStage(nbfcRequestDto, LenderAssociationStages.RPS);
        try {
            if (!ObjectUtils.isEmpty(nbfcResponseDto) && nbfcResponseDto.getSuccess() && !ObjectUtils.isEmpty(nbfcResponseDto.getData())) {
                SmfgRpsResponse response = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDto.getData()), SmfgRpsResponse.class);
                return getLenderEdiSchedule(response);
            }
        } catch (Exception e) {
            log.info("exception occurred while parsing response data of SMFG repayment schedule for {} {}, {}", applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    private LenderEdIScheduleResponseDTO getLenderEdiSchedule(SmfgRpsResponse smfgRpsResponse) {
        if(!ObjectUtils.isEmpty(smfgRpsResponse) && !ObjectUtils.isEmpty(smfgRpsResponse.getData()) && !ObjectUtils.isEmpty(smfgRpsResponse.getData().getRepaymentSummary())) {
            List<LenderEdIScheduleResponseDTO.RepaymentSchedule> ediSchedules = new ArrayList<>();

            int totalEdis = smfgRpsResponse.getData().getRepaymentSummary().size();
            Date maturityDate = smfgRpsResponse.getData().getRepaymentSummary().get(totalEdis-1).getDueDate();
            Double totalInterest = 0D;

            ediSchedules.add(new LenderEdIScheduleResponseDTO.RepaymentSchedule()); // for skipping 0th installment while creating EDI schedule
            for (int arr_i = 0; arr_i < totalEdis; arr_i++) {
                SmfgRpsResponse.RepaymentSummary schedule = smfgRpsResponse.getData().getRepaymentSummary().get(arr_i);
                ediSchedules.add(LenderEdIScheduleResponseDTO.RepaymentSchedule.builder()
                        .dueDate(schedule.getDueDate())
                        .openingBalance(schedule.getOpPrincipal()) // todo check if this is correct
                        .principal(schedule.getPrincipal())
                        .interest(schedule.getInterest())
                        .totalEdi(schedule.getInstlAmt()).build());
                totalInterest += schedule.getInterest();
            }
            return LenderEdIScheduleResponseDTO.builder().repaymentSchedule(ediSchedules).totalInterestPayable(totalInterest).loanMaturityDate(maturityDate).build();
        }
        return null;
    }

}
