package com.bharatpe.lending.loanV3.dto.response.trillionloans;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanDetailsResponseDTO {

    private Double amount;
    private Double netForeclosureAmount;
    private Double principalPortion;
    private Double interestPortion;
    private Double feeChargesPortion;
    private Double penaltyChargesPortion;
    private Double outstandingLoanBalance;
    private Boolean manuallyReversed;

}