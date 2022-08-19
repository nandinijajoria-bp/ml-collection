package com.bharatpe.lending.dao;

import com.bharatpe.lending.common.query.dao.LendingPaymentScheduleDaoSlave;
import com.bharatpe.lending.common.query.entity.LendingPaymentScheduleSlave;
import org.springframework.stereotype.Repository;

import com.bharatpe.common.entities.LendingPaymentSchedule;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

@Repository
public interface LendingPaymentScheduleDao extends CrudRepository<LendingPaymentSchedule, Long> {
	@Query(value="SELECT * FROM lending_payment_schedule WHERE merchant_id = :merchantId and credit_loan =false ORDER BY ID DESC LIMIT 1",nativeQuery=true)
	LendingPaymentSchedule findLatestLendingPaymentScheduleByMerchantId(Long merchantId);
	
	LendingPaymentSchedule	findTop1ByMerchantIdAndStatusAndCreditLoanOrderByIdDesc(Long merchantId, String status,Boolean creditLoan);
		
	List<LendingPaymentSchedule> findByMerchantIdAndStatusAndCreditLoan(Long merchantId, String status, Boolean creditLoan);
	
	@Query(value="SELECT * FROM lending_payment_schedule WHERE merchant_id = :merchantId and status=:status and credit_loan=false order by id limit 1",nativeQuery=true)
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

	@Query(value="SELECT * FROM lending_payment_schedule WHERE merchant_id = :merchantId and merchant_store_id = :merchantStoreId and status=:status and credit_loan=false order by id", nativeQuery=true)
	List<LendingPaymentSchedule> findByMerchantIdAndMerchantStoreIdAndStatus(Long merchantId, Long merchantStoreId, String status);

	@Query(value="SELECT * FROM lending_payment_schedule WHERE merchant_id = :merchantId and status=:status and credit_loan=false order by id", nativeQuery=true)
	List<LendingPaymentSchedule> findByMerchantIdAndStatusList(Long merchantId, String status);

	LendingPaymentSchedule findByTlDetailsIdAndCreditLoanAndStatus(Long tlDetailsId, Boolean creditLoan, String status);

	LendingPaymentSchedule findByIdAndMerchantId(Long id, Long merchantId);

	@Query(value = "SELECT count(1) FROM lending_payment_schedule WHERE merchant_id = :merchantId  and credit_loan=false", nativeQuery = true)
	Integer getRepeatLoan(Long merchantId);

	@Query(value="SELECT lps.* FROM lending_payment_schedule lps join lending_ledger ll on ll.loan_id=lps.id and ll.adjustment_mode in ('TOPUP','HALF_TOPUP','IO_TOPUP') and ll.amount>0 WHERE lps.merchant_id = :merchantId and lps.credit_loan=false order by id desc limit 1", nativeQuery=true)
	LendingPaymentSchedule findTopupLoan(Long merchantId);

	@Query(nativeQuery = true, value = "select * from lending_payment_schedule where merchant_id =:merchantId and status=:status order by id desc")
	List<LendingPaymentSchedule> getLoansByMerchantIdAndStatus(Long merchantId, String status);

	@Query(nativeQuery = true, value = "select * from lending_payment_schedule where merchant_id =:merchantId and status='CLOSED' order by id desc limit 1")
	Optional<LendingPaymentSchedule> findLatestClosedLoan(Long merchantId);

	LendingPaymentSchedule findTop1ByMerchantIdOrderByIdDesc(Long merchantId);

	LendingPaymentSchedule findByApplicationId(Long applicationId);

	LendingPaymentSchedule findByApplicationIdAndCreditLoan(Long applicationId, Boolean creditLoan);
}
