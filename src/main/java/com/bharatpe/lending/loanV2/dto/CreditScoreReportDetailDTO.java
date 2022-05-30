package com.bharatpe.lending.loanV2.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
public class CreditScoreReportDetailDTO {

    @JsonProperty("credit_card_utilization")
    private CreditCardUtilization creditCardUtilization;

    @JsonProperty("payment_history")
    private PaymentHistory paymentHistory;

    @JsonProperty("age_of_account")
    private AgeOfAccount ageOfAccount;

    @JsonProperty("total_account")
    private TotalAccount totalAccount;

    @JsonProperty("credit_enquiries")
    private CreditEnquries creditEnquries;

    @JsonProperty("average_credit_score")
    private AverageCreditScore averageCreditScore;

    @JsonProperty("experian_number")
    private String experianNumber;

    @Data
    @Builder
    public static class AgeOfAccount{

        @JsonProperty("average_age")
        private Integer averageAge;

        @JsonProperty("newest_account")
        private Integer newestAccount;

        @JsonProperty("oldest_account")
        private Integer oldestAccount;

        @JsonProperty("impact")
        private String impact;

    }
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AverageCreditScore{
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

    }

    @Data
    @Builder
    public static class TotalAccount{

        @JsonProperty("total_account")
        private Integer totalAccount;

        @JsonProperty("active_account")
        private Integer activeAccount;

        @JsonProperty("closedAccount")
        private Integer closedAccount;

        @JsonProperty("impact")
        private String impact;

    }

    @Data
    @Builder
    public static class CreditEnquries{

        @JsonProperty("total_enquiries")
        private Integer totalEnquiries;

        @JsonProperty("loan_enquiries")
        private Integer loanEnquiries;

        @JsonProperty("credit_card_enquiries")
        private Integer creditCardEnquiries;

        @JsonProperty("impact")
        private String impact;

    }


    @Data
    @Builder
    public static class CreditCardUtilization{

        @JsonProperty("total_utilization")
        private Integer totalUtilization;

        @JsonProperty("card_utilization")
        private Integer cardUtilization;

        @JsonProperty("card_limit")
        private Integer cardLimit;

        @JsonProperty("impact")
        private String impact;

    }
    @Data
    @Builder
    public static class PaymentHistory{

        @JsonProperty("timely_payment")
        private Integer timelyPayment;

        @JsonProperty("total_payment")
        private Integer totalPayment;

        @JsonProperty("ontime_payment")
        private Integer ontimePayment;

        @JsonProperty("impact")
        private String impact;

    }
}
