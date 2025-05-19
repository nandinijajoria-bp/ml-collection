package com.bharatpe.lending.lendingplatform.lms.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.Date;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LoanStatusRequest {

    @NotBlank
    private String bpLoanId;

    private String externalLmsId;

    @NotBlank
    private String remarks;

    @NotNull
    private Date date;
}
