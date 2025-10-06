package com.bharatpe.lending.dao;

import com.bharatpe.lending.entity.AutopayUPIConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AutopayUpiConfigDao extends JpaRepository<AutopayUPIConfig, Long> {

    AutopayUPIConfig findAutoPayUpiConfigByLenderAndLoanSegmentAndStatus(String lender, String loanSegment, String status);
}
