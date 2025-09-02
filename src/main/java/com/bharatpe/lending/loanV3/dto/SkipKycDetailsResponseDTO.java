package com.bharatpe.lending.loanV3.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkipKycDetailsResponseDTO {
    private Long applicationId;
    private String aadhaarName;
    private String aadhaarAddress;
    private String dob;
}
