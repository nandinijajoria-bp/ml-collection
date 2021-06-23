package com.bharatpe.lending.dto;

import com.amazonaws.services.dynamodbv2.xspec.S;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ApplicationStatusResponseDTO {

    private HeaderDTO header;

//    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
    public static class HeaderDTO{
        private String title;
        private String comment;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getComment() {
            return comment;
        }

        public void setComment(String comment) {
            this.comment = comment;
        }
    }

    @JsonProperty(value = "loan_details")
    private ApplicationLoanDetailsDTO applicationLoanDetailsDTO;

    public static class ApplicationLoanDetailsDTO{

        private Double amount;

        private String status;

        @JsonProperty(value = "transfer_days")
        private String transferDays;

        @JsonProperty(value = "failed_msg")
        private String failedMsg;

        @JsonProperty(value = "order_id")
        private String orderID;

        private String modalType;

        private String lender;

        private boolean covid = false;

        private String tenure;

        @JsonProperty(value = "interest_rate")
        private Double interestRate;

        @JsonProperty(value = "edi_amount")
        private Double ediAmount;

        public String getModalType() {
            return modalType;
        }

        public void setModalType(String modalType) {
            this.modalType = modalType;
        }

        public Double getAmount() {
            return amount;
        }

        public String getLender() {
            return lender;
        }

        public void setLender(String lender) {
            this.lender = lender;
        }

        public void setAmount(Double amount) {
            this.amount = amount;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getTransferDays() {
            return transferDays;
        }

        public void setTransferDays(String transferDays) {
            this.transferDays = transferDays;
        }

        public String getFailedMsg() {
            return failedMsg;
        }

        public void setFailedMsg(String failedMsg) {
            this.failedMsg = failedMsg;
        }

        public String getOrderID() {
            return orderID;
        }

        public void setOrderID(String orderID) {
            this.orderID = orderID;
        }

        public boolean isCovid() {
            return covid;
        }

        public void setCovid(boolean covid) {
            this.covid = covid;
        }

        public String getTenure() {
            return tenure;
        }

        public void setTenure(String tenure) {
            this.tenure = tenure;
        }

        public Double getInterestRate() {
            return interestRate;
        }

        public void setInterestRate(Double interestRate) {
            this.interestRate = interestRate;
        }

        public Double getEdiAmount() {
            return ediAmount;
        }

        public void setEdiAmount(Double ediAmount) {
            this.ediAmount = ediAmount;
        }
    }

    @JsonProperty(value = "application_status")
    private List<ApplicationDTO> applicationDTOList;

    public HeaderDTO getHeader() {
        return header;
    }

    public void setHeader(HeaderDTO header) {
        this.header = header;
    }

    public ApplicationLoanDetailsDTO getApplicationLoanDetailsDTO() {
        return applicationLoanDetailsDTO;
    }

    public void setApplicationLoanDetailsDTO(ApplicationLoanDetailsDTO applicationLoanDetailsDTO) {
        this.applicationLoanDetailsDTO = applicationLoanDetailsDTO;
    }

    public List<ApplicationDTO> getApplicationDTOList() {
        return applicationDTOList;
    }

    public void setApplicationDTOList(List<ApplicationDTO> applicationDTOList) {
        this.applicationDTOList = applicationDTOList;
    }
}
