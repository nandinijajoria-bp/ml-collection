package com.bharatpe.lending.dto;

import com.bharatpe.common.entities.Experian;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class InsertExperianRequestDTO {

    private Long merchantId;

    private String ip;

    private Double latitude;

    private Double longitude;

    private String response;

    private String merchantName;

    private String email;

    private Boolean rejected;

    private String reason;

    private Integer requestedLoanAmount;

    private String pancardNumber;

    private Boolean tnc;

    private Double bpScore;

    private Double experianScore;

    private String category;

    private String color;

    private Integer retryCount;

    private boolean skip;

    private Integer pincode;

    private Date rejectedDate;

    private Date reportDate;

    private Double eligibleAmount;

    private String eligibleTenure;

    private String loanType;

    private String source;

    private String bureau;

    private String hitId;

}
