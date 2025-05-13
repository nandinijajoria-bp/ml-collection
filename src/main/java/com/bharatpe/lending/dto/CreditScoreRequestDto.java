package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreditScoreRequestDto {

    @JsonProperty("pan_number")
    private String panNumber;

    @JsonProperty("pin_code")
    private Integer pinCode;

    @JsonProperty("bureau_pull")
    private boolean bureauPull;

    private boolean skip;
}
