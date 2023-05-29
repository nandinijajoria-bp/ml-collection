package com.bharatpe.lending.dto;

public class EnachInitiateRequestDTO {

    private String token;
    private Long merchantId;
    private Long applicationId;
    private String clientName = "LENDING";
    private String nachAmount;
    private String enachProvider;
    private String lender;



    private String nachMode;

    public EnachInitiateRequestDTO(String token, Long merchantId, Long applicationId, String nachAmount, String enachProvider) {
        this.token = token;
        this.merchantId = merchantId;
        this.applicationId = applicationId;
        this.nachAmount = nachAmount;
        this.enachProvider = enachProvider;
    }

    public EnachInitiateRequestDTO(String token, Long merchantId, Long applicationId, String nachAmount, String enachProvider, String lender, String nachMode) {
        this.token = token;
        this.merchantId = merchantId;
        this.applicationId = applicationId;
        this.nachAmount = nachAmount;
        this.enachProvider = enachProvider;
        this.lender = lender;
        this.nachMode = nachMode;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Long getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(Long merchantId) {
        this.merchantId = merchantId;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public String getNachAmount() {
        return nachAmount;
    }

    public void setNachAmount(String nachAmount) {
        this.nachAmount = nachAmount;
    }

    public String getEnachProvider() {
        return enachProvider;
    }

    public String getLender() {
        return lender;
    }

    public void setLender(String lender) {
        this.lender = lender;
    }

    public void setEnachProvider(String enachProvider) {
        this.enachProvider = enachProvider;
    }

    public String getNachMode() {
        return nachMode;
    }

    public void setNachMode(String nachMode) {
        this.nachMode = nachMode;
    }

    @Override
    public String toString() {
        return "EnachInitiateRequestDTO{" +
                "token='" + token + '\'' +
                ", merchantId=" + merchantId +
                ", applicationId=" + applicationId +
                ", clientName='" + clientName + '\'' +
                ", nachAmount='" + nachAmount + '\'' +
                ", enachProvider='" + enachProvider + '\'' +
                ", lender='" + lender + '\'' +
                ", nachMode='" + nachMode + '\'' +
                '}';
    }
}
