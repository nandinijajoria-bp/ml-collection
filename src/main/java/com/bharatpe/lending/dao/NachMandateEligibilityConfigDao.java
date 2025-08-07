package com.bharatpe.lending.dao;

import com.bharatpe.lending.entity.NachMandateEligibilityConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NachMandateEligibilityConfigDao extends JpaRepository<NachMandateEligibilityConfig, Long> {

    @Query(
            nativeQuery = true,
            value = "select * from nach_mandate_eligibility_config where lender=:lender and min_total_payable_amount < :totalPayableAmount and max_total_payable_amount >= :totalPayableAmount and loan_segment = :loanSegment and status = 1 order by id desc limit 1"
    )
    NachMandateEligibilityConfig findNachMandateEligibilityConfigLenderAndLoanSegmentAndLoanAmountWise(String lender, Double totalPayableAmount, String loanSegment);

    @Query(nativeQuery = true, value = "select * from nach_mandate_eligibility_config where lender=:lender " +
            "and status = 1 order by id")
    List<NachMandateEligibilityConfig> findNachMandateEligibilityConfigByLender(String lender);

}
