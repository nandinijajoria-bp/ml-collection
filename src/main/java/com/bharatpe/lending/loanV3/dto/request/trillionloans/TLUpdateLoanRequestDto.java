package com.bharatpe.lending.loanV3.dto.request.trillionloans;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TLUpdateLoanRequestDto {

    private String leadId;
    private String loanAmountRequested;
    private String rateOfInterest;
    private String expectedDisbursementDate;// optional
    private String dateFormat;// optional
    private String repaymentsStartingFromDate;// optional
    private String lender;
    private Integer tenure;

}