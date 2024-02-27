package com.bharatpe.lending.loanV3.services.associationsV2.usfb.impl;

import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.loanV3.dto.LenderEdIScheduleResponseDTO;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.request.usfb.ReceiptRequestDTO;
import com.bharatpe.lending.loanV3.dto.request.usfb.RepaymentScheduleRequestDTO;
import com.bharatpe.lending.loanV3.dto.response.usfb.RepaymentScheduleResponseDTO;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.*;

@Slf4j
@Service
public class RepaymentService {

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Autowired
    ObjectMapper objectMapper;

    public LenderEdIScheduleResponseDTO invokeRpsGenerate(Long applicationId) {
        LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1ByApplicationIdAndLenderOrderByIdDesc(applicationId, Lender.USFB.name());
        if (ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
            log.info("lender record not exist of USFB for {}", applicationId);
            return null;
        }
        NBFCRequestDTO nbfcRequestDto = NBFCRequestDTO.builder()
                .productName("LENDING")
                .lender(Lender.USFB.name())
                .applicationId(applicationId)
                .payload(RepaymentScheduleRequestDTO.builder().leadId(lendingApplicationLenderDetails.getLeadId()).build())
                .build();
        NBFCResponseDTO nbfcResponseDto = lenderAPIGateway.invokeStage(nbfcRequestDto, LenderAssociationStages.RPS);
        try {
            if (!ObjectUtils.isEmpty(nbfcResponseDto) && nbfcResponseDto.getSuccess() && !ObjectUtils.isEmpty(nbfcResponseDto.getData())) {
                RepaymentScheduleResponseDTO response = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDto.getData()), RepaymentScheduleResponseDTO.class);
                return getLenderEdiSchedule(response);
            }
        } catch (Exception e) {
            log.info("exception occurred while parsing response data of USFB repayment schedule for {} {}, {}", applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    private LenderEdIScheduleResponseDTO getLenderEdiSchedule(RepaymentScheduleResponseDTO repaymentScheduleResponse) {
        if(!ObjectUtils.isEmpty(repaymentScheduleResponse) && !ObjectUtils.isEmpty(repaymentScheduleResponse.getData()) && !ObjectUtils.isEmpty(repaymentScheduleResponse.getData().getRepayments())) {
            List<LenderEdIScheduleResponseDTO.RepaymentSchedule> ediSchedules = new ArrayList<>();
            double totalInterestPayable = 0D;
            Date maturityDate = DateTimeUtil.parseDate(repaymentScheduleResponse.getData().getRepayments().get(0).getDueDate(),"dd-MMM-yyyy");
            for (int arr_i = (repaymentScheduleResponse.getData().getRepayments().size() - 1); arr_i >= 0; arr_i--) {
                RepaymentScheduleResponseDTO.Repayment repayment = repaymentScheduleResponse.getData().getRepayments().get(arr_i);
                ediSchedules.add(LenderEdIScheduleResponseDTO.RepaymentSchedule.builder()
                        .dueDate(DateTimeUtil.parseDate(repayment.getDueDate(), "dd-MMM-yyyy"))
                        .openingBalance(repayment.getOpeningBalance())
                        .principal(repayment.getPrincipalAmount())
                        .interest(repayment.getInterestAmount())
                        .totalEdi(repayment.getRepaymentAmount().intValue())
                        .build()
                );
                totalInterestPayable += repayment.getInterestAmount();
            }
            return LenderEdIScheduleResponseDTO.builder().repaymentSchedule(ediSchedules).totalInterestPayable(totalInterestPayable).loanMaturityDate(maturityDate).build();
        }
        return null;
    }

    public NBFCRequestDTO getForeclosureReceiptRequest(Long applicationId, LendingLedger lendingLedger) {
      try {
          LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1ByApplicationIdAndLenderOrderByIdDesc(applicationId, Lender.USFB.name());
          String paymentDate = DateTimeUtil.getDateInFormat(lendingLedger.getDate(), "dd-MMM-yyyy");
          String txnId = Optional.ofNullable(lendingLedger.getTerminalOrderId()).orElse(String.valueOf(lendingLedger.getId()));
          LinkedHashMap<String, Object> identifier = new LinkedHashMap<>();
          identifier.put("paymentMode", lendingLedger.getAdjustmentMode());
          NBFCRequestDTO nbfcRequestDTO = NBFCRequestDTO.builder()
                  .applicationId(applicationId)
                  .lender("USFB")
                  .productName("LENDING")
                  .payload(ReceiptRequestDTO.builder()
                          .leadId(lendingApplicationLenderDetails.getLeadId())
                          .amount(lendingLedger.getAmount())
                          .paymentDate(paymentDate)
                          .isForeclosure(Boolean.TRUE)
                          .paymentId(txnId)
                          .allocation(getAllocations(lendingLedger))
                          .build())
                  .identifier(identifier)
                  .build();
          return nbfcRequestDTO;
      } catch (Exception e) {
          log.info("Exception in generating foreclosure receipt payload of USFB for {}, {}, {}", applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
      }
      return null;
    }

    private List<ReceiptRequestDTO.Allocation> getAllocations(LendingLedger lendingLedger) {
        List<ReceiptRequestDTO.Allocation> allocations = new ArrayList<>();
        /*
        allocations.add(ReceiptRequestDTO.Allocation.builder().type(1).amount(lendingLedger.getPrinciple()).build());
        allocations.add(ReceiptRequestDTO.Allocation.builder().type(2).amount(lendingLedger.getInterest()).build());

         */
        return allocations;
    }

}
