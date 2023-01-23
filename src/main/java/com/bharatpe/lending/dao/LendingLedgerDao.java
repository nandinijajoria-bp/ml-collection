package com.bharatpe.lending.dao;

import java.util.Date;
import java.util.List;

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

    @Query(nativeQuery = true, value = "select date, ifnull(-1*sum(case when amount<0 then amount end),0) as due,ifnull(sum(case when amount>0 then amount end),0) as paid from lending_ledger where loan_id=:loanId group by date(date)")
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

    List<LendingLedger> findByLendingPaymentScheduleOrderByDate(LendingPaymentSchedule lendingPaymentSchedule);

    @Query(value = "select * from lending_ledger where amount > 0 and created_at > :date and merchant_id = :merchantId", nativeQuery = true)
    LendingLedger findLastLedgerEntry(Long merchantId, Date date);

}
