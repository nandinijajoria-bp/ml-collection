package com.bharatpe.lending.exception;

import com.bharatpe.lending.dto.GlobalLimitResponse;
import com.bharatpe.lending.loanV2.dto.LoanDetailsResponse;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BureauCallMaskedApiException extends Exception {
    private LoanDetailsResponse loanDetailsResponse;

    public BureauCallMaskedApiException(String message, LoanDetailsResponse loanDetailsResponse) {
        super(message);
        this.loanDetailsResponse = loanDetailsResponse;
    }
}
