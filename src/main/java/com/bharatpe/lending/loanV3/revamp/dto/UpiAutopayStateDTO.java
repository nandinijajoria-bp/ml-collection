package com.bharatpe.lending.loanV3.revamp.dto;

import com.bharatpe.lending.loanV2.dto.BankAccountDetails;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import lombok.*;

import java.util.Date;

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
    private boolean isCurrentLoanActive;
    private boolean isActiveApplicationAutoPaySetupFlow;
    private Double dailyInstalmentAmount;
    private Date mandateEndDate;
    private Double maxMandateAmount;
    private LendingViewStates autoPaySuccessNextState;

}
