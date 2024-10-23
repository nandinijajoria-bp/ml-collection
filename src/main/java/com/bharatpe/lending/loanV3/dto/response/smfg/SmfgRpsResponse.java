package com.bharatpe.lending.loanV3.dto.response.smfg;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SmfgRpsResponse {

    private String status;
    private String message;
    private DataResponse data;
    private String dataResponse;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DataResponse {
        @JsonProperty("STATUS")
        private String status;
        @JsonProperty("MESSAGE")
        private String message;
        @JsonProperty("REPAYMENT_SUMMARY")
        private ArrayList<RepaymentSummary> repaymentSummary;
        @JsonProperty("REPAYMENT_SCHEDULE")
        private ArrayList<RepaymentSchedule> repaymentSchedule;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RepaymentSchedule {
        @JsonProperty("LOAN_TYPE")
        private String loanType;
        @JsonProperty("FREQUENCY")
        private String frequency;
        @JsonProperty("STATE")
        private String state;
        @JsonProperty("ASSET_VALUE")
        private Double assetValue;
        @JsonProperty("CUSTOMER_NAME")
        private String customerName;
        @JsonProperty("AGREEMENT_DATE")
        private String agreementDate;
        @JsonProperty("NET_DISBURSED_AMOUNT")
        private Double netDisbursedAmount;
        @JsonProperty("TOTAL_INSTALLMENT")
        private Integer totalInstallment;
        @JsonProperty("BRANCH")
        private String branch;
        @JsonProperty("AMOUNT_FINANCED")
        private Double amountFinanced;
        @JsonProperty("TENURE")
        private Integer tenure;
        @JsonProperty("INTEREST_RATE")
        private Double interestRate;
        @JsonProperty("CURRENCY")
        private String currency;
        @JsonProperty("ADVANCE_EMI")
        private Double advanceEmi;
        @JsonProperty("CITY")
        private String city;
        @JsonProperty("AGREEMENT_NO")
        private String agreementNo;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RepaymentSummary {
        @JsonProperty("DUE_TYPE")
        private String dueType;
        @JsonProperty("INSTL_AMT")
        private Integer instlAmt;
        @JsonProperty("CL_PRINCIPAL")
        private String clPrincipal;
        @JsonProperty("PROSPECTCODE")
        private String prospectcode;
        @JsonProperty("RATE_PER")
        private Double ratePer;
        @JsonProperty("INSTL_NUM")
        private Integer instlNum;
        @JsonProperty("INTEREST")
        private Double interest;
        @JsonProperty("PRINCIPAL")
        private Double principal;
        @JsonProperty("OP_PRINCIPAL")
        private Double opPrincipal;

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd-MMM-yyyy")
        @JsonProperty("DUE_DATE")
        private Date dueDate;
    }
}



