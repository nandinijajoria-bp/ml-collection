package com.bharatpe.lending.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LedgerEntry {
    private String createdAt;
    private double amount;
    private double penaltyAmount;
    private double paidAmount;
}
