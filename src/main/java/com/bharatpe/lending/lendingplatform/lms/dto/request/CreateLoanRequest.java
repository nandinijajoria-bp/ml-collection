package com.bharatpe.lending.lendingplatform.lms.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateLoanRequest {

    @NotBlank
    private String bpLoanId;

    @NotNull
    @Valid
    private LoanDetails loanDetails;

    @NotNull
    @Valid
    private CustomerDetails customerDetails;

    @NotNull
    @Valid
    private NBFCDetails nbfcDetails;

    @NotNull
    @Valid
    private ArrayList<LoanDocuments> loanDocuments;

    @NotNull
    @Valid
    private ArrayList<CustomerReferences> customerReferences;

    @NotNull
    @Valid
    private ArrayList<ChargesList> chargesList;

    @NotNull
    @Valid
    private MandateDetails mandateDetails;

    @NotBlank
    private String productName;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ChargesList {
        private BigDecimal chargeAmount;
        private String chargeName;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class LoanDetails {
        @Positive
        private BigDecimal loanAmount;
        @Positive
        private Integer ediAmount;
        @Positive
        private BigDecimal disbursedAmount;
        @NotNull
        private Date disburseDate;
        @NotNull
        private Date instrumentDate;
        @NotNull
        private BigDecimal roi;
        @Positive
        private int loanTenure;
        @NotNull
        private Date firstDueDate;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CustomerDetails {
        @NotBlank
        private String customerId;
        @NotBlank
        private String customerAddress;
        @NotBlank
        private String customerCity;
        @NotBlank
        private String customerState;
        @Positive
        private long customerPinCode;
        @NotBlank
        private String customerBankAccNo;
        @NotBlank
        private String customerBankBranch;
        @NotBlank
        private String customerBankIFSC;
        @NotBlank
        private String customerAadharNo;
        @NotNull
        private String customerDOB;
        @NotBlank
        private String customerFatherName;
        @NotBlank
        private String customerGender;
        @NotNull
        private String customerMobileNo;
        @NotBlank
        private String customerName;
        @NotBlank
        private String customerPAN;
        @NotBlank
        private String shopAddress;
        @NotBlank
        private String shopCity;
        @Positive
        private long shopPinCode;
        @NotBlank
        private String shopState;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class NBFCDetails {
        @NotBlank
        private String nbfcId;
        @NotBlank
        private String nbfcBankAcc;
        @NotBlank
        private String nbfcBankIFSC;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class LoanDocuments {
        @NotBlank
        private String docType;

        @NotBlank
        private String docUrl;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CustomerReferences {
        @NotNull
        private String referenceContactNumber;

        @NotBlank
        private String referenceFirstName;

        @NotBlank
        private String referenceLastName;

        @NotBlank
        private String referenceRelation;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class MandateDetails {
        @NotBlank
        private String mandateAmount;

        @NotNull
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private Date mandateEndDate;

        @NotNull
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private Date mandateStartDate;

        private String mandateId;

        @NotBlank
        private String umrn;

        @NotBlank
        private String bankIFSCCode;

        @NotBlank
        private String bankName;
    }
}
