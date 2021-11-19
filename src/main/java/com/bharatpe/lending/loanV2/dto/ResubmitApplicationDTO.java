package com.bharatpe.lending.loanV2.dto;

import com.bharatpe.lending.enums.LendingResubmitEnum;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

import java.util.List;

@Data
@ToString
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResubmitApplicationDTO {
    Long merchantId;
    Long applicationId;
    List<String> resubmitReason;
    LendingResubmitEnum type;
}
