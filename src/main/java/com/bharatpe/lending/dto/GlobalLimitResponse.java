package com.bharatpe.lending.dto;

import com.bharatpe.common.entities.Experian;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class GlobalLimitResponse {
    private boolean success;
    private String message;
    private Data data;
    private String errorCode;

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @ToString
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {
        private Long merchantId;
        private Double globalLimit;
        private boolean derog;
        private String rejectReason;
        private String rejectionType;
        private String pancardName;
        private Experian experian;
        private String errorString;
        private String stageOneId_;
        private String stageTwoId_;
        List<OfferDetail> offerDetails;
        Double version;
        String riskSegment;
        String riskGroup;
        Double limit;
        String loanType;
        Boolean isClubV2Member;
        Boolean bankAffectedOffer;
        Boolean gst3bAffectedOffer;
        private Boolean preApprovedLoan;
        private Double previousFinalOffer;
        private String offerIncreased;
        private Long refreshCountDownMinutes;
        private List<String> maskedMobiles;
        private String mobile;

    }

    @lombok.Data
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OfferDetail {
        private Double interestRate;
        private Double initialRoi;
        private Double maxLoanAmount;
        private Double loanAmount;
        private Double bureauLimit;
        private Double ntcLimit;
        private Integer tenure;
        private Integer ediCount;
        private Double processingFee;
        private Double clubV2Amount;
    }

    public static GlobalLimitResponse form(MaskedGlobalLimitResponse maskedGlobalLimitResponse){
        GlobalLimitResponse globalLimitResponse = new GlobalLimitResponse();
        globalLimitResponse.setSuccess(maskedGlobalLimitResponse.isSuccess());
        globalLimitResponse.setMessage(maskedGlobalLimitResponse.getMessage());
        globalLimitResponse.setErrorCode(maskedGlobalLimitResponse.getErrorCode());

        Data data1 =  new Data();
        data1.setMaskedMobiles(maskedGlobalLimitResponse.getData().getMaskedMobiles());
        globalLimitResponse.setData(data1);
        return globalLimitResponse;
    }

    public static GlobalLimitResponse form(ScenapticResponseDTO scenapticResponseDTO){
        GlobalLimitResponse globalLimitResponse = new GlobalLimitResponse();
        globalLimitResponse.setSuccess(scenapticResponseDTO.getSuccess());
        globalLimitResponse.setMessage(scenapticResponseDTO.getMessage());
        globalLimitResponse.setErrorCode(scenapticResponseDTO.getErrorCode());
        globalLimitResponse.setData(scenapticResponseDTO.getData());
        return globalLimitResponse;
    }
}
