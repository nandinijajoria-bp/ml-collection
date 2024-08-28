package com.bharatpe.lending.loanV3.dto.response.payu;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PayUForeclosureDetailsResponseDTO {

    private Double loanId;

    private Integer amount;

    private Integer principalPortion;

    private Integer interestPortion;

    private Integer feeChargesPortion;

    private Integer penaltyChargesPortion;

    private Integer outstandingLoanBalance;

    private Integer amountOrPercentage;

    private String value;

    private List<Integer> Date;

    private List<Integer> valueDate;

}
