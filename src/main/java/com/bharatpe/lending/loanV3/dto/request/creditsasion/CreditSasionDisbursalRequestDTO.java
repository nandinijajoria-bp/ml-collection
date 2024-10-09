package com.bharatpe.lending.loanV3.dto.request.creditsasion;

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
public class CreditSasionDisbursalRequestDTO {

    private String partnerLoanId;
    private String loanProduct;
    private String mandateType;
    private String mandateIdentifier;
    private NetDisbursalAmount netDisbursalAmount;
    private LoanComponents loanComponents;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class NetDisbursalAmount {
        private Double total;
        private Double ksfShare;
        private Double partnerShare;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LoanComponents {
        private ProcessingFee processingFee;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class ProcessingFee {
            private Double total;
            private Double ksfShare;
            private Double partnerShare;
        }
    }
}
