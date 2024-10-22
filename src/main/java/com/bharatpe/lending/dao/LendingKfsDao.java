package com.bharatpe.lending.dao;

import com.bharatpe.lending.entity.LendingKfs;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LendingKfsDao extends JpaRepository<LendingKfs, Long> {
    LendingKfs findTop1ByApplicationIdOrderByIdDesc(long applicationId);

    LendingKfs findTop1ByApplicationIdAndLenderOrderByIdDesc(Long applicationId, String lender);
}
