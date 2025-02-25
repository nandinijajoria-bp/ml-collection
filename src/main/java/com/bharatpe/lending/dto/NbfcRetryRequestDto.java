package com.bharatpe.lending.dto;

import lombok.Builder;
import lombok.Data;
import lombok.ToString;

@Data
@Builder
@ToString
public class NbfcRetryRequestDto {
    private Long merchantId;
    private Long applicationId;
    private String lender;
    private String requestType;
    private String retryId;
}
