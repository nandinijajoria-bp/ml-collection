package com.bharatpe.lending.lendingplatform.underwriting.service;

import com.bharatpe.lending.dto.GlobalLimitResponse;
import com.bharatpe.lending.lendingplatform.underwriting.dto.reresponse.FetchBureauConsentResponse;
import com.bharatpe.lending.lendingplatform.underwriting.dto.reresponse.UnderwritingApiResponse;
import com.bharatpe.lending.lendingplatform.underwriting.dto.reresponse.UpdateBureauConsentResponse;
import com.bharatpe.lending.loanV2.dto.BureauConsentDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class UnderwritingService {
    private final EligibilityService eligibilityService;
    private final BureauService bureauService;

    public GlobalLimitResponse getEligibility(String merchantId, String source, Boolean pincodeChanged, Boolean skipCache) {
        return eligibilityService.getEligibility(merchantId, source, pincodeChanged, skipCache);
    }

    public BureauConsentDTO.Data getBureauConsentData(String source, String mobile) {
        UnderwritingApiResponse<FetchBureauConsentResponse> fetchBureauConsentResponse = bureauService.fetchBureauConsent(source, mobile);
        return transformBureauConsentResponseToBureauConsentDtoData(fetchBureauConsentResponse.getData());
    }
    public BureauConsentDTO.Data updateBureauConsent(long merchantId, String source, String mobile, Boolean consentExpired, String bureauMobile) {
        UnderwritingApiResponse<UpdateBureauConsentResponse> updateBureauConsentResponse =
                bureauService.updatedBureauConsent(merchantId, source, mobile, consentExpired, bureauMobile);
        return transformUpdateBureauConsentResponseToBureauConsentDtoData(updateBureauConsentResponse.getData());
    }

    private BureauConsentDTO.Data transformBureauConsentResponseToBureauConsentDtoData(FetchBureauConsentResponse fetchBureauConsentResponse) {
        log.info("FetchBureauConsentResponse to transform: {}", fetchBureauConsentResponse);
        return BureauConsentDTO.Data.builder()
                .mobile(fetchBureauConsentResponse.getMobile())
                .consent_expired(fetchBureauConsentResponse.isConsentExpired())
                .consent_date(fetchBureauConsentResponse.getConsentDate())
                .created_at(fetchBureauConsentResponse.getCreatedAt())
                .source(fetchBureauConsentResponse.getSource())
                .mobile(fetchBureauConsentResponse.getMobile())
                .bureau_mobile(fetchBureauConsentResponse.getBureauMobile())
                .build();

    }

    private BureauConsentDTO.Data transformUpdateBureauConsentResponseToBureauConsentDtoData(UpdateBureauConsentResponse updateBureauConsentResponse) {
        log.info("UpdateBureauConsentResponse to transform: {}", updateBureauConsentResponse);
        return BureauConsentDTO.Data.builder()
                .mobile(updateBureauConsentResponse.getMobile())
                .consent_expired(updateBureauConsentResponse.isConsentExpired())
                .consent_date(updateBureauConsentResponse.getConsentDate())
                .created_at(updateBureauConsentResponse.getCreatedAt())
                .source(updateBureauConsentResponse.getSource())
                .mobile(updateBureauConsentResponse.getMobile())
                .bureau_mobile(updateBureauConsentResponse.getBureauMobile())
                .build();

    }
}
