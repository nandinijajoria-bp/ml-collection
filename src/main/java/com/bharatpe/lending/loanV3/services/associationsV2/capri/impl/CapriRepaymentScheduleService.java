package com.bharatpe.lending.loanV3.services.associationsV2.capri.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.loanV3.dto.LenderEdIScheduleResponseDTO;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.request.capri.CapriRpsRequestDTO;
import com.bharatpe.lending.loanV3.dto.response.capri.CapriRpsResponseDTO;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.*;

@Slf4j
@Service
public class CapriRepaymentScheduleService {

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Autowired
    ObjectMapper objectMapper;

    public LenderEdIScheduleResponseDTO invokeRpsGenerate(Long applicationId) {
        LendingApplication lendingApplication = lendingApplicationDao.findById(applicationId).orElse(null);
        if (ObjectUtils.isEmpty(lendingApplication)) {
            log.info("lender record not exist of CAPRI for {}", applicationId);
            return null;
        }
        NBFCRequestDTO nbfcRequestDto = NBFCRequestDTO.builder()
                .productName("LENDING")
                .lender(Lender.CAPRI.name())
                .applicationId(applicationId)
                .payload(CapriRpsRequestDTO.builder()
                        .loanId(lendingApplication.getNbfcId())
                        .associations("repaymentSchedule")
                        .exclude("loanBasicDetails")
                        .isFetchSpecificData(Boolean.TRUE)
                        .build())
                .build();
        NBFCResponseDTO nbfcResponseDto = lenderAPIGateway.invokeStage(nbfcRequestDto, LenderAssociationStages.RPS);
        try {
            if (!ObjectUtils.isEmpty(nbfcResponseDto) && nbfcResponseDto.getSuccess() && !ObjectUtils.isEmpty(nbfcResponseDto.getData())) {
                CapriRpsResponseDTO response = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDto.getData()), CapriRpsResponseDTO.class);
                return getLenderEdiSchedule(response);
            }
        } catch (Exception e) {
            log.info("exception occurred while parsing response data of Capri repayment schedule for {} {}, {}", applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    private LenderEdIScheduleResponseDTO getLenderEdiSchedule(CapriRpsResponseDTO repaymentScheduleResponse) {
        if(!ObjectUtils.isEmpty(repaymentScheduleResponse) && !ObjectUtils.isEmpty(repaymentScheduleResponse.getRepaymentSchedule()) && !ObjectUtils.isEmpty(repaymentScheduleResponse.getRepaymentSchedule().getPeriods())) {
            List<LenderEdIScheduleResponseDTO.RepaymentSchedule> ediSchedules = new ArrayList<>();
            Date maturityDate = getDateFromArray(repaymentScheduleResponse.getRepaymentSchedule().getPeriods().get(repaymentScheduleResponse.getRepaymentSchedule().getPeriods().size()-1).getDueDate());
            for (int arr_i = 0; arr_i < repaymentScheduleResponse.getRepaymentSchedule().getPeriods().size(); arr_i++) {
                CapriRpsResponseDTO.Period period = repaymentScheduleResponse.getRepaymentSchedule().getPeriods().get(arr_i);
                ediSchedules.add(LenderEdIScheduleResponseDTO.RepaymentSchedule.builder()
                        .dueDate(getDateFromArray(period.getDueDate()))
                        .openingBalance(period.getPrincipalLoanBalanceOutstanding())
                        .principal(period.getPrincipalOriginalDue())
                        .interest(period.getInterestOriginalDue())
                        .totalEdi(period.getTotalDueForPeriod().intValue())
                        .build()
                );
            }
            return LenderEdIScheduleResponseDTO.builder().repaymentSchedule(ediSchedules).totalInterestPayable(repaymentScheduleResponse.getRepaymentSchedule().getTotalInterestCharged()).loanMaturityDate(maturityDate).build();
        }
        return null;
    }

    private Date getDateFromArray(List<Integer> dateInputs) {
        if(dateInputs.size() == 3) {
            Date date = DateTimeUtil.getDateFromInputs(dateInputs.get(0), (dateInputs.get(1) - 1), dateInputs.get(2), 0,0,0,0);
            return date;
        }
        throw new RuntimeException("Exception in parsing due date of Capri repaymentSchedule");
    }
}
