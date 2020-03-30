package com.bharatpe.lending.dao;

import org.springframework.stereotype.Repository;

import com.bharatpe.common.entities.LendingPaymentSchedule;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

@Repository
public interface LendingPaymentScheduleDao extends CrudRepository<LendingPaymentSchedule, Long> {
	@Query(value="SELECT * FROM lending_payment_schedule WHERE merchant_id = :merchantId ORDER BY ID DESC LIMIT 1",nativeQuery=true)
	LendingPaymentSchedule findLatestLendingPaymentScheduleByMerchantId(Long merchantId);
	LendingPaymentSchedule findByMerchantIdAndStatus(Long merchantId, String status);
	List<LendingPaymentSchedule> findByMerchantIdOrderByIdDesc(Long merchantId);
	@Query(value = "SELECT * FROM lending_payment_schedule WHERE merchant_id = :merchantId ORDER BY start_date DESC", nativeQuery = true)
	List<LendingPaymentSchedule> findPreviousLoansByMerchant(Long merchantId);

	@Query(value = "select * from lending_payment_schedule WHERE merchant_id = :merchantId and application_id=:applicationId", nativeQuery = true)
	LendingPaymentSchedule findByMerchantIdAndApplicationId(Long merchantId, Long applicationId);
}