package com.bharatpe.lending.dao;

import com.bharatpe.common.entities.BharatSwipeAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface BharatSwipeAccountDao extends JpaRepository<BharatSwipeAccount, Long> {

    @Query(nativeQuery = true, value = "select * from bharatswipe_account where merchant_id=:merchantId and status='ACTIVE' order by id desc limit 1")
    BharatSwipeAccount findByMerchantId(Long merchantId);
}
