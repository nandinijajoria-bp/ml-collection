package com.bharatpe.lending.dao;

import com.bharatpe.lending.entity.LendingApplicationKycDetails;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

@Repository
public interface LendingApplicationKycDetailsDao extends JpaRepository<LendingApplicationKycDetails, Long> {
    LendingApplicationKycDetails findTop1ByApplicationIdOrderByIdDesc(Long applicationId);

    LendingApplicationKycDetails findTop1ByMerchantIdOrderByIdDesc(Long merchantId);

    @Query(value = "select lakd.* from lending_application la join lending_application_kyc_details lakd on la.merchant_id = lakd.merchant_id where\n" +
            "la.status in ('approved','Approved','APPROVED')\n" +
            "and la.id = lakd.application_id\n" +
            "and la.disburse_timestamp is not null\n" +
            "and la.loan_disbursal_status = 'DISBURSED'\n" +
            "and lakd.selfie_approved_at is not null\n" +
            "and lakd.aadhar_approved_at is not null\n" +
            "and lakd.pan_approved_at is not null\n" +
            "and la.merchant_id = :merchantId\n" +
            "and lakd.lender = :lender\n" +
            "and datediff(curdate(), date(lakd.consent_date))<731\n" +
            "order by lakd.id desc limit 1", nativeQuery = true)
    LendingApplicationKycDetails findSuccessKycDetails(Long merchantId, String lender);
}
