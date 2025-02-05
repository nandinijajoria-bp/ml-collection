package com.bharatpe.lending.loanV3.dto.response.muthoot;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MFBreCallbackResponseDTO {

        private UserData data;
        private String error;
        private String statusCode;

        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class UserData {
            private String referenceID;
            private String status;
            private List<LoanOffer> offers;
        }

        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class LoanOffer {
            private String offerID;
            private String status;
            private String lenderID;
            private String lenderName;
            private String offerType;
            private String repaymentFrequency;
            private Integer minAmount;
            private Integer maxAmount;
            private Integer stepAmount;
            private Integer minTenure;
            private Integer maxTenure;
            private Integer stepTenure;
            private String tenureType;
            private Double interest;
            private String interestType;
            private String interestMethod;
            private Integer processingFee;
            private String processingFeeType;
            private String expiry;
            private DataRequired dataRequired;
        }

        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class DataRequired {
            private String bureau;
            private String banking;
            private String gst;
            private String xyz;
        }

}
