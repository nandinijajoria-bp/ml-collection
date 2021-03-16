package com.bharatpe.lending.dao;

import org.springframework.data.jpa.repository.JpaRepository;
 
import com.bharatpe.lending.entity.LoanAgreement;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface LoanAgreementDao extends JpaRepository<LoanAgreement,Long> {

    @Query(nativeQuery = true, value = "select * from loan_agreement where application_id=:id and type=:type order by id desc limit 1")
    LoanAgreement findByApplicationIdAndType(Long id,String type);

    @Query(nativeQuery = true, value = "select * from loan_agreement where  type='invoice' order by id desc limit 1")
    LoanAgreement findByLastId();

 }
