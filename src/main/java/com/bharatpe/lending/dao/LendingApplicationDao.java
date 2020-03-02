package com.bharatpe.lending.dao;

import java.util.List;
import java.util.Optional;

import com.bharatpe.common.entities.Merchant;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingPaymentSchedule;

@Repository
@Transactional
public interface LendingApplicationDao extends CrudRepository<LendingApplication , Long> {
	
	LendingApplication findByIdAndMerchantAndStatus(Long id, Merchant merchant, String status);

	LendingApplication findByIdAndMerchant(Long id, Merchant merchant);

	LendingApplication findTop1ByMerchantOrderByIdDesc(Merchant merchant);

	List<LendingApplication> fetchLatestOpenApplication(Merchant merchant);

	Optional<LendingApplication> findFirstByMerchantAndStatus(Merchant merchant, String status);
	
	@Modifying
	@Query(value="UPDATE lending_application SET manual_kyc=:status where id=:applicationId",nativeQuery = true)
	void updateApplicationManualKyc(String status, Long applicationId);
	
	List<LendingPaymentSchedule> findByMerchant(Merchant merchant);
}