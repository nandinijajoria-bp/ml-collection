package com.bharatpe.lending.loanV3.dto.piramal;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.loanV3.dto.CKycResponseDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class LenderAssociationDetailsDto {
    Long applicationId;
    Long merchantId;
    CKycResponseDto cKycResponseDto;

    LendingApplication lendingApplication;
    LendingApplicationLenderDetails lendingApplicationLenderDetails;
}
