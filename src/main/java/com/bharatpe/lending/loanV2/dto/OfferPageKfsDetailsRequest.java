package com.bharatpe.lending.loanV2.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Positive;

@Data
@AllArgsConstructor
public class OfferPageKfsDetailsRequest {

    @NotEmpty(message = "Lender is required")
    private String lender;

    @Positive(message = "Loan amount must be greater than 0")
    private Double loanAmount;

    @Min(value = 0, message = "Processing fee cannot be negative")
    private Double processingFee;
}
