package com.bharatpe.lending.lendingplatform.underwriting.util;

import com.bharatpe.lending.lendingplatform.authentication.service.LendingPlatformTokenHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ClientUtil {
    private final LendingPlatformTokenHandler lendingPlatformTokenHandler;

    public HttpEntity<?> populateHeadersAndPayload(Object payload) throws Exception {
        HttpHeaders headers = getAuthHeader();
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        return new HttpEntity<>(payload, headers);
    }

    public HttpHeaders getAuthHeader() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(lendingPlatformTokenHandler.getAuthenticationToken());
        return headers;
    }
}
