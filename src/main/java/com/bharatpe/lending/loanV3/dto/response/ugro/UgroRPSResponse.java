package com.bharatpe.lending.loanV3.dto.response.ugro;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
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
public class UgroRPSResponse {
    private String status;
    private DataDetails data;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DataDetails {
        private String product;
        private String aggregatorId;
        private String acquisitionPlatformId;
        private String leadId;
        private Integer userId;
        private String mobile;
        private String email;
        private String panNumber;
        private List<RepaymentSchedule> repaymentSchedule;
        private Integer loanId;
        private String productCode;
        private Double amount;
        private Integer tenureMonths;
        private Integer tenurePeriod;
        private String paymentFrequency;
        private Double interestPercent;
        private Double emiAmount;
        private Double installmentAmount;
        private Long emiStartDate;
        private Long installmentStartDate;
        private Long disbursalDate;
        private Double brokenInterest;
        private Double processingFee;
        private Double processingFeeGst;
        private Double convenienceFee;
        private Double convenienceFeeGst;
        private Double convenienceFeePct;
        private Double minConvenienceFee;
        private Integer lenderId;
        private Integer dpd;
        private List<Tranche> tranches;
        private String repaymentLink;
        private Double principalOutstanding;
        private Double principalDue;
        private Double interestDue;
        private Double excessAmount;
        private Double chargesDue;

        @JsonProperty("principal_paid")
        private Double principalPaid;

        @JsonProperty("interest_paid")
        private Double interestPaid;

        @JsonProperty("charge_paid")
        private Integer charge_paid;

        @JsonProperty("total_repayment")
        private Double totalRepayment;

        @JsonProperty("total_outstanding")
        private Double totalOutstanding;
        private Double availableLimit;
        private Long asOf;
        private Boolean isExpired;
        private Long expiryDate;
        private Long closureDate;
        private String closureType;
        private Integer realtimeLimit;
        private Boolean isClosed;
        private Boolean isWrittenOff;
        private Boolean isCancelled;
        private String link;
        private Epi epi;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RepaymentSchedule {
        @JsonProperty("schedule_id")
        private String scheduleId;

        @JsonProperty("sr_id")
        private String srId;
        private Long date;
        private Double interest;
        private Double principal;
        private Double balance;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Tranche {
        @JsonProperty("tranche_id")
        private String trancheId;
        private List<TrancheDue> dues;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TrancheDue {
        @JsonProperty("sr_id")
        private String srId;

        @JsonProperty("due_date")
        private Long dueDate;
        private Double principal;
        private Double interest;

        @JsonProperty("principal_paid")
        private Double principalPaid;

        @JsonProperty("interest_paid")
        private Double interestPaid;
        private Double balance;
        private String status;

        @JsonProperty("paid_date")
        private Long paidDate;

        @JsonProperty("delayed_days")
        private Integer delayedDays;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Epi {
        private Double epiTrancheAmount;
        private Double epiAmount;
        private Double epiDue;
        private Integer epiDpd;
        private List<Object> epiRepaymentSchedules; // Assuming an empty array for now
    }
}


