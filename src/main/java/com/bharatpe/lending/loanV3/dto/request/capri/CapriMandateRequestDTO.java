package com.bharatpe.lending.loanV3.dto.request.capri;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CapriMandateRequestDTO {
     String status;
     String umrn;
     Integer bankAccountId;
     String bankAccountHolderName;
     String bankName;
     String branchName;
     String bankAccountNumber;
     String micr;
     String ifsc;
     String bankAccountType;
     String mandateRegistrationRequestedDate;
     String dateFormat;
     String periodStartDate;
     String periodEndDate;
     String mode;
     Boolean periodUntilCancelled;
     String debitTypeEnum;
     String debitFrequencyEnum;
     Double amount;
}
