package com.bharatpe.lending.loanV3.services.associationsV2.ugro.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.loanV3.dto.LenderEdIScheduleResponseDTO;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.request.ugro.UgroRPSRequest;
import com.bharatpe.lending.loanV3.dto.response.ugro.UgroRPSResponse;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.*;

@Slf4j
@Service
public class UgroRepaymentScheduleService {
    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Autowired
    ObjectMapper objectMapper;

    public LenderEdIScheduleResponseDTO invokeRpsGenerate(Long applicationId) {
        Optional<LendingApplication> lendingApplicationOptional = lendingApplicationDao.findById(applicationId);
        LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(applicationId, Status.ACTIVE.name(), Lender.UGRO.name());
        if (!lendingApplicationOptional.isPresent() || ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
            log.error("UGRO: Lending application / LALD not present for application id: {}", applicationId);
            return null;
        }
        NBFCRequestDTO<?> nbfcRequestDto = NBFCRequestDTO.builder()
                .productName("LENDING")
                .lender(lendingApplicationOptional.get().getLender())
                .applicationId(applicationId)
                .payload(UgroRPSRequest.builder()
                        .leadId(lendingApplicationLenderDetails.getLeadId())
                        .loanId(lendingApplicationOptional.get().getNbfcId())
                        .build())
                .build();
        NBFCResponseDTO<?> nbfcResponseDto = lenderAPIGateway.invokeStage(nbfcRequestDto, LenderAssociationStages.RPS);
        try {
            if (!ObjectUtils.isEmpty(nbfcResponseDto) && nbfcResponseDto.getSuccess() && !ObjectUtils.isEmpty(nbfcResponseDto.getData())) {
                UgroRPSResponse response = objectMapper.convertValue(nbfcResponseDto.getData(), UgroRPSResponse.class);
                return getLenderEdiSchedule(response);
            }
        } catch (Exception e) {
            log.info("UGRO: exception occurred while parsing response data of repayment schedule for {} {}, {}", applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    private LenderEdIScheduleResponseDTO getLenderEdiSchedule(UgroRPSResponse repaymentScheduleResponse) {
        if (!ObjectUtils.isEmpty(repaymentScheduleResponse) && !ObjectUtils.isEmpty(repaymentScheduleResponse.getData().getRepaymentSchedule()) && !ObjectUtils.isEmpty(repaymentScheduleResponse.getData().getExpiryDate())) {

            List<LenderEdIScheduleResponseDTO.RepaymentSchedule> ediSchedules = new ArrayList<>();

            Date maturityDate = new Date(repaymentScheduleResponse.getData().getExpiryDate());
            Double totalInterest = 0D;

            ediSchedules.add(new LenderEdIScheduleResponseDTO.RepaymentSchedule()); // for skipping 0th installment while creating EDI schedule
            for (int arr_i = 0; arr_i < repaymentScheduleResponse.getData().getRepaymentSchedule().size(); arr_i++) {
                UgroRPSResponse.RepaymentSchedule rps = repaymentScheduleResponse.getData().getRepaymentSchedule().get(arr_i);
                double totalEdi = rps.getPrincipal() + rps.getInterest();
                ediSchedules.add(LenderEdIScheduleResponseDTO.RepaymentSchedule.builder()
                        .dueDate(new Date(rps.getDate()))
                        .openingBalance(rps.getBalance() + rps.getPrincipal())
                        .principal(ObjectUtils.isEmpty(rps.getPrincipal()) ? 0 : rps.getPrincipal())
                        .interest(ObjectUtils.isEmpty(rps.getInterest()) ? 0 : rps.getInterest())
                        .totalEdi(!ObjectUtils.isEmpty(rps.getPrincipal()) || !ObjectUtils.isEmpty(rps.getInterest()) ? (int) totalEdi : 0)
                        .build()
                );
                totalInterest += rps.getInterest();
            }
            return LenderEdIScheduleResponseDTO.builder().repaymentSchedule(ediSchedules).totalInterestPayable(totalInterest).loanMaturityDate(maturityDate).build();
        }
        return null;
    }
}
