package com.bharatpe.lending.lendingplatform.lms.service;


import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.lendingplatform.lms.client.LendingPlatformHttpClient;
import com.bharatpe.lending.lendingplatform.lms.dto.request.RpsRequest;
import com.bharatpe.lending.lendingplatform.lms.dto.response.ApiResponse;
import com.bharatpe.lending.lendingplatform.lms.dto.response.RpsResponse;
import com.bharatpe.lending.service.LiquiloansService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static com.bharatpe.lending.lendingplatform.lms.constant.Constants.ApiEndPointConstants.GET_RPS;

@Service
@Slf4j
public class RepaymentScheduleService {
    @Autowired
    private LendingPlatformHttpClient lendingPlatformHttpClient;

    @Autowired
    private LiquiloansService liquiloansService;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    ObjectMapper objectMapper;

    private final Logger logger = LoggerFactory.getLogger(RepaymentScheduleService.class);

    public List<RpsResponse.RepaymentSchedule> fetchRepaymentSchedule(LendingApplication lendingApplication,
                                                                      Double interestRate, String lender) {
        try {
            RpsRequest rpsRequest = createRpsRequest(lendingApplication, interestRate, lender);

            log.info("Repayment Schedule Request: {}", objectMapper.writeValueAsString(rpsRequest));

            ApiResponse<RpsResponse> rpsResponse = lendingPlatformHttpClient.sendPostRequest(GET_RPS, rpsRequest, RpsResponse.class);

            if (!ObjectUtils.isEmpty(rpsResponse) && !ObjectUtils.isEmpty(rpsResponse.getData()) &&
                    !ObjectUtils.isEmpty(rpsResponse.getData().getRepaymentSchedule())) {
                log.info("Repayment Schedule posted successfully. Repayment Schedule: {}", rpsResponse.getData().getRepaymentSchedule());
                return rpsResponse.getData().getRepaymentSchedule();
            }

            log.error("Repayment Schedule request failed: Empty or invalid response received: {}",
                    !ObjectUtils.isEmpty(rpsResponse) ? rpsResponse.getData() : "null response");

            throw new RuntimeException("Repayment Schedule request failed: Invalid response from lending platform.");
        } catch (Exception e) {
            log.error("Exception occurred while initiating Repayment Schedule request for application ID {}: {}",
                    lendingApplication.getId(), e.getMessage(), e);
            throw new RuntimeException("Error during loan request initiation: " + e.getMessage(), e);
        }
    }

    private RpsRequest createRpsRequest(LendingApplication lendingApplication, Double interestRate, String lender) {
        Date ediStartDate = lendingApplication.getCreatedAt();
        log.info("loanStartDate date for createRpsRequest : {}", ediStartDate);

        LocalDate startDate = ediStartDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();

        log.info("loanStartDate date for createRpsRequest : {}", java.sql.Date.valueOf(startDate));
        log.info("firstDueDate for createRpsRequest {}", java.sql.Date.valueOf(startDate.plusDays(1)));


        return RpsRequest.builder()
                .loanDetails(RpsRequest.LoanDetails.builder()
                        .loanAmount(BigDecimal.valueOf(lendingApplication.getLoanAmount()))
                        .interestRate(BigDecimal.valueOf(interestRate))
                        .loanTenure(Integer.parseInt(lendingApplication.getPayableDays().toString()))
                        .lender(lender)
                        .ediAmount(lendingApplication.getEdi().intValue())
                        .build())
                .scheduleDetails(RpsRequest.ScheduleDetails.builder()
                        .loanStartDate(String.valueOf(java.sql.Date.valueOf(startDate)))
                        .firstDueDate(String.valueOf(java.sql.Date.valueOf(startDate.plusDays(1))))
                        .build())
                .build();
    }

    public String createOneLmsEdiSchedule(Long applicationId, String lender) {
        logger.info("[createOneLmsEdiSchedule] Creating EDI Schedule from 1LMS for applicationId:{}", applicationId);

        Optional<LendingApplication> lendingApplication = lendingApplicationDao.findById(applicationId);
        if (!lendingApplication.isPresent()) {
            log.error("[createOneLmsEdiSchedule] application not found !! {}", applicationId);
        }

        if (ObjectUtils.isEmpty(lendingApplication)) {
            throw new LendingApplicationNotFoundException(
                    "Lending application not found for Application ID: " + applicationId);
        }
        LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao
                .findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(
                        lendingApplication.get().getId(),
                        "ACTIVE",
                        lender);
        Double annualRoi = lendingApplicationLenderDetails.getAnnualRoi();

        List<RpsResponse.RepaymentSchedule> repaymentScheduleList = fetchRepaymentSchedule(
                lendingApplication.get(),
                annualRoi,
                lender);

        String html = "";
        for(int i = 0; i < repaymentScheduleList.size(); i++){
            if(repaymentScheduleList.get(i).getInstallmentNumber() == 0)continue;
            html += "    <tr class=\"width-100\">\n" +
                    "      <td class=\"width-auto\">\n" +
                    "        <p style=\"margin: 0px; font-size: 10px;\" class=\"fw400 text-center\">" + repaymentScheduleList.get(i).getInstallmentNumber() + "</p>\n" +
                    "      </td>\n" +
                    "      <td class=\"width-auto\">\n" +
                    "        <p style=\"margin: 0px; font-size: 10px;\" class=\"fw400 text-center\">" + repaymentScheduleList.get(i).getOpeningBalance()+ "</p>\n" +
                    "      </td>\n" +
                    "      <td class=\"width-auto\">\n" +
                    "        <p style=\"margin: 0px; font-size: 10px;\" class=\"fw400 text-center\">" + repaymentScheduleList.get(i).getPrincipal() + "</p>\n" +
                    "      </td>\n" +
                    "      <td class=\"width-auto\">\n" +
                    "        <p style=\"margin: 0px; font-size: 10px;\" class=\"fw400 text-center\">" + repaymentScheduleList.get(i).getInterest() + "</p>\n" +
                    "      </td>\n" +
                    "      <td class=\"width-auto\">\n" +
                    "        <p style=\"margin: 0px; font-size: 10px;\" class=\"fw400 text-center\">" + repaymentScheduleList.get(i).getEdi() + "</p>\n" +
                    "      </td>\n" +
                    "    </tr>\n";
        }
        return html;
    }

    public class LendingApplicationNotFoundException extends RuntimeException {
        public LendingApplicationNotFoundException(String message) {
            super(message);
        }
    }
}
