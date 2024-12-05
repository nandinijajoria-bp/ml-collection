package com.bharatpe.lending.loanV2.service;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.loanV2.dto.ApiResponse;
import org.junit.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

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
    @InjectMocks
    LoanDetailsServiceV2 loanDetailsServiceV2;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
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
}
