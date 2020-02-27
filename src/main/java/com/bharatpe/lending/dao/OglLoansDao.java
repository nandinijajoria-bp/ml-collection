package com.bharatpe.lending.dao;

import com.bharatpe.lending.entity.OglLoans;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OglLoansDao extends JpaRepository<OglLoans, Long> {

    OglLoans findByMerchantIdAndExternalLoanId(Long merchantId, String externalLoanId);
}
