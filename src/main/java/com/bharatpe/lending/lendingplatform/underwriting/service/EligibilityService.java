package com.bharatpe.lending.lendingplatform.underwriting.service;

import com.bharatpe.lending.dto.GlobalLimitResponse;
import com.bharatpe.lending.lendingplatform.underwriting.client.EligibilityClient;
import com.bharatpe.lending.lendingplatform.underwriting.dto.request.EligibilityRequest;
import com.bharatpe.lending.lendingplatform.underwriting.dto.request.UnderwritingBaseRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EligibilityService {
    private final EligibilityClient eligibilityClient;

    public GlobalLimitResponse getEligibility(String merchantId, String source, Boolean pincodeChanged, Boolean skipCache) {
        UnderwritingBaseRequest<EligibilityRequest> eligibilityRequest = getEligibilityRequest(merchantId, source, pincodeChanged, skipCache);
        return eligibilityClient.getEligibility(eligibilityRequest);
    }

    private UnderwritingBaseRequest<EligibilityRequest> getEligibilityRequest(String merchantId, String source, Boolean pincodeChanged, Boolean skipCache) {
        EligibilityRequest eligibilityRequest = EligibilityRequest.builder()
                .pincodeChanged(pincodeChanged)
                .skipCache(skipCache)
                .build();
        return UnderwritingBaseRequest.<EligibilityRequest>builder()
                .customerId(merchantId)
                .clientIdentifier(source)
                .data(eligibilityRequest)
                .build();
    }
}
