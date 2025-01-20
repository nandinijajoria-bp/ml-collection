package com.bharatpe.lending.loanV3.dto.request.ugro;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UgroConsentRequest {
    private String leadId;
    private Boolean consent;
    private CounterOfferDetails counterOffer;

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CounterOfferDetails {
        private Double amount;
        private Integer tenure;
        private Double interestRate;
        private Double processingFeePct;
        private Double convenienceFeePct;
    }
}
