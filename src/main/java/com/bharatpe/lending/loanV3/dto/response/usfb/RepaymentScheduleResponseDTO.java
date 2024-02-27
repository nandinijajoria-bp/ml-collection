package com.bharatpe.lending.loanV3.dto.response.usfb;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class RepaymentScheduleResponseDTO {
    Boolean isSuccess;
    ErrorResponseDTO error;
    ResponseDTO data;


    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
    public static class ResponseDTO {
       String status;
       Collection collection;
       Integer goalseekAmount;
       LoanDetails loanDetails;
       List<Repayment> repayments;

    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
    public static class Collection {
        Double principal;
        Double interest;
        Double bounceCharges;
        Double lateInterest;
        Double excess;
        Double accruedInterest;
        Double foreclosureFeeTax;
        Double foreclosureFee;
        Double waivedOff;
        Double refund;
        Double tdsCharges;
        Double dpd;
        Double total;
        Double outstanding;
        Double totalOutstanding;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
    public static class Repayment {
        String dueDate;
        String status;
        Double openingBalance;
        Double repaymentAmount;
        Double principalAmount;
        Double interestAmount;
        Double remainingPrincipal;
        Double remainingInterest;
        Integer repaymentNumber;
        Double closingBalance;
        PaymentDetails paymentDetails;
        String uuid;
        Object trancheId;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
    public static class LoanDetails {
        Double emiAmount;
        Double interestRate;
        Double cashback;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
    public static class PaymentDetails {
        String transactionTime;
        String paymentType;
        String paymentId;
    }
}
