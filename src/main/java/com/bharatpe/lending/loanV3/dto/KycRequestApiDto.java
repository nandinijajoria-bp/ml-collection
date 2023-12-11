package com.bharatpe.lending.loanV3.dto;

import com.bharatpe.lending.enums.Lender;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
//@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@Builder
public class KycRequestApiDto {
    String lender;
    Long applicationId;
    String productName;
    Payload payload;
    Identifier identifier;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Identifier {
        String accountId;
        String cccId;
        String productCode;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
//    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
    @Builder
    public static class Payload {
        String kycType;
        String accountId;
        String cccId;
        String transactionId;
        String selfie;
        String okycXml;
        String declaredAddress;
        String okycDocType;
        String declaredName;
        @JsonProperty("declaredDoB")
        String declaredDob;
        @JsonProperty("declaredPAN")
        String declaredPan;
        String declaredCity;
        String declaredState;
        Integer declaredPincode;
        String mobile;
        String gender;
        @JsonProperty("nsdlPAN")
        String nsdlPan;
        String nsdlName;
    }
}
