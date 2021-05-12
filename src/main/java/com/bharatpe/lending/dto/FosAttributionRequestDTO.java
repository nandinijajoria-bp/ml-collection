package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.Date;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class FosAttributionRequestDTO {

    private Long merchantId;
    private String fseName;
    private String fseRefcode;
    private String visitId;
    private String taskId;
    private String taskUuid;
    private Date taskStartedAt;
    private Date taskCompletedAt;
    private String requestId;

    public Long getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(Long merchantId) {
        this.merchantId = merchantId;
    }

    public String getFseName() {
        return fseName;
    }

    public void setFseName(String fseName) {
        this.fseName = fseName;
    }

    public String getFseRefcode() {
        return fseRefcode;
    }

    public void setFseRefcode(String fseRefcode) {
        this.fseRefcode = fseRefcode;
    }

    public String getVisitId() {
        return visitId;
    }

    public void setVisitId(String visitId) {
        this.visitId = visitId;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getTaskUuid() {
        return taskUuid;
    }

    public void setTaskUuid(String taskUuid) {
        this.taskUuid = taskUuid;
    }

    public Date getTaskStartedAt() {
        return taskStartedAt;
    }

    public void setTaskStartedAt(Date taskStartedAt) {
        this.taskStartedAt = taskStartedAt;
    }

    public Date getTaskCompletedAt() {
        return taskCompletedAt;
    }

    public void setTaskCompletedAt(Date taskCompletedAt) {
        this.taskCompletedAt = taskCompletedAt;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}