package com.bharatpe.lending.dao;

import java.util.List;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.bharatpe.common.entities.Validate;

@Repository
@Transactional
public interface ValidateDao extends CrudRepository<Validate, Long> {
	@Modifying
	@Query(value="UPDATE validate SET settlement=:type WHERE mobile=:mobile", nativeQuery=true)
	int updateSettlement(String mobile, String type);
	
	List<Validate> findByMobile(String mobile);
}