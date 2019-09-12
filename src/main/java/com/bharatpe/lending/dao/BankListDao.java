package com.bharatpe.lending.dao;

import java.util.List;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import com.bharatpe.common.entities.BankList;

@Repository
public interface BankListDao extends CrudRepository<BankList, Long> {

	List<BankList> fetchByIfsc(String ifsc);
	
}
