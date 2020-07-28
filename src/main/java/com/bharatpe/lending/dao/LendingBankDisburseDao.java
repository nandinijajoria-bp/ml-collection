package com.bharatpe.lending.dao;

import com.bharatpe.common.entities.LendingBankDisburse;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LendingBankDisburseDao extends CrudRepository<LendingBankDisburse, Long>  {

	@Query(value = "select * from lending_bank_disburse where order_id=:applicationId", nativeQuery = true)
	public LendingBankDisburse findByLendingApplication(Long applicationId);
	
}
