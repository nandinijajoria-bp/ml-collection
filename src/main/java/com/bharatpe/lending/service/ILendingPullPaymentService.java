package com.bharatpe.lending.service;

import com.bharatpe.lending.common.entity.LendingPullPayment;
import com.bharatpe.lending.dto.LendingPullPaymentResponseDTO;
import com.bharatpe.lending.dto.UpdateLendingPullPaymentDto;

import java.util.List;

public interface ILendingPullPaymentService {

    LendingPullPaymentResponseDTO getLendingPullPaymentByMerchantIdAndMode(Long merchantId, String mode);

    LendingPullPaymentResponseDTO getLendingPullPaymentByMerchantIdAndModeAndOwnerId(Long merchantId, String mode, Long ownerId);

    List<LendingPullPaymentResponseDTO> getLendingPullPaymentWithDueAmountGreaterThan(Long merchantId, String mode, Long ownerId, Double dueAmount);

    LendingPullPaymentResponseDTO updateLendingPullPayment(UpdateLendingPullPaymentDto updateLendingPullPaymentDto);

}
