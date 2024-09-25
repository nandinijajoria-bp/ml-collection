package com.bharatpe.lending.dao;

import com.bharatpe.lending.entity.NachMandateRevokeRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NachMandateRevokeRequestDao extends JpaRepository<NachMandateRevokeRequest, Long> {
    List<NachMandateRevokeRequest> findByMerchantIdAndStatus(Long merchantId, String status);
}
