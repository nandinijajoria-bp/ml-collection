package com.bharatpe.lending.lendingplatform.nbfc.dto.pojo;

import com.bharatpe.lending.lendingplatform.nbfc.enums.DocType;
import com.bharatpe.lending.lendingplatform.nbfc.enums.KycDocStatus;
import com.bharatpe.lending.lendingplatform.nbfc.enums.KycDocType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@NoArgsConstructor
@AllArgsConstructor
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
public class KYCDocuments {
    private String name;
    private String docIdentifier;
    private String docFrontImageUrl;
    private String docBackImageUrl;
    private KycDocType kycDocType;
    private KycDocType subDocType;
    private DocType docType;
    private KycDocStatus status;
    private String address;
    private String city;
    private int pincode;
    private String state;
    private String xml;
    private String digioXml;
    private String provider;
    private String passPhrase;
    private String dob;
    private String base64;
    private String remarks;
    private String gender;
    private String response;
    private String lastName;
    private String middleName;
    private String firstName;
    private String aadhaarSeedingStatus;
    private BigDecimal faceMatchPer;
    private BigDecimal livelinessScore;
    private BigDecimal nameMatchPer;
    private String url;
    private BigDecimal shopFrontExistenceScore;
    private BigDecimal shopFrontStructureScore;
    private BigDecimal shopStockCategoryScore;
    private BigDecimal shopBoardScore;
}