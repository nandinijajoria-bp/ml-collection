package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class MerchantScoreResponseDto {
    private Long merchantId;
    private Long applicationId;
    private Integer merchantStoreId;
    private String businessCategory;
    private String vintage;
    private Integer vintageScore;
    private Double dailyTpv;
    private Integer dailyTpvScore;

    @JsonProperty(value = "tpv_3_month")
    private Double tpv3Month;

    @JsonProperty(value = "tpv_3_month_score")
    private Integer tpv3MonthScore;

    private Integer activeDays;
    private Integer activeDaysScore;
    private Integer uniqueCustomer;
    private Integer uniqueCustomerScore;
    private Integer trajectoryScore;

    @JsonProperty(value = "finalScore")
    private Double finalScore;
}
