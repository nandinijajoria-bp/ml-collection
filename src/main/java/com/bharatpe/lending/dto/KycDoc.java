package com.bharatpe.lending.dto;

import com.bharatpe.lending.enums.KycDocStatus;
import com.bharatpe.lending.enums.KycDocType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.ToString;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@ToString
public class KycDoc {
    private String id;
    private Long merchantId;
    private Long merchantStoreId;
    private String name;
    private String docIdentifier;
    private String docFrontImageUrl;
    private String docBackImageUrl;
    private KycDocType docType;
    private KycDocType subDocType;
    private KycDocStatus status;
    private String address;
    private String city;
    private String pincode;
    private String state;
    private String xml;
    private String passPhrase;
    private String dob;
    private String remarks;
    private String gender;
    private String response;
}
