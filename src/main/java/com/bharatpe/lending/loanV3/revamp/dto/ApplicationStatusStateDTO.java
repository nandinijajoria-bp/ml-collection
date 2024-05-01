package com.bharatpe.lending.loanV3.revamp.dto;


import lombok.*;

@Data
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApplicationStatusStateDTO {
    private Long applicationId;
    private String transferDays;
    private String enachDeeplink;
    private String reapply;
    private Boolean enachBank;
    private Long reapplyTime;
    private Long reapplyTimeEpoch;
    private Boolean enachDone;
    private String lender;
}
