package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class ENachSubmitRequestDTO {

    private Long mandateId;

    private Long identifier;

    private Long applicationId;

    private Boolean status;

    private String response;

    private Long transactionIdentifier;

    public Long getMandateId() {
        return mandateId;
    }

    public void setMandateId(Long mandateId) {
        this.mandateId = mandateId;
    }

    public Long getIdentifier() {
        return identifier;
    }

    public void setIdentifier(Long identifier) {
        this.identifier = identifier;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public Boolean getStatus() {
        return status;
    }

    public void setStatus(Boolean status) {
        this.status = status;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    public Long getTransactionIdentifier() {
        return transactionIdentifier;
    }

    public void setTransactionIdentifier(Long transactionIdentifier) {
        this.transactionIdentifier = transactionIdentifier;
    }

    @Override
    public String toString() {
        return "ENachSubmitRequestDTO{" +
                "mandateId=" + mandateId +
                ", identifier=" + identifier +
                ", applicationId=" + applicationId +
                ", status=" + status +
                ", response='" + response + '\'' +
                ", transactionIdentifier=" + transactionIdentifier +
                '}';
    }
}
