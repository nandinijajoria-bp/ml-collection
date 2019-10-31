package com.bharatpe.lending.dao;

import org.springframework.data.repository.CrudRepository;

import com.bharatpe.common.entities.Agent;

public interface AgentDao extends CrudRepository <Agent, Long>{
	Agent fetchByReferalCode(String referalCode);
}
