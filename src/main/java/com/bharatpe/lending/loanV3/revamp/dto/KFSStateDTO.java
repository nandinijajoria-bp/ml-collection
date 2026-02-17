package com.bharatpe.lending.loanV3.revamp.dto;

import com.bharatpe.lending.common.enums.MandateType;
import lombok.*;

@Data
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class KFSStateDTO {
    private LoanApplicationDetailsV3 loanApplication;
    private LoanApplicationDetailsV3 topupLoanApplication;
    private boolean repeatLoan;
    private String mobile;
    private Boolean upiAutoPayEligible;
    private String upiAutoPayMandateStatus;
    private Boolean agreementDone;
    private String lender;
    private Long merchantId;

    private MandateType mandateType;
    private boolean isUpiAutoPayDone;
    private boolean isNachDone;
}