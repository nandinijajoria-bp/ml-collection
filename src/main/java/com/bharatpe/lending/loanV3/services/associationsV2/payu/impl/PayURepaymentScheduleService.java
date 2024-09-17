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
import com.bharatpe.lending.loanV3.dto.request.payu.PayULoanPreviewRequestDTO;
import com.bharatpe.lending.loanV3.dto.request.payu.PayURpsRequestDTO;
import com.bharatpe.lending.loanV3.dto.response.payu.PayUCommonResponseDTO;
import com.bharatpe.lending.loanV3.dto.response.payu.PayULoanPreviewResponseDTO;
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

    public LenderEdIScheduleResponseDTO invokeRpsGenerate(Long applicationId, Boolean isPreview) {
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

        if (isPreview) {
            return getLenderEdiScheduleFromLoanPreview(lendingApplicationOptional.get(), lendingApplicationLenderDetails);
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

                PayURpsResponseDTO response = objectMapper.convertValue(commonResponseDTO.getApiResponse(), PayURpsResponseDTO.class);

                return getLenderEdiSchedule(response);
            }
        } catch (Exception e) {
            log.error("exception occurred while parsing response data of PAYU repayment schedule for {} {}, {}", applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
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
                        .openingBalance(periods.getPrincipalLoanBalanceOutstanding().doubleValue())
                        .principal(periods.getPrincipalDue().doubleValue())
                        .interest(periods.getInterestDue().doubleValue())
                        .totalEdi(periods.getTotalDueForPeriod().intValue())
                        .build()
                );
                totalInterest += periods.getInterestDue().doubleValue();
            }
            return LenderEdIScheduleResponseDTO.builder().repaymentSchedule(ediSchedules).totalInterestPayable(totalInterest).loanMaturityDate(maturityDate).build();
        }
        return null;
    }

private NBFCRequestDTO getLoanPreviewPayload(LendingApplication lendingApplication, LendingApplicationLenderDetails lendingApplicationLenderDetails){

    return NBFCRequestDTO.builder()
            .applicationId(lendingApplication.getId())
            .productName("LENDING")
            .lender(lendingApplication.getLender())
            .payload(PayULoanPreviewRequestDTO.builder()
                    .applicationId(lendingApplicationLenderDetails.getLeadId())
                    .amount(lendingApplication.getLoanAmount().intValue())
                    .tenure(lendingApplication.getPayableDays())
                    .roi(lendingApplicationLenderDetails.getAnnualRoi().toString())
                    .pf(lendingApplication.getProcessingFee())
                    .pfType("FIXED_AMOUNT")
                    .build())
            .build();
}

    private LenderEdIScheduleResponseDTO getLenderEdiScheduleFromLoanPreview(LendingApplication lendingApplication, LendingApplicationLenderDetails lendingApplicationLenderDetails) {
        List<LenderEdIScheduleResponseDTO.RepaymentSchedule> ediSchedules = new ArrayList<>();
        double totalInterest = 0D;

        NBFCRequestDTO loanPreviewRequestDto = getLoanPreviewPayload(lendingApplication, lendingApplicationLenderDetails);

        if (Objects.isNull(loanPreviewRequestDto)) {
            log.info("error in loan preview payload of PayU for applicationId: {}", lendingApplication.getId());
            return null;
        }

        NBFCResponseDTO nbfcResponseDto = lenderAPIGateway.invokeStage(loanPreviewRequestDto, LenderAssociationStages.LOAN_PREVIEW);

        log.info("loan preview response of PayU from nbfc: {} with applicationId: {}", nbfcResponseDto, lendingApplication.getId());
        if (Objects.nonNull(nbfcResponseDto) && nbfcResponseDto.getSuccess() && Objects.nonNull(nbfcResponseDto.getData())) {
            log.info("loan preview request of payU success for {}", lendingApplication.getId());

            PayUCommonResponseDTO commonResponseDTO = objectMapper.convertValue(nbfcResponseDto.getData(), PayUCommonResponseDTO.class);

            PayULoanPreviewResponseDTO payULoanPreviewResponseDTO = objectMapper.convertValue(commonResponseDTO.getApiResponse(), PayULoanPreviewResponseDTO.class);


            if ("SUCCESS".equalsIgnoreCase(commonResponseDTO.getApiStatus())) {

                int totalEdis = payULoanPreviewResponseDTO.getPeriods().size();

                PayULoanPreviewResponseDTO.Periods lastPeriod = payULoanPreviewResponseDTO.getPeriods().get(totalEdis - 1);

                Calendar calender = Calendar.getInstance();
                calender.set(Integer.valueOf(lastPeriod.getDueDate().get(0)), Integer.valueOf(lastPeriod.getDueDate().get(1)) - 1, Integer.valueOf(lastPeriod.getDueDate().get(2)));

                Date maturityDate = calender.getTime();

                for (int arr_i = 1; arr_i < totalEdis; arr_i++) { // for skipping 0th installment while creating EDI schedule

                    PayULoanPreviewResponseDTO.Periods periods = payULoanPreviewResponseDTO.getPeriods().get(arr_i);

                    Calendar calendar = Calendar.getInstance();
                    calendar.set(Integer.valueOf(periods.getDueDate().get(0)), Integer.valueOf(periods.getDueDate().get(1)) - 1, Integer.valueOf(periods.getDueDate().get(2)));

                    Date date = calendar.getTime();

                    ediSchedules.add(LenderEdIScheduleResponseDTO.RepaymentSchedule.builder()
                            .dueDate(date)
                            .openingBalance(periods.getPrincipalLoanBalanceOutstanding())
                            .principal(periods.getPrincipalDue())
                            .interest(periods.getInterestDue())
                            .totalEdi(periods.getTotalDueForPeriod().intValue())
                            .build()
                    );

                    totalInterest += periods.getInterestDue();
                }
                log.info("Payu : inside loan preview - repaymentSchedule for applicationId {} fetched successfully", lendingApplication.getId());
                return LenderEdIScheduleResponseDTO.builder().repaymentSchedule(ediSchedules).totalInterestPayable(totalInterest).loanMaturityDate(maturityDate).build();

            }
        }
        return null;

    }
}
