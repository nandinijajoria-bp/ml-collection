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
        private LoanDetail loanDetail;
        private String customerID;
        private List<RepaymentSchedule> repaymentSchedule;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LoanDetail {
        private String loanID;
        private String status;
        private Date closureDate;
        private String applicationId;
        private Double interestRate;
        private String interestRateType;
        private Date startDate;
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
