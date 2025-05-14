package com.bharatpe.lending.dto;

import com.bharatpe.lending.enums.NachRevokeStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class NachDetail {
    private Long id;
    private Long applicationId;
    private String mandateId;
    private Long bankCode;
    private String bankName;
    private String branchName;
    private String accountNumber;
    private String ifscCode;
    private String beneficiaryName;
    private String nachStatus;
    private String status;
    private String nachLender;
    private NachRevokeStatus revokeStatus;
    private Date startDate;
}
