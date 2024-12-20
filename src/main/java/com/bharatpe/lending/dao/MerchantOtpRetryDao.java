package com.bharatpe.lending.dao;

import com.bharatpe.lending.entity.AutoPayUPI;
import com.bharatpe.lending.entity.MerchantOtpRetry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MerchantOtpRetryDao extends JpaRepository<MerchantOtpRetry, Long> {
    Optional<MerchantOtpRetry> findById(String orderId);

    MerchantOtpRetry findTop1ByUserIdOrderByIdDesc(Long userId);
}
