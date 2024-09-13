package com.bharatpe.lending.loanV3.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

import java.util.Date;
import java.util.List;

@Data
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
public class RegulatoryDataDto {
    String latitude;
    String longitude;
    String contactReferences; // lending_merchant_references
    String gstIn;
    String address;
    String businessCategory;
    String businessSubCategory;
    String shopAddress;
    String signedIpAdress;
    Date signedTimestamp;
    String professionalDeclaration;
    String smsData;
    Boolean customerConsent;
    Date nsdlTimestamp;
    String nsdlLog;
    List<Consent> consents;

    @Data
    @ToString
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Builder
    public static class Consent {
        String header;
        String text;
        String ip;
        String timestamp;
    }
}

// Declaration of Self emp/salaried
// sms data
// shop photos -> upload api ??
// Consent/permission from customer (lending_merchant_permissions)
// NSDL check time stamp (bureau ?? )
// NDSL Log of Request/response