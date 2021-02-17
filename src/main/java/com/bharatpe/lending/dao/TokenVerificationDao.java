package com.bharatpe.lending.dao;

import com.bharatpe.common.entities.TokenVerification;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TokenVerificationDao extends CrudRepository<TokenVerification , Long> {
	List<TokenVerification> fetchTokenDetails(String token);

	@Query(nativeQuery = true, value = "select * from verify where wipeout=0 and merchant_id=:merchantId order by id desc limit 1")
	TokenVerification findByMerchantId(Long merchantId);
}