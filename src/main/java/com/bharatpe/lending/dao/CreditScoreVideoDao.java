package com.bharatpe.lending.dao;

import com.bharatpe.lending.entity.CreditScoreVideo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CreditScoreVideoDao extends JpaRepository<CreditScoreVideo, Long> {

    Optional<CreditScoreVideo> findByMerchantIdAndIsValidTrue(String merchantId);

    Optional<CreditScoreVideo> findByMerchantId(String merchantId);
}