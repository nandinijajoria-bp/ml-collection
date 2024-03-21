package com.bharatpe.lending.loanV3.services.associationsV2.muthoot.impl;

import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.loanV3.dto.LenderEdIScheduleResponseDTO;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.request.muthoot.MFRpsRequestDTO;
import com.bharatpe.lending.loanV3.dto.response.muthoot.MFRpsResponseDTO;
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

@Slf4j
@Service
public class MFRepaymentScheduleService {

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Autowired
    ObjectMapper objectMapper;

    public LenderEdIScheduleResponseDTO invokeRpsGenerate(Long applicationId) {
        LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1ByApplicationIdAndLenderOrderByIdDesc(applicationId, Lender.MUTHOOT.name());
        if (ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
            log.info("lender record not exist of MUTHOOT for {}", applicationId);
            return null;
        }
        NBFCRequestDTO nbfcRequestDto = NBFCRequestDTO.builder()
                .productName("LENDING")
                .lender(Lender.MUTHOOT.name())
                .applicationId(applicationId)
                .payload(MFRpsRequestDTO.builder()
                        .customerID(lendingApplicationLenderDetails.getLeadId())
                        .program("EDI")
                        .build())
                .build();

        NBFCResponseDTO nbfcResponseDto = lenderAPIGateway.invokeStage(nbfcRequestDto, LenderAssociationStages.RPS);
        try {
            if (!ObjectUtils.isEmpty(nbfcResponseDto) && nbfcResponseDto.getSuccess() && !ObjectUtils.isEmpty(nbfcResponseDto.getData())) {
                MFRpsResponseDTO response = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDto.getData()), MFRpsResponseDTO.class);
                return getLenderEdiSchedule(response);
            }
        } catch (Exception e) {
            log.info("exception occurred while parsing response data of MUTHOOT repayment schedule for {} {}, {}", applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    private LenderEdIScheduleResponseDTO getLenderEdiSchedule(MFRpsResponseDTO repaymentScheduleResponse) {
        if(!ObjectUtils.isEmpty(repaymentScheduleResponse) && !ObjectUtils.isEmpty(repaymentScheduleResponse.getData()) && !ObjectUtils.isEmpty(repaymentScheduleResponse.getData().getRepaymentSchedule())) {
            List<LenderEdIScheduleResponseDTO.RepaymentSchedule> ediSchedules = new ArrayList<>();

            int totalEdis = repaymentScheduleResponse.getData().getRepaymentSchedule().size();
            Date maturityDate = repaymentScheduleResponse.getData().getRepaymentSchedule().get(totalEdis-1).getDueDate();
            Double totalInterest = 0D;

            ediSchedules.add(new LenderEdIScheduleResponseDTO.RepaymentSchedule()); // for skipping 0th installment while creating EDI schedule
            for (int arr_i = 0; arr_i < totalEdis; arr_i++) {
                MFRpsResponseDTO.RepaymentSchedule schedule = repaymentScheduleResponse.getData().getRepaymentSchedule().get(arr_i);
                ediSchedules.add(LenderEdIScheduleResponseDTO.RepaymentSchedule.builder()
                        .dueDate(schedule.getDueDate())
                        .openingBalance(schedule.getComponents().getPrincipal() + schedule.getClosingBalance())
                        .principal(schedule.getComponents().getPrincipal())
                        .interest(schedule.getComponents().getInterest())
                        .totalEdi(schedule.getAmount().intValue())
                        .build()
                );
                totalInterest += schedule.getComponents().getInterest();
            }
            return LenderEdIScheduleResponseDTO.builder().repaymentSchedule(ediSchedules).totalInterestPayable(totalInterest).loanMaturityDate(maturityDate).build();
        }
        return null;
    }

}
