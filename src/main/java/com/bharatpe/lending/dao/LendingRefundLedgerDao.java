package com.bharatpe.lending.dao;

import com.bharatpe.lending.entity.LendingRefundLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LendingRefundLedgerDao extends JpaRepository<LendingRefundLedger, Long> {

    List<LendingRefundLedger> findByMerchantIdAndLoanIdAndStatus(Long merchantId, Long loanId, String status);

    List<LendingRefundLedger> findByMerchantIdAndLoanIdAndAdjustmentModeOrderByIdDesc(Long merchantId, Long loanId, String adjustmentMode);

    LendingRefundLedger findByTerminalOrderId(String terminalOrderId);

    @Query(
            value = "select sum(amount) from lending_refund_ledger " +
                    "where merchant_id = :merchantId " +
                    "and loan_id = :loanId " +
                    "and adjustment_mode = :adjustmentMode " +
                    "and status = :status " +
                    "group by loan_id", nativeQuery = true
    )
    Double findTotalExcessNachAmount(Long merchantId, Long loanId, String adjustmentMode, String status);
}
