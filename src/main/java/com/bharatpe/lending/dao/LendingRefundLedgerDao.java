package com.bharatpe.lending.dao;

import com.bharatpe.lending.entity.LendingRefundLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LendingRefundLedgerDao extends JpaRepository<LendingRefundLedger, Long> {

    List<LendingRefundLedger> findByMerchantIdAndLoanIdAndStatus(Long merchantId, Long loanId, String status);
}
