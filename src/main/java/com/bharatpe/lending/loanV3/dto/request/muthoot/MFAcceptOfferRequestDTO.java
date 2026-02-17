package com.bharatpe.lending.loanV3.dto.request.muthoot;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MFAcceptOfferRequestDTO {

    public String customerID;
    RequestData offerDetails;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RequestData{

        private String offerID;
        private Double amount;
        private Integer tenure;
        private String tenureType;
        private Double processingFee;
        private Double interest;
        private ArrayList<Charge> charges;

    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Charge{
        private Integer chargeGST;
        private Double chargeVal;
        private String chargeName;
        private String chargesType;
    }


}
