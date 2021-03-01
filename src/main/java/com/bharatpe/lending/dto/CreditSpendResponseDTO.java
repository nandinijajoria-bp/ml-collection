package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.Date;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class CreditSpendResponseDTO {

    private boolean success = true;

    private String uuid;

    private String message;

    private Long requestId;

    private String deeplink;

    private String authentication;

    private Narration details;

    private CL cl;

    private List<TL> tl;

    private String upiString;

    private String initiateEndpoint;

    private String verifyEndpoint;

    private String client;
    
    private Double availableCl;

    public CreditSpendResponseDTO(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public CreditSpendResponseDTO(Long requestId, String deeplink) {
        this.requestId = requestId;
        this.deeplink = deeplink;
    }

    public CreditSpendResponseDTO() {
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public CL getCl() {
        return cl;
    }

    public void setCl(CL cl) {
        this.cl = cl;
    }

    public List<TL> getTl() {
        return tl;
    }

    public void setTl(List<TL> tl) {
        this.tl = tl;
    }

    public Long getRequestId() {
        return requestId;
    }

    public void setRequestId(Long requestId) {
        this.requestId = requestId;
    }

    public String getDeeplink() {
        return deeplink;
    }

    public void setDeeplink(String deeplink) {
        this.deeplink = deeplink;
    }

    public String getAuthentication() {
        return authentication;
    }

    public Narration getDetails() {
        return details;
    }

    public void setDetails(Narration details) {
        this.details = details;
    }

    public void setAuthentication(String authentication) {
        this.authentication = authentication;
    }

    public String getUpiString() {
        return upiString;
    }

    public void setUpiString(String upiString) {
        this.upiString = upiString;
    }

    public String getInitiateEndpoint() {
        return initiateEndpoint;
    }

    public void setInitiateEndpoint(String initiateEndpoint) {
        this.initiateEndpoint = initiateEndpoint;
    }

    public String getVerifyEndpoint() {
        return verifyEndpoint;
    }

    public void setVerifyEndpoint(String verifyEndpoint) {
        this.verifyEndpoint = verifyEndpoint;
    }

    public String getClient() {
        return client;
    }

    public void setClient(String client) {
        this.client = client;
    }

    public Double getAvailableCl() {
		return availableCl;
	}

	public void setAvailableCl(Double availableCl) {
		this.availableCl = availableCl;
	}

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    @Override
    public String toString() {
        return "CreditSpendResponseDTO{" +
                "success=" + success +
                ", message='" + message + '\'' +
                ", cl=" + cl +
                ", tl=" + tl +
                '}';
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
    public static class CL {
        private Integer loanAmount;
        private Date dueDate;
        private Date billDate;
        private Double interestRate = 0.1D;
        private String billingCycle = "Monthly";
        private Integer tenure = 12;

        public CL(Integer loanAmount, Date dueDate, Date billDate) {
            this.loanAmount = loanAmount;
            this.dueDate = dueDate;
            this.billDate = billDate;
        }

        public Integer getLoanAmount() {
            return loanAmount;
        }

        public void setLoanAmount(Integer loanAmount) {
            this.loanAmount = loanAmount;
        }

        public Date getDueDate() {
            return dueDate;
        }

        public void setDueDate(Date dueDate) {
            this.dueDate = dueDate;
        }

        public Date getBillDate() {
            return billDate;
        }

        public void setBillDate(Date billDate) {
            this.billDate = billDate;
        }

        public Double getInterestRate() {
            return interestRate;
        }

        public void setInterestRate(Double interestRate) {
            this.interestRate = interestRate;
        }

        public String getBillingCycle() {
            return billingCycle;
        }

        public void setBillingCycle(String billingCycle) {
            this.billingCycle = billingCycle;
        }

        public Integer getTenure() {
            return tenure;
        }

        public void setTenure(Integer tenure) {
            this.tenure = tenure;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
    public static class TL {
        private Integer ediAmount;
        private Integer tenure;
        private Double interestRate;
        private Integer processingFee;
        private Integer loanAmount;
        private Integer interestAmount;
        private Integer repaymentAmount;
        private Integer ediCount;

        public TL(Integer ediAmount, Integer tenure, Double interestRate, Integer processingFee, Integer loanAmount, Integer interestAmount, Integer repaymentAmount, Integer ediCount) {
            this.ediAmount = ediAmount;
            this.tenure = tenure;
            this.interestRate = interestRate;
            this.processingFee = processingFee;
            this.loanAmount = loanAmount;
            this.interestAmount = interestAmount;
            this.repaymentAmount = repaymentAmount;
            this.ediCount = ediCount;
        }

        public Integer getEdiAmount() {
            return ediAmount;
        }

        public void setEdiAmount(Integer ediAmount) {
            this.ediAmount = ediAmount;
        }

        public Integer getTenure() {
            return tenure;
        }

        public void setTenure(Integer tenure) {
            this.tenure = tenure;
        }

        public Double getInterestRate() {
            return interestRate;
        }

        public void setInterestRate(Double interestRate) {
            this.interestRate = interestRate;
        }

        public Integer getProcessingFee() {
            return processingFee;
        }

        public void setProcessingFee(Integer processingFee) {
            this.processingFee = processingFee;
        }

        public Integer getLoanAmount() {
            return loanAmount;
        }

        public void setLoanAmount(Integer loanAmount) {
            this.loanAmount = loanAmount;
        }

        public Integer getInterestAmount() {
            return interestAmount;
        }

        public void setInterestAmount(Integer interestAmount) {
            this.interestAmount = interestAmount;
        }

        public Integer getRepaymentAmount() {
            return repaymentAmount;
        }

        public void setRepaymentAmount(Integer repaymentAmount) {
            this.repaymentAmount = repaymentAmount;
        }

        public Integer getEdiCount() {
            return ediCount;
        }

        public void setEdiCount(Integer ediCount) {
            this.ediCount = ediCount;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
    public static class Narration{

        private String narrationHeading;
        private String narration1;
        private String narration2;
        private String narration3;
        private String icon;
        private String mobile;

        public Narration(String narrationHeading, String narration1, String narration2, String narration3, String icon, String mobile) {
            this.narrationHeading = narrationHeading;
            this.narration1 = narration1;
            this.narration2 = narration2;
            this.narration3 = narration3;
            this.icon = icon;
            this.mobile = mobile;
        }

        public String getNarrationHeading() {
            return narrationHeading;
        }
        public void setNarrationHeading(String narrationHeading) {
            this.narrationHeading = narrationHeading;
        }
        public String getNarration1() {
            return narration1;
        }
        public void setNarration1(String narration1) {
            this.narration1 = narration1;
        }
        public String getNarration2() {
            return narration2;
        }
        public void setNarration2(String narration2) {
            this.narration2 = narration2;
        }
        public String getNarration3() {
            return narration3;
        }
        public void setNarration3(String narration3) {
            this.narration3 = narration3;
        }
        public String getIcon() {
            return icon;
        }
        public void setIcon(String icon) {
            this.icon = icon;
        }

        public String getMobile() {
            return mobile;
        }

        public void setMobile(String mobile) {
            this.mobile = mobile;
        }
    }
}
