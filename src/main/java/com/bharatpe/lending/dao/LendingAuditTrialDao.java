package com.bharatpe.lending.dao;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import com.bharatpe.common.entities.LendingAuditTrial;

@Repository
public interface LendingAuditTrialDao extends CrudRepository<LendingAuditTrial , Long> {

}