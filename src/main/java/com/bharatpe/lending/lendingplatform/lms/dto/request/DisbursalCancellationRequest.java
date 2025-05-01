package com.bharatpe.lending.lendingplatform.lms.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Positive;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DisbursalCancellationRequest {

    @NotBlank
    private String bpLoanId;

    @NotBlank
    private String externalLmsId;

    @NotBlank
    private String date;

    @NotBlank
    private String remarks;

    @Positive
    private int loanDisbursalId;
}
