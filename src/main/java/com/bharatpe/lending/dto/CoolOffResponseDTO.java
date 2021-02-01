package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
@Setter
@ToString
@NoArgsConstructor
public class CoolOffResponseDTO {
    private boolean isEligible;
    private String coolOffPeriod = "24";
    private boolean showOrderQr;
    @JsonProperty(value = "panCard")
    private String pancard;
    private Integer pincode;

    public CoolOffResponseDTO(boolean isEligible, boolean showOrderQr, String pancard, Integer pincode) {
        this.isEligible = isEligible;
        this.showOrderQr = showOrderQr;
        this.pancard = pancard;
        this.pincode = pincode;
    }
}
