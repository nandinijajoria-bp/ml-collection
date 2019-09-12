package com.bharatpe.lending.dao;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.bharatpe.common.entities.LendingApplication;

@Repository
@Transactional
public interface LendingApplicationDao extends CrudRepository<LendingApplication , Long> {
	@Modifying
	@Query(value="update lending_application SET status = :newStatus WHERE id = :applicationId AND merchant_id = :merchantId AND status = :oldStatus",nativeQuery = true)
	int updateApplicationStatus(String newStatus, Long applicationId, Long merchantId, String oldStatus);
	@Modifying
	@Query(value="update lending_application SET status = :newStatus, agreement= 1, agreement_at = now() WHERE id = :applicationId AND merchant_id = :merchantId AND status in ( :oldStatus1, :oldStatus2) ",nativeQuery = true)
	int updateApplicationStatusAndAgreement(String newStatus, Long applicationId, Long merchantId, String oldStatus1, String oldStatus2);
	LendingApplication fetchApplicationByIdAndStatus(Long applicationId, Long merchantId);
	LendingApplication findTop1ByMerchantIdOrderByApplicationIdDesc(Long merchantId);
	LendingApplication findByApplicationId(Long applicationId);
}