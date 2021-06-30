package com.bharatpe.lending.loanV2.dto;

import com.bharatpe.lending.enums.KycDocType;
import com.bharatpe.lending.enums.KycStatus;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

@Data
@ToString
@Builder
public class KycStatusDTO {
    private KycStatus kycStatus;
    private String remarks;
    private KycDocType kycDocType;
}
