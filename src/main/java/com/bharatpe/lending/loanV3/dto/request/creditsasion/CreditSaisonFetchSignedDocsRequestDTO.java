package com.bharatpe.lending.loanV3.dto.request.creditsasion;

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
public class CreditSaisonFetchSignedDocsRequestDTO {
    String partnerLoanId;
    String documentType;
    String url;
}
