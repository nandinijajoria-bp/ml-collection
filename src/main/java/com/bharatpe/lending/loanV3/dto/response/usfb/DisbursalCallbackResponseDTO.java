package com.bharatpe.lending.loanV3.dto.response.usfb;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DisbursalCallbackResponseDTO {
    String eventName;
    String partnerApplicationId;
    String partnerId;
    Payload payload;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Payload {

        String leadId;
        String decisionStatus;
        String utrNo;
        String loanAccountNumber;
        String disbursalAmount;
        String interestRate;

    }
}
