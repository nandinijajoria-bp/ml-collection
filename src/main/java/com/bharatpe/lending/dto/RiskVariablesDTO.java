package com.bharatpe.lending.dto;

import com.bharatpe.lending.common.enums.PincodeColor;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class RiskVariablesDTO {
    private double bureauScore;
    private String riskSegment;
    private String riskGroup;
    private String riskGroupLike;
    private PincodeColor pincodeColor;
    private Boolean isGstOffer;
    private double summaryTpv;
    private long vintage;
    private double tpvOffer;
    private Set<String> rejectedLenders;
    private int maxTenure;
}