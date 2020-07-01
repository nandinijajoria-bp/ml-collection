package com.bharatpe.lending.dao;

import org.springframework.data.jpa.repository.JpaRepository;
 
import com.bharatpe.lending.entity.LoanAgreement;
import org.springframework.stereotype.Repository;

@Repository
public interface LoanAgreementDao extends JpaRepository<LoanAgreement,Long> {

	LoanAgreement findByApplicationId(Long id);

 }
