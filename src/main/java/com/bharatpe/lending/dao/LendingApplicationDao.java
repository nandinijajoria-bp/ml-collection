package com.bharatpe.lending.dao;

import com.bharatpe.common.entities.Merchant;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.bharatpe.common.entities.LendingApplication;

@Repository
@Transactional
public interface LendingApplicationDao extends CrudRepository<LendingApplication , Long> {

	LendingApplication findByIdAndMerchantAndStatus(Long id, Merchant merchant, String status);

	LendingApplication findByIdAndMerchant(Long id, Merchant merchant);

	LendingApplication findTop1ByMerchantOrderByIdDesc(Merchant merchant);

	LendingApplication findByApplicationId(Long applicationId);
	@Query(value="select * from lending_application where merchant_id = ?  and status not in ('closed','deleted') AND id not in (select application_id from loan_details) order by id desc LIMIT 1",nativeQuery = true)
	LendingApplication fetchLatestOpenApplication(Long applicationId);
	
	@Modifying
	@Query(value="UPDATE lending_application SET manual_kyc=:status where id=:applicationId",nativeQuery = true)
	void updateApplicationManualKyc(String status, Long applicationId);

}