package com.bharatpe.lending.dao;

import com.bharatpe.lending.entity.LenderEligiblePincodes;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LenderEligiblePincodesDao extends JpaRepository<LenderEligiblePincodes, Long> {

    LenderEligiblePincodes findByLenderAndPincodeAndStatus(String lender, Integer pincode
            , LenderEligiblePincodes.LenderEligiblePincodesStatus status);

}