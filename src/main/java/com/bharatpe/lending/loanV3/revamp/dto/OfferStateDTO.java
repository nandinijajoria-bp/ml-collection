package com.bharatpe.lending.loanV3.revamp.dto;

import com.bharatpe.common.entities.Experian;
import com.bharatpe.lending.common.dto.MerchantResponseDTO;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.loanV2.dto.Eligibility;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Data
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OfferStateDTO {

    private String kycPancard;
    private String pancard;

    private MerchantResponseDTO merchantResponseDTO;

    private BasicDetailsDto merchant;

    private Experian experian;

    private String ineligible;
    private boolean changeBankAccount;

    private boolean hasExperian;

    private Integer ediDaysModel;

    private Boolean bpClubMember;
    private Boolean clubV2Member;
    private Eligibility eligibility;

    private String errorString;
    private String stageOneHitId;
    private String stageTwoHitId;
}
