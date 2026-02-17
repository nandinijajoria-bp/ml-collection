package com.bharatpe.lending.loanV3.revamp.dto;

import com.bharatpe.lending.dto.EnachErrorMessageDTO;
import com.bharatpe.lending.loanV2.dto.BankAccountDetails;
import lombok.*;

import java.util.List;


@Data
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EnachStateDTO {


    private BankAccountDetails bankDetails;
    private String applicationId;

    private String enachDeeplink;
    private NachDetail nachDetail;
    private List<EnachModeDTO> enachModes;
    private Boolean enachDone;
    private EnachErrorMessageDTO enachErrorResponse;

    private Long nachStartedAt;
    private String nachSessionStatus;
    private String nachSessionMode;
    private boolean isTopup;
    private String lender;
    private Long merchantId;
    private boolean isPaymentBank;
    private boolean hasLinkedPaymentBank;
    private boolean isNativeMandateRequired;
    private boolean isCurrentLoanActive;
    private Double loanAmount;
    private Double maxMandateAmount;
}
