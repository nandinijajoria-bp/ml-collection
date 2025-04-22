package com.bharatpe.lending.lendingplatform.underwriting.dto.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OfferDetail {
    private Double interestRate;
    private Double initialRoi;
    private Double maxLoanAmount;
    private Double loanAmount;
    private Double bureauLimit;
    private Double ntcLimit;
    private Double gstLimit;
    private Double bankStatementLimit;
    private Double gst3bLimit;
    private Integer tenure;
    private Integer ediCount;
    private Double processingFee;
    private Double clubV2Amount;
    private String pilotIdentifier;
    private boolean gstAffectedOffer;
    private boolean bankAffectedOffer;
    private boolean gst3bAffectedOffer;

}
