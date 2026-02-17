package com.bharatpe.lending.consumer;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.objects.Meta;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.common.util.ConfigResolver;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.enums.ApplicationStatus;
import com.bharatpe.lending.service.PostAgreementAsyncFlowService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostAgreementAsyncFlowConsumer {
    private final ConfigResolver configResolver;
    private final MerchantService merchantService;
    private final LendingApplicationDao lendingApplicationDao;
    private final PostAgreementAsyncFlowService postAgreementAsyncFlowService;
    private final ObjectMapper objectMapper;

    private final List<String> inActiveApplicationStatuses = Arrays.asList(ApplicationStatus.DELETED.name().toLowerCase(), ApplicationStatus.REJECTED.name().toLowerCase());

    @KafkaListener(
            topics = "${post.agreement.flow.topic:post_agreement_async_flow}",
            concurrency = "5",
            autoStartup = "${kafka.confluent.consumer.new:false}",
            containerFactory = "ConfluentKafkaListenerContainer")
    public void invokePostAgreementAsyncFlow(String request) {
        MDC.put("requestId", UUID.randomUUID().toString());
        log.info("Received post agreement async flow request:{}", request);
        Map<String, Object> postAgreementAsyncFlowRequest = configResolver.getConfig(request, new TypeReference<Map<String, Object>>() {
        });
        Long applicationId = Long.valueOf(postAgreementAsyncFlowRequest.get("application_id").toString());
        Optional<LendingApplication> lendingApplication = lendingApplicationDao.findById(applicationId);
        if (!lendingApplication.isPresent() || inActiveApplicationStatuses.contains(lendingApplication.get().getStatus())) {
            log.info("no active application found for id {}", postAgreementAsyncFlowRequest.get("application_id"));
            return;
        }
        Long merchantId = Long.valueOf(postAgreementAsyncFlowRequest.get("merchant_id").toString());
        Meta meta = new Meta();
        try {
            meta = configResolver.getConfig(objectMapper.writeValueAsString(postAgreementAsyncFlowRequest.get("meta")), Meta.class);
        } catch (Exception e) {
            log.error("Exception in parsing meta details for post agreement flow for merchantId {} {}", merchantId, Arrays.asList(e.getStackTrace()));
        }
        BasicDetailsDto basicDetailsDto = merchantService.fetchMerchantBasicDetails(merchantId).orElse(null);
        if (ObjectUtils.isEmpty(basicDetailsDto)) {
            log.info("merchant basic details not found for merchantId {}", merchantId);
            return;
        }
        Integer retryCount = Integer.valueOf(postAgreementAsyncFlowRequest.get("retry_count").toString());
        postAgreementAsyncFlowService.postAgreementAsyncFlow(basicDetailsDto, lendingApplication.get(), meta, retryCount);
    }
}
