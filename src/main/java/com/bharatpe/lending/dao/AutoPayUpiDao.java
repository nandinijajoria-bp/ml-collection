package com.bharatpe.lending.dao;

import com.bharatpe.lending.entity.AutoPayUpi;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AutoPayUpiDao extends CrudRepository<AutoPayUpi, Long> {

    // Find all AutoPayUpi records by merchant ID
    List<AutoPayUpi> findAllByMerchantId(Long merchantId);

    // Find AutoPayUpi by merchant ID and status
    List<AutoPayUpi> findByMerchantIdAndStatus(Long merchantId, String status);

    // Find the latest AutoPayUpi record by merchant ID
    Optional<AutoPayUpi> findTop1ByMerchantIdOrderByIdDesc(Long merchantId);

    // Find AutoPayUpi by merchant ID and mandate ID
    Optional<AutoPayUpi> findByMerchantIdAndMandateId(Long merchantId, String mandateId);

    // Find AutoPayUpi by merchant ID and order ID
    Optional<AutoPayUpi> findByMerchantIdAndOrderId(Long merchantId, String orderId);

    // Find AutoPayUpi by merchant ID and application ID
    List<AutoPayUpi> findByMerchantIdAndApplicationId(Long merchantId, Long applicationId);

    // Find active AutoPayUpi records by merchant ID (excluding failed/cancelled status)
    @Query("SELECT apu FROM AutoPayUpi apu WHERE apu.merchantId = :merchantId AND apu.status NOT IN ('FAILED', 'CANCELLED', 'EXPIRED')")
    List<AutoPayUpi> findActiveByMerchantId(Long merchantId);

    // Find AutoPayUpi by merchant ID and lender
    List<AutoPayUpi> findByMerchantIdAndLender(Long merchantId, String lender);

    // Find AutoPayUpi by merchant ID and gateway
    List<AutoPayUpi> findByMerchantIdAndGateway(Long merchantId, String gateway);

    // Custom query to find records by merchant ID with specific status and order by created date
    @Query(value = "SELECT * FROM autopay_upi WHERE merchant_id = :merchantId AND status = :status ORDER BY created_at DESC LIMIT :limit", nativeQuery = true)
    List<AutoPayUpi> findByMerchantIdAndStatusOrderByCreatedAtDesc(Long merchantId, String status, int limit);

    // Find standalone autopay setup records by merchant ID
    List<AutoPayUpi> findByMerchantIdAndIsStandaloneAutopaySetup(Long merchantId, Boolean isStandaloneAutopaySetup);
}
