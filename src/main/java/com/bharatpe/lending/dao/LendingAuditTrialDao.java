package com.bharatpe.lending.dao;

import java.util.List;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import com.bharatpe.common.entities.LendingAuditTrial;

@Repository
public interface LendingAuditTrialDao extends CrudRepository<LendingAuditTrial, Long> {

	public List<LendingAuditTrial> findByMerchantIdAndApplicationIdAndNewStatus(Long merchantId, Long applicationId, String newStatus);

	public List<LendingAuditTrial> findByApplicationIdAndMerchantIdAndType(Long applicationId, Long merchantId, String type);

}