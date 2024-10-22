package com.bharatpe.lending.loanV3.dto.request.trillionloans;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TLAddChargeRequestDto {
    private String leadId;
    private Integer chargeId;
    private Double amount;
    private Boolean isAmountNonEditable;
    private Boolean isMandatory;
}
