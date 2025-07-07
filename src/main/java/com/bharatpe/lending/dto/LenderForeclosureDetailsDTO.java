package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LenderForeclosureDetailsDTO {
    Double foreclosureAmount;
    Double principalOutstanding;

    public static LenderForeclosureDetailsDTO buildEmptyResponse(){
        return LenderForeclosureDetailsDTO.builder()
                .foreclosureAmount(0d)
                .principalOutstanding(0d)
                .build();
    }
}

