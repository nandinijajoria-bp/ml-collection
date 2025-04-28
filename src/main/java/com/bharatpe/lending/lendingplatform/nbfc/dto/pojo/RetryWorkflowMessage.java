package com.bharatpe.lending.lendingplatform.nbfc.dto.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RetryWorkflowMessage {
    private Long applicationId;
    private Long lenderWorkflowRetryId;
}
