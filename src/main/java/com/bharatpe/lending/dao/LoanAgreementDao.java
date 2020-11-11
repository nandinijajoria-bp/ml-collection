package com.bharatpe.lending.dao;

import org.springframework.data.jpa.repository.JpaRepository;
 
import com.bharatpe.lending.entity.LoanAgreement;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface LoanAgreementDao extends JpaRepository<LoanAgreement,Long> {

    @Query(nativeQuery = true, value = "select * from loan_agreement where application_id=:id and type='agreement' order by id desc limit 1")
    LoanAgreement findByApplicationId(Long id);

    @Query(nativeQuery = true, value = "select * from loan_agreement where  type='invoice' order by id desc limit 1")
    LoanAgreement findByLastId();

 }
