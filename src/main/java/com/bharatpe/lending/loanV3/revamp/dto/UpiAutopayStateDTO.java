package com.bharatpe.lending.loanV3.revamp.dto;

import com.bharatpe.lending.loanV2.dto.BankAccountDetails;
import lombok.*;

@Data
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpiAutopayStateDTO {
    private BankAccountDetails bankDetails;
    private Long applicationId;

    private boolean isTopup;
    private LoanApplicationDetailsV3 loanApplication;
    private String lender;
    private Double loanAmount;
    private Integer tenure;
    private Long merchantId;
    private String loanType;
    private String upiDeeplink;
    private String mandateStatus;
    private Long createdAt;
    private Long waitTime;
    private Integer retryCount;
    private String errorCode;
    private String errorReason;
    private String displayMessage;
    private Boolean retrySuggested;
    private Integer pollingTime;
    private boolean isUpiAutopayMandateEligible;
    private boolean isEnachEligible;
    private boolean isCurrentLoanActive;
    private boolean isActiveApplicationAutoPaySetupFlow;
}
