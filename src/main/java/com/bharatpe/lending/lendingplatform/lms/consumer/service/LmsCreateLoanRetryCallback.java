package com.bharatpe.lending.lendingplatform.lms.consumer.service;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.dao.LmsLoanStatusDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.entity.LmsLoanStatus;
import com.bharatpe.lending.common.enums.LeadSubStatus;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dto.PostPayoutRequestDto;
import com.bharatpe.lending.dto.PostPayoutResponseDto;
import com.bharatpe.lending.service.LiquiloansService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class LmsCreateLoanRetryCallback {

    private final LmsLoanStatusDao lmsLoanStatusDao;
    private final ObjectMapper objectMapper;
    private final LiquiloansService liquiloansService;
    private final LendingApplicationDao lendingApplicationDao;
    private final LendingApplicationLenderDetailsDao laldDao;

    public void retryLoanCreationAtLms(String bpLoanId) {
        try {
            bpLoanId = bpLoanId.replace("\"", "");
            LmsLoanStatus lmsLoanStatus = lmsLoanStatusDao.findLoanByBpLoanIdAndStatus(bpLoanId, "INIT");
            Map<String, Object> disbursalRequestMap = lmsLoanStatus.getDisbursalRequest();
            PostPayoutRequestDto postPayoutRequestDto = objectMapper.convertValue(disbursalRequestMap, PostPayoutRequestDto.class);

            PostPayoutResponseDto postPayoutResponseDto = liquiloansService.populatePostPayoutSchedule(postPayoutRequestDto).getBody();
            if (null == postPayoutResponseDto || !"SUCCESS".equalsIgnoreCase(postPayoutResponseDto.getStatus())) {
                log.info("failed to process loan event of for {}", bpLoanId);
                return;
            }
            LendingApplication lendingApplication = lendingApplicationDao.findByExternalLoanId(bpLoanId);
            LendingApplicationLenderDetails lendingApplicationLenderDetails =
                    laldDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(
                            lendingApplication.getId(),
                            Status.ACTIVE.name(),
                            lendingApplication.getLender());
            lendingApplicationLenderDetails.setLan(postPayoutResponseDto.getNbfcId());
            lendingApplicationLenderDetails.setLoanCreationTimestamp(postPayoutResponseDto.getLoanStartDate());
            lendingApplicationLenderDetails.setDrawDownStatus(LenderAssociationStatus.DRAWDOWN_COMPLETED.name());
            lendingApplicationLenderDetails.setLeadSubStatus(LeadSubStatus.SUCCESS);
            laldDao.save(lendingApplicationLenderDetails);
            log.info("loan created successfully of lender {} for {}", lendingApplication.getLender(), lendingApplication.getId());

        } catch (Exception e) {
            log.error("Exception in processing loan creation callback: {}", e.getMessage(), e);
        }
    }
}
