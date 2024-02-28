package com.bharatpe.lending.loanV3.dto.request.trillionloans;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;
@Data
@Builder
public class TLBreRequestDto {
    private String accountId;
    private String productCode;
    private String source;
    private CustomerReport customerReport;
    private LoanApplicationRequest loanApplicationRequest;
    private Values values;

    @Data
    @Builder
    public static class CustomerReport {
        private KycInfo kycInfo;

        @Data
        @Builder
        public static class KycInfo {
            private String city;
            private String gender;
            private String firstName;
            private String middleName;
            private String lastName;
            private String panNumber;
            private String pincode;
            private String mobile;
            private String state;
            private String addressLine1;
            private String addressLine2;
            private String addressLine3;
            private String dob;
        }
    }

    @Data
    @Builder
    public static class LoanApplicationRequest {
        private Double requestedLoanAmount;
        private String roi;
        private String tenure;
    }

    @Data
    @Builder
    public static class Values {
        private Input input;

        @Data
        @Builder
        public static class Input {
            private String loanSegment;
            private String riskSegment;
            private String riskGroup;
            private String businessCategory;
            private String shopStructure;
            private String bureauReport;
            private Double bureauScore;
            private Double drs;
            private Double bbs;
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
        }
    }
}