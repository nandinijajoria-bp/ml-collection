package com.bharatpe.lending.loanV3.revamp.scopes;


import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingConsentDao;
import com.bharatpe.lending.common.dao.LendingLoanInsuranceDao;
import com.bharatpe.lending.common.entity.LendingConsent;
import com.bharatpe.lending.common.entity.LendingLoanInsurance;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dto.LoanInsuranceDTO;
import com.bharatpe.lending.util.LoanUtil;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class AgreementStageDataServiceTest {

    @InjectMocks
    AgreementStageDataService agreementStageDataService;
    @Mock
    LoanUtil loanUtil;
    @Mock
    LendingApplicationDao lendingApplicationDao;
    @Mock
    LendingConsentDao lendingConsentDao;
    LendingLoanInsurance validLoanInsurance1;
    LoanInsuranceDTO validLoanInsuranceDto1;
    LendingLoanInsurance validLoanInsurance2;
    LendingConsent lendingConsent;
    LendingApplication validLendingApplication;

    @Before
    public void setup() {
        validLoanInsurance1 = LendingLoanInsurance.builder()
                .insurancePremium(200D)
                .policyTermsInMonths(12)
                .product("CARE")
                .sumInsured(50000D)
                .build();

        validLoanInsuranceDto1 = LoanInsuranceDTO.builder()
                .insurancePremium(200D)
                .policyTermsInMonths(12)
                .product("CARE")
                .sumInsured(50000D)
                .build();

        validLoanInsurance2 = LendingLoanInsurance.builder()
                .insurancePremium(420D)
                .policyTermsInMonths(24)
                .product("CARE")
                .sumInsured(100000D)
                .build();

        lendingConsent = LendingConsent.builder()
                .consentType("INSURANCE")
                .applicationId(1234L)
                .merchantId(1111L)
                .isAccepted(true)
                .build();

        validLendingApplication = new LendingApplication();
        validLendingApplication.setId(1234L);
        validLendingApplication.setMerchantId(1111L);
        validLendingApplication.setLoanAmount(50000D);
        validLendingApplication.setTenureInMonths(9);
        validLendingApplication.setLender("PIRAMAL");
        validLendingApplication.setLoanType("REGULAR");
        validLendingApplication.setState("draft");
        validLendingApplication.setProcessingFee(1180D);
        validLendingApplication.setDisbursalAmount(48820D);
    }

    @Test
    public void isInsureBeforeTestForSuccess() {
        when(loanUtil.getInsuranceDetails(any(), eq("PIRAMAL"), eq("SELECTED")))
                .thenReturn(validLoanInsurance1);
        List<LoanInsuranceDTO> insurances = agreementStageDataService.isInsuredBefore(validLendingApplication);
        assertEquals(1, insurances.size());
    }

    @Test
    public void isInsureBeforeTestForDowngrade() {
        when(loanUtil.getInsuranceDetails(any(), eq("PIRAMAL"), eq("SELECTED")))
                .thenReturn(validLoanInsurance2);
        List<LoanInsuranceDTO> insurances = agreementStageDataService.isInsuredBefore(validLendingApplication);
        assertNull(insurances);
    }

    @Test
    public void isInsureBeforeTestForNotInsured() {
        when(loanUtil.getInsuranceDetails(any(), eq("PIRAMAL"), eq("SELECTED")))
                .thenReturn(null);
        List<LoanInsuranceDTO> insurances = agreementStageDataService.isInsuredBefore(validLendingApplication);
        assertNull(insurances);
    }

    @Test
    public void updateDisbursementAmountChangeWhenSelectingInsurance(){
        when(lendingApplicationDao.save(any())).thenReturn(validLendingApplication);

        validLoanInsuranceDto1.setIsSelected(true);
        agreementStageDataService.updateLoanDisbursalAmount(validLendingApplication, true, Collections.singletonList(validLoanInsuranceDto1));
        assertEquals(48620, validLendingApplication.getDisbursalAmount(), 0.000001);
    }

    @Test
    public void updateDisbursementAmountChangeWhenRemovingInsurance(){
        when(loanUtil.getInsuranceDetails(any(), eq("PIRAMAL"), eq("SELECTED")))
                .thenReturn(validLoanInsurance1);
        when(lendingApplicationDao.save(any())).thenReturn(validLendingApplication);
        validLendingApplication.setDisbursalAmount(48620D);

        validLoanInsuranceDto1.setIsSelected(false);
        agreementStageDataService.updateLoanDisbursalAmount(validLendingApplication, false, Collections.singletonList(validLoanInsuranceDto1));
        assertEquals(48820, validLendingApplication.getDisbursalAmount(), 0.000001);
    }

    @Test
    public void updateDisbursementAmountNoChange(){
        when(loanUtil.getInsuranceDetails(any(), eq("PIRAMAL"), eq("SELECTED")))
                .thenReturn(null);
        when(lendingApplicationDao.save(any())).thenReturn(validLendingApplication);

        validLoanInsuranceDto1.setIsSelected(false);
        agreementStageDataService.updateLoanDisbursalAmount(validLendingApplication, false, Collections.singletonList(validLoanInsuranceDto1));
        assertEquals(48820, validLendingApplication.getDisbursalAmount(), 0.000001);
    }
}
