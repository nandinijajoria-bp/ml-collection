package com.bharatpe.lending.loanV3.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ForeClosureAmountResponse {
    Long applicationId;
    String lender;
    String productName;
    Boolean success;
    Data data;

    @lombok.Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Data {

        String responseStatus;
        ForeClosureAmountResponse.Data.ErrorPayload error;
        ForeClosureData data;

        @lombok.Data
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ErrorPayload {
            String code;
            String description;
            String errorType;
        }

        @lombok.Data
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ForeClosureData {
            Double balancePrincipal;
            Double overduePrincipal;
            Double overdueInterest;
            Double otherDues;
            Double gapInterest;
            Double penalCharges;
            Double otherPayable;
            Double advanceEmiRefunds;
            Double netReceivable;
            Double netPayable;
            Double netReceivablePayable;
        }
    }
}
