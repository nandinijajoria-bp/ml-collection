package com.bharatpe.lending.service;

import com.bharatpe.common.service.MongoPublisher;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.query.dao.InternalClientDaoSlave;
import com.bharatpe.lending.common.query.entity.InternalClientSlave;
import com.bharatpe.lending.common.util.AesEncryptionUtil;
import com.bharatpe.lending.common.util.LendingHmacCalculator;
import com.bharatpe.lending.dto.PayloadDTO;

import com.bharatpe.lending.util.BQPublisherUtil;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import javax.annotation.PostConstruct;

@Service
public class MerchantUpdateService {
	Logger logger = LoggerFactory.getLogger(MerchantUpdateService.class);

	@Autowired
	private LendingHmacCalculator lendingHmacCalculator;

	@Autowired
	AesEncryptionUtil aesEncryptionUtil;

	@Autowired
	InternalClientDaoSlave internalClientDaoSlave;

	@Autowired
	RestTemplate restTemplate;

	private String clientSecret;

	@Value("${merchant.partialUpdate.api}")
    String merchantPartialUpdateApiUrl;

	@Autowired
	MongoPublisher mongoPublisher;

	@Autowired
	BQPublisherUtil bqPublisherUtil;

	@PostConstruct
    public void init() {
        try {
            getSecret("LENDING", "ACTIVE");
        } catch (Exception ex) {
            logger.error("Exception while loading Secret in MerchantUpdateService: ", ex);
        }
    }

	public Boolean curlMerchantPartialUpdateAPI(Long merchantId, List<PayloadDTO> payload) {
		logger.info("calling merchant api for update for merchant id : {}",merchantId);
		boolean status = false;
		Map response;
		try {
			HttpHeaders headers = new HttpHeaders();
			Map<String, Object> paramMap = getParams(payload);
			headers.setContentType(MediaType.APPLICATION_JSON);
			headers.setCacheControl(CacheControl.noCache());
			headers.set("client-Name", "LENDING");

			String hash = lendingHmacCalculator.calculateHMACHexEncoded(
					lendingHmacCalculator.getObjectPayloadList((List<Map<String, Object>>) paramMap.get("payload")),
					getSecret("LENDING", "ACTIVE"));
			logger.info("generated hash value is : {}", hash);
			headers.set("merchantId", merchantId.toString());
			headers.set("Hash", hash);

			Instant start = Instant.now();
			HttpEntity<Map> request = new HttpEntity<>(paramMap, headers);
			logger.info("Merchant Updated API request : {}", request);
			response = restTemplate.exchange(merchantPartialUpdateApiUrl, HttpMethod.PUT, request, Map.class).getBody();
			Instant end = Instant.now();
			logger.info("Merchant Updated API response : {}, response time: {}", response, Duration.between(start, end).toMillis());
			status = (Boolean)response.get("status");
			if (status) {
                logger.info("merchant details updated successfully for merchant id : {}",merchantId);

            } else {
                logger.error("failed to update merchant details for merchant id : {}",merchantId);
            }
		} catch (Exception e) {
			logger.error("Exception while Merchant Updated API for merchantId: {}, Exception: {}", merchantId, e);
		}
		return status;
	}

	private Map<String, Object> getParams(List<PayloadDTO> payloadList){
		Map<String,Object> requestParams = new HashMap<>();
		List<Map<String, Object>> payloadMap = new ArrayList<>();
		for(PayloadDTO payload: payloadList){
			Map<String, Object> mapData = new HashMap<>();
			mapData.put("op", payload.getOp());
			mapData.put("key", payload.getKey());
			mapData.put("value", payload.getValue());
			payloadMap.add(mapData);
		}
		requestParams.put("payload", payloadMap);
		return requestParams;
	}

	private String getSecret(String clientName, String status) {
		if(StringUtils.isEmpty(this.clientSecret)) {
			InternalClientSlave client = internalClientDaoSlave.findByClientNameAndStatus(clientName, status);
			if (client != null) {
				this.clientSecret = aesEncryptionUtil.decrypt(client.getSecret());
			}
		}
		return this.clientSecret;
	}

	public void saveAlgo360Logs(BasicDetailsDto merchant, String data){

		logger.info("start processing logs for merchant_id: {}", merchant.getId());

		try{
			Map<String, Object> request = new HashMap<>();

			request.put("body", data);
			request.put("date", new Date());
			logger.info("push data to BQ for save algo 360 logs for merchant id {}",merchant.getId());
			bqPublisherUtil.publish("Lending", "algo360_logs", new ArrayList<Map>(){{add(request);}});

		}catch (Exception ex){
			logger.error("Error Occurred while processing logs: {} for merchant_id: ", merchant.getId(), ex);

		}

		logger.info("processed logs for merchant_id: {}", merchant.getId());
	}
}
