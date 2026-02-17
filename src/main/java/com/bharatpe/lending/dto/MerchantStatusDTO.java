package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class MerchantStatusDTO {
    @JsonProperty("is_merchant_interim")
    private String isMerchantInterim;

    @JsonProperty("settlement_level")
    private String settlementLevel;

    @JsonProperty("merchant_id")
    private String merchantId;

    @JsonProperty("mcc")
    private String mcc;

    @JsonProperty("status")
    private String status;
}
