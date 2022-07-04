package com.bharatpe.lending.service;

import com.bharatpe.lending.dto.LendingPancardResponseDTO;

public interface ILendingPancardService {

    LendingPancardResponseDTO findByMerchantId(Long merchantId);
}
