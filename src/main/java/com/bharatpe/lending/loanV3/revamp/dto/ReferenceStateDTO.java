package com.bharatpe.lending.loanV3.revamp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ReferenceStateDTO {
    private boolean isDummyMerchant;

    private String applicationStatus;

}
