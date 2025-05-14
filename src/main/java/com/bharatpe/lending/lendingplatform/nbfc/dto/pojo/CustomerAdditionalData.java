package com.bharatpe.lending.lendingplatform.nbfc.dto.pojo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
public class CustomerAdditionalData {
    private String applicationType;
    private String aggregatedId;
    private JsonNode sources;
    private JsonNode scienapticProperties;
    private String ip;
    private String settlementType;
    private String settlementLevel;
    private float mobileLatitude;
    private float mobileLongitude;
}
