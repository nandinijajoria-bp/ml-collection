package com.bharatpe.lending.loanV3.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Data
@ToString
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
        public class LoanReceiptResponseDTO {

    private String responseStatus;

    private ErrorPayload error;

    private LoanReceiptData data;

    @Data
    @ToString
    @Builder
    public static class LoanReceiptData {

        private String message;

        private String transactionRefNumber;

        private String uniqueId;

    }

    @Data
    @ToString
    @Builder
    public static class ErrorPayload {

        private String code;

        private String description;

        private String errorType;

    }

}