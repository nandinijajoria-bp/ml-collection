package com.bharatpe.lending.loanV3.revamp.dto;

import com.bharatpe.lending.dto.EnachErrorMessageDTO;
import com.bharatpe.lending.dto.LoanInsuranceDTO;
import com.bharatpe.lending.loanV2.dto.AdditionalDetails;
import com.bharatpe.lending.loanV2.dto.AddressDetails;
import com.bharatpe.lending.loanV2.dto.LoanApplicationStage;
import com.bharatpe.lending.loanV2.dto.ProfessionalDetails;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Data
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
@AllArgsConstructor
@NoArgsConstructor
public class LoanApplicationDetailsV3 {
    private Long applicationId;
    private String externalLoanId;
    private String transferDays;
    private Double loanAmount;
    private String applicationStatus;
    private String rejectReason;
    private String enachDeeplink;
    private Boolean shopPhotoRequired;
    private String reapply;
    private Boolean enachBank;
    private AddressDetails addressDetails;
    private String currentAddress;
    private ProfessionalDetails professionalDetails;
    private AdditionalDetails additionalDetails;
    private EnachErrorMessageDTO enachErrorResponse;
    private Boolean skipEnach;
    private String resubmitReason;
    private String completedResubmitReason;
    private Long reapplyTime;
    private Long reapplyTimeEpoch;
    private Boolean lenderAssc;
    private Boolean enachDone;
    private String enachMode;
    private String lender;
    private String tenure;
    private Double interestRate;
    private Double edi;

    private String nachSessionStatus;
    private String nachSessionMode;
    private Long nachStartedAt;

    private List<LoanApplicationStage> loanApplicationStageList=new ArrayList<>();

    private Boolean isInsured;
    private List<LoanInsuranceDTO> loanInsurances;
    private Double apr;
}

