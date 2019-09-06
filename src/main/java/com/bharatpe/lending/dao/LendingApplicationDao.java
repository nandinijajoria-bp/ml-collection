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
	LendingApplication fetchApplicationByIdAndStatus(Long applicationId, Long merchantId);
	LendingApplication findTop1ByMerchantIdOrderByApplicationIdDesc(Long merchantId);
}