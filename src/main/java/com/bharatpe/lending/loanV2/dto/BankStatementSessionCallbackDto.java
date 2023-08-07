package com.bharatpe.lending.loanV2.dto;

import com.bharatpe.lending.common.enums.BankStatementSessionStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class BankStatementSessionCallbackDto {
    String sessionType;
    String sessionId;
    BankStatementSessionStatus status;
    String accountNo;
    String accountName;
    String accountType;
    Long period;
    String message;
    String ifscCode;
}
