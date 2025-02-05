package com.bharatpe.lending.loanV3.dto.request.muthoot;


import com.bharatpe.lending.loanV3.dto.response.muthoot.MFBreCallbackResponseDTO;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MFAcceptOfferDTO {
    String OfferId;
    MFBreCallbackResponseDTO.Slab slab;
}
