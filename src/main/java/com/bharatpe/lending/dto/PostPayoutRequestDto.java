package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;

import java.util.Date;


@Getter
@Setter
@ToString
@Data
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@AllArgsConstructor
@NoArgsConstructor
public class PostPayoutRequestDto {
    @JsonProperty("lender")
    String lender;
    @JsonProperty("amount")
    Double disbursedAmount;
    // only to be consumed for LL
    @JsonProperty("disbursed_amount")
    Double disbursalAmountLL;
    @JsonProperty("status")
    String loanDisbursalStatus;
    @JsonProperty("timestamp")
    @JsonFormat(shape= JsonFormat.Shape.STRING, pattern= "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Kolkata")
    Date disbursalDate;
    @JsonProperty("bp_loan_id")
    String applicationId;
    @JsonProperty("reason")
    String reason;
    @JsonProperty("loan_id")
    String nbfcId;
    @JsonProperty("utr")
    String utr;
    @JsonProperty("urn")
    String urn;
    @JsonProperty("emi")
    Double emi;
    @JsonProperty("roi")
    Double roi;
    @JsonProperty("tenure")
    Integer tenure;
}
