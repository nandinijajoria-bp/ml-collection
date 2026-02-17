package com.bharatpe.lending.dto.underwriting.write;

import com.bharatpe.lending.common.enums.PincodeColor;
import com.bharatpe.lending.common.enums.RiskDecision;
import com.bharatpe.lending.common.enums.RiskSegment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LendingRiskVariablesSnapshotWriteDto {
    private Long id;
    private Long merchantId;
    private Long applicationId;
    private Double bpScore;
    private Double bbs;
    private Long vintage;
    private Double bureauScore;
    private String riskColor;
    private RiskSegment riskSegment;
    private RiskDecision decisionId;
    private Integer pincode;
    private PincodeColor pincodeColor;
    private Double tpvOffer;
    private Double bureauOffer;
    private Double regularLimit;
    private Double ntbLimit;
    private Double finalOffer;
    private String loanType;
    private Boolean repeatLoan;
    private String tpvRejection;
    private String bureauRejection;
    private String riskRejection;
    private String experianRejection;
    private Integer underwritingVersion;
    private String riskGroup;
    private String loanSegment;
    private Double clubV2Amount;
    private Boolean clubV2;
    private Double drsScore;
    private Boolean drsScoreActive;
    private Double smallTicketLimit;
    private String smallTicketRejection;
    private Double monthlyNfi;
    private Double monthlyTpv;
    private Integer referenceCount;
    private Integer tenure;
    private Double roi;
    private Double dsTpv;
    private Double summaryTpv;
    private Integer uniqueCustomer1mon;
    private Double gstOffer;
    private Double gstAffectedOffer;
    private String pilotIdentifier;
    private Integer dpd30Count;
    private Integer dpd60Count;
    private String categoryToken;
    private String shopStructure;
    private Double bankBasedOffer;
    private Double gst3bBasedOffer;
    private Double inferredPincodeOffer;
    private String computeSource;
    private Double bbs2;
    private String aggregateId;
    private Double monthlyIncome;
    private Double aaBasedOffer;
    private Double approvalRate;
    private Double gst3bBasedAffectedOffer;
    private Double aaBasedAffectedOffer;
    private Double bankBasedAffectedOffer;
    private Double tpv6Mon;
    private String clientIdentifier;
    private Double summaryTpv60d;
    private String rejectedLenders;
    private Integer minTvrCount;
    private Boolean newContactReferenceLogic;
    private Boolean stpFlag;
    private String loanCategory;
    private String metaData;
}
