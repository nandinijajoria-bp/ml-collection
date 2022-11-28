package com.bharatpe.lending.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;
import lombok.ToString;

@Data
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@ToString
public class NbfcStatusApiResponseDTO {
    Boolean success;

    String message;

    String status;

    String errorCode;

    String loanId;

    String borrowerId;

    String virtualAccountNumber;
}
