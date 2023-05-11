package com.bharatpe.lending.dao;

import com.bharatpe.lending.entity.AutoPayUPI;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface AutoPayUPIDao extends JpaRepository<AutoPayUPI, Long> {

    Optional<AutoPayUPI> findByMerchantIdAndApplicationId(Long merchantId, Long applicationId);

    AutoPayUPI findByMerchantIdAndOrderId(Long merchantId, String orderId);

    AutoPayUPI findByOrderId(String orderId);

    AutoPayUPI findByApplicationId(Long applicationId);

    @Query(nativeQuery = true, value= "select * from autopay_upi where application_id =:applicationId and status IN ('PENDING','SUCCESS','INIT') order by id desc limit 1")
    AutoPayUPI findTop1ByApplicationIdAndStatusOrderByIdDesc(long applicationId);


}
