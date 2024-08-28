package com.bharatpe.lending.loanV3.services.associationsV2.payu.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.loanV3.dto.LenderEdIScheduleResponseDTO;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.request.payu.PayURpsRequestDTO;
import com.bharatpe.lending.loanV3.dto.response.payu.PayUCommonResponseDTO;
import com.bharatpe.lending.loanV3.dto.response.payu.PayURpsResponseDTO;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import java.util.*;

@Slf4j
@Service
public class PayURepaymentScheduleService {

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    public LenderEdIScheduleResponseDTO invokeRpsGenerate(Long applicationId) {
        LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1ByApplicationIdAndLenderOrderByIdDesc(applicationId, Lender.PAYU.name());

        Optional<LendingApplication> lendingApplicationOptional = lendingApplicationDao.findById(applicationId);
        if (!lendingApplicationOptional.isPresent()) {
            log.error("PAYU: Lending application not present for application id: {}", applicationId);
            return null;
        }

        if (ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
            log.info("lender record not exist of PAYU for {}", applicationId);
            return null;
        }
        NBFCRequestDTO nbfcRequestDto = NBFCRequestDTO.builder()
                .productName("LENDING")
                .lender(Lender.PAYU.name())
                .applicationId(applicationId)
                .payload(PayURpsRequestDTO.builder()
                        .applicationId(lendingApplicationLenderDetails.getLeadId())
                        .loanId((Integer.valueOf(lendingApplicationOptional.get().getNbfcId())))
                        .build())
                .build();

        NBFCResponseDTO nbfcResponseDto = lenderAPIGateway.invokeStage(nbfcRequestDto, LenderAssociationStages.RPS);
        try {
            if (!ObjectUtils.isEmpty(nbfcResponseDto) && nbfcResponseDto.getSuccess() && !ObjectUtils.isEmpty(nbfcResponseDto.getData())) {

                PayUCommonResponseDTO commonResponseDTO = objectMapper.convertValue(nbfcResponseDto.getData(), PayUCommonResponseDTO.class);

                PayURpsResponseDTO response =  objectMapper.convertValue(commonResponseDTO.getApiResponse(), PayURpsResponseDTO.class);

                return getLenderEdiSchedule(response);
            }
        } catch (Exception e) {
            log.info("exception occurred while parsing response data of PAYU repayment schedule for {} {}, {}", applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    private LenderEdIScheduleResponseDTO getLenderEdiSchedule(PayURpsResponseDTO repaymentScheduleResponse) {
        if(!ObjectUtils.isEmpty(repaymentScheduleResponse) && !ObjectUtils.isEmpty(repaymentScheduleResponse.getCurrency()) && !ObjectUtils.isEmpty(repaymentScheduleResponse.getPeriods())) {
            List<LenderEdIScheduleResponseDTO.RepaymentSchedule> ediSchedules = new ArrayList<>();

            int totalEdis = repaymentScheduleResponse.getPeriods().size();

            PayURpsResponseDTO.Periods lastPeriod = repaymentScheduleResponse.getPeriods().get(totalEdis-1);

            Calendar calender = Calendar.getInstance();
            calender.set(lastPeriod.getDueDate().get(0), lastPeriod.getDueDate().get(1) - 1, lastPeriod.getDueDate().get(2));

            Date maturityDate = calender.getTime();

            Double totalInterest = 0D;

            ediSchedules.add(new LenderEdIScheduleResponseDTO.RepaymentSchedule());
            for (int arr_i = 1; arr_i < totalEdis; arr_i++) {       // for skipping 0th installment while creating EDI schedule
                PayURpsResponseDTO.Periods periods = repaymentScheduleResponse.getPeriods().get(arr_i);

                Calendar calendar = Calendar.getInstance();
                calendar.set(periods.getDueDate().get(0), periods.getDueDate().get(1) - 1, periods.getDueDate().get(2));

                Date date = calendar.getTime();

                ediSchedules.add(LenderEdIScheduleResponseDTO.RepaymentSchedule.builder()
                        .dueDate(date)
                        .openingBalance((double)periods.getPrincipalLoanBalanceOutstanding())
                        .principal((double)periods.getPrincipalDue())
                        .interest((double)periods.getInterestDue())
                        .totalEdi(periods.getTotalDueForPeriod().intValue())
                        .build()
                );
                totalInterest += (double)periods.getInterestDue();
            }
            return LenderEdIScheduleResponseDTO.builder().repaymentSchedule(ediSchedules).totalInterestPayable(totalInterest).loanMaturityDate(maturityDate).build();
        }
        return null;
    }
}
