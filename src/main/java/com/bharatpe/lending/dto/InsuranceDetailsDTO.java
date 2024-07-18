package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InsuranceDetailsDTO {
    @JsonProperty("insurance_applicable")
    private boolean insuranceApplicable;

    @JsonProperty("insurance_availed_date")
    private Date insuranceAvailedDate;

    @JsonProperty("insurance_premium_amount")
    private Double insurancePremiumAmount;

    @JsonProperty("insurance_provider_name")
    private String insuranceProviderName;

    @JsonProperty("benefits_of_the_insurance")
    private String benefitsOfTheInsurance;

    @JsonProperty("sum_insured")
    private Double sumInsured;

    //(email,mobile)
    @JsonProperty("insurance_partner_contact_details")
    private Map<String,String> insurancePartnerContactDetails;

    @JsonProperty("insurance_doc")
    private String insuranceDocument;
}
