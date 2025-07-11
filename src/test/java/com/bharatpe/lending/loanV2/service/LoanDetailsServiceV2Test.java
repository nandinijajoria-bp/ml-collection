package com.bharatpe.lending.loanV2.service;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.dao.LendingRiskVariablesSnapshotDao;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.entity.LendingRiskVariablesSnapshot;
import com.bharatpe.lending.common.service.FunnelService;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dto.UpdateMerchantReferencesRequestDto;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.loanV2.dto.ApiResponse;
import com.bharatpe.lending.loanV3.revamp.services.LoanDashboardService;
import com.bharatpe.lending.service.MerchantReferencesDataService;
import org.junit.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.Assert.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(SpringJUnit4ClassRunner.class)
@TestConfiguration
public class LoanDetailsServiceV2Test {
    @Mock
    LendingApplicationDetailsDao lendingApplicationDetailsDao;
    @Mock
    LendingApplicationDao lendingApplicationDao;

    @Mock
    private LendingRiskVariablesSnapshotDao lendingRiskVariablesSnapshotDao;


    @Mock
    private FunnelService funnelService;

    @Mock
    private LoanDashboardService loanDashboardService;

    @Mock
    private MerchantReferencesDataService merchantReferencesDataService;
    @InjectMocks
    LoanDetailsServiceV2 loanDetailsServiceV2;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        ReflectionTestUtils.setField(loanDetailsServiceV2, "referencePageDisabledForTopup", true);
    }

    @Test
    public void testUpdateDocSkipData() {
        BasicDetailsDto merchant = new BasicDetailsDto();
        merchant.setId(20000760L);

        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setId(1L);
        lendingApplication.setMerchantId(20000760L);

        LendingApplicationDetails lendingApplicationDetails = new LendingApplicationDetails();
        lendingApplicationDetails.setApplicationId(1L);
        lendingApplicationDetails.setIsDocSkip(false);  // Initial value

        Mockito.when(lendingApplicationDao.findBymerchantId(any(Long.class))).thenReturn(lendingApplication);
        Mockito.when(lendingApplicationDetailsDao.findByApplicationId(any(Long.class))).thenReturn(lendingApplicationDetails);

        ApiResponse<?> response = loanDetailsServiceV2.updateDocSkipData(merchant, true);
        System.out.println(response);
        assertTrue(response.success);
        assertEquals("Doc skipped data updated successfully", response.getMessage());
        assertEquals(true, lendingApplicationDetails.getIsDocSkip()); // Ensure docSkip is updated

        verify(lendingApplicationDao, times(1)).findBymerchantId(merchant.getId());
        verify(lendingApplicationDetailsDao, times(1)).findByApplicationId(lendingApplication.getId());
        verify(lendingApplicationDetailsDao, times(1)).save(lendingApplicationDetails);
    }

    @Test
    public void testUpdateDocSkipData_NullApplication() {
        BasicDetailsDto merchant = new BasicDetailsDto();
        merchant.setId(20000760L);

        Mockito.when(lendingApplicationDao.findBymerchantId(any(Long.class))).thenReturn(null);
        ApiResponse<?> response = loanDetailsServiceV2.updateDocSkipData(merchant, true);
        assertEquals("Application not found", response.getMessage());

        verify(lendingApplicationDao, times(1)).findBymerchantId(merchant.getId());
    }

    @Test
    public void testUpdateDocSkipData_NullApplicationDetails() {
        BasicDetailsDto merchant = new BasicDetailsDto();
        merchant.setId(20000760L);

        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setId(1L);
        lendingApplication.setMerchantId(20000760L);

        Mockito.when(lendingApplicationDao.findBymerchantId(any(Long.class))).thenReturn(lendingApplication);
        Mockito.when(lendingApplicationDetailsDao.findByApplicationId(any(Long.class))).thenReturn(null);
        ApiResponse<?> response = loanDetailsServiceV2.updateDocSkipData(merchant, true);
        assertEquals("Application details not found", response.getMessage());

        verify(lendingApplicationDao, times(1)).findBymerchantId(merchant.getId());
        verify(lendingApplicationDetailsDao, times(1)).findByApplicationId(lendingApplication.getId());
    }

    //Test for updateMErchantReferences()
    @Test
    public void testUpdateMerchantReferences_NoApplication() {
        BasicDetailsDto merchant = new BasicDetailsDto();
        merchant.setId(20000760L);

        UpdateMerchantReferencesRequestDto requestDto = new UpdateMerchantReferencesRequestDto();

        Mockito.when(lendingApplicationDao.findBymerchantId(merchant.getId())).thenReturn(null);

        ApiResponse<?> response = loanDetailsServiceV2.updateMerchantReferences(merchant, requestDto);

        assertFalse(response.success);
        assertEquals("No applicationId found for given merchantId", response.getMessage());
        verify(lendingApplicationDao, times(1)).findBymerchantId(merchant.getId());
    }

    @Test
    public void testUpdateMerchantReferences_NoLRVS() {
        BasicDetailsDto merchant = new BasicDetailsDto();
        merchant.setId(20000760L);

        UpdateMerchantReferencesRequestDto requestDto = new UpdateMerchantReferencesRequestDto();

        LendingApplication application = new LendingApplication();
        application.setId(1L);

        Mockito.when(lendingApplicationDao.findBymerchantId(merchant.getId())).thenReturn(application);
        Mockito.when(lendingRiskVariablesSnapshotDao.findByApplicationId(application.getId())).thenReturn(null);

        ApiResponse<?> response = loanDetailsServiceV2.updateMerchantReferences(merchant, requestDto);

        assertFalse(response.success);
        assertEquals("LRVS details not found for given merchantId", response.getMessage());
        verify(lendingApplicationDao, times(1)).findBymerchantId(merchant.getId());
        verify(lendingRiskVariablesSnapshotDao, times(1)).findByApplicationId(application.getId());
    }

    @Test
    public void testUpdateMerchantReferences_IneligibleNull() {
        BasicDetailsDto merchant = new BasicDetailsDto();
        merchant.setId(20000760L);

        UpdateMerchantReferencesRequestDto requestDto = new UpdateMerchantReferencesRequestDto();
        requestDto.setIneligible(null);

        LendingApplication application = new LendingApplication();
        application.setId(1L);

        LendingRiskVariablesSnapshot lrvs = new LendingRiskVariablesSnapshot();

        Mockito.when(lendingApplicationDao.findBymerchantId(merchant.getId())).thenReturn(application);
        Mockito.when(lendingRiskVariablesSnapshotDao.findByApplicationId(application.getId())).thenReturn(lrvs);

        ApiResponse<?> response = loanDetailsServiceV2.updateMerchantReferences(merchant, requestDto);

        assertFalse(response.success);
        assertEquals("ineligible field can not be null!", response.getMessage());
    }

    //Test cases for getMerchantReferences
    @Test
    public void testGetMerchantReferences_NoApplication() {
        BasicDetailsDto merchant = new BasicDetailsDto();
        merchant.setId(20000760L);

        Mockito.when(lendingApplicationDao.findBymerchantId(merchant.getId())).thenReturn(null);

        ApiResponse<?> response = loanDetailsServiceV2.getMerchantReferences(merchant);

        assertFalse(response.success);
        assertEquals("No applicationId found for given merchantId", response.getMessage());
        verify(lendingApplicationDao, times(1)).findBymerchantId(merchant.getId());
    }

    @Test
    public void testGetMerchantReferences_TopupWithDisabledReferences() {
        BasicDetailsDto merchant = new BasicDetailsDto();
        merchant.setId(20000760L);

        LendingApplication application = new LendingApplication();
        application.setId(1L);
        application.setLoanType(LoanType.TOPUP.name());

        Mockito.when(lendingApplicationDao.findBymerchantId(merchant.getId())).thenReturn(application);
        // Set referencePageDisabledForTopup to true via ReflectionTestUtils
        ReflectionTestUtils.setField(loanDetailsServiceV2, "referencePageDisabledForTopup", true);

        ApiResponse<?> response = loanDetailsServiceV2.getMerchantReferences(merchant);

        assertFalse(response.success);
        assertEquals("Reference page disabled for topup loan", response.getMessage());
    }

    @Test
    public void testGetMerchantReferences_NoLRVS() {
        BasicDetailsDto merchant = new BasicDetailsDto();
        merchant.setId(20000760L);

        LendingApplication application = new LendingApplication();
        application.setId(1L);
        application.setLoanType("NORMAL");

        Mockito.when(lendingApplicationDao.findBymerchantId(merchant.getId())).thenReturn(application);
        Mockito.when(lendingRiskVariablesSnapshotDao.findByApplicationId(application.getId())).thenReturn(null);

        ApiResponse<?> response = loanDetailsServiceV2.getMerchantReferences(merchant);

        assertFalse(response.success);
        assertEquals("LRVS details not found for given merchantId", response.getMessage());
        verify(lendingApplicationDao, times(1)).findBymerchantId(merchant.getId());
        verify(lendingRiskVariablesSnapshotDao, times(1)).findByApplicationId(application.getId());
    }
    @Test
    public void testGetMerchantReferences_Success() {
        BasicDetailsDto merchant = new BasicDetailsDto();
        merchant.setId(20000760L);

        LendingApplication application = new LendingApplication();
        application.setId(1L);
        application.setLoanType("NORMAL");

        LendingRiskVariablesSnapshot lrvs = new LendingRiskVariablesSnapshot();
        lrvs.setReferenceCount(5L);

        Mockito.when(lendingApplicationDao.findBymerchantId(merchant.getId())).thenReturn(application);
        Mockito.when(lendingRiskVariablesSnapshotDao.findByApplicationId(application.getId())).thenReturn(lrvs);

        ApiResponse<?> response = loanDetailsServiceV2.getMerchantReferences(merchant);

        assertTrue(response.success);
        assertEquals("Merchant references retrieved successfully", response.getMessage());
        verify(lendingApplicationDao, times(1)).findBymerchantId(merchant.getId());
        verify(lendingRiskVariablesSnapshotDao, times(1)).findByApplicationId(application.getId());
    }
    @Test
    public void testGetMerchantReferences_MaxReferenceLimit() {
        BasicDetailsDto merchant = new BasicDetailsDto();
        merchant.setId(20000760L);

        LendingApplication application = new LendingApplication();
        application.setId(1L);
        application.setLoanType("NORMAL");

        LendingRiskVariablesSnapshot lrvs = new LendingRiskVariablesSnapshot();
        lrvs.setReferenceCount(10L);

        Mockito.when(lendingApplicationDao.findBymerchantId(merchant.getId())).thenReturn(application);
        Mockito.when(lendingRiskVariablesSnapshotDao.findByApplicationId(application.getId())).thenReturn(lrvs);

        ApiResponse<?> response = loanDetailsServiceV2.getMerchantReferences(merchant);

        assertTrue(response.success);
        assertEquals("Merchant references retrieved successfully", response.getMessage());
        verify(lendingApplicationDao, times(1)).findBymerchantId(merchant.getId());
        verify(lendingRiskVariablesSnapshotDao, times(1)).findByApplicationId(application.getId());
    }
    @Test
    public void testGetMerchantReferences_ExceptionHandling() {
        BasicDetailsDto merchant = new BasicDetailsDto();
        merchant.setId(20000760L);

        Mockito.when(lendingApplicationDao.findBymerchantId(merchant.getId())).thenThrow(new RuntimeException("Database error"));

        ApiResponse<?> response = loanDetailsServiceV2.getMerchantReferences(merchant);

        assertFalse(response.success);
        assertEquals("Something Went Wrong while getting merchant references!", response.getMessage());
    }
}
