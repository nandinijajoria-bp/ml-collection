package com.bharatpe.lending.loanV3.dto.request.muthoot;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MuthootPreQualificationRequest {
    private String customerId;
    private String program;
}
