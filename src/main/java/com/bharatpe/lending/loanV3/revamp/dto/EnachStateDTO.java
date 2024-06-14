package com.bharatpe.lending.loanV3.revamp.dto;

import com.bharatpe.lending.dto.EnachErrorMessageDTO;
import com.bharatpe.lending.loanV2.dto.BankAccountDetails;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;


@Data
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EnachStateDTO {


    private BankAccountDetails bankDetails;
    private String applicationId;

    private String enachDeeplink;
    private String enachMode;
    private Boolean enachDone;
    private EnachErrorMessageDTO enachErrorResponse;

    private Long nachStartedAt;
    private String nachSessionStatus;
    private String nachSessionMode;
    private boolean isTopup;
    private String lender;
}
