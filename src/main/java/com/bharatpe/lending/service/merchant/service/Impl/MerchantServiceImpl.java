package com.bharatpe.lending.service.merchant.service.Impl;


import com.bharatpe.common.utils.HmacCalculator;
import com.bharatpe.lending.service.merchant.constants.Constants;
import com.bharatpe.lending.service.merchant.dto.APIResponseDto;
import com.bharatpe.lending.service.merchant.dto.AddressDetailsDto;
import com.bharatpe.lending.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.service.merchant.dto.MerchantDetailsDto;
import com.bharatpe.lending.service.merchant.service.MerchantService;
import com.bharatpe.lending.service.merchant.util.Util;
import com.bharatpe.lending.util.MapperUtil;
import com.bharatpe.lending.util.RestUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
public class MerchantServiceImpl implements MerchantService {


  private final MapperUtil mapperUtil;
  private final RestUtils restUtils;
  private final HmacCalculator hmacCalculator;
  private final Constants constants;

  @Autowired
  public MerchantServiceImpl(MapperUtil mapperUtil, RestUtils restUtils, HmacCalculator hmacCalculator,
                             Constants constants) {
    this.mapperUtil = mapperUtil;
    this.restUtils = restUtils;
    this.hmacCalculator = hmacCalculator;
    this.constants = constants;
  }

  public BasicDetailsDto fetchMerchantDetails(String token) throws Exception {

    Map<String, String> headers = new HashMap<>();
    headers.put(Constants.TOKEN, token);
    headers.put("Client-Name", constants.MERCHANT_CLIENT_NAME);
    String url = constants.MERCHANT_HOST + Constants.MerchantUtil.MERCHANT_TOKEN_VERIFY_API;

    APIResponseDto responseDto = restUtils.getForObject(url, headers, APIResponseDto.class, RestUtils.ExceptionLevel.INFO);
    BasicDetailsDto merchantDto = mapperUtil.objectMapper.readValue(mapperUtil.getJsonString(responseDto.getData()), BasicDetailsDto.class);

    log.info("Merchant Details: {}", mapperUtil.getJsonString(merchantDto));

    return merchantDto;
  }

  public MerchantDetailsDto fetchMerchantDetails(Long merchantId, List<String> scopeList) throws Exception {

    String url = constants.MERCHANT_HOST + Constants.MerchantUtil.MERCHANT_INFO;

    Map<String, String> queryParams = new HashMap<>();
    queryParams.put("merchantids", String.valueOf(merchantId));
    queryParams.put("scopes", String.join(",", scopeList));

    Map<String, String> header = new HashMap<>();
    header.put(Constants.HASH, hmacCalculator.calculateHmac(hmacCalculator.getPayload(queryParams),
        constants.MERCHANT_CLIENT_SECRET));
    header.put(Constants.CLIENT, constants.MERCHANT_CLIENT_NAME);

    APIResponseDto responseDto = restUtils.getForObject(url, header, queryParams, APIResponseDto.class);
    List<MerchantDetailsDto> merchantDetailsDtos = mapperUtil.objectMapper.readValue(mapperUtil.getJsonString(responseDto.getData()),
        mapperUtil.objectMapper.getTypeFactory().constructCollectionType(List.class, MerchantDetailsDto.class));

    log.info("Merchant Details: {}", mapperUtil.getJsonString(merchantDetailsDtos));

    return merchantDetailsDtos.get(0);
  }

  @Override
  public boolean updateDetails(Long merchantId, String operation, String key, String value) throws Exception {
    Map<String, String> operations = new HashMap<>();
    operations.put("op", operation);
    operations.put("key", key);
    operations.put("value", value);
    return updateDetails(merchantId, Arrays.asList(operations));
  }

  @Override
  public boolean updateDetails(Long merchantId, List<Map<String, String>> operations) throws Exception {
    String url = constants.MERCHANT_HOST + Constants.MerchantUtil.PARTIAL_UPDATE;

    Map<String, Object> payload = new HashMap<>();
    payload.put("payload", operations);

    Map<String, String> header = new HashMap<>();
    header.put(Constants.HASH, hmacCalculator.calculateHmac(Util.partialUpdatePayload(operations),
        constants.MERCHANT_CLIENT_SECRET));
    header.put(Constants.CLIENT, constants.MERCHANT_CLIENT_NAME);
    header.put(Constants.MerchantUtil.MERCHANT_ID, merchantId.toString());
    header.put(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

    APIResponseDto responseDto = restUtils.putForObject(url, header, payload, APIResponseDto.class);
    log.info("Merchant Service Update Operation Status : {}", responseDto.isStatus());
    return responseDto.isStatus();
  }

  public MerchantDetailsDto fetchMerchantDetails(Long merchantId) throws Exception {
    return fetchMerchantDetails(merchantId, Arrays.asList(
        Constants.MerchantUtil.Scope.BANK_DETAIL,
        Constants.MerchantUtil.Scope.ADDRESS,
        Constants.MerchantUtil.Scope.VPA,
        Constants.MerchantUtil.Scope.MERCHANT_USER
    ));
  }

  @Override
  public Optional<AddressDetailsDto> fetchMerchantAddressDetails(Long merchantId) throws Exception {
    MerchantDetailsDto merchantDetailsDto = fetchMerchantDetails(merchantId, Arrays.asList(
        Constants.MerchantUtil.Scope.ADDRESS
    ));
    List<AddressDetailsDto> addresses = merchantDetailsDto.getAddressDetail();
    if (!ObjectUtils.isEmpty(addresses)) {
      Optional<AddressDetailsDto> addressDetailsDtoOptional = addresses.stream()
          .filter(addressDetailsDto -> "SELF".equals(addressDetailsDto.getType()))
          .findFirst();
      return addressDetailsDtoOptional;
    }
    return null;
  }

}
