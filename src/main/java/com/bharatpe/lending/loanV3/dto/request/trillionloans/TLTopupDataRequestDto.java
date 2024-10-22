package com.bharatpe.lending.loanV3.dto.request.trillionloans;

import com.bharatpe.lending.enums.Lender;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TLTopupDataRequestDto {
    private String leadId;
    private String sourcingChannel;
    private String topupId;
    private Double outstandingAmount;
}
