package com.bharatpe.lending.collection.core.service.impl;

import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.dao.PenalChargesDao;
import com.bharatpe.lending.common.dao.PenaltyFeeLedgerDao;
import com.bharatpe.lending.common.entity.PenalCharges;
import com.bharatpe.lending.common.entity.PenaltyFeeLedger;
import com.bharatpe.lending.common.query.dao.LendingPullPaymentDaoSlave;
import com.bharatpe.lending.common.query.dao.PenaltyFeeConfigDaoSlave;
import com.bharatpe.lending.common.query.entity.LendingPullPaymentSlave;
import com.bharatpe.lending.common.query.entity.PenaltyFeeConfigSlave;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Objects;

@Service
@Slf4j
public class GenericOverDueBasedPenaltyService {

    private final Logger logger = LoggerFactory.getLogger(GenericOverDueBasedPenaltyService.class);

    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;

    @Autowired
    PenaltyFeeLedgerDao penaltyFeeLedgerDao;

    @Autowired
    PenalChargesDao penalChargesDao;

    @Autowired
    LendingPullPaymentDaoSlave lendingPullPaymentDaoSlave;

    @Autowired
    PenaltyFeeConfigDaoSlave penaltyFeeConfigDaoSlave;

    @Value("${overdue.penalty.eligible.lenders}")
    String overDuePenaltyEligibleLenders;

    Double applyPenalty(LendingPaymentSchedule loan) {
        logger.info("Creating Lender Specific Max OverDue Penalty Fee for loan: {}", loan);

        double penaltyFee = 0;
        double existingPenaltyAmount = Objects.nonNull(loan.getTotalPenaltyAmount()) ? loan.getTotalPenaltyAmount() : 0;
        double nachBouncePenaltyCharge = 650.0;

        try {
            int overdueEdiCount = getOverdueEdiCount(loan);
            double lastOverDueAmount = Objects.nonNull(loan.getLastOverDueAmount()) ? loan.getLastOverDueAmount() : 0;
            double overdueAmount = Objects.nonNull(loan.getOverdueAmount()) ? loan.getOverdueAmount() : 0;

            overdueAmount = Math.max(overdueAmount, loan.getDueAmount() - lastOverDueAmount);
            overdueEdiCount += 1;

            if (overDuePenaltyEligibleLenders.contains(loan.getNbfc()) && overdueEdiCount > 30) {
                logger.info("Applying Penalty Fee for loan: {} with overdueCount: {}", loan.getId(), overdueEdiCount);

                PenaltyFeeConfigSlave penaltyFeeConfigSlave = penaltyFeeConfigDaoSlave.findByDueAmountAndVersionAndStatusAndLender(overdueAmount, 2.0, loan.getNbfc());

                penaltyFee = getPenaltyFromConfig(penaltyFeeConfigSlave, overdueAmount);
                if (penaltyFee > 0) {
                    creatingPenaltyInPenaltyLedger(loan, penaltyFee, "Penalty Fee", false);
                }

                logger.info("Total Penalty Fee after Penalty on Overdue Amount for loan: {}: {}", loan.getId(), penaltyFee);

                //Nach Bounce Charge Penalty
                boolean isNachBounce = checkForNachBounce(loan);
                savePenalCharges(loan, isNachBounce, penaltyFee, nachBouncePenaltyCharge);

                if (isNachBounce) {
                    penaltyFee += nachBouncePenaltyCharge;
                    creatingPenaltyInPenaltyLedger(loan, nachBouncePenaltyCharge, "Nach Bounce", false);
                }

                logger.info("Total Penalty Fee after Penalty on Nach Bounce for loan: {}: {}", loan.getId(), penaltyFee);

                //resetting everything to 0, start 30 days cycle again
                lastOverDueAmount = overdueAmount;
                overdueEdiCount = 0;
                overdueAmount = 0;

                existingPenaltyAmount += penaltyFee;

                penaltyFee += Objects.nonNull(loan.getDuePenalty()) ? loan.getDuePenalty() : 0;

                loan.setDuePenalty(penaltyFee);
                loan.setTotalPenaltyAmount(existingPenaltyAmount);
                loan.setLastOverDueAmount(lastOverDueAmount);

            }
            loan.setOverdueAmount(overdueAmount);
            loan.setOverdueEdiCount(overdueEdiCount);
            lendingPaymentScheduleDao.save(loan);

        }
        catch (Exception e){
            logger.error("Error in Generic Overdue Based Penalty for loan: {}: {}", loan.getId(), e.getMessage(), e);
        }
        return penaltyFee;
    }

    private int getOverdueEdiCount(LendingPaymentSchedule lendingPaymentSchedule) {
        return Objects.nonNull(lendingPaymentSchedule.getOverdueEdiCount()) ? lendingPaymentSchedule.getOverdueEdiCount() : 0;
    }

    private void savePenalCharges(LendingPaymentSchedule lendingPaymentSchedule, boolean isNachBounce, double penaltyFee, double nachBounceCharge) {
        try {
            logger.info("Saving Penal Charges for loan: {} with penaltyFee: {}, is nach bounce: {}", lendingPaymentSchedule.getId(), penaltyFee, isNachBounce);
            PenalCharges penalCharge = penalChargesDao.findByLoanId(lendingPaymentSchedule.getId());
            double nachCharge = isNachBounce ? nachBounceCharge : 0;
            if (Objects.isNull(penalCharge)) {
                penalCharge = new PenalCharges(lendingPaymentSchedule.getId(), lendingPaymentSchedule.getMerchantId(),
                        lendingPaymentSchedule.getNbfc(), penaltyFee, 0, nachCharge, 0);
            } else {
                penalCharge.setDuePenalty(penalCharge.getDuePenalty() + penaltyFee);
                penalCharge.setDueNachBounce(penalCharge.getDueNachBounce() + nachCharge);
            }

            penalChargesDao.save(penalCharge);
        } catch (Exception e) {
            logger.info("Exception occurred while saving penal charges for loan: {}, {}", lendingPaymentSchedule.getId(), e.getMessage(), e);
        }
    }

    private boolean checkForNachBounce(LendingPaymentSchedule activeLoan) {
        LendingPullPaymentSlave lendingPullPaymentSlave = lendingPullPaymentDaoSlave.findByMerchantIdAndLoanIdAndModeAndDateBetweenAndStatus(activeLoan.getMerchantId(),
                activeLoan.getId(), "NACH",
                DateTimeUtil.addDays(activeLoan.getUpdatedAt(), -30), activeLoan.getUpdatedAt(), "FAILED");

        return !ObjectUtils.isEmpty(lendingPullPaymentSlave);
    }

    private double getPenaltyFromConfig(PenaltyFeeConfigSlave penaltyFeeConfigSlave, Double dueAmount) {
        if("FLAT".equals(penaltyFeeConfigSlave.getType())){
            return penaltyFeeConfigSlave.getPenalty();
        }
        double rate = penaltyFeeConfigSlave.getPenalty();
        return dueAmount * rate;
    }

    private void creatingPenaltyInPenaltyLedger(LendingPaymentSchedule loan, double penaltyFee, String description, boolean isWaiveOff) {
        PenaltyFeeLedger penaltyFeeLedger = new PenaltyFeeLedger(loan.getMerchantId(), loan.getId(), -penaltyFee, description, isWaiveOff, loan.getNbfc());
        penaltyFeeLedgerDao.save(penaltyFeeLedger);
    }

}
