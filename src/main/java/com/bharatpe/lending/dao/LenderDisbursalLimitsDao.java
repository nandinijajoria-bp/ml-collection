package com.bharatpe.lending.dao;

import com.bharatpe.lending.entity.LendingLenderQuota;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LenderDisbursalLimitsDao extends JpaRepository<LendingLenderQuota, Long> {

    @Query(value = "select * from easy_loan.lending_lender_quota where lender in :lenders and remaining_balance>=:amount order by remaining_balance desc", nativeQuery = true)
    List<LendingLenderQuota> fetchEligibleLenderLimits(List<String> lenders, Double amount);

    @Query(value = "select sum(assigned_amount) from easy_loan.lending_lender_quota", nativeQuery = true)
    Double fetchDisbursedCount();

    LendingLenderQuota findByLender(String lender);

    LendingLenderQuota findByEdiModelIsNull();
}
