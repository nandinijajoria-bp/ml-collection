package com.bharatpe.lending.ai.services;


import com.bharatpe.lending.ai.dto.LedgerApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;


@Service
@Slf4j
public class AILedgerService {

    private final RestTemplate restTemplate;
    private final String ledgerServiceHost = "";

    public AILedgerService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public LedgerApiResponse fetchLedger(Long merchantId, String token) {
        String url = "http://localhost:9091/ai/collection/ledger?merchantId=" + merchantId;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("token", token);

        HttpEntity<String> entity = new HttpEntity(headers);
        try{
            log.info("Fetching ledger for merchantId: {}", merchantId);

            ResponseEntity<LedgerApiResponse> responseEntity =
                    restTemplate.exchange(url, HttpMethod.GET, entity, LedgerApiResponse.class);
            log.info("Received response from ledger service for merchantId: {}, is {}", merchantId, responseEntity);
            LedgerApiResponse response = responseEntity.getBody();
            if(response!=null && response.getData() !=null){
                return null;
            }
        }
        catch (RestClientException e){
            log.error("Error while fetching ledger for merchantId: {}, error: {}", merchantId, e.getMessage());
        }catch (Exception e){
            log.error("Unknown error while fetching ledger for merchantId: {}, error: {}", merchantId, e.getMessage());
        }
    }
}
