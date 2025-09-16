package com.bharatpe.lending.dao;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.common.entities.LendingPaymentSchedule;

@Repository
public interface LendingLedgerDao extends JpaRepository<LendingLedger, Long> {

    @Query(value = "SELECT * FROM lending_ledger WHERE merchant_id = :merchantId AND loan_id = :loanId AND transaction_type = 'EDI' AND amount > 0 ORDER BY date DESC LIMIT 1", nativeQuery = true)
    LendingLedger findLastPaymentEntryByMerchantAndLoan(Long merchantId, Long loanId);

    @Query(value = "SELECT * FROM lending_ledger WHERE merchant_id = :merchantId AND loan_id = :loanId AND transaction_type = 'EDI' AND amount < 0 ORDER BY date DESC LIMIT 1", nativeQuery = true)
    LendingLedger findLastEDIDueEntryByMerchantAndLoan(Long merchantId, Long loanId);

    List<LendingLedger> findByLendingPaymentScheduleAndDescription(LendingPaymentSchedule lendingPaymentSchedule, String description);

    List<LendingLedger> findByLendingPaymentSchedule(LendingPaymentSchedule lendingPaymentSchedule);

    List<LendingLedger> findByLendingPaymentScheduleOrderByDateDescAmountAsc(LendingPaymentSchedule lendingPaymentSchedule);

    @Query(value = "SELECT * FROM lending_ledger WHERE merchant_id = :id AND amount > 0 ORDER BY date DESC", nativeQuery = true)
	 List<LendingLedger> findByMerchantIdOrderByDateDesc(Long id);

    List<LendingLedger> findByLendingPaymentScheduleOrderByDateAscAmountAsc(LendingPaymentSchedule lendingPaymentSchedule);

    @Query(value = "select count(ll.id) from lending_ledger ll where ll.loan_id=:lpsId and ll.amount>=:ediAmount", nativeQuery = true)
    Integer findLedgerCountOnAmountGreaterThanEdiAmount(Long lpsId, Double ediAmount);

    @Query(nativeQuery = true, value = "select * from lending_ledger where description like 'PRECLOSER%' and loan_id=:lpsId limit 1")
    LendingLedger getForClosedLedger(Long lpsId);

    @Query(nativeQuery = true, value = "select ifnull(sum(amount),0) from lending_ledger where loan_id=:loanId and amount>0 and date>=subdate(current_date, 30)")
    Double getAmountPaidLastMonth(Long loanId);

    @Query(nativeQuery = true, value = "select date, ifnull(-1*sum(case when amount<0 then amount end),0) as due,ifnull(sum(case when amount>0 then amount end),0) as paid from lending_ledger where loan_id=:loanId and (adjustment_mode is null or adjustment_mode!='EXTRA_COLLECTED_ARC') group by date(date)")
    List<SettlementDTO> getSettlements(Long loanId);

    interface SettlementDTO{
        Date getDate();
        Double getDue();
        Double getPaid();
    }

    @Query(nativeQuery = true, value = "select loan_id from lending_ledger where loan_id=:loanId and "
      + "adjustment_mode in ('TOPUP','IO_TOPUP','HALF_TOPUP') order by created_at desc limit 1")
    Long getLedgerByAdjustmentModes(Long loanId);

    @Query(nativeQuery = true, value = "select ifnull(sum(amount),0) from lending_ledger where loan_id=:loanId and amount>0 and adjustment_mode in ('SETTLEMENT', 'FP')")
    Double findSettlementAmount(Long loanId);

    List<LendingLedger> findByLendingPaymentScheduleOrderByDateAsc(LendingPaymentSchedule lendingPaymentSchedule);

    @Query(nativeQuery = true, value = "select * from lending_ledger WHERE loan_id=:loanId order by id desc limit 7")
    List<LendingLedger> findByLendingPaymentScheduleOrderByDateAsc(Long loanId);


    @Query(value = "select * from lending_ledger where amount > 0 and created_at > :date and merchant_id = :merchantId", nativeQuery = true)
    LendingLedger findLastLedgerEntry(Long merchantId, Date date);

    @Query(value = "SELECT * FROM lending_ledger WHERE merchant_id = :merchantId AND amount > 0 ORDER BY id DESC limit 1", nativeQuery = true)
    LendingLedger findByMerchantIdOrderByIdDesc(Long merchantId);

    @Query(value = "SELECT * FROM lending_ledger WHERE loan_id = :lpsId AND created_at >= :date AND amount < 0 AND adjustment_mode is null ORDER BY id DESC limit 1", nativeQuery = true)
    LendingLedger findAdvanceEdiDueOfPerpetualDpdLoan(Long lpsId, Date date);

    @Query(value = "select ifnull(sum(interest),0) as totalDueInterest,ifnull(sum(principle),0) as totalDuePrinciple, " +
            "ifnull(sum(amount),0) as totalDueAmount from lending_ledger where loan_id=:loanId and amount<0 and penalty =0", nativeQuery = true)
    LendingLedgerSummaryDto fetchLedgerEdiSummary(Long loanId);

    @Query(value = "select ifnull(sum(interest),0) as totalPaidInterest,ifnull(sum(principle),0) as totalPaidPrinciple, " +
            "ifnull(sum(amount),0) as totalPaidAmount from lending_ledger where loan_id=:loanId and amount>0", nativeQuery = true)
    LendingLedgerSummaryDto fetchLedgerSummaryPaid(Long loanId);

    @Query(value = "select ifnull(sum(interest),0) as totalPaidInterest,ifnull(sum(principle),0) as totalPaidPrinciple, " +
            "ifnull(sum(amount),0) as totalPaidAmount from lending_ledger where loan_id=:loanId AND settlement_id = :settlementId " +
            "AND amount>0 and created_at >= :settlementInitDate", nativeQuery = true)
    LendingLedgerSummaryDto fetchLedgerSummaryPaidAfterSettlementInit(Long loanId, Long settlementId, Date settlementInitDate);

    @Query(value = "select ifnull(sum(interest),0) as totalPaidInterest,ifnull(sum(principle),0) as totalPaidPrinciple, " +
            "ifnull(sum(amount),0) as totalPaidAmount, ifnull(sum(penalty),0) as totalPaidPenalty, " +
            "ifnull(sum(other_charges),0) as totalPaidOtherCharges from lending_ledger where loan_id=:loanId and amount>0 " +
            "and description = :desc", nativeQuery = true)
    LendingLedgerSummaryDto fetchLedgerSummaryWaiverEntry(Long loanId, String desc);

    interface LendingLedgerSummaryDto {
        Double getTotalDueInterest();

        Double getTotalDuePrinciple();

        Double getTotalPaidPrinciple();

        Double getTotalPaidInterest();

        Double getTotalPaidAmount();

        Double getTotalDueAmount();

        Double getTotalPaidPenalty();
        Double getTotalPaidOtherCharges();
    }
    @Query(nativeQuery = true, value ="SELECT COALESCE(SUM(amount), 0) AS amount, COALESCE(SUM(principle), 0) AS principle, COALESCE(SUM(interest), 0) AS interest FROM lending_ledger WHERE loan_id = :loanId AND amount < 0")
    Map<String, Object> totalNegativeEdiAmount(Long loanId);

    @Query(nativeQuery = true, value = "SELECT COALESCE(SUM(amount), 0) AS amount, COALESCE(SUM(principle), 0) AS principle, COALESCE(SUM(interest), 0) AS interest FROM lending_ledger WHERE loan_id = :loanId AND amount > 0")
    Map<String, Object> totalPositiveEdiAmount(Long loanId);

    @Query(nativeQuery = true, value ="SELECT * FROM lending_ledger WHERE loan_id = :loanId AND amount > 0")
    List<LendingLedger> positiveEdiEntries(Long loanId);
    @Query(value = "SELECT * FROM lending_ledger WHERE loan_id = :lpsId AND created_at >= :date ORDER BY id DESC", nativeQuery = true)
    List<LendingLedger> findAdvanceEdiLedgerList(Long lpsId, Date date);

    @Query(value = "SELECT * FROM lending_ledger WHERE merchant_id = :id AND amount > 0 AND date >= :date ORDER BY date DESC", nativeQuery = true)
    List<LendingLedger> findByMerchantIdAndDateAfter(Long id, Date date);


}
