package com.bharatpe.lending.dao;

import com.bharatpe.lending.entity.LendingBlockedPancard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface LendingBlockedPancardDao extends JpaRepository<LendingBlockedPancard, String> {

    LendingBlockedPancard findByPancard(String pancard);

    @Query(nativeQuery = true, value = "select * from lending_blocked_pancard where pancard= :pancard or phone_number= :mobileNumber or merchant_id= :merchantId limit 1")
    LendingBlockedPancard getByPancardOrMerchanIdOrMobileNumber(String pancard, Long merchantId, String mobileNumber);
}
