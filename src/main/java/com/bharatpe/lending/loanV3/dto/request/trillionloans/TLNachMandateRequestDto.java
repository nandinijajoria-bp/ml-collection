package com.bharatpe.lending.loanV3.dto.request.trillionloans;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TLNachMandateRequestDto {
    private String leadId;
    private String status;
    private String umrn;
    private String bankAccountHolderName;
    private String bankName;
    private String branchName;
    private String bankAccountNumber;
    private String micr;
    private String ifsc;
    private String bankAccountType;
    private String mandateRegistrationRequestedDate;
    private String periodStartDate;
    private String periodEndDate;
    private Boolean periodUntilCancelled;
    private String debitTypeEnum;
    private String debitFrequencyEnum;
    private Double amount;
    private String externalRefernceNumber;
    private String mode;
}
