package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@AllArgsConstructor
@Data
@NoArgsConstructor
@Builder
public class LoanDetailsEligibilityAuditDto {
    String request;
    String response;
    String requestParams;
    String requestHeaders;
    String requestId;
    @JsonFormat(shape= JsonFormat.Shape.STRING, pattern= "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Kolkata")
    Date createdAt;
    Long merchantId;
    Long applicationId;
    String source;
    Double offerAmt;
    Long offerPrimaryKey;
    String eligibility;
    String rejectionReason;
    Long reapplyTimeline;
    String ineligibility;

    public LoanDetailsEligibilityAuditDto(RequestResponseAuditDto payload) {
        this.request = payload.getRequest();
        this.response = payload.getResponse();
        this.requestParams = payload.getRequestParams();
        this.requestHeaders = payload.getRequestHeaders();
        this.createdAt = new Date();
        this.requestId = payload.getRequestId();
    }
}
