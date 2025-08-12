package com.bharatpe.lending.loanV3.revamp.dto;

import com.bharatpe.common.entities.Experian;
import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.enums.KycStatus;
import com.bharatpe.lending.loanV2.dto.BankAccountDetails;
import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OfferEvaluationRequestDTO {
    private BasicDetailsDto merchant;
    private String merchantId;
    private BankAccountDetails accountDetails;
    private KycStatus kycStatus;
    private Experian experianData;
    private LendingApplication lendingApplication;
    private List<String> previousLenders;
    private Boolean isRepeatLoan;
    private String lenderAggregationScreen;
}