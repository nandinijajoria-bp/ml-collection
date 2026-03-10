package com.bharatpe.lending.util;

import com.bharatpe.common.entities.LendingEDISchedule;
import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.Constants.SnapshotSource;
import com.bharatpe.lending.common.dao.LendingPaymentScheduleSnapDao;
import com.bharatpe.lending.common.entity.LendingPaymentScheduleSnapshot;
import com.bharatpe.lending.entity.LendingEdiScheduleSnapshot;
import com.bharatpe.lending.entity.LendingLedgerSnapshot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class SnapshotUtil {

    @Autowired
    private LendingPaymentScheduleSnapDao lendingPaymentScheduleSnapDao;

    public LendingEdiScheduleSnapshot createSnapshotForEdiSchedule(LendingEDISchedule lendingEDISchedule, SnapshotSource snapshotSource, int version) {
        return LendingEdiScheduleSnapshot.builder()
                .source(snapshotSource.name())
                .version(version)
                .merchantId(lendingEDISchedule.getMerchantId())
                .merchantStoreId(lendingEDISchedule.getMerchantStoreId())
                .loanId(lendingEDISchedule.getLendingPaymentSchedule().getId())
                .applicationId(lendingEDISchedule.getLoanApplication().getId())
                .construct(lendingEDISchedule.getConstruct())
                .date(lendingEDISchedule.getDate())
                .installmentNumber(lendingEDISchedule.getInstallmentNumber())
                .ediType(lendingEDISchedule.getEdiType())
                .openingBalance(lendingEDISchedule.getOpeningBalance())
                .totalEdi(lendingEDISchedule.getTotalEdi())
                .principle(lendingEDISchedule.getPrinciple())
                .interest(lendingEDISchedule.getInterest())
                .processingFee(lendingEDISchedule.getProcessingFee())
                .otherCharges(lendingEDISchedule.getOtherCharges())
                .paidPrinciple(lendingEDISchedule.getPaidPrinciple())
                .paidInterest(lendingEDISchedule.getPaidInterest())
                .build();
    }

    public LendingLedgerSnapshot createSnapshotForLedger(LendingPaymentSchedule lps, LendingLedger lendingLedger, SnapshotSource snapshotSource, int version) {
        return LendingLedgerSnapshot.builder()
                .source(snapshotSource.name())
                .version(version)
                .ledgerId(lendingLedger.getId())
                .merchantId(lendingLedger.getMerchantId())
                .merchantStoreId(lendingLedger.getMerchantStoreId())
                .loanId(lps.getId())
                .txnType(lendingLedger.getTxnType())
                .amount(BigDecimal.valueOf(lendingLedger.getAmount()))
                .date(lendingLedger.getDate())
                .description(lendingLedger.getDescription())
                .settlementId(lendingLedger.getSettlementId())
                .principle(lendingLedger.getPrinciple() != null ? BigDecimal.valueOf(lendingLedger.getPrinciple()) : null)
                .interest(lendingLedger.getInterest() != null ? BigDecimal.valueOf(lendingLedger.getInterest()) : null)
                .otherCharges(lendingLedger.getOtherCharges() != null ? BigDecimal.valueOf(lendingLedger.getOtherCharges()) : null)
                .penalty(lendingLedger.getPenalty() != null ? BigDecimal.valueOf(lendingLedger.getPenalty()) : null)
                .adjustmentMode(lendingLedger.getAdjustmentMode())
                .transferType(lendingLedger.getTransferType())
                .terminalOrderId(lendingLedger.getTerminalOrderId())
                .build();
    }

    public LendingPaymentScheduleSnapshot createSnapshotForLPS(LendingPaymentSchedule lendingPaymentSchedule, SnapshotSource snapshotSource, int version) {
        LendingPaymentScheduleSnapshot lpsSnap = LendingPaymentScheduleSnapshot.builder()
                .loanId(lendingPaymentSchedule.getId())
                .merchantId(lendingPaymentSchedule.getMerchantId())
                .merchantStoreId(lendingPaymentSchedule.getMerchantStoreId())
                .loanType(lendingPaymentSchedule.getLoanType())
                .loanAmount(lendingPaymentSchedule.getLoanAmount())
                .ediAmount(lendingPaymentSchedule.getEdiAmount())
                .startDate(lendingPaymentSchedule.getStartDate())
                .ediCount(lendingPaymentSchedule.getEdiCount())
                .interestOnlyEdiAmount(lendingPaymentSchedule.getInterestOnlyEdiAmount())
                .interestOnlyStartDate(lendingPaymentSchedule.getInterestOnlyStartDate())
                .interestOnlyEdiCount(lendingPaymentSchedule.getInterestOnlyEdiCount())
                .remainingInterestOnlyEdiCount(lendingPaymentSchedule.getRemainingInterestOnlyEdiCount())
                .overdueIntrestRate(lendingPaymentSchedule.getOverdueIntrestRate())
                .overdueEdiCount(lendingPaymentSchedule.getOverdueEdiCount())
                .overdueAmount(lendingPaymentSchedule.getOverdueAmount())
                .incentiveAmount(lendingPaymentSchedule.getIncentiveAmount())
                .ediRemainingCount(lendingPaymentSchedule.getEdiRemainingCount())
                .dueAmount(lendingPaymentSchedule.getDueAmount())
                .paidAmount(lendingPaymentSchedule.getPaidAmount())
                .totalCashbackAmount(lendingPaymentSchedule.getTotalCashbackAmount())
                .totalPenaltyAmount(lendingPaymentSchedule.getTotalPenaltyAmount())
                .nextEdiDate(lendingPaymentSchedule.getNextEdiDate())
                .status(lendingPaymentSchedule.getStatus())
                .offDay(lendingPaymentSchedule.getOffDay())
                .applicationId(lendingPaymentSchedule.getApplicationId())
                .totalPayableAmount(lendingPaymentSchedule.getTotalPayableAmount())
                .mobile(lendingPaymentSchedule.getMobile())
                .nbfc(lendingPaymentSchedule.getNbfc())
                .closingDate(lendingPaymentSchedule.getClosingDate())
                .tentativeClosingDate(lendingPaymentSchedule.getTentativeClosingDate())
                .loanConstruct(lendingPaymentSchedule.getLoanConstruct())
                .interest(lendingPaymentSchedule.getInterest())
                .otherCharges(lendingPaymentSchedule.getOtherCharges())
                .duePrinciple(lendingPaymentSchedule.getDuePrinciple())
                .dueInterest(lendingPaymentSchedule.getDueInterest())
                .dueOtherCharges(lendingPaymentSchedule.getDueOtherCharges())
                .duePenalty(lendingPaymentSchedule.getDuePenalty())
                .paidPrinciple(lendingPaymentSchedule.getPaidPrinciple())
                .paidInterest(lendingPaymentSchedule.getPaidInterest())
                .paidOtherCharges(lendingPaymentSchedule.getPaidOtherCharges())
                .paidPenalty(lendingPaymentSchedule.getPaidPenalty())
                .disbursalSettlementId(lendingPaymentSchedule.getDisbursalSettlementId())
                .creditLoan(lendingPaymentSchedule.getCreditLoan())
                .tlDetailsId(lendingPaymentSchedule.getTlDetailsId())
                .lenderDisbursalNotify(lendingPaymentSchedule.getLenderDisbursalNotify())
                .adjustedDueAmount(lendingPaymentSchedule.getAdjustedDueAmount())
                .adjustedPaidAmount(lendingPaymentSchedule.getAdjustedPaidAmount())
                .settlementStatus(lendingPaymentSchedule.getSettlementStatus())
                .settlementMechanism(lendingPaymentSchedule.getSettlementMechanism())
                .settleAllPrinciple(lendingPaymentSchedule.getSettleAllPrinciple())
                .writeoffFor(lendingPaymentSchedule.getWriteoffFor())
                .lastOverDueAmount(lendingPaymentSchedule.getLastOverDueAmount())
                .isSettlementInitiated(lendingPaymentSchedule.getSettlementInitiated())
                .settlementDate(lendingPaymentSchedule.getSettlementDate())
                .source(snapshotSource.name())
                .version(version)
//                .writeoffDate(lendingPaymentSchedule.getWriteoffDate())
//                .fldgSettled(lendingPaymentSchedule.getFldgSettled())
//                .fldgSettlementDate(lendingPaymentSchedule.getFldgSettlementDate())
//                .fldgTotalSettled(lendingPaymentSchedule.getFldgTotalSettled())
//                .perpetualDpdAdjusted(lendingPaymentSchedule.getPerpetualDpdAdjusted())
                .isNpa(lendingPaymentSchedule.getNpa())
                .lmsSource(lendingPaymentSchedule.getLmsSource())
                .build();

        return lendingPaymentScheduleSnapDao.save(lpsSnap);
    }
}
