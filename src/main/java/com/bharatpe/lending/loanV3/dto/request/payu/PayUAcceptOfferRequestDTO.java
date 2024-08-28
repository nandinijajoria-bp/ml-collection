package com.bharatpe.lending.loanV3.dto.request.payu;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PayUAcceptOfferRequestDTO {

    @JsonProperty("application-id")
    private String applicationId;

    private Double amount;
    private Integer tenure;

    @JsonProperty("tenure_metric")
    private String tenureMetric;

    private Double roi;

    @JsonProperty("roi_type")
    private String roiType;

    private List<Charge> charges;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Charge {

        private String type;

        private Integer value;

        @JsonProperty("value_type")
        private String valueType;

        @JsonProperty("is_gst_included")
        private Boolean isGstIncluded;

    }

}
