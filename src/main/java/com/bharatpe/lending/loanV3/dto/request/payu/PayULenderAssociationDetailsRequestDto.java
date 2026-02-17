package com.bharatpe.lending.loanV3.dto.request.payu;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingGstDetail;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.loanV3.dto.CKycResponseDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class PayULenderAssociationDetailsRequestDto {
    Long applicationId;
    Long merchantId;
    CKycResponseDto cKycResponseDto;

    LendingApplication lendingApplication;

    LendingGstDetail lendingGstDetail;
    LendingApplicationLenderDetails lendingApplicationLenderDetails;

    boolean modifyLender = false;
    boolean manageState = false;

    String topupParentLender;
}
