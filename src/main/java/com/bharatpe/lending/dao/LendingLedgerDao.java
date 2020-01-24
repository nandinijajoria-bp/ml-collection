package com.bharatpe.lending.dao;

import com.bharatpe.common.entities.LendingLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface LendingLedgerDao extends JpaRepository<LendingLedger, Long> {

    @Query(value = "SELECT * FROM lending_ledger WHERE merchant_id = :merchantId AND loan_id = :loanId AND transaction_type = 'EDI' AND amount > 0 ORDER BY date DESC LIMIT 1", nativeQuery = true)
    LendingLedger findLastPaymentEntryByMerchantAndLoan(Long merchantId, Long loanId);

    @Query(value = "SELECT * FROM lending_ledger WHERE merchant_id = :merchantId AND loan_id = :loanId AND transaction_type = 'EDI' AND amount < 0 ORDER BY date DESC LIMIT 1", nativeQuery = true)
    LendingLedger findLastEDIDueEntryByMerchantAndLoan(Long merchantId, Long loanId);
}
