package com.bharatpe.lending.loanV3.dto.response.trillionloans;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TLBreResponseDto {

    private Boolean success;
    private String transactionId;
    private String message;
    private Double loanAmount;
    private String roi;
    private String tenure;
}
