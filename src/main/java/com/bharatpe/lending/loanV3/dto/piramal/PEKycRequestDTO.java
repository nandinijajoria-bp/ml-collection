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
public class PEKycRequestDTO {
     String source;
     String inputIdType;
     String phone;
     String kycUrl;
     String leadId;
     String applicantReferenceId;
     String kycType;
     String productId;
}
