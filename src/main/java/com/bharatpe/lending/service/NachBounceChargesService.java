package com.bharatpe.lending.service;

import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.dao.PenalChargesDao;
import com.bharatpe.lending.common.dao.PenaltyFeeLedgerDao;
import com.bharatpe.lending.common.entity.PenalCharges;
import com.bharatpe.lending.common.entity.PenaltyFeeLedger;
import com.bharatpe.lending.common.enums.LendingEnum;
import com.bharatpe.lending.common.query.dao.LendingPullPaymentDaoSlave;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.dao.LendingLedgerDao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Calendar;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class NachBounceChargesService {
    @Autowired
    PenalChargesDao penalChargesDao;

    @Autowired
    LendingPullPaymentDaoSlave lendingPullPaymentDaoSlave;

    @Autowired
    PenaltyFeeLedgerDao penaltyFeeLedgerDao;

    @Autowired
    LendingLedgerDao lendingLedgerDao;

    @Value("${payu.nach.bounce.charge:500}")
    Integer payUNachBounceCharge;

    public Double getNachCharges(LendingPaymentSchedule lendingPaymentSchedule) {
        Double nachCharges = 0d;
        if (LendingEnum.LENDER.PAYU.name().equals(lendingPaymentSchedule.getNbfc()) && checkForNachBounce(lendingPaymentSchedule)) {
            log.info("Found Nach Bounce Charges for loan: {}, nbfc: {} ", lendingPaymentSchedule.getId(), lendingPaymentSchedule.getNbfc());
            nachCharges += payUNachBounceCharge;
        }
        return nachCharges;
    }

    public void createCharges(LendingPaymentSchedule loan, String requestId) {
        savePenalCharges(loan, 0.0, payUNachBounceCharge);
        creatingPenaltyInPenaltyLedger(loan, payUNachBounceCharge, "Nach Bounce", false, requestId);
        createLendingLedgerForPenalty(loan, payUNachBounceCharge, "NACH BOUNCE PENALTY FEE");
    }

    public boolean checkForNachBounce(LendingPaymentSchedule activeLoan) {
        // remove this if condition when payu is live
        if(LendingEnum.LENDER.PAYU.name().equals(activeLoan.getNbfc())){
            return false;
        }
        Date loanStartDate = activeLoan.getStartDate();

        Date currentDate = DateTimeUtil.getCurrentDayStartTime();
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.MINUTE, 0);

        long diffInDays = TimeUnit.DAYS.convert(Math.abs(currentDate.getTime() - loanStartDate.getTime()), TimeUnit.MILLISECONDS);

        int remainingDays = (int) (diffInDays % 30);
        log.info("No. of remaining days : {}, startDate {} , endDate {} ", remainingDays+1, DateTimeUtil.addDays(DateTimeUtil.getCurrentDayStartTime(), -(remainingDays)), Calendar.getInstance().getTime());

        Integer lastMonthNachPullCount = lendingPullPaymentDaoSlave.findFailedNachPullInLastMonth(activeLoan.getMerchantId(),
                activeLoan.getId(), "NACH", DateTimeUtil.addDays(DateTimeUtil.getCurrentDayStartTime(), -(remainingDays)),
                Calendar.getInstance().getTime(), "FAILED");
        log.info("No. of failed Nach Pull in remaining days for loan: {}: {}", activeLoan.getId(), lastMonthNachPullCount);
        return lastMonthNachPullCount > 0;
    }

    private void savePenalCharges(LendingPaymentSchedule lendingPaymentSchedule, double penaltyFee, double nachBounceCharge) {
        try {
            log.info("Saving Penal Charges for loan: {} with penaltyFee: {}, and nach bounce: {}", lendingPaymentSchedule.getId(), penaltyFee, nachBounceCharge);
            PenalCharges penalCharge = penalChargesDao.findByLoanId(lendingPaymentSchedule.getId());
            if (Objects.isNull(penalCharge)) {
                penalCharge = new PenalCharges(lendingPaymentSchedule.getId(), lendingPaymentSchedule.getMerchantId(),
                        lendingPaymentSchedule.getNbfc(), penaltyFee, 0, nachBounceCharge, 0);
            } else {
                penalCharge.setDuePenalty(penalCharge.getDuePenalty() + penaltyFee);
                penalCharge.setDueNachBounce(penalCharge.getDueNachBounce() + nachBounceCharge);
            }

            penalChargesDao.save(penalCharge);
        } catch (Exception e) {
            log.info("Exception occurred while saving penal charges for loan: {}, {}", lendingPaymentSchedule.getId(), e.getMessage(), e);
        }
    }

    private void creatingPenaltyInPenaltyLedger(LendingPaymentSchedule loan, double penaltyFee, String description, boolean isWaiveOff, String requestID) {
        PenaltyFeeLedger penaltyFeeLedger = PenaltyFeeLedger.builder()
                .chargeRequestId(requestID)
                .loanId(loan.getId())
                .merchantId(loan.getMerchantId())
                .amount((-1)*penaltyFee)
                .description(description)
                .isWaveOff(isWaiveOff)
                .lender(loan.getNbfc())
                .build();
        penaltyFeeLedgerDao.save(penaltyFeeLedger);
    }

    private LendingLedger createLendingLedgerForPenalty(LendingPaymentSchedule loan, double penaltyFee, String penaltyDescription) {
        if(ObjectUtils.isEmpty(penaltyFee)) {
            return null;
        }

        LendingLedger lendingLedger = new LendingLedger();
        lendingLedger.setMerchantId(loan.getMerchantId());
        if(loan.getMerchantStoreId() != null && loan.getMerchantStoreId() > 0) lendingLedger.setMerchantStoreId(loan.getMerchantStoreId());
        lendingLedger.setLendingPaymentSchedule(loan);
        lendingLedger.setDate(DateTimeUtil.getCurrentDayStartTime());
        lendingLedger.setTxnType("EDI");
        lendingLedger.setAmount(-penaltyFee);
        lendingLedger.setInterest(0d);
        lendingLedger.setPrinciple(0d);
        lendingLedger.setOtherCharges(0d);
        lendingLedger.setPenalty(-penaltyFee);
        lendingLedger.setDescription(penaltyDescription);
        lendingLedgerDao.save(lendingLedger);
        return lendingLedger;
    }
}
