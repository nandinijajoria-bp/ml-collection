package com.bharatpe.lending.loanV3.dto.response.creditsasion;

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
public class CreditSaisonFetchSignedDocsResponseDTO {
    String url;
    String docType;
}
