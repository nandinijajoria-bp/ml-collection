package com.bharatpe.lending.lendingplatform.nbfc.dto.pojo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

@NoArgsConstructor
@AllArgsConstructor
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
public class NachDetails {
    private String mandateId;
    private String beneficiaryName;
    private String branchName;
    private String status;
    private String type;
    private String bankName;
    private String ifsc;
    private String accountNumber;
    private String accountType;
    private Date startDate;
    private Date endDate;
    private String frequency;
    private BigDecimal nachAmount;
    private String mode;
    private String maxAmount;
    private String referenceNumber;
    private String txnId;
    private Date txnDate;
    private String lender;
    private String micr;
    private String umrn;

}
