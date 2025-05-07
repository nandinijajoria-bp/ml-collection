package com.bharatpe.lending.lendingplatform.underwriting.dto.reresponse;


import com.bharatpe.common.entities.Experian;
import com.bharatpe.lending.dto.GlobalLimitResponse;
import com.bharatpe.lending.lendingplatform.underwriting.enums.BureauDerog;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
@AllArgsConstructor
public class EligibilityResponse {
    private List<GlobalLimitResponse.OfferDetail> offerDetails;
    private Double version;
    private String riskSegment;
    private String riskGroup;
    private Double limit;
    private String loanType;
    private BureauDerog bureauDerog;
    private Boolean isClubV2Member;
    private boolean bankAffectedOffer;
    private boolean gst3bAffectedOffer;
    private Long merchantId;
    private Double globalLimit;
    private Double bureauLimit;
    private Double ntcLimit;
    private Double gstLimit;
    private boolean derog;
    private String rejectReason;
    private String rejectionType;
    private String pancardName;
    private Experian experian;
    private String errorString;
    private String stageOneId_;
    private String stageTwoId_;
    private boolean enachBank;
    private boolean isETC;
    private boolean preApprovedLoan;
    private boolean fromCache;
    private String cacheKey;
    private boolean internalMerchant;
    private String offerIncreased;
    private Double previousFinalOffer;
}
