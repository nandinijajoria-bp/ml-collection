package com.bharatpe.lending.loanV3.dto.response.payu;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PayUAcceptOfferResponseDTO {

        @JsonProperty("application_id")
        private String applicationId;

        @JsonProperty("offer_detail_list")
        private List<OfferDetail> offerDetailList;

        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        @Builder
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class OfferDetail {
            @JsonProperty("offer_id")
            private int offerId;

            @JsonProperty("loan_type")
            private String loanType;

            @JsonProperty("loan_amount")
            private String loanAmount;

            @JsonProperty("loan_tenure")
            private String loanTenure;

            @JsonProperty("revlove_rate")
            private String revloveRate;

            @JsonProperty("interest_rate")
            private String interestRate;

            @JsonProperty("subvention_period")
            private String subventionPeriod;

            @JsonProperty("pre_payment_charges")
            private String prePaymentCharges;

            @JsonProperty("processing_fee")
            private String processingFee;

            @JsonProperty("offer_category")
            private String offerCategory;

            @JsonProperty("total_interest_amount")
            private String totalInterestAmount;

            @JsonProperty("accepted_offer")
            private boolean acceptedOffer;

            @JsonProperty("subvention_value")
            private String subventionValue;

            @JsonProperty("documentation_charges")
            private String documentationCharges;

            @JsonProperty("stamp_charges")
            private String stampCharges;

            @JsonProperty("insurance_amount")
            private String insuranceAmount;

            @JsonProperty("calculated_irr")
            private String calculatedIrr;

            @JsonProperty("roi_type")
            private String roiType;

            @JsonProperty("apr")
            private int apr;

            @JsonProperty("disbursement_amount")
            private String disbursementAmount;

            @JsonProperty("emi_amount")
            private String emiAmount;

            @JsonProperty("equivalent_reducing_roi")
            private String equivalentReducingRoi;

            @JsonProperty("no_advance_emi")
            private String noAdvanceEmi;

            @JsonProperty("processing_fee_gst")
            private String processingFeeGst;

            @JsonProperty("processing_fee_without_gst")
            private String processingFeeWithoutGst;

            @JsonProperty("message")
            private String message;
        }
}
