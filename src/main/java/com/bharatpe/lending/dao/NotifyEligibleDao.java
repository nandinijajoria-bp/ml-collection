package com.bharatpe.lending.dao;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import com.bharatpe.common.entities.NotifyEligible;

@Repository
public interface NotifyEligibleDao extends  CrudRepository<NotifyEligible , Long> {

}