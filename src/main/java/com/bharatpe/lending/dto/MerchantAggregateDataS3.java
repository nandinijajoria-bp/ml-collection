package com.bharatpe.lending.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class MerchantAggregateDataS3 {
    private Long merchantId;
    private String applicationType;
    private JsonNode sources;
    private JsonNode scienapticProperties;
    private String aggregateId;
}
