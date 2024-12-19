package com.bharatpe.lending.loanV3.revamp.dto;

import lombok.*;

import java.util.List;

@Data
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MaskedMobileDTO {
    private List<String> maskedMobileList;
    private boolean retryLimitExhausted;
}
