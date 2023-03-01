package com.bharatpe.lending.loanV3.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Data
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InvokeLenderAssociationRequest {
    Long applicationId;
    Boolean forceEnable;
    String stage;
    String referenceNo;
    Double amount;
    Long lpsId;
    String utr;
    String accountId;
    String lan;
    String mobile;
    String payload;
    Long ledgerId;
}
