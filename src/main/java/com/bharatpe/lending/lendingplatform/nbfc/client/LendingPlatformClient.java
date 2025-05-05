package com.bharatpe.lending.lendingplatform.nbfc.client;

import com.bharatpe.lending.lendingplatform.authentication.service.LendingPlatformTokenHandler;
import com.bharatpe.lending.lendingplatform.config.LendingPlatformConfiguration;
import com.bharatpe.lending.lendingplatform.nbfc.dto.request.*;
import com.bharatpe.lending.lendingplatform.nbfc.dto.response.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import static com.bharatpe.lending.lendingplatform.nbfc.constants.ErrorStatusCode.*;

@Service
@Slf4j
public class LendingPlatformClient {

    @Autowired
    @Qualifier("LendingPlatformRestTemplate")
    private RestTemplate restTemplate;

    @Autowired
    private LendingPlatformConfiguration lendingPlatformConfiguration;

    @Autowired
    private LendingPlatformTokenHandler lendingPlatformTokenHandler;
    @Autowired
    private ObjectMapper objectMapper;

    public LenderApiResponse<BREResponse> initiateBRE(LenderBaseRequest<BRERequest> breRequest) {
        return sendPostRequest(
                lendingPlatformConfiguration.getBreUrl(),
                breRequest,
                "BRE",
                breRequest.getData().getApplicationDetails().getApplicationId(),
                BREResponse.class);
    }

    public LenderApiResponse<CreateLeadResponse> initiateCreateLead(LenderBaseRequest<CreateLeadRequest> createLeadRequest) {
        return sendPostRequest(
                lendingPlatformConfiguration.getCreateLeadUrl(),
                createLeadRequest,
                "Create Lead",
                createLeadRequest.getData().getApplicationDetails().getApplicationId(),
                CreateLeadResponse.class);
    }

    public LenderApiResponse<KYCDocumentUploadResponse> initiateKYCDocumentUpload(LenderBaseRequest<KYCDocumentUploadRequest> kycDocumentUploadRequest) {
        return sendPostRequest(
                lendingPlatformConfiguration.getUploadKycDocumentUrl(),
                kycDocumentUploadRequest,
                "KYC Document Upload",
                kycDocumentUploadRequest.getData().getApplicationDetails().getApplicationId(),
                KYCDocumentUploadResponse.class);
    }

    public LenderApiResponse<KYCResponse> initiateKYC(LenderBaseRequest<KYCRequest> kycRequest) {
        return sendPostRequest(
                lendingPlatformConfiguration.getPerformKycUrl(),
                kycRequest,
                "KYC",
                kycRequest.getData().getApplicationDetails().getApplicationId(),
                KYCResponse.class);
    }

    public LenderApiResponse<LoanDocumentDigiSignResponse> initiateDigiSign(LenderBaseRequest<LoanDocumentDigiSignRequest> loanDocumentDigiSignRequest) {
        return sendPostRequest(
                lendingPlatformConfiguration.getSignLoanDocumentUrl(),
                loanDocumentDigiSignRequest,
                "DigiSign",
                loanDocumentDigiSignRequest.getData().getApplicationDetails().getApplicationId(),
                LoanDocumentDigiSignResponse.class);
    }


    public LenderApiResponse<Boolean> initiateLoanSanction(LenderBaseRequest<LoanSanctionRequest> loanSanctionRequest) {
        return sendPostRequest(
                lendingPlatformConfiguration.getSanctionLoanUrl(),
                loanSanctionRequest,
                "Loan Sanction",
                loanSanctionRequest.getData().getApplicationDetails().getApplicationId(),
                Boolean.class);
    }

    public LenderApiResponse<NachRegistrationResponse> initiateNach(LenderBaseRequest<NachRegistrationRequest> nachRegistrationRequest) {
        return sendPostRequest(
                lendingPlatformConfiguration.getRegisterNachUrl(),
                nachRegistrationRequest,
                "Nach Registration",
                nachRegistrationRequest.getData().getApplicationDetails().getApplicationId(),
                NachRegistrationResponse.class);
    }

    public LenderApiResponse<UpdateLeadResponse> initiateUpdateLead(LenderBaseRequest<UpdateLeadRequest> updateLeadRequest) {
        return sendPostRequest(
                lendingPlatformConfiguration.getUpdateLeadUrl(),
                updateLeadRequest,
                "Update Lead",
                updateLeadRequest.getData().getApplicationDetails().getApplicationId(),
                UpdateLeadResponse.class);
    }

    public LenderApiResponse<LoanDocumentUploadResponse> initateLoanDocUpload(LenderBaseRequest<LoanDocumentUploadRequest> loanDocumentUploadRequest) {
        return sendPostRequest(
                lendingPlatformConfiguration.getUploadLoanDocumentUrl(),
                loanDocumentUploadRequest,
                "Loan Document Upload",
                loanDocumentUploadRequest.getData().getApplicationDetails().getApplicationId(),
                LoanDocumentUploadResponse.class);
    }

    public LenderApiResponse<LoanDisbursalResponse> initiateLoanDisbursal(LenderBaseRequest<LoanDisbursalRequest> loanDisbursalRequest){
        return sendPostRequest(
                lendingPlatformConfiguration.getDisburseLoanUrl(),
                loanDisbursalRequest,
                "Loan Disbursal",
                loanDisbursalRequest.getData().getApplicationDetails().getApplicationId(),
                LoanDisbursalResponse.class);
    }


    /**
     * Generic method to send a POST request with error handling.
     */
    private <T> LenderApiResponse<T> sendPostRequest(String url, Object requestData, String operation, String applicationId, Class<T> responseType) {
        try {
            HttpEntity<?> request = populateHeadersAndPayload(requestData);
            log.info("Initiating {} request for applicationId={} to URL={}, payload:{}", operation, applicationId, url, requestData);
            ResponseEntity<LenderApiResponse> responseEntity = restTemplate.exchange(url, HttpMethod.POST, request, LenderApiResponse.class);

            if (responseEntity.getStatusCode() == HttpStatus.OK && responseEntity.getBody() != null && responseEntity.getBody().isSuccess()) {
                log.info("{} request successful for applicationId={} - response: {}", operation, applicationId, responseEntity.getBody());
                LenderApiResponse<?> rawResponse = responseEntity.getBody();
                T typedData = objectMapper.convertValue(rawResponse.getData(), responseType);
                // Return new LenderApiResponse with properly typed data
                return new LenderApiResponse<>(
                        rawResponse.isSuccess(),
                        rawResponse.getApplicationId(),
                        rawResponse.getCustomerId(),
                        rawResponse.getLender(),
                        rawResponse.getApiError(),
                        typedData
                );
            } else {
                log.warn("failed for applicationId={} - HTTP Status: {},response status code: {}", operation, applicationId, responseEntity.getStatusCode());
                return LenderApiResponse.error(INTERNAL_SERVER_ERROR, "Unexpected response from lender", null);
            }
        } catch (HttpClientErrorException e) {
            log.error("{} request failed for applicationId={} - Client Error: {} - Response: {}", operation, applicationId, e.getStatusCode(), e.getResponseBodyAsString());
            return LenderApiResponse.error(BAD_REQUEST, "Client error: " + e.getMessage(), null);
        } catch (HttpServerErrorException e) {
            log.error("{} request failed for applicationId={} - Server Error: {} - Response: {}", operation, applicationId, e.getStatusCode(), e.getResponseBodyAsString());
            return LenderApiResponse.error(BAD_GATEWAY, "Lender service error: " + e.getMessage(), null);
        } catch (RestClientException e) {
            log.error("{} request failed for applicationId={} - Network Error: {}", operation, applicationId, e.getMessage(), e);
            return LenderApiResponse.error(SERVICE_UNAVAILABLE, "Network error: Unable to reach lender", null);
        } catch (Exception e) {
            log.error("{} request failed for applicationId={} - Unexpected Error: {}", operation, applicationId, e.getMessage(), e);
            return LenderApiResponse.error(INTERNAL_SERVER_ERROR, "Unexpected error: " + e.getMessage(), null);
        }
    }

    private HttpEntity<?> populateHeadersAndPayload(Object payload) throws Exception {
        HttpHeaders headers = getAuthHeader();
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        return new HttpEntity<>(payload, headers);
    }

    private HttpHeaders getAuthHeader() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(lendingPlatformTokenHandler.getAuthenticationToken());
        return headers;
    }


}
