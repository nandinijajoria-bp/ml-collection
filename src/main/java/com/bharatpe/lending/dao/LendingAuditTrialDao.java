package com.bharatpe.lending.dao;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import com.bharatpe.common.entities.LendingAuditTrial;

@Repository
public interface LendingAuditTrialDao extends CrudRepository<LendingAuditTrial, Long> {

	public List<LendingAuditTrial> findByMerchantIdAndApplicationIdAndNewStatus(Long merchantId, Long applicationId, String newStatus);

	public List<LendingAuditTrial> findByApplicationIdAndMerchantIdAndType(Long applicationId, Long merchantId, String type);

	@Query(nativeQuery = true, value = "select COALESCE(old_status, '') from easy_loan.lending_audit_trial where application_id = :applicationId and merchant_id = :merchantId " +
			"and type = :type order by id desc limit 1")
	public String findByApplicationIdAndMerchantIdAndTypeOrderByIdDesc(Long applicationId, Long merchantId, String type);

	LendingAuditTrial findTopByApplicationIdAndType(Long applicationId, String type);


}