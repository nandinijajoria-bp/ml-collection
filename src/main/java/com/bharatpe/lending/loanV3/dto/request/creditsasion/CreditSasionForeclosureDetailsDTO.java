package com.bharatpe.lending.loanV3.dto.request.creditsasion;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreditSasionForeclosureDetailsDTO {

    private String partnerLoanId;
    private String closureDate;
}
