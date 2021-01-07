package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CreditScoreReportDetailDTO {

    @JsonProperty("credit_card_utilization")
    private CreditScoreReportDetailDTO.CreditCardUtilization creditCardUtilization;

    @JsonProperty("payment_history")
    private CreditScoreReportDetailDTO.PaymentHistory paymentHistory;

    @JsonProperty("age_of_account")
    private CreditScoreReportDetailDTO.AgeOfAccount ageOfAccount;

    @JsonProperty("total_account")
    private CreditScoreReportDetailDTO.TotalAccount totalAccount;

    @JsonProperty("credit_enquiries")
    private CreditScoreReportDetailDTO.CreditEnquries creditEnquries;

    @JsonProperty("average_credit_score")
    private CreditScoreReportDetailDTO.AverageCreditScore averageCreditScore;

    @JsonProperty("experian_number")
    private String experianNumber;

    public String getExperianNumber() {
        return experianNumber;
    }

    public void setExperianNumber(String experianNumber) {
        this.experianNumber = experianNumber;
    }

    public CreditCardUtilization getCreditCardUtilization() {
        return creditCardUtilization;
    }

    public void setCreditCardUtilization(CreditCardUtilization creditCardUtilization) {
        this.creditCardUtilization = creditCardUtilization;
    }

    public PaymentHistory getPaymentHistory() {
        return paymentHistory;
    }

    public void setPaymentHistory(PaymentHistory paymentHistory) {
        this.paymentHistory = paymentHistory;
    }

    public AgeOfAccount getAgeOfAccount() {
        return ageOfAccount;
    }

    public void setAgeOfAccount(AgeOfAccount ageOfAccount) {
        this.ageOfAccount = ageOfAccount;
    }

    public TotalAccount getTotalAccount() {
        return totalAccount;
    }

    public void setTotalAccount(TotalAccount totalAccount) {
        this.totalAccount = totalAccount;
    }

    public CreditEnquries getCreditEnquries() {
        return creditEnquries;
    }

    public void setCreditEnquries(CreditEnquries creditEnquries) {
        this.creditEnquries = creditEnquries;
    }

    public AverageCreditScore getAverageCreditScore() {
        return averageCreditScore;
    }

    public void setAverageCreditScore(AverageCreditScore averageCreditScore) {
        this.averageCreditScore = averageCreditScore;
    }

    public class AgeOfAccount{

        @JsonProperty("average_age")
        private Integer averageAge;

        @JsonProperty("newest_account")
        private Integer newestAccount;

        @JsonProperty("oldest_account")
        private Integer oldestAccount;

        @JsonProperty("impact")
        private String impact;

        public String getImpact() {
            return impact;
        }

        public void setImpact(String impact) {
            this.impact = impact;
        }

        public Integer getAverageAge() {
            return averageAge;
        }

        public void setAverageAge(Integer averageAge) {
            this.averageAge = averageAge;
        }

        public Integer getNewestAccount() {
            return newestAccount;
        }

        public void setNewestAccount(Integer newestAccount) {
            this.newestAccount = newestAccount;
        }

        public Integer getOldestAccount() {
            return oldestAccount;
        }

        public void setOldestAccount(Integer oldestAccount) {
            this.oldestAccount = oldestAccount;
        }
    }

    public class AverageCreditScore{
        @JsonProperty("city")
        private String city;

        @JsonProperty("state")
        private String state;

        @JsonProperty("country")
        private String country;

        @JsonProperty("city_average_score")
        private Double cityAverageScore;

        @JsonProperty("state_average_score")
        private Double stateAverageScore;

        @JsonProperty("country_average_score")
        private Double countryAverageScore;

        @JsonProperty("city_percentile")
        private Double cityPercentile;

        @JsonProperty("state_percentile")
        private Double statePercentile;

        @JsonProperty("country_percentile")
        private Double countryPercentile;

        public Double getCityAverageScore() {
            return cityAverageScore;
        }

        public void setCityAverageScore(Double cityAverageScore) {
            this.cityAverageScore = cityAverageScore;
        }

        public Double getStateAverageScore() {
            return stateAverageScore;
        }

        public void setStateAverageScore(Double stateAverageScore) {
            this.stateAverageScore = stateAverageScore;
        }

        public Double getCountryAverageScore() {
            return countryAverageScore;
        }

        public void setCountryAverageScore(Double countryAverageScore) {
            this.countryAverageScore = countryAverageScore;
        }

        public Double getCityPercentile() {
            return cityPercentile;
        }

        public void setCityPercentile(Double cityPercentile) {
            this.cityPercentile = cityPercentile;
        }

        public Double getStatePercentile() {
            return statePercentile;
        }

        public void setStatePercentile(Double statePercentile) {
            this.statePercentile = statePercentile;
        }

        public Double getCountryPercentile() {
            return countryPercentile;
        }

        public void setCountryPercentile(Double countryPercentile) {
            this.countryPercentile = countryPercentile;
        }

        public String getCity() {
            return city;
        }

        public void setCity(String city) {
            this.city = city;
        }

        public String getState() {
            return state;
        }

        public void setState(String state) {
            this.state = state;
        }

        public String getCountry() {
            return country;
        }

        public void setCountry(String country) {
            this.country = country;
        }
    }


    public class TotalAccount{

        @JsonProperty("total_account")
        private Integer totalAccount;

        @JsonProperty("active_account")
        private Integer activeAccount;

        @JsonProperty("closedAccount")
        private Integer closedAccount;

        @JsonProperty("impact")
        private String impact;

        public String getImpact() {
            return impact;
        }

        public void setImpact(String impact) {
            this.impact = impact;
        }

        public Integer getTotalAccount() {
            return totalAccount;
        }

        public void setTotalAccount(Integer totalAccount) {
            this.totalAccount = totalAccount;
        }

        public Integer getActiveAccount() {
            return activeAccount;
        }

        public void setActiveAccount(Integer activeAccount) {
            this.activeAccount = activeAccount;
        }

        public Integer getClosedAccount() {
            return closedAccount;
        }

        public void setClosedAccount(Integer closedAccount) {
            this.closedAccount = closedAccount;
        }
    }


    public class CreditEnquries{

        @JsonProperty("total_enquiries")
        private Integer totalEnquiries;

        @JsonProperty("loan_enquiries")
        private Integer loanEnquiries;

        @JsonProperty("credit_card_enquiries")
        private Integer creditCardEnquiries;

        @JsonProperty("impact")
        private String impact;

        public String getImpact() {
            return impact;
        }

        public void setImpact(String impact) {
            this.impact = impact;
        }

        public Integer getTotalEnquiries() {
            return totalEnquiries;
        }

        public void setTotalEnquiries(Integer totalEnquiries) {
            this.totalEnquiries = totalEnquiries;
        }

        public Integer getLoanEnquiries() {
            return loanEnquiries;
        }

        public void setLoanEnquiries(Integer loanEnquiries) {
            this.loanEnquiries = loanEnquiries;
        }

        public Integer getCreditCardEnquiries() {
            return creditCardEnquiries;
        }

        public void setCreditCardEnquiries(Integer creditCardEnquiries) {
            this.creditCardEnquiries = creditCardEnquiries;
        }
    }



    public class CreditCardUtilization{

        @JsonProperty("total_utilization")
        private Integer totalUtilization;

        @JsonProperty("card_utilization")
        private Integer cardUtilization;

        @JsonProperty("card_limit")
        private Integer cardLimit;

        @JsonProperty("impact")
        private String impact;

        public String getImpact() {
            return impact;
        }

        public void setImpact(String impact) {
            this.impact = impact;
        }

        public Integer getTotalUtilization() {
            return totalUtilization;
        }

        public void setTotalUtilization(Integer totalUtilization) {
            this.totalUtilization = totalUtilization;
        }

        public Integer getCardUtilization() {
            return cardUtilization;
        }

        public void setCardUtilization(Integer cardUtilization) {
            this.cardUtilization = cardUtilization;
        }

        public Integer getCardLimit() {
            return cardLimit;
        }

        public void setCardLimit(Integer cardLimit) {
            this.cardLimit = cardLimit;
        }
    }

    public class PaymentHistory{

        @JsonProperty("timely_payment")
        private Integer timelyPayment;

        @JsonProperty("total_payment")
        private Integer totalPayment;

        @JsonProperty("ontime_payment")
        private Integer ontimePayment;

        @JsonProperty("impact")
        private String impact;

        public String getImpact() {
            return impact;
        }

        public void setImpact(String impact) {
            this.impact = impact;
        }

        public Integer getTimelyPayment() {
            return timelyPayment;
        }

        public void setTimelyPayment(Integer timelyPayment) {
            this.timelyPayment = timelyPayment;
        }

        public Integer getTotalPayment() {
            return totalPayment;
        }

        public void setTotalPayment(Integer totalPayment) {
            this.totalPayment = totalPayment;
        }

        public Integer getOntimePayment() {
            return ontimePayment;
        }

        public void setOntimePayment(Integer ontimePayment) {
            this.ontimePayment = ontimePayment;
        }
    }

}
