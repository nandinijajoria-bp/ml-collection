package com.bharatpe.lending.dao;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import com.bharatpe.common.entities.LendingApplication;

@Repository
public interface LendingApplicationDao extends CrudRepository<LendingApplication , Long> {
	LendingApplication fetchApplicationByIdAndStatus(Long applicationId, Long merchantId);
}