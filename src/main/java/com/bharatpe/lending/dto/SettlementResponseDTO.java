package com.bharatpe.lending.dto;

import java.util.Date;
import java.util.List;

public class SettlementResponseDTO {

    private Boolean success = true;
    private String message;
    private List<Settlement> settlement;

    public SettlementResponseDTO(Boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public SettlementResponseDTO() {
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

    @Override
    public String toString() {
        return "SettlementResponseDTO{" +
                "success=" + success +
                ", message='" + message + '\'' +
                ", settlement=" + settlement +
                '}';
    }

    public static class Settlement {
        private Date date;
        private Double amount;
        private String mode;

        public Settlement(Date date, Double amount, String mode) {
            this.date = date;
            this.amount = amount;
            this.mode = mode;
        }

        public Settlement() {
        }

        public Date getDate() {
            return date;
        }

        public void setDate(Date date) {
            this.date = date;
        }

        public Double getAmount() {
            return amount;
        }

        public void setAmount(Double amount) {
            this.amount = amount;
        }

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }
    }
}
