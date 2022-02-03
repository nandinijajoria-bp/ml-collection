package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class DsImageValidationResponseDto {
    Double responseTime;
    String status;
    ShopParams shopFrontExistence;
    ShopParams shopFrontStructure;
    ShopParams shopStockCategory;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
    public class ShopParams {
        @JsonProperty(value = "class")
        String dsClass;
        @JsonProperty(value = "conf")
        Double confidence;
        @JsonProperty(value = "verified_shop")
        Boolean verifiedShop;
    }
}
