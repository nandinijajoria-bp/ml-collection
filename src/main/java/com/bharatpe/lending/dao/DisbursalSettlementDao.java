package com.bharatpe.lending.dao;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import com.bharatpe.common.entities.DisbursalSettlement;

@Repository
public interface DisbursalSettlementDao extends CrudRepository<DisbursalSettlement, Integer>{

}