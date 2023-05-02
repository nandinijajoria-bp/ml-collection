package com.bharatpe.lending.dao;

import com.bharatpe.lending.entity.AutoPayUPIEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutoPayUPIDao extends JpaRepository<AutoPayUPIEntity, Long> {

    AutoPayUPIEntity findByMerchantIdAndApplicationId(Long merchantId, Long applicationId);

    AutoPayUPIEntity findByMerchantIdAndOrderId(Long merchantId, String orderId);

    AutoPayUPIEntity findByOrderId(String orderId);

    AutoPayUPIEntity findByApplicationId(Long applicationId);
}
