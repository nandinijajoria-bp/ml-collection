package com.bharatpe.lending.dao;

import com.bharatpe.lending.entity.LendingApplicationKycDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LendingApplicationKycDetailsDao extends JpaRepository<LendingApplicationKycDetails, Long> {
    LendingApplicationKycDetails findTop1ByApplicationIdOrderByIdDesc(Long applicationId);

    LendingApplicationKycDetails findTop1ByMerchantIdOrderByIdDesc(Long merchantId);
}
