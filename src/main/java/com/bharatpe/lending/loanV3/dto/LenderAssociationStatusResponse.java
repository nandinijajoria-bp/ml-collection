package com.bharatpe.lending.loanV3.dto;

import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

@Data
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
public class LenderAssociationStatusResponse {
    LenderAssociationStages stage;
    LenderAssociationStatus status;
    Boolean ediModelModified;
    String lender;
}
