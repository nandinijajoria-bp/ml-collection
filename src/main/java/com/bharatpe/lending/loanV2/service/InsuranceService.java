package com.bharatpe.lending.loanV2.service;


import com.bharatpe.lending.common.query.dao.LendingApplicationDaoSlave;
import com.bharatpe.lending.common.query.entity.LendingApplicationSlave;
import com.bharatpe.lending.common.query.entity.LendingPaymentScheduleSlave;
import com.bharatpe.lending.dto.InsuranceEligibilityRequestDTO;
import com.bharatpe.lending.dto.InsuranceEligibilityResponseDTO;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import com.bharatpe.lending.service.APIGatewayService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Map;

@Service
@Slf4j
public class InsuranceService {

    @Autowired
    APIGatewayService apiGatewayService;

    @Autowired
    LendingApplicationDaoSlave lendingApplicationDaoSlave;

    @Autowired
    KycUtils kycUtils;

    public InsuranceEligibilityResponseDTO.InsuranceEligibilityData checkInsuranceEligibility(LendingPaymentScheduleSlave activeLoan) {
        log.info("check insurance eligibility for merchant {}", activeLoan.getMerchantId());
        LendingApplicationSlave lendingApplicationSlave = lendingApplicationDaoSlave.findByIdAndMerchantId(activeLoan.getApplicationId(), activeLoan.getMerchantId());
        if(!ObjectUtils.isEmpty(lendingApplicationSlave)) {
            Map<String, String> businessCategories = kycUtils.getBusinessCategoryAndSubCategoryByApplicationId(activeLoan.getApplicationId());
            InsuranceEligibilityRequestDTO insuranceEligibilityRequest = InsuranceEligibilityRequestDTO.builder()
                    .customerId(lendingApplicationSlave.getMerchantId())
                    .amount(lendingApplicationSlave.getLoanAmount())
                    .tenure(lendingApplicationSlave.getTenureInMonths())
                    .pinCode(lendingApplicationSlave.getPincode())
                    .businessCategory(businessCategories.getOrDefault("businessCategory", null))
                    .businessSubCategory(businessCategories.getOrDefault("businessSubcategory", null))
                    .build();
            return apiGatewayService.fetchInsuranceEligibility(insuranceEligibilityRequest);
        }
        return null;
    }
}
