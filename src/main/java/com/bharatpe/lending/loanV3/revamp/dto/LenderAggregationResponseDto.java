package com.bharatpe.lending.loanV3.revamp.dto;

import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Getter;
import lombok.Setter;
import lombok.Data;

import java.util.List;

@Data
public class LenderAggregationResponseDto {
    private Long applicationId;
    private String message;
    private List<LenderData> lenders;
    private Integer edi;
    private Double interestRate;
    private Double apr;
    private Double processingFee;
    private Double loanAmount;
    private String tenure;
    private String loanType;
    private String screenType;
    private Integer attemptCount;

    private String previousLender;

    private Boolean isRepeatLoan;

    public Boolean getRepeatLoan() {
        return isRepeatLoan;
    }

    public void setRepeatLoan(Boolean repeatLoan) {
        isRepeatLoan = repeatLoan;
    }

    public Double getProcessingFee() {
        return processingFee;
    }

    public void setProcessingFee(Double processingFee) {
        this.processingFee = processingFee;
    }

    public Double getLoanAmount() {
        return loanAmount;
    }

    public void setLoanAmount(Double loanAmount) {
        this.loanAmount = loanAmount;
    }

    public String getTenure() {
        return tenure;
    }

    public void setTenure(String tenure) {
        this.tenure = tenure;
    }

    public Integer getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(Integer attemptCount) {
        this.attemptCount = attemptCount;
    }

    public String getPreviousLender() {
        return previousLender;
    }

    public void setPreviousLender(String previousLender) {
        this.previousLender = previousLender;
    }

    public String getLoanType() {
        return loanType;
    }

    public void setLoanType(String loanType) {
        this.loanType = loanType;
    }

    public String getScreenType() {
        return screenType;
    }

    public void setScreenType(String screenType) {
        this.screenType = screenType;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<LenderData> getLenders() {
        return lenders;
    }

    public void setLenders(List<LenderData> lenders) {
        this.lenders = lenders;
    }

    public Integer getEdi() {
        return edi;
    }

    public void setEdi(Integer edi) {
        this.edi = edi;
    }

    public Double getInterestRate() {
        return interestRate;
    }

    public void setInterestRate(Double interestRate) {
        this.interestRate = interestRate;
    }

    public Double getApr() {
        return apr;
    }

    public void setApr(Double apr) {
        this.apr = apr;
    }

    @Override
    public String toString() {
        return "LenderAggregationResponseDto{" +
                "applicationId=" + applicationId +
                ", message='" + message + '\'' +
                ", lenders=" + lenders +
                ", edi=" + edi +
                ", interestRate=" + interestRate +
                ", apr=" + apr +
                ", processingFee=" + processingFee +
                ", loanAmount=" + loanAmount +
                ", tenure='" + tenure + '\'' +
                ", loanType='" + loanType + '\'' +
                ", screenType='" + screenType + '\'' +
                ", attemptCount=" + attemptCount +
                '}';
    }

    public static class LenderData {
        private String lenderName;
        private String approvalRate="HIGH";
        private Boolean isRejected = false;
        private List<PenaltyConfig> penaltyConfigs;

        private Integer nachBounceAmount;

        List<ForeClosureEntityDTO> foreClosureEntityDTOList;

        public Integer getNachBounceAmount() {
            return nachBounceAmount;
        }

        public void setNachBounceAmount(Integer nachBounceAmount) {
            this.nachBounceAmount = nachBounceAmount;
        }

        public List<ForeClosureEntityDTO> getForeClosureEntityDTOList() {
            return foreClosureEntityDTOList;
        }

        public void setForeClosureEntityDTOList(List<ForeClosureEntityDTO> foreClosureEntityDTOList) {
            this.foreClosureEntityDTOList = foreClosureEntityDTOList;
        }

        public List<PenaltyConfig> getPenaltyConfigs() {
            return penaltyConfigs;
        }

        public void setPenaltyConfigs(List<PenaltyConfig> penaltyConfigs) {
            this.penaltyConfigs = penaltyConfigs;
        }


        private Double apr;

        private Double irr;

        public Double getIrr() {
            return irr;
        }

        public void setIrr(Double irr) {
            this.irr = irr;
        }

        public Double getApr() {
            return apr;
        }

        public void setApr(Double apr) {
            this.apr = apr;
        }

        public String getLenderName() {
            return lenderName;
        }

        public void setLenderName(String lenderName) {
            this.lenderName = lenderName;
        }

        public String getApprovalRate() {
            return approvalRate;
        }

        public void setApprovalRate(String approvalRate) {
            this.approvalRate = approvalRate;
        }

        public Boolean getRejected() {
            return isRejected;
        }

        public void setRejected(Boolean rejected) {
            isRejected = rejected;
        }


        public static class PenaltyConfig {
            private Long minAmount;
            private Long maxAmount;
            private Double penalty;

            @Override
            public String toString() {
                return "PenaltyConfig{" +
                        "minAmount=" + minAmount +
                        ", maxAmount=" + maxAmount +
                        ", penalty=" + penalty +
                        '}';
            }

            public Long getMinAmount() {
                return minAmount;
            }

            public void setMinAmount(Long minAmount) {
                this.minAmount = minAmount;
            }

            public Long getMaxAmount() {
                return maxAmount;
            }

            public void setMaxAmount(Long maxAmount) {
                this.maxAmount = maxAmount;
            }

            public Double getPenalty() {
                return penalty;
            }

            public void setPenalty(Double penalty) {
                this.penalty = penalty;
            }
        }

        public static class ForeClosureEntityDTO {

            private Double rate;

            private Double minAmount;

            private long tenure;

            private long durationTo;

            private long durationFrom;

            public Double getRate() {
                return rate;
            }

            public void setRate(Double rate) {
                this.rate = rate;
            }

            public Double getMinAmount() {
                return minAmount;
            }

            public void setMinAmount(Double minAmount) {
                this.minAmount = minAmount;
            }

            public long getTenure() {
                return tenure;
            }

            public void setTenure(long tenure) {
                this.tenure = tenure;
            }

            public long getDurationTo() {
                return durationTo;
            }

            public void setDurationTo(long durationTo) {
                this.durationTo = durationTo;
            }

            public long getDurationFrom() {
                return durationFrom;
            }

            public void setDurationFrom(long durationFrom) {
                this.durationFrom = durationFrom;
            }

            @Override
            public String toString() {
                return "ForeClosureEntityDTO{" +
                        "rate=" + rate +
                        ", minAmount=" + minAmount +
                        ", tenure=" + tenure +
                        ", durationTo=" + durationTo +
                        ", durationFrom=" + durationFrom +
                        '}';
            }
        }

        public LenderData() {
        }

        public LenderData(String lenderName, String lenderLogo, Boolean isRejected) {
            this.lenderName = lenderName;
            this.isRejected = isRejected;
        }

        @Override
        public String toString() {
            return "LenderData{" +
                    "lenderName='" + lenderName + '\'' +
                    ", approvalRate='" + approvalRate + '\'' +
                    ", isRejected=" + isRejected +
                    ", penaltyConfig=" + penaltyConfigs +
                    ", nachBounceAmount=" + nachBounceAmount +
                    ", foreClosureEntityDTOList=" + foreClosureEntityDTOList +
                    ", apr=" + apr +
                    ", irr=" + irr +
                    '}';
        }
    }
}