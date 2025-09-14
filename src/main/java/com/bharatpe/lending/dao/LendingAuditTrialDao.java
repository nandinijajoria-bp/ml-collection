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

	LendingAuditTrial findByApplicationIdAndType(Long applicationId, String type);

	LendingAuditTrial findTopByApplicationIdAndType(Long applicationId, String type);

	LendingAuditTrial findTopByEvaluationIdAndType(String evaluationId, String type);

	@Query(nativeQuery = true, value = "select COALESCE(old_status, '') from easy_loan.lending_audit_trial where application_id = :applicationId and merchant_id = :merchantId " +
			"and type = :type order by id desc limit 1")
	public String findByApplicationIdAndMerchantIdAndTypeOrderByIdDesc(Long applicationId, Long merchantId, String type);


	@Query(nativeQuery = true, value =
			"WITH ordered_entries AS (" +
					"    SELECT *, " +
					"           ROW_NUMBER() OVER (ORDER BY created_at DESC) as row_num " +
					"    FROM easy_loan.lending_audit_trial " +
					"    WHERE merchant_id = :merchantId " +
					") " +
					"SELECT id, merchant_id, application_id, evaluation_id, old_status, new_status, type, " +
					"       created_at, created_by " +
					"FROM ordered_entries " +
					"WHERE evaluation_id = :evaluationId " +
					"AND (row_num <= (" +
					"    SELECT COALESCE(MIN(row_num), 999999) " +
					"    FROM ordered_entries " +
					"    WHERE evaluation_id != :evaluationId" +
					")) " +
					"ORDER BY created_at DESC")
	List<LendingAuditTrial> findByMerchantIdAndEvaluationId(Long merchantId, String evaluationId);

	List<LendingAuditTrial> findByMerchantIdAndEvaluationIdAndTypeOrderByIdDesc(Long merchantId, String evaluationId, String type);



}