package com.bharatpe.lending.dao;

import com.bharatpe.lending.entity.LendingApplicationKycDetails;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

@Repository
public interface LendingApplicationKycDetailsDao extends JpaRepository<LendingApplicationKycDetails, Long> {
    LendingApplicationKycDetails findTop1ByApplicationIdOrderByIdDesc(Long applicationId);

    LendingApplicationKycDetails findTop1ByMerchantIdOrderByIdDesc(Long merchantId);

    @Query(value = "select * from lending_application_kyc_details where merchant_id=:merchantId and lender=:lender and pan_approved_at is not null and selfie_approved_at is not null and aadhar_approved_at is not null order by id desc limit 1", nativeQuery = true)
    LendingApplicationKycDetails findSuccessKycDetails(Long merchantId, String lender);
}
