package com.bharatpe.lending.dto;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Getter;
import lombok.Setter;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@Getter
@Setter
public class PostPayoutAuditDto {
    PostPayoutRequestDto postPayoutRequest;
    PostPayoutResponseDto postPayoutResponse;
    String lender;
    Long applicationId;
    String externalLoanId;
    Long merchantId;
    String status;
}
