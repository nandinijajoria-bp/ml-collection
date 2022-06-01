package com.bharatpe.lending.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Slf4j
public class RestUtils {

  public enum ExceptionLevel {
    INFO, ERROR, WARN
  }

  private final RestTemplate restTemplate;
  private final ObjectMapper objectMapper;
  private final MapperUtil mapperUtil;

  @Autowired
  public RestUtils(RestTemplate restTemplate, MapperUtil mapperUtil) {
    this.restTemplate = restTemplate;
    this.mapperUtil = mapperUtil;
    this.objectMapper = mapperUtil.objectMapper;
  }

  public <T> T getForObject(String url, Map<String, String> headers, Map<String, String> queryParams, Class<T> clazz) throws Exception {

    UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromHttpUrl(url);
    queryParams.forEach(uriComponentsBuilder::queryParam);
    return getForObject(uriComponentsBuilder.toUriString(), headers, clazz, ExceptionLevel.ERROR);

  }

  public <T> T getForObject(String url, Map<String, String> headers, Map<String, String> queryParams, Class<T> clazz, ExceptionLevel logLevel) throws Exception {

    UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromHttpUrl(url);
    queryParams.forEach(uriComponentsBuilder::queryParam);
    return getForObject(uriComponentsBuilder.toUriString(), headers, clazz, logLevel);

  }

  public <T> T getForObject(String url, Map<String, String> headers, Class<T> clazz) throws Exception {
    return getForObject(url, headers, clazz, ExceptionLevel.ERROR);
  }

  public <T> T getForObject(String url, Map<String, String> headers, Class<T> clazz, ExceptionLevel logLevel) throws Exception {
    HttpEntity<?> request = getHeader(headers);
    UriComponents uriComponents = UriComponentsBuilder.fromHttpUrl(url).build();

    try {
      log.info("Url : " + url + ", Request : " + mapperUtil.getJsonString(request));
      long startTime = System.currentTimeMillis();
      ResponseEntity<String> response = restTemplate.exchange(uriComponents.encode().toUri(), HttpMethod.GET, request, String.class);
      log.info("Fetched response for Url:" + uriComponents.encode().toUri() + " in " + (System.currentTimeMillis() - startTime) + "ms");
      log.info("Response : " + mapperUtil.getJsonString(response));
      if (HttpStatus.OK.equals(response.getStatusCode())) {
        return objectMapper.readValue(response.getBody(), clazz);
      } else {
        throw new RestClientException(response.getStatusCode().value() + "");
      }
    } catch (RestClientException e) {
      printLog("Rest Client Exception:" + e.getMessage() + ":" + e, logLevel);
      throw e;
    } catch (Exception e) {
      printLog("Error : " + e.getMessage() + " : " + e, logLevel);
      throw e;
    }

  }

  public <T> T putForObject(String url, Map<String, String> headers, Map<String, Object> payload, Class<T> clazz, ExceptionLevel logLevel) throws Exception {
    HttpEntity<?> request = getHeader(headers, payload);
    UriComponents uriComponents = UriComponentsBuilder.fromHttpUrl(url).build();

    try {
      log.info("Url : {}, Request : {}", url, mapperUtil.getJsonString(request));
      long startTime = System.currentTimeMillis();
      ResponseEntity<String> response = restTemplate.exchange(uriComponents.encode().toUri(), HttpMethod.PUT, request, String.class);
      log.info("Fetched response for Url:{} in {}ms", uriComponents.encode().toUri(), System.currentTimeMillis() - startTime);
      log.info("Response : {}", mapperUtil.getJsonString(response));
      if (HttpStatus.OK.equals(response.getStatusCode())) {
        return objectMapper.readValue(response.getBody(), clazz);
      } else {
        throw new RestClientException(response.getStatusCode().value() + "");
      }
    } catch (RestClientException e) {
      printLog("Rest Client Exception: " + e.getMessage() + " :" + e, logLevel);
      throw e;
    } catch (Exception e) {
      printLog("Error : " + e.getMessage() + ":" + e, logLevel);
      throw e;
    }
  }

  public <T> T putForObject(String url, Map<String, String> headers, Map<String, Object> payload, Class<T> clazz) throws Exception {
    return putForObject(url, headers, payload, clazz, ExceptionLevel.ERROR);
  }

  public <T> T postForObject(String url, Map<String, String> headers, Map<String, Object> payload, Class<T> clazz) throws Exception {
    return postForObject(url, headers, payload, clazz, ExceptionLevel.ERROR);
  }

  public <T> T postForObject(String url, Map<String, String> headers, Map<String, Object> payload, Class<T> clazz, ExceptionLevel logLevel) throws Exception {
    HttpEntity<?> request = getHeader(headers, payload);
    UriComponents uriComponents = UriComponentsBuilder.fromHttpUrl(url).build();

    try {
      log.info("Url : {}, Request : {}", url, mapperUtil.getJsonString(request));
      long startTime = System.currentTimeMillis();
      ResponseEntity<String> response = restTemplate.exchange(uriComponents.encode().toUri(), HttpMethod.POST, request, String.class);
      log.info("Fetched response for Url:{} in {}ms", uriComponents.encode().toUri(), System.currentTimeMillis() - startTime);
      log.info("Response : {}", mapperUtil.getJsonString(response));
      if (HttpStatus.OK.equals(response.getStatusCode())) {
        return objectMapper.readValue(response.getBody(), clazz);
      } else {
        throw new RestClientException(response.getStatusCode().value() + "");
      }
    } catch (RestClientException e) {
      printLog("Rest Client Exception: " + e.getMessage(), logLevel);
      throw e;
    } catch (Exception e) {
      printLog("Error : " + e.getMessage() + ":" + e, logLevel);
      throw e;
    }
  }

  public String postForObjectForString(String url, Map<String, String> headers, Map<String, Object> payload, Class<String> clazz) throws Exception {
    return postForObjectForString(url, headers, payload, clazz, ExceptionLevel.ERROR);
  }

  public String postForObjectForString(String url, Map<String, String> headers, Map<String, Object> payload, Class<String> clazz, ExceptionLevel logLevel) throws Exception {
    HttpEntity<?> request = getHeader(headers, payload);
    UriComponents uriComponents = UriComponentsBuilder.fromHttpUrl(url).build();

    try {
      log.info("Url : {}, Request : {}", url, mapperUtil.getJsonString(request));
      long startTime = System.currentTimeMillis();
      ResponseEntity<String> response = restTemplate.exchange(uriComponents.encode().toUri(), HttpMethod.POST, request, String.class);
      log.info("Fetched response for Url:{} in {}ms", uriComponents.encode().toUri(), System.currentTimeMillis() - startTime);
//      log.info("Response : {}", mapperUtil.getJsonString(response));
      return String.valueOf(response.getStatusCodeValue());
    } catch (RestClientException e) {
      printLog("Rest Client Exception: " + e.getMessage() + ":" + e, logLevel);
      throw e;
    } catch (Exception e) {
      printLog("Error :" + e.getMessage() + ":" + e, logLevel);
      throw e;
    }
  }

  private HttpEntity<?> getHeader(Map<String, String> headers) {
    HttpHeaders header = new HttpHeaders();
    headers.entrySet().forEach(entry -> {
      header.set(entry.getKey(), entry.getValue());
    });
    return new HttpEntity<>(header);
  }

  private HttpEntity<?> getHeader(Map<String, String> headers, Map<String, Object> payload) {
    HttpHeaders header = new HttpHeaders();
    headers.entrySet().forEach(entry -> {
      header.set(entry.getKey(), entry.getValue());
    });
    return new HttpEntity<>(payload, header);
  }

  private void printLog(String logData, ExceptionLevel level) {
    switch (level) {
      case INFO:
        log.info(logData);
        break;
      case ERROR:
      default:
        log.error(logData);
    }
  }

}
