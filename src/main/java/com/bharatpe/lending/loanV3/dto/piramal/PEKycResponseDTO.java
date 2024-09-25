package com.bharatpe.lending.loanV3.dto.piramal;

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
public class PEKycResponseDTO {
     String requestReferenceId;
     String leadReferenceId;
     String applicantReferenceId;
     String kycStatus;
     String message;
     String code;
     String kycType;
     String mode;
     String digilockerUrl;
     String digilockerRequestId;
}
