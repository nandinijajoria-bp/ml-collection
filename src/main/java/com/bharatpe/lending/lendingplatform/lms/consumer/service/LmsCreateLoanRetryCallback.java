package com.bharatpe.lending.lendingplatform.lms.consumer.service;

import com.bharatpe.lending.common.dao.LmsLoanStatusDao;
import com.bharatpe.lending.common.entity.LmsLoanStatus;
import com.bharatpe.lending.dto.PostPayoutRequestDto;
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

    public void retryLoanCreationAtLms(String bpLoanId) {
        try {
            bpLoanId = bpLoanId.replace("\"", "");
            LmsLoanStatus lmsLoanStatus = lmsLoanStatusDao.findLoanByBpLoanIdAndStatus(bpLoanId, "INIT");
            Map<String, Object> disbursalRequestMap = lmsLoanStatus.getDisbursalRequest();
            PostPayoutRequestDto postPayoutRequestDto = objectMapper.convertValue(disbursalRequestMap, PostPayoutRequestDto.class);

            liquiloansService.populatePostPayoutSchedule(postPayoutRequestDto);
        } catch (Exception e) {
            log.error("Exception in processing loan creation callback: {}", e.getMessage(), e);
        }
    }
}
