package com.bharatpe.lending.loanV3.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PoaXmlDTO {
    @JsonProperty("KycRes")
    KycResult kycRes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class KycResult {
        String code;
        String ret;
        String ts;
        String ttl;
        String txn;
        @JsonProperty("UidData")
        UidData uidData;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UidData {
        String tkn;
        String uid;
        @JsonProperty("Poi")
        Poi poi;
        @JsonProperty("Poa")
        Poa poa;
        @JsonProperty("Pht")
        String pht;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Poi {
        String dob;
        String gender;
        String name;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Poa {
       String co;
       String country;
       String dist;
       String house;
       String loc;
       String pc;
       String po;
       String state;
       String subdist;
       String vtc;
    }

}
