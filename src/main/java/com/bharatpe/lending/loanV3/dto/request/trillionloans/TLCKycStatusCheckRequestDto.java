package com.bharatpe.lending.loanV3.dto.request.trillionloans;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TLCKycStatusCheckRequestDto {
    private String clientId;
    private String leadId;
}
