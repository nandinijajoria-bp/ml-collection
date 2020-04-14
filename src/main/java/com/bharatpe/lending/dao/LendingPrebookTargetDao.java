package com.bharatpe.lending.dao;

import com.bharatpe.lending.entity.LendingPrebookTarget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LendingPrebookTargetDao extends JpaRepository<LendingPrebookTarget, Long> {

    LendingPrebookTarget findByMerchantIdAndApplicationId(Long merchantId, Long applicationId);
}
