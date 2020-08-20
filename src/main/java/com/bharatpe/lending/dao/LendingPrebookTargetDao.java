package com.bharatpe.lending.dao;

import com.bharatpe.lending.entity.LendingPrebookTarget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Repository
public interface LendingPrebookTargetDao extends JpaRepository<LendingPrebookTarget, Long> {

    LendingPrebookTarget findByMerchantIdAndApplicationId(Long merchantId, Long applicationId);

    @Modifying
    @Transactional
    @Query(value = "UPDATE LendingPrebookTarget l set l.targetAchieveDate=:achievedDate,l.targetAchieved=true where l.merchantId=:merchantId")
    int updateTargetAchieved(Long merchantId, Date achievedDate);
}
