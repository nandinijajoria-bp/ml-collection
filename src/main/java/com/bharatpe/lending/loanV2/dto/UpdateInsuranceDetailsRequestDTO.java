package com.bharatpe.lending.loanV2.dto;

import com.bharatpe.lending.dto.LoanInsuranceDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdateInsuranceDetailsRequestDTO {
    private Long applicationId;
    private LoanInsuranceDTO insuranceDetails;
}
