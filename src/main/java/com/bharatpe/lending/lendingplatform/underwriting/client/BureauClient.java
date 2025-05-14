package com.bharatpe.lending.lendingplatform.underwriting.client;

import com.bharatpe.lending.lendingplatform.config.LendingPlatformConfiguration;
import com.bharatpe.lending.lendingplatform.underwriting.dto.request.UnderwritingBaseRequest;
import com.bharatpe.lending.lendingplatform.underwriting.dto.request.UpdateBureauConsentRequest;
import com.bharatpe.lending.lendingplatform.underwriting.dto.reresponse.FetchBureauConsentResponse;
import com.bharatpe.lending.lendingplatform.underwriting.dto.reresponse.UnderwritingApiResponse;
import com.bharatpe.lending.lendingplatform.underwriting.dto.reresponse.UpdateBureauConsentResponse;
import com.bharatpe.lending.lendingplatform.underwriting.util.ClientUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;

import static com.bharatpe.lending.lendingplatform.underwriting.constants.BureauConstants.CLIENT_IDENTIFIER;
import static com.bharatpe.lending.lendingplatform.underwriting.constants.BureauConstants.MOBILE;

@Service
@Slf4j
public class BureauClient {
    private final ClientUtil clientUtil;
    private final LendingPlatformConfiguration lendingPlatformConfiguration;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    public BureauClient(ClientUtil clientUtil, LendingPlatformConfiguration lendingPlatformConfiguration,
                        @Qualifier("underwritingRestTemplate") RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.clientUtil = clientUtil;
        this.lendingPlatformConfiguration = lendingPlatformConfiguration;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public UnderwritingApiResponse<FetchBureauConsentResponse> getBureauConsent(String source, String mobile) {
        try {
            HttpEntity<?> request = clientUtil.populateHeadersAndPayload(Collections.emptyMap());
            String url = UriComponentsBuilder.fromUriString(lendingPlatformConfiguration.getBureauConsentUrl())
                    .queryParam(CLIENT_IDENTIFIER, source)
                    .queryParam(MOBILE, mobile)
                    .toUriString();
            log.info("Fetch bureau consent request for mobile: {}, {} , {}", mobile, url, request);
            ResponseEntity<UnderwritingApiResponse> responseEntity = restTemplate.exchange(url, HttpMethod.GET,
                    request, UnderwritingApiResponse.class);
            log.info("Fetch bureau consent response for mobile, {} {}", mobile, responseEntity.getBody());
            if (responseEntity.getStatusCode() == HttpStatus.OK && responseEntity.getBody() != null) {
                log.info("Bureau consent request successfull for mobile: {} {}", mobile, responseEntity.getBody());
                UnderwritingApiResponse<?> rawResponse = responseEntity.getBody();
                FetchBureauConsentResponse fetchBureauConsentResponse = objectMapper.convertValue(rawResponse.getData(), FetchBureauConsentResponse.class);
                // Return new LenderApiResponse with properly typed data
                return new UnderwritingApiResponse<>(
                        rawResponse.isSuccess(),
                        rawResponse.getApiError(),
                        fetchBureauConsentResponse
                );
            }
            log.error("Fetch bureau consent request failed for mobile: {} {} {}", mobile, responseEntity.getStatusCode(),
                    responseEntity.getBody());
        } catch (HttpServerErrorException | HttpClientErrorException e) {
            log.error("Http server exception while fetching bureau consent for mobile {} {}", mobile, e.getMessage());
        } catch (Exception e) {
            log.error("Exception in fetching bureau consent response for mobile: {} {}", mobile, e.getMessage(), e);
        }
        return null;
    }

    public UnderwritingApiResponse<UpdateBureauConsentResponse> updateBureauConsent(
            UnderwritingBaseRequest<UpdateBureauConsentRequest> updateBureauConsentRequest) {
        try {
            HttpEntity<?> request = clientUtil.populateHeadersAndPayload(updateBureauConsentRequest);
            String url = UriComponentsBuilder.fromUriString(lendingPlatformConfiguration.getBureauConsentUrl())
                    .toUriString();
            log.info("Update bureau consent request for mobile: {}, {} , {}",
                    updateBureauConsentRequest.getCustomerMobile(), url, request);
            ResponseEntity<UnderwritingApiResponse> responseEntity = restTemplate.exchange(url, HttpMethod.POST,
                    request, UnderwritingApiResponse.class);
            log.info("Update bureau consent response for mobile, {} {}", updateBureauConsentRequest.getCustomerMobile(),
                    responseEntity.getBody());
            if (responseEntity.getStatusCode() == HttpStatus.OK && responseEntity.getBody() != null) {
                log.info("Update consent request successfull for mobile: {} {}", updateBureauConsentRequest.getCustomerMobile(),
                        responseEntity.getBody());
                UnderwritingApiResponse<?> rawResponse = responseEntity.getBody();
                UpdateBureauConsentResponse updateBureauConsentResponse = objectMapper.convertValue(rawResponse.getData(),
                        UpdateBureauConsentResponse.class);
                // Return new LenderApiResponse with properly typed data
                return new UnderwritingApiResponse<>(
                        rawResponse.isSuccess(),
                        rawResponse.getApiError(),
                        updateBureauConsentResponse
                );
            }
            log.error("Update bureau consent request failed for mobile: {} {} {}",
                    updateBureauConsentRequest.getCustomerMobile(), responseEntity.getStatusCode(), responseEntity.getBody());
        } catch (HttpServerErrorException | HttpClientErrorException e) {
            log.error("Http server exception while updating bureau consent for mobile {} {}",
                    updateBureauConsentRequest.getCustomerMobile(), e.getMessage());
        } catch (Exception e) {
            log.error("Exception in updating bureau consent response for mobile: {} {}",
                    updateBureauConsentRequest.getCustomerMobile(), e.getMessage(), e);
        }
        return null;
    }
}
