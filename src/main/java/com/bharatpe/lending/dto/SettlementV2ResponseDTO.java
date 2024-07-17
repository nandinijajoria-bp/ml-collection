package com.bharatpe.lending.dto;

import java.util.List;

public class SettlementV2ResponseDTO {

    private Boolean success = true;
    private String message;
    private String lender;
    private List<Settlement> settlement;

    private Boolean isInsured;
    private String insuranceProvider;


    public SettlementV2ResponseDTO(Boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public SettlementV2ResponseDTO() {
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<Settlement> getSettlement() {
        return settlement;
    }

    public void setSettlement(List<Settlement> settlement) {
        this.settlement = settlement;
    }

    public String getLender() {
        return lender;
    }

    public void setLender(String lender) {
        this.lender = lender;
    }

    public Boolean getInsured() {
        return isInsured;
    }

    public void setInsured(Boolean insured) {
        isInsured = insured;
    }

    public String getInsuranceProvider() {
        return insuranceProvider;
    }

    public void setInsuranceProvider(String insuranceProvider) {
        this.insuranceProvider = insuranceProvider;
    }

    @Override
    public String toString() {
        return "SettlementResponseDTO{" +
                "success=" + success +
                ", message='" + message + '\'' +
                ", settlement=" + settlement +
                '}';
    }

    public static class Settlement {
        private String date;
        private Double paidAmount;
        private Double dueAmount;

        public Settlement(String date, Double paidAmount, Double dueAmount) {
            this.date = date;
            this.paidAmount = paidAmount;
            this.dueAmount = dueAmount;
        }

        public Settlement() {
        }

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }

        public Double getPaidAmount() {
            return paidAmount;
        }

        public void setPaidAmount(Double paidAmount) {
            this.paidAmount = paidAmount;
        }

        public Double getDueAmount() {
            return dueAmount;
        }

        public void setDueAmount(Double dueAmount) {
            this.dueAmount = dueAmount;
        }
    }
}
