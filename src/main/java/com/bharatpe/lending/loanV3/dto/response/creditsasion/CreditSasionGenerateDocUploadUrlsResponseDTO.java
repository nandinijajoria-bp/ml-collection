package com.bharatpe.lending.loanV3.dto.response.creditsasion;

import com.bharatpe.lending.loanV3.dto.request.creditsasion.enums.CreditSasionContentType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreditSasionGenerateDocUploadUrlsResponseDTO {
    private String partnerLoanId;
    private String loanProductCode;
    private String stage;
    private CreditSasionContentType contentType;
    private String fileName;
}