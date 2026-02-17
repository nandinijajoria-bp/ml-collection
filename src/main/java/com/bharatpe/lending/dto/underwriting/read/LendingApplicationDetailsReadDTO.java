package com.bharatpe.lending.dto.underwriting.read;
import com.bharatpe.lending.common.enums.MandateType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class LendingApplicationDetailsReadDTO {

    private Long applicationId;
    private String ediModel;
    private Boolean ediModelModified;
    private String stage;
    private Boolean lenderAssc;
    private Integer totalReferences;
    private Integer referencesFromDe;
    private Integer savedReferences;
    private Integer referencesAddedByMerchant;
    private String cpvReferralCode;
    private String cpvReferralCodeNach;
    private Boolean isNachSkip;
    private Long prevAppId;
    private Boolean isKycSkip;
    private Boolean currentAddressSameAsPermanentAddress;
    private Boolean skipDistanceCheck;
    private Date leadAcceptanceTime;
    private String applicationViewState;
    private String loanPurpose;
    private Boolean isDocSkip;
    private Long offerId;
    private Map<String, Object> metaData;
    private String p2mStatus;
    private Long id;
    private Date createdAt;
    private Date updatedAt;

    private String disbursalMode;
    private boolean nachEligible;
    private boolean autoPayUpiEligible;
    private Date mandateFlagsToggledOn;
    private MandateType mandateType;
}