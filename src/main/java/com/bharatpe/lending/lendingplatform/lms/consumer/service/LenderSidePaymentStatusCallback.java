package com.bharatpe.lending.lendingplatform.lms.consumer.service;

import com.bharatpe.lending.common.dao.LmsPaymentDetailsDao;
import com.bharatpe.lending.common.entity.LmsPaymentDetails;
import com.bharatpe.lending.common.enums.LMSPaymentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class LenderSidePaymentStatusCallback {

    private final LmsPaymentDetailsDao lmsPaymentDetailsDao;

    @Transactional
    public void updateLenderPostingStatus(String terminalOrderId) {
        try {
            terminalOrderId = terminalOrderId.replace("\"", "");
            log.info("terminalOrderId - {}", terminalOrderId);
            LmsPaymentDetails lmsPaymentDetails = lmsPaymentDetailsDao.findByTerminalOrderId(terminalOrderId);
            lmsPaymentDetails.setSentToLender(LMSPaymentStatus.SUCCESS);
            lmsPaymentDetailsDao.save(lmsPaymentDetails);
        } catch (Exception e) {
            log.error("Exception in processing lender receipt posting callback: {}", e.getMessage(), e);
        }
    }
}
