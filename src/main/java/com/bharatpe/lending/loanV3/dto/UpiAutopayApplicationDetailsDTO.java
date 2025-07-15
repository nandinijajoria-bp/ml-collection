package com.bharatpe.lending.loanV3.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Data
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpiAutopayApplicationDetailsDTO {
    private Double loanAmount;
    private Integer tenure;
    private String loanType;
    private String mandateStatus;
    private Long createdAt;
    private Long waitTime;
    private Integer retryCount;
    private Integer pollingTime;
    private String errorCode;
    private String errorReason;
    private String displayMessage;
    private Boolean retryEligible;
}
