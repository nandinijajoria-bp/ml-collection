package com.bharatpe.lending.loanV3.revamp.dto;

import lombok.Data;

@Data
public class NachDetail {
    private Double loanAmount;
    private Integer tenure;
    private String loanType;
    private String mandateStatus;
    private int waitTime;
    private int retryCount;
    private int pollingTime;
    private boolean retryEligible;
    private Long createdAt;
    private Double dailyInstalmentAmount;
}
