package com.bharatpe.lending.dto;

public class ENachSubmitRequestDTO {

//    "status" : true/false,
//            "loan_id" : 1234
//            "response" : full response from sdk
//    "transaction_identifier" : 1
//            "identifier" : 122
//            "mandate_id" : 12

    private Long mandateId;
    private Long identifier;
    private Long applicationId;
    private Boolean status;
    private String response;

    public ENachSubmitRequestDTO() {
    }

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
}
