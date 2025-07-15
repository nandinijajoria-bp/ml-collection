package com.bharatpe.lending.dto.vkyc.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VkycStatusResponseDto {
    private String leadId;
    private String sessionId;
    private Status sessionStatus;
    private Date lastUpdatedAt;
    private Date userScheduledStartAt;
    private Date userScheduledEndAt;
    private String reason;
    private Boolean leadRejected;

    public enum Status {
        NOT_INITIATED, IN_PROGRESS, USER_SCHEDULED, CALL_STARTED, CALL_COMPLETED, AGENT_CALL_ENDED, NEEDS_REVIEW,
        AUTO_DECLINED, MANUALLY_DECLINED, MANUALLY_APPROVED
    }

}
