package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.*;

import java.util.*;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class CollectionDataDTO {

    private String loanId;
    private String merchantId;
    private Long lpsId;
    private String referenceId;
    private String loanStatus;
    private Date transaferDate;
    private String bpLoanId;
    private Double emiAmount;
    private String settlementStatus;
    private Double settlementAmount;
    private Double interest;
    private Double principal;
    private Double settlementPrincipal;
    private Double settlementInterest;
    private Long settlementId;
    private String settlementMode;
}
