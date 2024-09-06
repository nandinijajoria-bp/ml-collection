package com.bharatpe.lending.loanV3.revamp.dto;

import com.bharatpe.common.entities.Experian;
import com.bharatpe.lending.common.dto.MerchantResponseDTO;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.enums.KycStatus;
import com.bharatpe.lending.loanV2.dto.BankAccountDetails;
import com.bharatpe.lending.loanV2.dto.Eligibility;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Data
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EligibilityStateDTO {

    private String kycPancard;
    private KycStatus kycPanStatus;
    private String pancard;
    private String pincode;

    private MerchantResponseDTO merchantResponseDTO;

    private BasicDetailsDto merchant;
    private String merchantName;

    private Experian experian;

    private String ineligible;
    private BankAccountDetails accountDetails;
    private boolean changeBankAccount;

    private boolean hasExperian = false;

    private Integer ediDaysModel;
    private Boolean bpClubMember;
    private Boolean clubV2Member = false;
    private Eligibility eligibility;

    private String errorString;
    private String stageOneHitId;
    private String stageTwoHitId;
    private Boolean isPreapprovedRepeatLoan = false;
    private String riskSegment;

    private boolean isPincodeChanged;
    private String kycMessage;
    private Boolean isPanNsdlVerified;
    private Boolean maxCountReached;
    private String message;
    private Boolean dummyMerchant;

    private Boolean eligibilityExceptionFlag;
    private String offerIncreased;
    private Double previousFinalOffer;
    private String fullName;
    private String dob;

}
