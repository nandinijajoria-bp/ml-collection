package com.bharatpe.lending.dao;

import com.bharatpe.common.entities.TokenVerification;

import java.util.List;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TokenVerificationDao extends CrudRepository<TokenVerification , Long> {
	List<TokenVerification> fetchTokenDetails(String token);
}