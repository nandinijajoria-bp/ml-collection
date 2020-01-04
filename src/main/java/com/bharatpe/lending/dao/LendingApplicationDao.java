package com.bharatpe.lending.dao;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.bharatpe.common.entities.LendingApplication;

@Repository
@Transactional
public interface LendingApplicationDao extends CrudRepository<LendingApplication , Long> {
	@Modifying
	@Query(value="update lending_application SET status = :newStatus WHERE id = :applicationId AND merchant_id = :merchantId AND status = :oldStatus",nativeQuery = true)
	int updateApplicationStatus(String newStatus, Long applicationId, Long merchantId, String oldStatus);

	@Modifying
	@Query(value="update lending_application SET status = :newStatus, agreement= 1, agreement_at = now() WHERE id = :applicationId AND merchant_id = :merchantId AND status in ( :oldStatus1, :oldStatus2) ",nativeQuery = true)
	int updateApplicationStatusAndAgreement(String newStatus, Long applicationId, Long merchantId, String oldStatus1, String oldStatus2);

	LendingApplication fetchApplicationByIdAndStatus(Long applicationId, Long merchantId);

	LendingApplication findTop1ByMerchantIdOrderByApplicationIdDesc(Long merchantId);

	LendingApplication findByApplicationId(Long applicationId);
	@Query(value="select * from lending_application where merchant_id = ?  and status not in ('closed','deleted') AND id not in (select application_id from loan_details) order by id desc LIMIT 1",nativeQuery = true)
	LendingApplication fetchLatestOpenApplication(Long applicationId);
	
	@Modifying
	@Query(value="UPDATE lending_application SET manual_kyc=:status where id=:applicationId",nativeQuery = true)
	void updateApplicationManualKyc(String status, Long applicationId);
	
	LendingApplication findByApplicationIdAndMerchantId(Long applicationId, Long merchantId);
	
	@Modifying
	@Query(value="UPDATE lending_application SET shop_number=:shopNumber, street_address=:streetAddress, area=:area, pincode=:pincode, city=:city, state=:state where id=:applicationId AND merchant_id=:merchantId",nativeQuery = true)
	int updateApplicationAddress(String shopNumber, String streetAddress, String area, Long pincode, String city, String state, Long applicationId, Long merchantId);
}