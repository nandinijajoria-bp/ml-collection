package com.bharatpe.lending.loanV3.dto.request.trillionloans;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TLReceiptRequestDto {
    private String loanId;
    private String transactionDate;
    private Boolean isTotalOutstandingInterest;
    private Boolean includePreClosureReason;
}
