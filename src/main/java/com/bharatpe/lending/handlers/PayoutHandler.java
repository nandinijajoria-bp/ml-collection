package com.bharatpe.lending.handlers;

import com.bharatpe.common.utils.HmacCalculator;
import com.bharatpe.lending.common.query.dao.InternalClientDaoSlave;
import com.bharatpe.lending.common.query.entity.InternalClientSlave;
import com.bharatpe.lending.common.util.AesEncryptionUtil;
import com.bharatpe.lending.constant.ServiceConstants;
import com.bharatpe.lending.dto.payout.BeneficiaryInfoDTO;
import com.bharatpe.lending.dto.payout.PayoutResponseDTO;
import com.bharatpe.lending.service.KafkaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

/**
 * @author dhvl
 */
@Service
@Slf4j
public class PayoutHandler {

    private final KafkaService kafkaService;
    private final ObjectMapper mapper;
    private final RestTemplate restTemplate;
    private final HmacCalculator hmacService;
    private final InternalClientDaoSlave internalClientDaoSlave;
    private final AesEncryptionUtil aesEncryptionUtil;
    private static String clientSecret = null;

    @Value("${payout2.service.url.endpoint}")
    private String baseUrl;

    public PayoutHandler(KafkaService kafkaService, ObjectMapper mapper, RestTemplate restTemplate
            , HmacCalculator hmacService, InternalClientDaoSlave internalClientDaoSlave, AesEncryptionUtil aesEncryptionUtil) {
        this.kafkaService = kafkaService;
        this.mapper = mapper;
        this.restTemplate = restTemplate;
        this.hmacService = hmacService;
        this.internalClientDaoSlave = internalClientDaoSlave;
        this.aesEncryptionUtil = aesEncryptionUtil;
    }

    private String getInternalSecret() {
        if (org.springframework.util.StringUtils.isEmpty(clientSecret)) {
            InternalClientSlave client = internalClientDaoSlave.findByClientName(ServiceConstants.PAYOUT.CLIENT_NAME);
            if (client != null) {
                clientSecret = aesEncryptionUtil.decrypt(client.getSecret());
            }
        }
        return clientSecret;
    }

    public void initiatePayout(String orderId, BigDecimal amount, String payoutType, BeneficiaryInfoDTO beneficiaryInfo) throws IOException {
        log.info("Creating payout for orderId: {}, amount: {}, payoutType: {}, beneficiary: {}", orderId, amount, payoutType, beneficiaryInfo);
        Map<String, Object> internalPayout = getInternalPayoutRequest(orderId, amount, payoutType, beneficiaryInfo);
        ProducerRecord producerRecord = new ProducerRecord(ServiceConstants.PAYOUT.DEFAULT_PAYOUT_REQUEST_TOPIC, orderId, internalPayout);
        producerRecord.headers().add(new RecordHeader(ServiceConstants.PAYOUT.HEADER.CLIENT_NAME, ServiceConstants.PAYOUT.CLIENT_NAME.getBytes()));
        producerRecord.headers().add(new RecordHeader(ServiceConstants.PAYOUT.HEADER.HASH_NAME,
                hmacService.calculateHmac(getNestedPayload(internalPayout), getInternalSecret()).getBytes()));
        kafkaService.send(producerRecord);
    }

    public PayoutResponseDTO checkStatus(String orderId, String payoutType) {
        try {
            long startTime = System.currentTimeMillis();
            Map<String, Object> param = new HashMap<>();
            param.put("orderId", orderId);
            param.put("payoutType", payoutType);

            String url = baseUrl + String.format(ServiceConstants.PAYOUT.CHECK_STATUS_URL, orderId, payoutType);
            log.info("status check request: {}", url);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<Object>(getHeaders(param)), String.class);
            log.info("status check response: {}, response time: {}", response,
                    (System.currentTimeMillis() - startTime));

            return mapper.readValue(response.getBody(), PayoutResponseDTO.class);
        } catch (Exception e) {
            log.error(e.getClass().getSimpleName() + " occurred while check status transaction for payout id: {} - {}",
                    orderId, e);
            return null;
        }
    }

    private Map<String, Object> getInternalPayoutRequest(String orderId, BigDecimal amount, String payoutType, BeneficiaryInfoDTO beneficiaryInfo) throws IOException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("beneficiaryType", ServiceConstants.PAYOUT.BeneficiaryType.ACCOUNT_DETAILS.name());
        payload.put("payoutType", payoutType);
        payload.put("amount", String.valueOf(amount));
        payload.put("orderId", orderId);
        payload.put("beneficiaryInfo", mapper.readValue(mapper.writeValueAsString(beneficiaryInfo), Map.class));
        return payload;
    }

    public HttpHeaders getHeaders(Map<String, Object> params) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.setCacheControl(CacheControl.noCache());
        headers.set(ServiceConstants.PAYOUT.HEADER.HASH_NAME, hmacService.calculateHmac(getNestedPayload(params), getInternalSecret()));
        headers.set(ServiceConstants.PAYOUT.HEADER.CLIENT_NAME, ServiceConstants.PAYOUT.CLIENT_NAME);
        return headers;
    }

    private String getNestedPayload(Map<String, Object> paramMap) {
        Map<String, Object> sortedMap = new TreeMap<>(paramMap);
        sortedMap.values().removeIf(Objects::isNull);

        for (Map.Entry<String, Object> obj : sortedMap.entrySet()) {
            if (obj.getValue() instanceof LinkedHashMap) {
                sortedMap.put(obj.getKey(), getNestedPayload((Map<String, Object>) obj.getValue()));
            }
        }
        return StringUtils.collectionToDelimitedString(sortedMap.values(), "|");
    }
}
