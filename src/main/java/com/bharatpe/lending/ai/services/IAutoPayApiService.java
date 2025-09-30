package com.bharatpe.lending.ai.services;

import com.bharatpe.lending.common.entity.AutoPayUPI;

import java.util.Optional;

public interface IAutoPayApiService {
    Optional<AutoPayUPI> getAutoPayDetails(Long merchantId);
}
