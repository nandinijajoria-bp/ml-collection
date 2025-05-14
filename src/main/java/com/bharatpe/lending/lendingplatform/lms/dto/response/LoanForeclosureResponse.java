package com.bharatpe.lending.lendingplatform.lms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoanForeclosureResponse {

    private String bpLoanId;
    private String externalLmsId;
    private Long applicationId;
    private Date date;
    private String message;
}
