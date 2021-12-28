package com.bharatpe.lending.loanV2.dto;

import com.bharatpe.lending.dto.EnachErrorMessageDTO;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.ToString;

@Data
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoanApplicationDetails {
    private Long applicationId;
    private String externalLoanId;
    private String transferDays;
    private Double loanAmount;
    private String applicationStatus;
    private String rejectReason;
    private String enachDeeplink;
    private boolean shopPhotoRequired = false;
    private String reapply;
    private Boolean enachBank;
    private AddressDetails addressDetails;
    private String currentAddress;
    private ProfessionalDetails professionalDetails;
    private AdditionalDetails additionalDetails;
    private EnachErrorMessageDTO enachErrorResponse;
    private boolean skipEnach;
    private String resubmitReason;
    private Long reapplyTime;
    private Long reapplyTimeEpoch;
}
