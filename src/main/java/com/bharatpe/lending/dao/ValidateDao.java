package com.bharatpe.lending.dao;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import com.bharatpe.common.entities.Validate;

@Repository
public interface ValidateDao extends CrudRepository<Validate, Long> {
	Validate findByMerchantId(Long merchantId);
}