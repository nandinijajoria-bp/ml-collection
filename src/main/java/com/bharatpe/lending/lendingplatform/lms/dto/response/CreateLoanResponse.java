package com.bharatpe.lending.lendingplatform.lms.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor

@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateLoanResponse {

    private String bpLoanId;
    private String externalLmsId;
    private String externalLosId;
    private String entityId;
    private String loanNo;
    private String loanDisbursalId;
}
