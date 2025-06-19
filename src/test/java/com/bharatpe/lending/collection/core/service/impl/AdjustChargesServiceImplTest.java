package com.bharatpe.lending.collection.core.service.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.dao.PenaltyFeeLedgerDao;
import com.bharatpe.lending.common.entity.PenaltyFeeLedger;
import com.bharatpe.lending.common.util.LenderConfigUtil;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.loanV3.services.associationsV2.creditsaison.impl.CreditSaisonPostChargeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;

import static com.bharatpe.lending.constant.LendingConfigKeys.CHARGES_APPORTIONMENT_ENABLED_LENDERS;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdjustChargesServiceImplTest {

    @Mock
    private PenaltyFeeLedgerDao penaltyFeeLedgerDao;

    @Mock
    private LendingApplicationDao lendingApplicationDao;

    @Mock
    private LenderConfigUtil lenderConfigUtil;

    @Mock
    private CreditSaisonPostChargeService creditSaisonPostChargeService;

    @InjectMocks
    private AdjustChargesServiceImpl adjustChargesServiceImpl;

    @Test
    void isChargesApportionmentEnabled_returnsTrue_whenAgreementDateAfterThreshold() {
        LendingPaymentSchedule activeLoan = mock(LendingPaymentSchedule.class);
        LendingApplication lendingApplication = mock(LendingApplication.class);

        when(activeLoan.getNbfc()).thenReturn("NBFC1");
        when(activeLoan.getApplicationId()).thenReturn(1L);
        when(activeLoan.getMerchantId()).thenReturn(2L);
        when(lenderConfigUtil.getLenderConfig("NBFC1", CHARGES_APPORTIONMENT_ENABLED_LENDERS))
                .thenReturn("2023-01-01 00:00:00");
        when(lendingApplicationDao.findByIdAndMerchantId(1L, 2L)).thenReturn(lendingApplication);
        when(lendingApplication.getAgreementAt()).thenReturn(new Date(2023 - 1900, Calendar.FEBRUARY, 1));

        boolean result = adjustChargesServiceImpl.isChargesApportionmentEnabled(activeLoan);

        assertTrue(result);
    }

    @Test
    void isChargesApportionmentEnabled_returnsFalse_whenAgreementDateBeforeThreshold() {
        LendingPaymentSchedule activeLoan = mock(LendingPaymentSchedule.class);
        LendingApplication lendingApplication = mock(LendingApplication.class);

        when(activeLoan.getNbfc()).thenReturn("NBFC1");
        when(activeLoan.getApplicationId()).thenReturn(1L);
        when(activeLoan.getMerchantId()).thenReturn(2L);
        when(lenderConfigUtil.getLenderConfig("NBFC1", CHARGES_APPORTIONMENT_ENABLED_LENDERS))
                .thenReturn("2023-01-01 00:00:00");
        when(lendingApplicationDao.findByIdAndMerchantId(1L, 2L)).thenReturn(lendingApplication);
        when(lendingApplication.getAgreementAt()).thenReturn(new Date(2022 - 1900, Calendar.DECEMBER, 31));

        boolean result = adjustChargesServiceImpl.isChargesApportionmentEnabled(activeLoan);

        assertFalse(result);
    }

    @Test
    void checkAndAdjustChargesApportionment_doesNotAdjust_whenPenaltyPaidIsZero() {
        LendingPaymentSchedule activeLoan = mock(LendingPaymentSchedule.class);

        adjustChargesServiceImpl.checkAndAdjustChargesApportionment(activeLoan, 0);

        verifyZeroInteractions(penaltyFeeLedgerDao);
    }

    @Test
    void adjustChargesApportionment_savesAdjustedLedgers_whenPenaltyPaidIsGreaterThanZero() {
        LendingPaymentSchedule activeLoan = mock(LendingPaymentSchedule.class);
        PenaltyFeeLedger ledger1 = mock(PenaltyFeeLedger.class);
        PenaltyFeeLedger ledger2 = mock(PenaltyFeeLedger.class);

        when(activeLoan.getId()).thenReturn(1L);
        when(penaltyFeeLedgerDao.findUnPaidPenaltyFeeLedgers(1L)).thenReturn(Arrays.asList(ledger1, ledger2));
        when(ledger1.getAmount()).thenReturn(100.0);
        when(ledger1.getPaidAmount()).thenReturn(50.0);
        when(ledger1.getDescription()).thenReturn("Nach Bounce");
        when(ledger2.getAmount()).thenReturn(200.0);
        when(ledger2.getPaidAmount()).thenReturn(0.0);
        when(ledger2.getDescription()).thenReturn("Penalty Fee");

        adjustChargesServiceImpl.adjustChargesApportionment(activeLoan, 150.0);

        verify(penaltyFeeLedgerDao).saveAll(anyList());
    }

    @Test
    void isChargesPostingRequiredCreditSaison_returnsFalse_whenLendingApplicationNotFound() {
        LendingPaymentSchedule activeLoan = mock(LendingPaymentSchedule.class);

        when(activeLoan.getApplicationId()).thenReturn(1L);
        when(activeLoan.getMerchantId()).thenReturn(2L);
        when(lendingApplicationDao.findByIdAndMerchantId(1L, 2L)).thenReturn(null);

        boolean result = adjustChargesServiceImpl.isChargesPostingRequiredCreditSaison(activeLoan);

        assertFalse(result);
    }
}
