package com.bharatpe.lending.lendingplatform.nbfc.dto.pojo;

import com.bharatpe.lending.lendingplatform.nbfc.enums.PincodeColor;
import com.bharatpe.lending.lendingplatform.nbfc.enums.RiskColor;
import com.bharatpe.lending.lendingplatform.nbfc.enums.RiskDecision;
import com.bharatpe.lending.lendingplatform.nbfc.enums.RiskSegment;
import com.bharatpe.lending.lendingplatform.nbfc.enums.ShopStructure;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;

@NoArgsConstructor
@AllArgsConstructor
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
public class LoanRiskVariables {
    private BigDecimal bpScore;
    private BigDecimal bbs;
    private BigDecimal bbs2;
    private BigDecimal bureauScore;
    private RiskColor riskColor;
    private RiskSegment riskSegment;
    private RiskDecision decisionId;
    private int pincode;
    private PincodeColor pincodeColor;
    private BigDecimal gstOffer;
    private BigDecimal finalOffer;
    private String loanType;
    private String experianRejection;
    private String riskGroup;
    private String loanSegment;
    private BigDecimal drsScore;
    private BigDecimal monthlyNfi;
    private BigDecimal monthlyTpv;
    private BigDecimal monthlyIncome;
    private long vintage;
    private long referenceCount;
    private int tenure;
    private BigDecimal summaryTpv;
    private int uniqueCustomer1mon;
    private boolean gstAffectedOffer;
    private ShopStructure shopStructure;
    private String pilotIdentifier;
    private BigDecimal bankBasedOffer;
    private BigDecimal gst3bBasedOffer;
    private String aggregateId;
    private BigDecimal aaBasedOffer;
    private boolean gst3bBasedAffectedOffer;
    private boolean aaBasedAffectedOffer;
    private boolean bankBasedAffectedOffer;
    private boolean newContactReferenceLogic;
    private String loanCategory;
    private Map<String, Object> metaData;
    private int maxDpdLastLoan;
    private int maxDpdCurrentLoan;
    private Date updatedAt;
}
