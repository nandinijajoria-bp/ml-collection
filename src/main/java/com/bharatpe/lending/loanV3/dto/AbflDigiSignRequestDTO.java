package com.bharatpe.lending.loanV3.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
public class AbflDigiSignRequestDTO {
    String lender;
    Long applicationId;
    Payload payload;
    String productName;
    boolean topup;

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Payload {
        String accountId;
        String mobile_number;
        Boolean merged_pdf_flag;
        String sanction_letter;
        String loan_agreement;
        String key_fact_statement;
    }
}

