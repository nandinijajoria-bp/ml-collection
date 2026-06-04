package com.bharatpe.lending.loanV3.dto.request.sib;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SibRepaymentRequestDTO {

    @JsonProperty("data")
    private RequestData requestData;

    @JsonProperty("npos_config_id")
    private Integer nposConfigId;

    @JsonProperty("originator_name")
    private String originatorName;

    @JsonProperty("client_request_id")
    private String clientRequestId;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RequestData {

        @JsonProperty("COLLECTION")
        private Collection collection;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Collection {

        @JsonProperty("COLLECTION")
        private List<CollectionRecord> records;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CollectionRecord {

        @JsonProperty("PARTNER_NAME")
        private String partnerName;

        @JsonProperty("PROGRAM_REFERENCE")
        private String programReference;

        @JsonProperty("INVESTOR_LOAN_ID")
        private String sibLoanId;

        @JsonProperty("PARTNER_LOAN_ID")
        private String bharatpeLoanId;

        @JsonProperty("COLLECTION_REFERENCE_ID")
        private String collectionReferenceId;

        @JsonProperty("COLLECTION_DATE")
        private String collectionDate;

        @JsonProperty("FORECLOSURE_DATE")
        private String foreclosureDate;

        @JsonProperty("MODE_OF_COLLECTION")
        private String modeOfCollection;

        @JsonProperty("AMOUNT_COLLECTED")
        private String amountCollected;

        @JsonProperty("PRINCIPAL_COLLECTED")
        private String principalCollected;

        @JsonProperty("INTEREST_COLLECTED")
        private String interestCollected;

        @JsonProperty("PENALTY_COLLECTED")
        private String penaltyCollected;

        @JsonProperty("CHARGE_COLLECTED")
        private String chargeCollected;

        @JsonProperty("LOAN_STATUS")
        private String loanStatus;
    }
}
