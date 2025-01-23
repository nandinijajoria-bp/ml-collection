package com.bharatpe.lending.dao;

import com.bharatpe.lending.entity.NachMandateEligibilityConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface NachMandateEligibilityConfigDao extends JpaRepository<NachMandateEligibilityConfig, Long> {

    @Query(
            nativeQuery = true,
            value = "select * from nach_mandate_eligibility_config where lender=:lender and min_total_payable_amount < :totalPayableAmount and max_total_payable_amount >= :totalPayableAmount and status = 1 order by id desc limit 1"
    )
    NachMandateEligibilityConfig findNachMandateEligibilityConfigLenderAndLoanAmountWise(String lender, Double totalPayableAmount);
}
