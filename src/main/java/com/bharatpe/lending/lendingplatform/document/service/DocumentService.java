package com.bharatpe.lending.lendingplatform.document.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentService {
    @Value("${com.bharatpe.platform.public.baseUrl:-}")
    private String lendingPlatformBaseUrl;
    @Value("${lending.platform.doc.endpoint:-}")
    private String lendingPlatformDocEndpoint;

    public String generateDocUrl(String objectKey) {
        return lendingPlatformBaseUrl + lendingPlatformDocEndpoint + objectKey;
    }
}
