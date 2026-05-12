package com.bharatpe.lending.collection.core.utils;

import com.bharatpe.lending.collection.core.service.impl.LoanPaymentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

@RunWith(MockitoJUnitRunner.class)
class LoanPaymentUtilTest {

    @InjectMocks
    private LoanPaymentUtil loanPaymentUtil;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    void testLenderNotEligible() {
        loanPaymentUtil.extraPaymentNotEligibleLenders = Collections.singleton("SMFG");
        boolean result = loanPaymentUtil.checkExtraPaymentRolloutPercentage(12345L, "SMFG");
        assertFalse(result);
    }

    @Test
    void testLenderNotInEligible() {
        loanPaymentUtil.extraPaymentNotEligibleLenders = Collections.singleton("SMFG");
        loanPaymentUtil.extraPaymentRolloutPercent = -1;
        boolean result = loanPaymentUtil.checkExtraPaymentRolloutPercentage(12345L, "PAYU");
        assertTrue(result);
    }


    @Test
    void testLenderEligible() {
        loanPaymentUtil.extraPaymentNotEligibleLenders = null; // Simulate null set
        loanPaymentUtil.extraPaymentRolloutPercent = -1;
        boolean result = loanPaymentUtil.checkExtraPaymentRolloutPercentage(12345L, "PAYU");
        assertTrue(result);
    }

    @Test
    void testRolloutPercentageAllowsAll() {
        loanPaymentUtil.extraPaymentRolloutPercent = -1;
        boolean result = loanPaymentUtil.checkExtraPaymentRolloutPercentage(12345L, "ELIGIBLE_LENDER");
        assertTrue(result);
    }

}