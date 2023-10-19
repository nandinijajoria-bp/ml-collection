package com.bharatpe.lending.dao;

import com.bharatpe.lending.entity.MileStoneEntity;
import com.bharatpe.lending.entity.MileStoneReward;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MileStoneRewardDao  extends JpaRepository<MileStoneReward, String> {


    MileStoneReward findTop1ByMerchantId(Long merchantId);

}
