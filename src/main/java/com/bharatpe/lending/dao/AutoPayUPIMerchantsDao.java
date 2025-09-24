package com.bharatpe.lending.dao;

import com.bharatpe.lending.entity.AutoPayUPIMerchants;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutoPayUPIMerchantsDao extends JpaRepository<AutoPayUPIMerchants, Long> {

    boolean existsByMerchantId(Long merchantId);
}
