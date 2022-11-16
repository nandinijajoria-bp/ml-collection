package com.bharatpe.lending.dto;


import com.bharatpe.lending.loanV2.dto.ApiResponse;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@Getter
@Setter
@ToString
public class MamtaDecisionAuditDTO {
    NbfcDecisionCallbackRequestDTO nbfcDecisionCallbackRequestDTO;
    String externalLoanId;
    ApiResponse response;
    Date createdAt = new Date();
}
