package com.bharatpe.lending.lendingplatform.underwriting.service;

import com.bharatpe.lending.lendingplatform.underwriting.client.BureauClient;
import com.bharatpe.lending.lendingplatform.underwriting.dto.request.UnderwritingBaseRequest;
import com.bharatpe.lending.lendingplatform.underwriting.dto.request.UpdateBureauConsentRequest;
import com.bharatpe.lending.lendingplatform.underwriting.dto.reresponse.FetchBureauConsentResponse;
import com.bharatpe.lending.lendingplatform.underwriting.dto.reresponse.UnderwritingApiResponse;
import com.bharatpe.lending.lendingplatform.underwriting.dto.reresponse.UpdateBureauConsentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class BureauService {
    private final BureauClient bureauClient;

    public UnderwritingApiResponse<FetchBureauConsentResponse> fetchBureauConsent(String source, String mobile) {
        return bureauClient.getBureauConsent(source, mobile);
    }

    public UnderwritingApiResponse<UpdateBureauConsentResponse> updatedBureauConsent(long merchantId, String source,
                                                                                     String mobile, Boolean consentExpired,
                                                                                     String bureauMobile) {
        UnderwritingBaseRequest<UpdateBureauConsentRequest> updateBureauConsentRequest = getUpdatedBureauConsentRequest(
                merchantId, source, mobile, consentExpired, bureauMobile);
        return bureauClient.updateBureauConsent(updateBureauConsentRequest);
    }

    private UnderwritingBaseRequest<UpdateBureauConsentRequest> getUpdatedBureauConsentRequest(long merchantId,
                                                                                               String source,
                                                                                               String mobile,
                                                                                               Boolean consentExpired,
                                                                                               String bureauMobile) {
        UpdateBureauConsentRequest updateBureauConsentRequest = UpdateBureauConsentRequest.builder()
                .consentExpired(consentExpired)
                .bureauMobile(bureauMobile)
                .build();
        return UnderwritingBaseRequest.<UpdateBureauConsentRequest>builder()
                .customerId(String.valueOf(merchantId))
                .customerMobile(mobile)
                .clientIdentifier(source)
                .data(updateBureauConsentRequest)
                .build();
    }
}
