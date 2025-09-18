package com.bharatpe.lending.loanV3.services.associationsV2.oxyzo.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.dao.LoanForeClosureChargesDao;
import com.bharatpe.lending.common.dao.PenaltyFeeLedgerDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.entity.LoanForeClosureCharges;
import com.bharatpe.lending.common.entity.PenaltyFeeLedger;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.request.oxyzo.OxyzoForeclosureDetailsRequestDTO;
import com.bharatpe.lending.collection.core.utils.LoanPaymentUtil;
import com.bharatpe.lending.util.LoanUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.bharatpe.lending.util.LoanUtil.COOL_OFF_PERIOD_DAYS;

@Slf4j
@Service
public class OxyzoForeclosureService {

    @Autowired
    LoanUtil loanUtil;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LoanForeClosureChargesDao loanForeClosureChargesDao;

    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;

    @Autowired
    private PenaltyFeeLedgerDao penaltyFeeLedgerDao;

    public NBFCRequestDTO getForeclosureReceiptRequest(Long applicationId, LendingLedger lendingLedger, Long orderId) {
        try {
            LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1ByApplicationIdAndLenderOrderByIdDesc(applicationId, Lender.OXYZO.name());
            LendingApplication lendingApplication = lendingApplicationDao.findById(applicationId).get();

            if (ObjectUtils.isEmpty(lendingApplication) || ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
                log.error("Oxyzo: lending application / LALD not found for {}", applicationId);
                return null;
            }

            LoanForeClosureCharges loanForeClosureCharges = loanForeClosureChargesDao.findByOrderId(orderId);
            LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.findByApplicationId(applicationId);

            LinkedHashMap<String, Object> identifier = new LinkedHashMap<>();
            identifier.put("loanId", lendingApplicationLenderDetails.getLan());
            String txnId = Optional.ofNullable(lendingLedger.getTerminalOrderId()).orElse(String.valueOf(lendingLedger.getId()));
            Date txnDate = LoanPaymentUtil.getNonFutureTransactionDate(lendingLedger.getDate());
            boolean loanCoolOffPeriod = checkLoanCoolOffPeriod(lendingPaymentSchedule.getCreatedAt());

            PenaltyFeeLedger penaltyFeeLedger = penaltyFeeLedgerDao.findByLoanIdAndTerminalIdAndPositiveAmt(
                    lendingLedger.getLendingPaymentSchedule().getId(), lendingLedger.getTerminalOrderId()
            );
            double nachBounce = 0D;
            double penaltyCharge = 0D;
            if (!ObjectUtils.isEmpty(penaltyFeeLedger)) {
                nachBounce = penaltyFeeLedger.getNachBounce() != null ? penaltyFeeLedger.getNachBounce() : 0D;
                penaltyCharge = penaltyFeeLedger.getPenalCharge() != null ? penaltyFeeLedger.getPenalCharge() : 0D;
            }

            return  NBFCRequestDTO.builder()
                    .applicationId(applicationId)
                    .lender(Lender.OXYZO.name())
                    .productName("LENDING")
                    .payload(OxyzoForeclosureDetailsRequestDTO.builder()
                            .loanId(String.valueOf(lendingApplicationLenderDetails.getLan()))
                            .amount(loanCoolOffPeriod ? BigDecimal.valueOf(lendingLedger.getAmount() - lendingApplication.getProcessingFee() - lendingPaymentSchedule.getDueInterest()) : BigDecimal.valueOf(lendingLedger.getAmount()))
                            .referenceNo(txnId)
                            .repaymentDate(txnDate.getTime())
                            .closureDate(txnDate.getTime())
                            .foreclosureCharges(getForeclosureCharges(loanForeClosureCharges))
                            .bounceCharges(BigDecimal.valueOf(nachBounce))
                            .penalCharges(BigDecimal.valueOf(penaltyCharge))
                            .latePaymentCharges(BigDecimal.valueOf(0))
                            .isCoolOffCase(loanCoolOffPeriod)
                            .coolingOffCharges(BigDecimal.valueOf(0))
                            .creditAmount(loanCoolOffPeriod ? BigDecimal.valueOf(lendingApplication.getProcessingFee()) : BigDecimal.valueOf(0))
                            .build())
                    .identifier(identifier)
                    .build();
        } catch (Exception e) {
            log.info("Exception in generating foreclosure receipt payload of Oxyzo for {}, {}, {}", applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    private BigDecimal getForeclosureCharges(LoanForeClosureCharges loanForeClosureCharges){

        BigDecimal foreclosureCharges = BigDecimal.valueOf(0);

        if(!ObjectUtils.isEmpty(loanForeClosureCharges)){
            Double foreclosureAmount = !ObjectUtils.isEmpty(loanForeClosureCharges.getAmount()) ? loanForeClosureCharges.getAmount() : 0D;
            Double foreclosureTax = !ObjectUtils.isEmpty(loanForeClosureCharges.getTax()) ? loanForeClosureCharges.getTax() : 0D;

            foreclosureCharges = BigDecimal.valueOf(foreclosureAmount + foreclosureTax);
        }

        return foreclosureCharges;
    }

    private boolean checkLoanCoolOffPeriod(Date createdAt) {
        double	durationInDays = calculateDurationInDays(createdAt);
        if(durationInDays < COOL_OFF_PERIOD_DAYS) return true;
        return false;
    }

    private double calculateDurationInDays(Date date) {
        log.info("inside calculate duration for loan {}",date);
        Date currentDate = new Date();
        // Convert milliseconds to days
        long differenceInMillis = currentDate.getTime() - date.getTime();
        return TimeUnit.MILLISECONDS.toDays(differenceInMillis);
    }

}
