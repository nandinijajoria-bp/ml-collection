package com.bharatpe.lending.loanV3.services.associationsV2.muthoot.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.dto.LoanInsuranceDTO;
import com.bharatpe.lending.enums.LoanType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
@Slf4j
public class MFInsuranceService {

    @Autowired
    private EasyLoanUtil easyLoanUtil;

    @Value("${muthoot.insurance.rollout.percent:0}")
    private Integer muthootInsuranceRolloutPercent;

    private static final List<String> topupLoans = Arrays.asList(LoanType.TOPUP.name(), LoanType.HALF_TOPUP.name(), LoanType.IO_TOPUP.name());

    private static final double TWELVE_MONTHS_PREMIUM_RATE = 0.01;
    private static final double TWELVE_TO_FIFTEEN_MONTHS_PREMIUM_RATE = 0.015;
    private static final double GST_RATE = 0.18;
    private static final String ACKO_INSURANCE_PROVIDER = "ACKO";
    private static final String MF_ACKO_IL = "MF_ACKO_IL";

    public LoanInsuranceDTO getInsurancePremiums(LendingApplication lendingApplication) {
        if (topupLoans.contains(lendingApplication.getLoanType()) || !easyLoanUtil.percentScaleUp(lendingApplication.getMerchantId(), muthootInsuranceRolloutPercent)) {
            log.info("{} insurance not enabled for merchantId {}", lendingApplication.getLender(), lendingApplication.getMerchantId());
            return LoanInsuranceDTO.builder().build();
        }
        log.info("Calculating insurance premiums for applicationId: {}", lendingApplication.getId());
        LoanInsuranceDTO insuranceDTO = LoanInsuranceDTO.builder()
                .insurances(Arrays.asList(
                        LoanInsuranceDTO.InsuranceDetails.builder()
                                .insurancePremium(calculatePremium(lendingApplication.getId(), lendingApplication.getLoanAmount(), lendingApplication.getTenureInMonths()))
                                .sumInsured(lendingApplication.getLoanAmount())
                                .policyTermsInMonths((lendingApplication.getTenureInMonths()))
                                .product(MF_ACKO_IL + "-" + lendingApplication.getTenureInMonths())
                                .provider(ACKO_INSURANCE_PROVIDER)
                                .build()
                ))
                .build();
        log.info("Returning insurance premiums: {}", insuranceDTO);
        return insuranceDTO;
    }

    private Double calculatePremium(Long applicationId, Double loanAmount, int tenureInMonths) {
        double premium = loanAmount * ((tenureInMonths <= 12) ? TWELVE_MONTHS_PREMIUM_RATE : TWELVE_TO_FIFTEEN_MONTHS_PREMIUM_RATE);
        double gst = premium * GST_RATE;
        log.info("Calculated premium: {}, GST: {}, Total: {} for applicationId: {}", premium, gst, premium + gst, applicationId);
        return Math.ceil(premium + gst);
    }
}
