package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class MerchantConfigInfo {
    private Long merchantId;
    private String identifier;
    private String state;
    private String comment;
    private String lender;
    private Long applicationId;
}

