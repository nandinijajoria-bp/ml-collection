package com.bharatpe.lending.lendingplatform.lms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LenderForeclosureDetailsResponse {
    private double foreclosureAmount;
    private String bpLoanId;
    private String lender;
    private String applicationId;
}
