package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class MerchantResponseDTO{
    private String status;

    private Double bpScore;

    private String category;

    @JsonProperty(value = "tpv_2mon")
    private Double tpv2Mon;

    @JsonProperty(value = "tpv_3mon")
    private Double tpv3Mon;

    private Long merchantId;

    private Double adjustedTpv;

    @JsonProperty(value = "last_txn_date")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastTransactionDate;

    @JsonProperty(value = "txn_days_3mon")
    private Integer txnDays3Mon;

    @JsonProperty(value = "txn_days_6mon")
    private Integer txnDays6Mon;

    @JsonProperty(value = "first_txn_date")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private Date firstTransactionDate;

    private String fraudCustomer;

    @JsonProperty(value = "topup_eligible")
    private Integer topUpEligible;

    @JsonProperty(value = "total_tpv_1mon")
    private Integer totalTpv1Month;

    @JsonProperty(value = "total_txns_1mon")
    private Integer totalTxns1Month;

    @JsonProperty(value = "total_txns_2mon")
    private Integer totalTxns2Month;

    @JsonProperty(value = "total_txns_3mon")
    private Integer totalTxns3Month;

    @JsonProperty(value = "average_tpv_6mon")
    private Double averageTpv6Mon;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updatedAt;

    private Integer dailyTxnCount;

    private Double dailyTxnAmount;

    private Double avgTpv;

    private Double averageTpv;

    private Double eligibleLoanAmount;

    private String eligibleLoanTenure;

    private String loanType;

    @JsonProperty(value = "txn_day_count_1mon")
    private Integer txnDayCount1Mon;

    @JsonProperty(value = "txn_day_count_2mon")
    private Integer txnDayCount2Mon;

    @JsonProperty(value = "txn_day_count_3mon")
    private Integer txnDayCount3Mon;

    @JsonProperty(value = "tpv_1mon")
    private Double tpv1Mon;

    private Integer totalLoansCount;

    @JsonProperty(value = "unique_customer_1mon")
    private Integer uniqueCustomer1mon;
    @JsonProperty(value = "self_txn_count_1mon")
    private Integer selfTxnCount1Mon;
    @JsonProperty(value = "self_txn_value_1mon")
    private Double selfTxnValue1Mon;

}
