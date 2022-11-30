package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Data
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MerchantReference {

    String name;
    String phoneNumber;
    Integer score;

    @JsonProperty("inferred_relation")
    String inferredRelation;

    @JsonProperty("inferred_name")
    String inferredName;
    @JsonProperty("inferred_name_confidence")
    Double inferredNameConfidence;
    @JsonProperty("num_hits")
    String numHits;
    @JsonProperty("inferred_location")
    String inferredLocation;
    @JsonProperty("fraud_flag")
    Boolean fraudFlag;
    @JsonProperty("inferred_occupation")
    String inferredOccupation;
    @JsonProperty("inferred_company")
    String inferredCompany;

}
