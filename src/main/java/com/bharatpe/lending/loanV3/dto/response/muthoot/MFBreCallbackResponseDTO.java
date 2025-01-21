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
    @NoArgsConstructor
    @AllArgsConstructor
    private static class OutputVariables {
        private Double thick_thin_multiplier;
        private String rejectionReason;
    }


    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class UserData {
        private List<LoanOffer> offers;
        private OutputVariables outputVariables;
        private String rejectionReason;
        private String status;
        private String referenceID;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LoanOffer {
        private String createdAt;
        private String expiresAt;
        private String lenderID;
        private String lenderLogoURL;
        private String lenderName;
        private String offerID;
        private String offerType;
        private List<Slab> slabs;
        private String status;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Slab {
        private Double maxAmount;
        private Double minAmount;
        private Integer tenure;
        private Double interest;
        private String method;
        private Double processingFee;
        private String processingFeeType;
        private Double gst;
    }

}
