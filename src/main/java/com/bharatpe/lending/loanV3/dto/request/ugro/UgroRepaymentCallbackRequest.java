package com.bharatpe.lending.loanV3.dto.request.ugro;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UgroRepaymentCallbackRequest {
    private String loanId;
    private String leadId;
    private String paidAt;
    private String amount;
    private String txnId;
    private String mode;
    private String lan;
    @JsonProperty("bankRefNo")
    private String bankRefNo;
    @JsonProperty("requestId")
    private String requestId;
    private String intent;
}
