package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

import java.util.Date;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@Getter
@Setter
public class PostPayoutRequestDto {
    @NonNull
    String lender;
    @JsonProperty("amount")
    @NonNull
    Double disbursedAmount;
    @NonNull
    @JsonProperty("status")
    String loanDisbursalStatus;
    @JsonProperty("timestamp")
    Date disbursalDate;
    @NonNull
    @JsonProperty("bp_loan_id")
    String applicationId;
    String reason;
    @NonNull
    @JsonProperty("loan_id")
    String nbfcId;
    String utr;
    String urn;
    Double emi;
    Double roi;
    Integer tenure;
}
