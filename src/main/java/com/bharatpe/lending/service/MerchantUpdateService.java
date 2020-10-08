package com.bharatpe.lending.service;

import com.bharatpe.common.dao.*;
import com.bharatpe.common.entities.*;
import com.bharatpe.common.utils.AesEncryption;
import com.bharatpe.common.utils.HmacCalculator;
import com.bharatpe.lending.constant.LendingConstants;
import com.bharatpe.lending.dto.PayloadDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MerchantUpdateService {
	Logger logger = LoggerFactory.getLogger(MerchantUpdateService.class);

	@Autowired
	private HmacCalculator hmacCalculator;

	@Autowired
	AesEncryption aesEncryption;

	@Autowired
	InternalClientDao internalClientDao;

	@Autowired
	RestTemplate restTemplate;

	public JsonNode curlMerchantPartialUpdateAPI(Long merchantId, List<PayloadDTO> payload) {
		String response = null;
		Map<String, String> paramMap = new LinkedHashMap<>();
		paramMap.put("payload", reducePayloads(payload));
		String hash = hmacCalculator.calculateHmac(hmacCalculator.getPayload(paramMap), getSecret("LENDING"));
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
		headers.set("merchantId", merchantId.toString());
		headers.set("hash", hash);
		headers.set("Client-Name", "LENDING");

		try {
			Instant start = Instant.now();
			HttpEntity<Map<String, String>> request = new HttpEntity<>(paramMap, headers);
			logger.info("Merchant Updated API request : {}", request);
			response = restTemplate.postForObject(LendingConstants.MERCHANT_PARTIAL_UPDATE_URL, request, String.class);
			logger.info("Merchant Updated API response : {}", response);
			Instant end = Instant.now();
			logger.info("Time Taken by Merchant Updated API API : {} miliseconds",
					Duration.between(start, end).toMillis());
		} catch (Exception e) {
			logger.info("Exception while Merchant Updated API API, Exception is ---", e);
		}
		ObjectMapper mapper = new ObjectMapper();
		JsonNode resp = null;
		try {
			resp = mapper.readTree(response);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return resp;
	}

	private String reducePayloads(List<PayloadDTO> payloads) {
		String reducedPayloads = payloads.stream().map(Object::toString).collect(Collectors.joining(","));
		if (reducedPayloads == null)
			return "[]";
		return "[" + reducedPayloads + "]";
	}

	private String getSecret(String clientName) {
		InternalClient client = internalClientDao.findByClientName(clientName);
		if (client != null) {
			return aesEncryption.decrypt(client.getSecret());
		}
		return "";
	}
}
