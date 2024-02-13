package com.bharatpe.lending.loanV3.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
public class DisbursalCallbackCommonDTO {
    Long applicationId;
    String lender;
    String leadId;
    Boolean status;
    String utr;
    String lan;
    Double disbursalAmount;
    String interestRate;
    Date disbursalDate;
}
