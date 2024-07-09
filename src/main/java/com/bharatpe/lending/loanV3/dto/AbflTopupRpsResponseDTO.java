package com.bharatpe.lending.loanV3.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
public class AbflTopupRpsResponseDTO {
    Long applicationId;
    String lender;
    Boolean success;
    String productName;
    RpsResponse data;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RpsResponse {
        String responseStatus;
        Error error;
        ResponseData data;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Error {
        private String code;
        private String description;
        private String errorType;
    }


    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResponseData {
        LoanDetail loanDetails;
        List<Installment> installments;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoanDetail {
         String loanAmount;
         String rateOfInterest;
         String loanTenure;
         String disbursalDate;
         String firstEMIdate;
         String preEMIshortDisbursed;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Installment {
        String installmentNumber;
        String dueDate;
        String dueAmount;
        String principalComponent;
        String interestComponent;
        String principalOutstanding;
    }
}
