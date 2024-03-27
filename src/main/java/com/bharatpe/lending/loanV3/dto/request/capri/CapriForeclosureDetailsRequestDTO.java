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
public class CapriForeclosureDetailsRequestDTO {
    String loanId;
    String dateFormat;
    String transactionDate;
    String locale;
    Boolean isTotalOutstandingInterest;
    Boolean includePreclosureReasoon;
}
