package com.bharatpe.lending.loanV3.services.associationsV2.trillionloans.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.loanV3.dto.LenderEdIScheduleResponseDTO;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.request.trillionloans.TLRpsRequestDto;
import com.bharatpe.lending.loanV3.dto.response.trillionloans.TLRpsResponseDto;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.*;

@Slf4j
@Service
public class TLRepaymentScheduleService {
    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Autowired
    ObjectMapper objectMapper;

    public LenderEdIScheduleResponseDTO invokeRpsGenerate(Long applicationId) {
        Optional<LendingApplication> lendingApplicationOptional = lendingApplicationDao.findById(applicationId);
        if (!lendingApplicationOptional.isPresent()) {
            log.error("TrillionLoans: Lending application not present for application id: {}", applicationId);
            return null;
        }
        NBFCRequestDTO nbfcRequestDto = NBFCRequestDTO.builder()
                .productName("LENDING")
                .lender(Lender.TRILLIONLOANS.name())
                .applicationId(applicationId)
                .payload(TLRpsRequestDto.builder()
                        .loanId(lendingApplicationOptional.get().getNbfcId())
                        .build())
                .build();
        NBFCResponseDTO nbfcResponseDto = lenderAPIGateway.invokeStage(nbfcRequestDto, LenderAssociationStages.RPS);
        try {
            if (!ObjectUtils.isEmpty(nbfcResponseDto) && nbfcResponseDto.getSuccess() && !ObjectUtils.isEmpty(nbfcResponseDto.getData())) {
                TLRpsResponseDto response = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDto.getData()), TLRpsResponseDto.class);
                return getLenderEdiSchedule(response);
            }
        } catch (Exception e) {
            log.info("exception occurred while parsing response data of TrillionLoans repayment schedule for {} {}, {}", applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    private LenderEdIScheduleResponseDTO getLenderEdiSchedule(TLRpsResponseDto repaymentScheduleResponse) {
        if(!ObjectUtils.isEmpty(repaymentScheduleResponse) && !ObjectUtils.isEmpty(repaymentScheduleResponse.getRepaymentSchedule()) && !ObjectUtils.isEmpty(repaymentScheduleResponse.getRepaymentSchedule().getPeriods())) {
            List<LenderEdIScheduleResponseDTO.RepaymentSchedule> ediSchedules = new ArrayList<>();
            Date maturityDate = getDateFromArray(repaymentScheduleResponse.getRepaymentSchedule().getPeriods().get(repaymentScheduleResponse.getRepaymentSchedule().getPeriods().size()-1).getDueDate());
            for (int arr_i = 0; arr_i < repaymentScheduleResponse.getRepaymentSchedule().getPeriods().size(); arr_i++) {
                TLRpsResponseDto.Period period = repaymentScheduleResponse.getRepaymentSchedule().getPeriods().get(arr_i);
                ediSchedules.add(LenderEdIScheduleResponseDTO.RepaymentSchedule.builder()
                        .dueDate(getDateFromArray(period.getDueDate()))
                        .openingBalance(period.getPrincipalLoanBalanceOutstanding())
                        .principal(ObjectUtils.isEmpty(period.getPrincipalOriginalDue()) ? 0 : period.getPrincipalOriginalDue())
                        .interest(ObjectUtils.isEmpty(period.getInterestOriginalDue()) ? 0 : period.getInterestOriginalDue())
                        .totalEdi(ObjectUtils.isEmpty(period.getTotalOutstandingForPeriod()) ? 0 : period.getTotalOutstandingForPeriod().intValue())
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
        throw new RuntimeException("Exception in parsing due date of TrillionLoans repaymentSchedule");
    }
}
