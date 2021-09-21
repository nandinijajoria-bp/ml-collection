package com.bharatpe.lending.dto;

import com.bharatpe.lending.enums.WaiverType;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;
import lombok.ToString;

@Data
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@ToString
public class LoanSettlementRequestDTO {
    Long loanId;
    Long merchantId;
    WaiverType waiverType;
    Long crmUserId;
}
