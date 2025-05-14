package com.bharatpe.lending.lendingplatform.lms.dto.request;

import com.bharatpe.lending.lendingplatform.lms.enums.DueType;
import com.bharatpe.lending.lendingplatform.lms.enums.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import java.util.Date;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PenaltyRequest {
    @NotNull
    private String bpLoanId;
    @Positive
    private Integer externalLosId;
    @Positive
    private Integer externalLmsId;

    private Date date;
    @Positive
    private Integer entityId;
    @NotNull
    private DueType dueType;
    @NotNull
    private TransactionType transactionType;

    @Min(0)
    private Integer originalDueId;

    @NotNull
    private String dueDate;

    @Min(0)
    private Integer originalAmount;

    @Min(0)
    private Integer reversalDueAmount;
}
