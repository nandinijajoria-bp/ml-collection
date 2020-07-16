package com.bharatpe.lending.dao;

import org.springframework.stereotype.Repository;

import com.bharatpe.common.entities.LendingPaymentSchedule;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

@Repository
public interface LendingPaymentScheduleDao extends CrudRepository<LendingPaymentSchedule, Long> {
	@Query(value="SELECT * FROM lending_payment_schedule WHERE merchant_id = :merchantId and credit_loan =false ORDER BY ID DESC LIMIT 1",nativeQuery=true)
	LendingPaymentSchedule findLatestLendingPaymentScheduleByMerchantId(Long merchantId);
	
	LendingPaymentSchedule	findTop1ByMerchantIdAndStatusAndCreditLoanOrderByIdDesc(Long merchantId, String status,Boolean creditLoan);
		
	List<LendingPaymentSchedule> findByMerchantIdAndStatusAndCreditLoan(Long merchantId, String status, Boolean creditLoan);
	
	@Query(value="SELECT * FROM lending_payment_schedule WHERE merchant_id = :merchantId and status=:status and credit_loan=false",nativeQuery=true)
	LendingPaymentSchedule findByMerchantIdAndStatus(Long merchantId, String status);
	
	List<LendingPaymentSchedule> findByMerchantIdAndCreditLoanOrderByIdDesc(Long merchantId, Boolean creditLoan);
	@Query(value = "SELECT * FROM lending_payment_schedule WHERE merchant_id = :merchantId and credit_loan = :creditLoan ORDER BY start_date DESC", nativeQuery = true)
	List<LendingPaymentSchedule> findPreviousLoansByMerchantAndCreditLoan(Long merchantId,Boolean creditLoan);

	@Query(value = "select * from lending_payment_schedule WHERE merchant_id = :merchantId and application_id=:applicationId and credit_loan=false", nativeQuery = true)
	LendingPaymentSchedule findByMerchantIdAndApplicationId(Long merchantId, Long applicationId);

	@Query(value = "SELECT * FROM lending_payment_schedule WHERE merchant_id = :merchantId and status = 'ACTIVE' and credit_loan=false order by id limit 1", nativeQuery = true)
	LendingPaymentSchedule getOldestActiveLoan(Long merchantId);

	@Query(value="SELECT * FROM lending_payment_schedule WHERE merchant_id = :merchantId and credit_loan=true ORDER BY ID DESC LIMIT 1",nativeQuery=true)
	LendingPaymentSchedule findLatestCreditLoanByMerchantId(Long merchantId);
	
	List<LendingPaymentSchedule> findByMerchantIdAndCreditLoan(Long merchantId, Boolean creditLoan);
}