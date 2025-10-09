package com.bharatpe.lending.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoanDetails {
    private double loanAmount;
    private double paidAmount;
    private String status;
    private List<LedgerEntry> penaltyLedger;
    private List<LedgerEntry> lendingLedger;
}
