package com.bharatpe.lending.service;

import com.bharatpe.common.dao.LendingEDIScheduleDao;
import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.dao.LendingPrepaymentDao;
import com.bharatpe.lending.dto.ForeClosureDetailDTO;
import com.bharatpe.lending.dto.PaymentDetailsResponseDTO;
import com.bharatpe.lending.loanV2.service.ExcessNachService;
import com.bharatpe.lending.loanV3.factory.LenderAssociationStageFactory;
import com.bharatpe.lending.loanV3.services.stages.ForeClosureAmtStageSvcFactory;
import com.bharatpe.lending.util.LoanUtil;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class PaymentServiceTest {

    @Mock
    LendingPrepaymentDao lendingPrepaymentDao;
    @InjectMocks
    PaymentService paymentService;
    @Mock
    ExcessNachService excessNachService;
    @Mock
    LendingEDIScheduleDao lendingEDIScheduleDao;
    @Mock
    LoanUtil loanUtil;
    @Mock
    LenderAssociationStageFactory lenderAssociationStageFactory;
    @Mock
    ForeClosureAmtStageSvcFactory foreClosureAmtStageSvcFactory;

    LendingPaymentSchedule activeLoan;

    @Before
    public void setup() {
        activeLoan = createStandardActiveLoan();
    }
    @Test
    @DisplayName("Payment details without foreclosure details")
    public void testGetPaymentDetailsForActiveLoan_ForeClosureDetailsNotIncluded() {
        Boolean showForeClosureDetails = false;

        when(lendingPrepaymentDao.findByMerchantIdAndLoanId(anyLong(), anyLong())).thenReturn(null);
        when(excessNachService.getExcessCollectionBalanceAmount(anyLong(), anyLong())).thenReturn(0D);

        PaymentDetailsResponseDTO response = paymentService.getPaymentDetailsForActiveLoan(activeLoan, showForeClosureDetails);
        assertNotNull(response);
        assertNull(response.getData().getForeClosureAmount());
        assertNull(response.getData().getForeClosureDetail());
        assertEquals(Double.valueOf(5000), response.getData().getPaidPrinciple());
        assertEquals(Integer.valueOf(50), response.getData().getPenaltyFee());
        assertEquals(Double.valueOf(5100), response.getData().getPendingAmount());
    }

    @Test
    @DisplayName("Payment details with foreclosure amount details, charges applicable")
    public void testGetPaymentDetailsForActiveLoan_ForeclosureAmountIncludedWithDetail() {
        Boolean showForeClosureDetails = true;

        ForeClosureDetailDTO foreClosureDetailDTO = new ForeClosureDetailDTO();
        foreClosureDetailDTO.setId(1L);
        foreClosureDetailDTO.setGst(5D);
        foreClosureDetailDTO.setForeclosureCharges(100D);
        foreClosureDetailDTO.setPrincipalOutstanding(20D);

        when(lendingPrepaymentDao.findByMerchantIdAndLoanId(anyLong(), anyLong())).thenReturn(null);
        when(excessNachService.getExcessCollectionBalanceAmount(anyLong(), anyLong())).thenReturn(0D);
        when(lendingEDIScheduleDao.getByLoanIdAndEdiType(anyLong(), anyString())).thenReturn(null);
        when(loanUtil.getForeclosureAmount(any(), any())).thenReturn(1000);
        when(lenderAssociationStageFactory.getStageAssociatedLenderService(anyString())).thenReturn(foreClosureAmtStageSvcFactory);
        when(foreClosureAmtStageSvcFactory.getLenderAssociationService(anyString())).thenReturn(null);
        when(loanUtil.checkIfForeClosureChargesApplicable(any(), anyString())).thenReturn(true);
        when(loanUtil.calculateForeClosureCharges(any(), any())).thenReturn(foreClosureDetailDTO);

        PaymentDetailsResponseDTO response = paymentService.getPaymentDetailsForActiveLoan(activeLoan, showForeClosureDetails);
        assertNotNull(response);
        assertNotNull(response.getData().getForeClosureAmount());
        assertEquals(100D, response.getData().getForeClosureDetail().getForeclosureCharges());
        assertEquals(Double.valueOf(1000), response.getData().getForeClosureAmountAtBp());
        assertEquals(Integer.valueOf(1000), response.getData().getPrincipalDueAmount());
        assertEquals(Double.valueOf(125), response.getData().getForeClosureAmount());
    }

    @Test
    @DisplayName("Payment details with foreclosure details, charges not applicable")
    public void testGetPaymentDetailsForActiveLoan_ForeclosureAmountIncludedWithDetailNoCharges() {
        Boolean showForeClosureDetails = true;

        when(lendingPrepaymentDao.findByMerchantIdAndLoanId(anyLong(), anyLong())).thenReturn(null);
        when(excessNachService.getExcessCollectionBalanceAmount(anyLong(), anyLong())).thenReturn(0D);
        when(lendingEDIScheduleDao.getByLoanIdAndEdiType(anyLong(), anyString())).thenReturn(null);
        when(loanUtil.getForeclosureAmount(any(), any())).thenReturn(1000);
        when(lenderAssociationStageFactory.getStageAssociatedLenderService(anyString())).thenReturn(foreClosureAmtStageSvcFactory);
        when(foreClosureAmtStageSvcFactory.getLenderAssociationService(anyString())).thenReturn(null);
        when(loanUtil.checkIfForeClosureChargesApplicable(any(), anyString())).thenReturn(false);

        PaymentDetailsResponseDTO response = paymentService.getPaymentDetailsForActiveLoan(activeLoan, showForeClosureDetails);
        assertNotNull(response);
        assertNotNull(response.getData().getForeClosureAmount());
        assertEquals(Double.valueOf(1000), response.getData().getForeClosureAmountAtBp());
        assertEquals(Integer.valueOf(1000), response.getData().getPrincipalDueAmount());
        assertEquals(Double.valueOf(1000), response.getData().getForeClosureAmount());
    }

    private LendingPaymentSchedule createStandardActiveLoan() {
        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setId(1L);
        lendingApplication.setCreatedAt(new Date());
        lendingApplication.setMerchantId(20000760L);

        LendingPaymentSchedule activeLoan = new LendingPaymentSchedule();
        activeLoan.setMerchantId(20000760L);
        activeLoan.setId(11L);
        activeLoan.setApplicationId(1L);
        activeLoan.setLoanAmount(10000.0);
        activeLoan.setDueAmount(500.0);
        activeLoan.setDuePenalty(50.0);
        activeLoan.setEdiAmount(100.0);
        activeLoan.setEdiRemainingCount(5);
        activeLoan.setEdiCount(10);
        activeLoan.setPaidAmount(5000.0);
        activeLoan.setPaidPrinciple(5000.0);
        activeLoan.setDueInterest(100.0);
        activeLoan.setTentativeClosingDate(new Date());
        activeLoan.setNbfc("ABFL");
        activeLoan.setLoanApplication(lendingApplication);
        return activeLoan;
    }

}
