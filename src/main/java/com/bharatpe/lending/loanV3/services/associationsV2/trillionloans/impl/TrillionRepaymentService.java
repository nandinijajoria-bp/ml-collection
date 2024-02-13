package com.bharatpe.lending.loanV3.services.associationsV2.trillionloans.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.request.trillionloans.TLReceiptRequestDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;


@Service
@Slf4j
public class TrillionRepaymentService {

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;

    public NBFCRequestDTO getForeclosureReceiptRequest(Long applicationId, LendingLedger lendingLedger) {
        try {
            LendingApplication lendingApplication = lendingApplicationDao.findById(applicationId).get();
            if (ObjectUtils.isEmpty(lendingApplication)) {
                return null;
            }
            LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.findById(lendingLedger.getLendingPaymentSchedule().getId()).get();
            String paymentDate = DateTimeUtil.getDateInFormat(lendingLedger.getDate(), "dd-MM-yyyy");
            boolean outstandingInterest = lendingPaymentSchedule.getDueInterest() > 1;
            LinkedHashMap<String, Object> identifier = new LinkedHashMap<>();
            identifier.put("paymentMode", lendingLedger.getAdjustmentMode());
            NBFCRequestDTO nbfcRequestDTO = NBFCRequestDTO.builder()
                    .applicationId(applicationId)
                    .lender(Lender.TRILLIONLOANS.name())
                    .productName("LENDING")
                    .payload(TLReceiptRequestDto.builder()
                            .loanId(lendingApplication.getNbfcId())
                            .transactionDate(DateTimeUtil.getDateInFormat(new Date(), "dd-MM-yyyy"))
                            .includePreClosureReason(Boolean.TRUE)
                            .isTotalOutstandingInterest(outstandingInterest)
                            .build())
                    .identifier(identifier)
                    .build();
            return nbfcRequestDTO;
        } catch (Exception e) {
            log.info("Exception in generating foreclosure receipt payload of USFB for {}, {}, {}", applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }
}
