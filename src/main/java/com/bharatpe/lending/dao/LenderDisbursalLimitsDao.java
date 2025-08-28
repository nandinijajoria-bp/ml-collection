package com.bharatpe.lending.dao;

import com.bharatpe.lending.entity.LendingLenderQuota;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LenderDisbursalLimitsDao extends JpaRepository<LendingLenderQuota, Long> {

    @Query(value = "select * from lending_lender_quota where lender in :lenders and remaining_balance>=:amount order by remaining_balance desc", nativeQuery = true)
    List<LendingLenderQuota> fetchEligibleLenderLimits(List<String> lenders, Double amount);

    @Query(value = "select *, ((assigned_amount / total_weekly_amount) * 100) as percentage_utilisation from lending_lender_quota where lender in :lenders and remaining_balance >= :amount order by percentage_utilisation asc", nativeQuery = true)
    List<LendingLenderQuota> fetchEligibleLenderLimitsOrderByAssignment(List<String> lenders, Double amount);

    LendingLenderQuota findByClassification(String classification);

    @Query(value = "select sum(assigned_amount) from lending_lender_quota", nativeQuery = true)
    Double fetchDisbursedCount();

    LendingLenderQuota findByLender(String lender);

    LendingLenderQuota findByEdiModelIsNull();

    @Query(value = "SELECT * FROM lending_lender_quota " +
            "WHERE updated_at BETWEEN :startDate AND :endDate",
            nativeQuery = true)
    List<LendingLenderQuota> findByUpdatedAtBetween(@Param("startDate") LocalDateTime startDate,
                                                    @Param("endDate") LocalDateTime endDate);
}
