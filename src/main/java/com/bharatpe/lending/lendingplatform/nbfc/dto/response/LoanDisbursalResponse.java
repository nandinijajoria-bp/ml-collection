package com.bharatpe.lending.lendingplatform.nbfc.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoanDisbursalResponse {
    private String loanAccountNumber;
    private String status;
    private String statusCode;
    private String loanId;
    private String errorCode;
    private boolean success;

}
