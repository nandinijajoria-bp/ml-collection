package com.bharatpe.lending.handlers;


import com.bharatpe.lending.dto.ExperimentConfigResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Objects;

@Component
@Slf4j
public class LaunchLabsHandler {

    @Value("${launch.labs.base.url:http://launch-labs-prod2-service}")
    private String BASE_URL;

    @Autowired
    @Qualifier("ExperimentConfigTemplate")
    private RestTemplate restTemplate;

    /**
     * Retrieves experiment configuration from Launch Labs API for a specific experiment and merchant.
     *
     * @param experimentId The ID of the experiment. This is will be generated from experimentation dashboard by the user/PM creating the experiment. All the logic of weather a merhcant is to be included/excluded will be configured on this dashboard itself.
     * @param merchantId The ID of the merchant.
     * @return ExperimentConfigResponseDTO containing experiment variant.
     */

    /**
     * NOTE: Please set read timeout for this request to ~100ms
     */

    public ExperimentConfigResponseDTO experimentConfig(Long experimentId, Long merchantId) {
        try {
            log.info("Calling Launch Labs experiment config API for experimentId: {}, merchantId: {}", experimentId, merchantId);

            String url = BASE_URL + "/launch-labs/v1/experiment/config/" + experimentId + "/" + merchantId;

            // Make API request
            ResponseEntity<ExperimentConfigResponseDTO> apiResponse = restTemplate.getForEntity(url, ExperimentConfigResponseDTO.class);
            log.info("Experiment config API response body: {}", apiResponse.getBody());

            return apiResponse.getBody();

            /*
                Parse the response and return applicable experiment variant.
                Response body will be a json value which tells the experiment variant to  be used
                Sample response:
                    {
                       "variationId": "-1"
                    }
                variationId -1 means this experiment is not applicable for this merchant
                variationId 1 means this experiment is applicable for this merchant
             */
        } catch (Exception ex) {
            log.error("Exception occurred while fetching experiment config from Launch Lab for experimentId: {}, ex: ", experimentId, ex);

            // Throw a RootException with a generic error code
        }
        return null;
    }

    public boolean checkIfFeatureIncludedInRollout(Long experimentId, Long merchantId) {
        try {
            ExperimentConfigResponseDTO experimentResponse = experimentConfig(experimentId, merchantId);
            log.info("Experiment response: {}", experimentResponse);
            return Objects.nonNull(experimentResponse) && experimentResponse.getVariationId().equalsIgnoreCase("1");
        }catch (Exception e){
            log.error("Exception occurred when calling launch labs api for merchantId {} and experimentId {}", merchantId, experimentId, e);
        }
        return false;
    }

}