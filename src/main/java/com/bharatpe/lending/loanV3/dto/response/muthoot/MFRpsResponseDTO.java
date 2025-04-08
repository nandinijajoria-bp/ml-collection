package com.bharatpe.lending.loanV3.dto.response.muthoot;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MFRpsResponseDTO {
    private ResponseData data;
    private String statusCode;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ResponseData {
        private LoanDetails loanDetails;
        private String customerID;
        private List<RepaymentSchedule> repaymentSchedule;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LoanDetails {
        private Double annualPercentageRate;
        private String applicationID;
        private Double disbursedAmount;
        private Double ediAmount;
        private Double interestAmount;
        private Double interestRate;
        private String interestRateType;
        private Double loanAmount;
        private String loanID;
        private Double monthlyInterestRate;
        private Integer processingFeeAmount;
        private Double processingFeeRate;
        private Integer status;
        private Integer tenureInDays;
        private Integer tenureInMonths;
        private Double totalPayableAmount;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RepaymentSchedule {
        private Double amount;
        private Double closingBalance;
        private Component components;
        private Date dueDate;
        private Integer installmentNumber;
        private Double interestRate;
        private String interestRateType;
        private Double netPayableAmount;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Component {
        private Double interest;
        private Double principal;
    }

}
