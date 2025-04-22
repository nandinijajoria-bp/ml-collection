package com.bharatpe.lending.lendingplatform.underwriting.client;

import com.bharatpe.lending.dto.GlobalLimitResponse;
import com.bharatpe.lending.dto.MaskedGlobalLimitResponse;
import com.bharatpe.lending.dto.MaskedGlobalLimitResponseDTO;
import com.bharatpe.lending.dto.ScenapticResponseDTO;
import com.bharatpe.lending.lendingplatform.config.LendingPlatformConfiguration;
import com.bharatpe.lending.lendingplatform.underwriting.dto.request.EligibilityRequest;
import com.bharatpe.lending.lendingplatform.underwriting.dto.request.UnderwritingBaseRequest;
import com.bharatpe.lending.lendingplatform.underwriting.dto.reresponse.EligibilityResponse;
import com.bharatpe.lending.lendingplatform.underwriting.dto.reresponse.UnderwritingApiResponse;
import com.bharatpe.lending.lendingplatform.underwriting.util.ClientUtil;
import com.bharatpe.lending.loanV3.revamp.constants.LoanDetailsConstant;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.json.XML;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;


import static com.bharatpe.lending.lendingplatform.underwriting.constants.EligibilityConstants.DATA;
import static com.bharatpe.lending.lendingplatform.underwriting.constants.EligibilityConstants.UNDERWRITING_API_RESPONSE;

@Service
@Slf4j
public class EligibilityClient {
    private final RestTemplate restTemplate;
    private final ClientUtil clientUtil;
    private final LendingPlatformConfiguration lendingPlatformConfiguration;
    private final ObjectMapper objectMapper;

    public EligibilityClient(@Qualifier("underwritingRestTemplate") RestTemplate restTemplate, ClientUtil clientUtil,
                             LendingPlatformConfiguration lendingPlatformConfiguration, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.clientUtil = clientUtil;
        this.lendingPlatformConfiguration = lendingPlatformConfiguration;
        this.objectMapper = objectMapper;
    }

    public GlobalLimitResponse getEligibility(UnderwritingBaseRequest<EligibilityRequest> eligibilityRequest) {
        try {
            HttpEntity<?> request = clientUtil.populateHeadersAndPayload(eligibilityRequest);
            String url = lendingPlatformConfiguration.getEligibilityUrl();
            log.info("Eligibility request for merchant id: {}, {} , {}", eligibilityRequest.getCustomerId(), url, request);
            ResponseEntity<UnderwritingApiResponse> responseEntity = restTemplate.exchange(url, HttpMethod.POST, request, UnderwritingApiResponse.class);
            log.info("Eligibility response for merchant id: {}, {}", eligibilityRequest.getCustomerId(), responseEntity.getBody());
            if (responseEntity.getStatusCode() == HttpStatus.OK && !ObjectUtils.isEmpty(responseEntity.getBody())) {
                return convertResponseEntityToGlobalLimitResponse(responseEntity, eligibilityRequest);
            }
        } catch (HttpServerErrorException | HttpClientErrorException e) {
            log.info("Exception in eligibility response :{} {}", e.getMessage(), e.getResponseBodyAsString());
            return parseErrorResponse(e);
        } catch (Exception e) {
            log.error("Exception in fetching eligibility response : {}", e.getMessage(), e);
        }
        return null;
    }

    private GlobalLimitResponse convertResponseEntityToGlobalLimitResponse(ResponseEntity<UnderwritingApiResponse> responseEntity,
                                                                           UnderwritingBaseRequest<EligibilityRequest> eligibilityRequest) {
        UnderwritingApiResponse<?> rawResponse = responseEntity.getBody();
        if (ObjectUtils.isEmpty(rawResponse) || ObjectUtils.isEmpty(rawResponse.getData())) {
            log.error("Error in getting eligibility response for customer id: {}", eligibilityRequest.getCustomerId());
            return null;
        }
        EligibilityResponse eligibilityResponse = objectMapper.convertValue(rawResponse.getData(), EligibilityResponse.class);
        GlobalLimitResponse.Data globalLimitResponseData = new GlobalLimitResponse.Data();
        BeanUtils.copyProperties(eligibilityResponse, globalLimitResponseData);
        GlobalLimitResponse globalLimitResponse = new GlobalLimitResponse();
        globalLimitResponse.setData(globalLimitResponseData);
        globalLimitResponse.setSuccess(rawResponse.isSuccess());
        log.info("Global limit response for merchant id: {}, {}", eligibilityRequest.getCustomerId(), globalLimitResponse);
        return globalLimitResponse;
    }

    private GlobalLimitResponse parseErrorResponse(HttpStatusCodeException e) {
        try {
            log.info("Error response body: {}", e.getResponseBodyAsString());
            JSONObject jsonObject = XML.toJSONObject(e.getResponseBodyAsString());
            JsonNode responseNode = objectMapper.readTree(jsonObject.toString());
            log.info("responseNode:{}", responseNode);
            JsonNode dataNode = responseNode.path(UNDERWRITING_API_RESPONSE).path(DATA);
            String val = dataNode.asText();
            JSONObject object = new JSONObject(val);
            if (e.getResponseBodyAsString().contains(LoanDetailsConstant.UNDERWRITING_MASKED_MOBILE_EXCEPTION)) {
                String xmlString = XML.toString(object);
                XmlMapper xmlMapper = new XmlMapper();
                object.remove(DATA);
                MaskedGlobalLimitResponseDTO data = xmlMapper.readValue(xmlString, MaskedGlobalLimitResponseDTO.class);
                MaskedGlobalLimitResponse maskedGlobalLimitResponse = objectMapper.readValue(object.toString(),
                        MaskedGlobalLimitResponse.class);
                maskedGlobalLimitResponse.setData(data);
                log.info("Masked mobile scenapticResponseDTO {}", maskedGlobalLimitResponse.toString());
                return GlobalLimitResponse.form(maskedGlobalLimitResponse);
            }
            ScenapticResponseDTO scenapticResponseDTO = objectMapper.readValue(object.toString(), ScenapticResponseDTO.class);
            log.info("ScenapticResponseDTO {}", scenapticResponseDTO.toString());
            return GlobalLimitResponse.form(scenapticResponseDTO);

        } catch (Exception ex) {
            log.error("Exception in parsing responseBody string : {}", ex.getMessage(), ex);
        }
        return null;
    }
}
