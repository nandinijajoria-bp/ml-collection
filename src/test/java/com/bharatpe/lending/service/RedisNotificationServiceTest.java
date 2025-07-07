package com.bharatpe.lending.service;

import com.bharatpe.lending.common.service.merchant.dto.BankDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.dto.InstantNotificationDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.mockito.Mockito.*;

class RedisNotificationServiceTest {

    @InjectMocks
    private RedisNotificationService redisNotificationService;

    @Mock
    private MerchantService merchantService;

    @Mock
    private LendingDelayedMessagePublisher lendingDelayedMessagePublisher;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    void testSendRepaymentNudge() {
        // Arrange
        Long merchantId = 12345L;
        Double processingFee = 100.0;
        BankDetailsDto bankDetailsDto = new BankDetailsDto();
        bankDetailsDto.setBeneficiaryName("John Doe");
        when(merchantService.fetchMerchantBankDetails(merchantId)).thenReturn(Optional.of(bankDetailsDto));

        // Act
        redisNotificationService.sendRepaymentNudge(merchantId, processingFee);

        // Assert
        verify(merchantService, times(1)).fetchMerchantBankDetails(merchantId);
        try {
            verify(lendingDelayedMessagePublisher, times(1)).publish(
                    eq("lending_notify"),
                    eq(merchantId.toString()),
                    any(InstantNotificationDto.class), // Default name
                    eq("pf_nudge_" + merchantId),
                    eq(5 * 60L)
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testGetBankDetailsDtoWithRetry() {
        // Arrange
        Long merchantId = 12345L;
        int retryCount = 3;
        BankDetailsDto bankDetailsDto = new BankDetailsDto();
        bankDetailsDto.setBeneficiaryName("John Doe");
        when(merchantService.fetchMerchantBankDetails(merchantId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(bankDetailsDto));

        // Act
        BankDetailsDto result = redisNotificationService.getBankDetailsDtoWithRetry(merchantId, retryCount);

        // Assert
        verify(merchantService, times(2)).fetchMerchantBankDetails(merchantId);
        assert result != null;
        assert "John Doe".equals(result.getBeneficiaryName());
    }

    @Test
    void testSendRepaymentNudge_BankDetailsNotFound() {
        // Arrange
        Long merchantId = 12345L;
        Double processingFee = 100.0;
        when(merchantService.fetchMerchantBankDetails(merchantId)).thenReturn(Optional.empty());

        // Act
        redisNotificationService.sendRepaymentNudge(merchantId, processingFee);

        // Assert
        verify(merchantService, times(3)).fetchMerchantBankDetails(merchantId); // Retry 3 times
        try {
            verify(lendingDelayedMessagePublisher, never()).publish(anyString(), anyString(), any(), anyString(), anyInt());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testSendRepaymentNudge_EmptyBeneficiaryName() {
        // Arrange
        Long merchantId = 12345L;
        Double processingFee = 100.0;
        BankDetailsDto bankDetailsDto = new BankDetailsDto();
        bankDetailsDto.setBeneficiaryName(""); // Empty name
        when(merchantService.fetchMerchantBankDetails(merchantId)).thenReturn(Optional.of(bankDetailsDto));

        // Act
        redisNotificationService.sendRepaymentNudge(merchantId, processingFee);

        // Assert
        verify(merchantService, times(1)).fetchMerchantBankDetails(merchantId);
        try {
            verify(lendingDelayedMessagePublisher, times(1)).publish(
                    eq("lending_notify"),
                    eq(merchantId.toString()),
                    any(InstantNotificationDto.class), // Default name
                    eq("pf_nudge_" + merchantId),
                    eq(5 * 60L)
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testGetBankDetailsDtoWithRetry_AllAttemptsFail() {
        // Arrange
        Long merchantId = 12345L;
        int retryCount = 3;
        when(merchantService.fetchMerchantBankDetails(merchantId)).thenReturn(Optional.empty());

        // Act
        BankDetailsDto result = redisNotificationService.getBankDetailsDtoWithRetry(merchantId, retryCount);

        // Assert
        verify(merchantService, times(3)).fetchMerchantBankDetails(merchantId); // Retry 3 times
        assert result == null;
    }

    @Test
    void testGetBankDetailsDtoWithRetry_SuccessOnLastAttempt() {
        // Arrange
        Long merchantId = 12345L;
        int retryCount = 3;
        BankDetailsDto bankDetailsDto = new BankDetailsDto();
        bankDetailsDto.setBeneficiaryName("John Doe");
        when(merchantService.fetchMerchantBankDetails(merchantId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(bankDetailsDto)); // Success on 3rd attempt

        // Act
        BankDetailsDto result = redisNotificationService.getBankDetailsDtoWithRetry(merchantId, retryCount);

        // Assert
        verify(merchantService, times(3)).fetchMerchantBankDetails(merchantId);
        assert result != null;
        assert "John Doe".equals(result.getBeneficiaryName());
    }
}