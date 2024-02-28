package com.bharatpe.lending.loanV3.dto.request.usfb;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReceiptRequestDTO {
    String leadId;
    Double amount;
    Integer paymentMode;
    String paymentDate;
    String paymentId;
    String paymentAccount;
    @JsonProperty("is_foreclosure")
    Boolean isForeclosure;
    @JsonProperty("is_cancelled")
    Boolean isCancelled;
    @JsonProperty("is_settled")
    Boolean isSettled;
    @JsonProperty("is_closed")
    Boolean isClosed;
    Date bureauDate;
    List<Allocation> allocation;

    //Trillions
    @JsonProperty("transaction_date")
    String transactionDate;

    @JsonProperty("loan_accounts")
    String loanAccounts;

    @JsonProperty("is_total_outstanding_interest")
    Boolean isTotalOutstandingInterest;

    @JsonProperty("include_pre_closure_reason")
    Boolean includePreClosureReason;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
    public static class Allocation {
        Date dueDate;
        String uuid;
        Double amount;
        Integer type;
    }
}
