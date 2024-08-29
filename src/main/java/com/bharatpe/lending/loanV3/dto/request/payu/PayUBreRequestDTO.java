package com.bharatpe.lending.loanV3.dto.request.payu;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PayUBreRequestDTO {

    @JsonProperty("application-id")
    private String applicationId;

    private Double tpv;

    @JsonProperty("merchant_segment")
    private String merchantSegment;

    @JsonProperty("merchant_category")
    private String merchantCategory;

    @JsonProperty("pincode_colour")
    private String pincodeColour;

    @JsonProperty("unique_customer_count_last_3_months")
    private Boolean uniqueCustomerCountLast3Months;

    @JsonProperty("transacting_days_last_3_months")
    private Boolean transactingDaysLast3Months;
}
