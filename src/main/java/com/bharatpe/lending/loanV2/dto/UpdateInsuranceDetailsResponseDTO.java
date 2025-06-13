package com.bharatpe.lending.loanV2.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdateInsuranceDetailsResponseDTO {
    private Boolean success;
    private String message;
}
