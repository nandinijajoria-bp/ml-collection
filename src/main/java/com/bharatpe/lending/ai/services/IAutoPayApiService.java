package com.bharatpe.lending.ai.services;

import com.bharatpe.lending.entity.AutoPayUpi;

import java.util.Optional;

public interface IAutoPayApiService {
    Optional<AutoPayUpi> getAutoPayDetails(Long merchantId);
}
