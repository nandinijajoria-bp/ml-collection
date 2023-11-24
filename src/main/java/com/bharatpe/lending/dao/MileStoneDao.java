package com.bharatpe.lending.dao;

import com.bharatpe.lending.entity.MileStoneEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MileStoneDao extends JpaRepository<MileStoneEntity, String> {

    MileStoneEntity findTop1ByMerchantId(Long merchantId);

    MileStoneEntity findTop1ByMerchantIdOrderByIdDesc(Long merchantId);
    MileStoneEntity findBySessionStatus(String status);

    MileStoneEntity findByMerchantIdAndSessionStatus(Long merchantId,String status);

    MileStoneEntity findTop1ByMerchantIdAndSessionStatus(Long merchantId,String status);

}
