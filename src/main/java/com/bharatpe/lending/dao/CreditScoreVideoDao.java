package com.bharatpe.lending.dao;

import com.bharatpe.lending.entity.CreditScoreVideo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface CreditScoreVideoDao extends JpaRepository<CreditScoreVideo, Long> {

    Optional<CreditScoreVideo> findByMerchantId(String merchantId);

    Optional<CreditScoreVideo> findByOrderId(String orderId);

    @Query("SELECT csv FROM CreditScoreVideo csv WHERE csv.merchantId = :merchantId " +
           "AND csv.status = 'SUCCESS' AND csv.updatedAt >= :thirtyDaysAgo " +
           "ORDER BY csv.updatedAt DESC")
    Optional<CreditScoreVideo> findValidByMerchantId(@Param("merchantId") String merchantId,
                                                     @Param("thirtyDaysAgo") LocalDateTime thirtyDaysAgo);

    @Query("SELECT csv FROM CreditScoreVideo csv WHERE csv.merchantId = :merchantId " +
           "AND csv.status = 'SUCCESS' ORDER BY csv.updatedAt DESC")
    Optional<CreditScoreVideo> findLatestSuccessByMerchantId(@Param("merchantId") String merchantId);
}