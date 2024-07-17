package com.bharatpe.lending.loanV3.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ForeclosureRequestDto {
    String lender;
    String productName;
    Long applicationId;
    Payload payload;
    boolean topup;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Payload {
        String accountId;
        String dealNo;
        String loanNo;
        String uniqueId;
        @JsonProperty("loan_receipt_details")
        LoanReceiptDetails loanReceiptDetails;

    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LoanReceiptDetails {
        Double receiptAmount;
        String paidByContactNo;
        String transactionRefNumber;
        @JsonFormat(shape=JsonFormat.Shape.STRING, pattern= "yyyy-MM-dd", timezone = "Asia/Kolkata")
        Date receiptDateTime;
    }

}
