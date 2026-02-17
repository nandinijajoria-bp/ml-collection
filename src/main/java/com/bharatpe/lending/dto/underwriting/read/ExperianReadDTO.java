package com.bharatpe.lending.dto.underwriting.read;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class ExperianReadDTO {
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
    private boolean noExperian;
    private List<String> maskedMobiles;
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
    private Boolean skipBureau;
    private Long id;
    private Date createdAt;
    private Date updatedAt;
}

