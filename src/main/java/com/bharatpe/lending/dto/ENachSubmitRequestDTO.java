package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class ENachSubmitRequestDTO {

    private String mandateId;

    private String identifier;

    private Long applicationId;

    private Boolean status;

    private String response;

    private Long transactionIdentifier;

    private String statusMessage;

    private String provider;

    private Boolean newApp=false;

    private String lender;

//    public Long getMandateId() {
//        return mandateId;
//    }
//
//    public void setMandateId(Long mandateId) {
//        this.mandateId = mandateId;
//    }
    
    
    
    
    public String getIdentifier() {
        return identifier;
    }

    public String getMandateId() {
		return mandateId;
	}

	public void setMandateId(String mandateId) {
		this.mandateId = mandateId;
	}

	public void setIdentifier(String identifier) {
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

    public String getStatusMessage() {
        return statusMessage;
    }

    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public Boolean getNewApp() {
        return newApp;
    }

    public void setNewApp(Boolean newApp) {
        this.newApp = newApp;
    }

    public String getLender() {
        return lender;
    }

    public void setLender(String lender) {
        this.lender = lender;
    }

    @Override
    public String toString() {
        return "ENachSubmitRequestDTO{" +
                "mandateId='" + mandateId + '\'' +
                ", identifier='" + identifier + '\'' +
                ", applicationId=" + applicationId +
                ", status=" + status +
                ", response='" + response + '\'' +
                ", transactionIdentifier=" + transactionIdentifier +
                ", statusMessage='" + statusMessage + '\'' +
                ", provider='" + provider + '\'' +
                ", newApp=" + newApp +
                ", lender='" + lender + '\'' +
                '}';
    }
}
