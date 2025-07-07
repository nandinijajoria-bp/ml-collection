package com.bharatpe.lending.loanV3.revamp.dto;

import lombok.*;

@Data
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UdyamRegistrationStateDTO {
    private String lender;
    private Long applicationId;
    private String udyamRegistrationLink;
    private Long merchantId;
    private Boolean isTopup;
    private String externalLoanId;
    private Boolean isUdyamRequired;
    private String udyamStatus;
}