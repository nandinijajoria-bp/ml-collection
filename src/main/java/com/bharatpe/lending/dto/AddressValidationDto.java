package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class AddressValidationDto {
    String message;
    String requestId;
    Result result;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
    public class Result {
        @JsonProperty(value = "address-validity")
        String addressValidity;
        @JsonProperty(value = "address-quality-score")
        Double addressQualityScore;
    }
}
