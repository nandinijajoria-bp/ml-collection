package com.bharatpe.lending.service;

import com.bharatpe.lending.common.dao.LendingLoanInsuranceDao;
import com.bharatpe.lending.common.entity.LendingConsent;
import com.bharatpe.lending.common.entity.LendingLoanInsurance;
import com.bharatpe.lending.common.query.entity.LendingApplicationSlave;
import com.bharatpe.lending.constant.LoanInsuranceConstants;
import com.bharatpe.lending.dto.InsuranceDetailsDTO;
import com.bharatpe.lending.loanV2.service.LendingApplicationServiceV2;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import static org.mockito.Mockito.*;

import java.util.Date;

@RunWith(MockitoJUnitRunner.class)
public class SupportServiceTest {

    @InjectMocks
    SupportService supportService;
    @Spy
    LoanInsuranceConstants loanInsuranceConstants;
    @Mock
    LendingLoanInsuranceDao lendingLoanInsuranceDao;

    @Mock
    LendingApplicationServiceV2 lendingApplicationServiceV2;

    LendingLoanInsurance validLoanInsurance1;
    InsuranceDetailsDTO insuranceDetailsDTO;
    LendingConsent lendingConsent;
    LendingApplicationSlave validLendingApplication;

    @Before
    public void setup() {
        validLoanInsurance1 = LendingLoanInsurance.builder()
                .applicationId(1234L)
                .lender("PIRAMAL")
                .status("SELECTED")
                .insurancePremium(200D)
                .policyTermsInMonths(12)
                .provider("CARE")
                .sumInsured(50000D)
                .policyDocUrl("docURL")
                .commencementDate(new Date())
                .build();

        lendingConsent = LendingConsent.builder()
                .consentType("INSURANCE")
                .applicationId(1234L)
                .merchantId(1111L)
                .isAccepted(true)
                .build();

        validLendingApplication = new LendingApplicationSlave();
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
    @DisplayName("Find Insurance Details for Valid Insurance")
    public void findInsuranceForApplicationTestForValidInsurance() {
        when(lendingLoanInsuranceDao.findByApplicationIdAndLenderAndStatus(eq(1234L), eq("PIRAMAL"), eq("SELECTED")))
                .thenReturn(validLoanInsurance1);
        when(lendingApplicationServiceV2.fetchLoanInsuranceDoc(eq(1234L), anyString())).thenReturn("docURL");

        insuranceDetailsDTO = supportService.findInsuranceForApplication(validLendingApplication);

        Assert.assertTrue(insuranceDetailsDTO.isInsuranceApplicable());
        Assert.assertEquals(validLendingApplication.getLoanAmount(), insuranceDetailsDTO.getSumInsured());
        Assert.assertEquals("docURL", insuranceDetailsDTO.getInsuranceDocument());
        Assert.assertEquals(validLoanInsurance1.getCommencementDate(), insuranceDetailsDTO.getInsuranceAvailedDate());
        Assert.assertEquals(validLoanInsurance1.getProvider(), insuranceDetailsDTO.getInsuranceProviderName());
        Assert.assertEquals("https://drive.google.com/file/d/1-jSdiwUACM4tmzORXjt2VW-IF2hP370K/view?usp=sharing",
                insuranceDetailsDTO.getBenefitsOfTheInsurance());
    }

    @Test
    @DisplayName("Find Insurance Details for No Insurance")
    public void findInsuranceForApplicationTestForNoInsurance() {
        when(lendingLoanInsuranceDao.findByApplicationIdAndLenderAndStatus(eq(1234L), eq("PIRAMAL"), eq("SELECTED")))
                .thenReturn(null);

        insuranceDetailsDTO = supportService.findInsuranceForApplication(validLendingApplication);

        Assert.assertNull(insuranceDetailsDTO);
    }
}
