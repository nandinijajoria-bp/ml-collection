package com.bharatpe.lending.lendingplatform.nbfc.dto.callback;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.util.Date;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DisbursalCallback {
    private long applicationId;
    private String lender;
    private String leadId;
    private boolean status;
    private String utr;
    private String loanAccountNumber;
    private BigDecimal disbursalAmount;
    private String interestRate;
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date disbursalDate;
}
