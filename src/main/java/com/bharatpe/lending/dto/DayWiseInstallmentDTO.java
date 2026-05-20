package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonPropertyOrder({
        "dt", "schAmt", "dispAmt", "status", "paidOn",
        "paidAmt", "applied", "rem", "prevDue", "dpd",
        "overdueSinceDt", "overdueEndDt", "excAmt", "settledUntilDt"
})
public class DayWiseInstallmentDTO {

    @JsonProperty("dt")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd", timezone = "Asia/Kolkata")
    private Date date;

    @JsonProperty("schAmt")
    private Integer scheduledEdiAmount;

    @JsonProperty("dispAmt")
    private Integer displayDueAmount;

    @JsonProperty("status")
    private String status;

    @JsonProperty("paidOn")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd", timezone = "Asia/Kolkata")
    private Date paidOnDate;

    @JsonProperty("paidAmt")
    private Integer paidAmount;

    @JsonProperty("applied")
    private Integer appliedToThisEdi;

    @JsonProperty("rem")
    private Integer remainingForThisEdi;

    @JsonProperty("prevDue")
    private Integer previousAmtDue;

    @JsonProperty("dpd")
    private Integer dpd;

    @JsonProperty("overdueSinceDt")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd", timezone = "Asia/Kolkata")
    private Date overdueSinceDt;

    @JsonProperty("overdueEndDt")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd", timezone = "Asia/Kolkata")
    private Date overdueEndDt;

    @JsonProperty("excAmt")
    private Integer excessAmt;

    @JsonProperty("settledUntilDt")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd", timezone = "Asia/Kolkata")
    private Date settledUntilDt;

    @JsonProperty("isPartiallyPaid")
    private Boolean isPartiallyPaid;

    @JsonProperty("showExcessMessage")
    private Boolean showExcessMessage;
}