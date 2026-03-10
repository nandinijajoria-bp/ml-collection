package com.bharatpe.lending.dao;

import com.bharatpe.lending.entity.LendingLedgerSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface LendingLedgerSnapshotDao extends JpaRepository<LendingLedgerSnapshot, Long> {

    @Query(value = "SELECT version FROM lending_ledger_snapshot WHERE loan_id = :loanId AND source = :source ORDER BY id DESC LIMIT 1", nativeQuery = true)
    Integer findTop1VersionByLoanIdAndSourceOrderByIdDesc(Long loanId, String source);
}
