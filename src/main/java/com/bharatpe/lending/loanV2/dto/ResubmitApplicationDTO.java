package com.bharatpe.lending.loanV2.dto;

import com.bharatpe.lending.enums.LendingResubmitEnum;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

@Data
@ToString
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResubmitApplicationDTO {
    Long merchantId;
    Long applicationId;
    String resubmitReason;
    Integer resubmitCount;
    LendingResubmitEnum type;
    Double customAmount;
    String shopStructure;
}
