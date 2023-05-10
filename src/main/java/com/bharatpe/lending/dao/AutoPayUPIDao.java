package com.bharatpe.lending.dao;

import com.bharatpe.lending.entity.AutoPayUPIEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface AutoPayUPIDao extends JpaRepository<AutoPayUPIEntity, Long> {

    Optional<AutoPayUPIEntity> findByMerchantIdAndApplicationId(Long merchantId, Long applicationId);

    AutoPayUPIEntity findByMerchantIdAndOrderId(Long merchantId, String orderId);

    AutoPayUPIEntity findByOrderId(String orderId);

    AutoPayUPIEntity findByApplicationId(Long applicationId);

    @Query(nativeQuery = true, value= "select * from autopay_upi where application_id =:applicationId and status IN ('PENDING','SUCCESS','INIT') order by id desc limit 1")
    AutoPayUPIEntity findTop1ByApplicationIdAndStatusOrderByIdDesc(long applicationId);


}
