package com.bharatpe.lending.lendingplatform.lms.consumer.service;

import com.bharatpe.lending.common.dao.LmsLoanStatusDao;
import com.bharatpe.lending.common.entity.LmsLoanStatus;
import com.bharatpe.lending.dto.PostPayoutRequestDto;
import com.bharatpe.lending.service.LiquiloansService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.Date;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class LmsCreateLoanFailureCallback {

    private final ObjectMapper objectMapper;
    private final LmsLoanStatusDao lmsLoanStatusDao;
    private final LiquiloansService liquiloansService;

    public void sendLoanToOldFlow(String bpLoanId) {
         try {
            LmsLoanStatus lmsLoanStatus = lmsLoanStatusDao.findLatestByBpLoanId(bpLoanId);

            Map<String, Object> disbursalRequestMap = lmsLoanStatus.getDisbursalRequest();
            PostPayoutRequestDto postPayoutRequestDto = objectMapper.convertValue(disbursalRequestMap, PostPayoutRequestDto.class);

            updateLmsLoanStatus(lmsLoanStatus);
            log.info("Marked loan as failure in lmsLoanStatus and sending loan to fallback method");
            liquiloansService.populatePostPayoutSchedule(postPayoutRequestDto);
         } catch (Exception e) {
             log.error("Exception in processing loan creation callback: {}", e.getMessage(), e);
         }
    }

    @Transactional
    private void updateLmsLoanStatus(LmsLoanStatus lmsLoanStatus) {
        lmsLoanStatus.setStatus("FAILED");
        lmsLoanStatus.setUpdatedAt(new Date());
        lmsLoanStatusDao.save(lmsLoanStatus);
    }

}
