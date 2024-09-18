package com.bharatpe.lending.loanV3.dto.request.trillionloans;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
@Data
@Builder
public class TLBreRequestDto {
    private Values values;

    @Data
    @Builder
    public static class Values {
        private Input input;

        @Data
        @Builder
        public static class Input {
            @JsonProperty("application_type")
            public String applicationType;
            @JsonProperty("merchant_id")
            public long merchantId;
            @JsonProperty("logger_id")
            public String loggerId;
            public String pancard;
            private String loanSegment;
            private String riskSegment;
            private String riskGroup;
            private String businessCategory;
            private String shopStructure;
            private String bureauReport;
            private Double bureauScore;
            private Double drs;
            private Double bbs;
            private Double bbs2;
            private Double bpScore;
            private Long vintage;
            private Integer uniqueCustomerCount;
            private Integer maxDPDlastLoan;
            private Integer maxDPDcurrentLoan;
            private String pincodeColor;
            private String pincode;
            private String merchantStatus;
            private Double adjMontlyNFI;
            private Double adjMontlyTPV;
            private Double bankEnancedOffer;
            private Boolean bankEnancedOfferEligibility;
            private Double aaEnancedOffer;
            private Boolean aaEnancedOfferEligibility;
            private Double gstEnancedOffer;
            private Boolean gstEnancedOfferEligibility;
            private Double gst3bEnancedOffer;
            private Boolean gst3bEnancedOfferEligibility;
            private Integer maxTenure;
            private Double loanCapping;
            private Integer age;
            private String pilots;
            private Object sources;
            @JsonProperty("scienaptic_properties")
            private Object scienapticProperties;
            @JsonProperty("aggregate_id")
            private String aggregateId;
        }
    }
}