package com.bharatpe.lending.loanV3.dto.response.muthoot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MuthootPreQualificationResponse {
    private MuthootPreQualificationResponseData data;
    private String error;
    private String statusCode;
}
