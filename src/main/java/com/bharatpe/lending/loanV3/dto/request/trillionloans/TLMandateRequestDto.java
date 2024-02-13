package com.bharatpe.lending.loanV3.dto.request.trillionloans;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TLMandateRequestDto {
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
    private Integer amount;
    private String externalRefernceNumber;
    private String mode;
}
