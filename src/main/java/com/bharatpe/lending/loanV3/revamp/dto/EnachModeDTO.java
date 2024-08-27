package com.bharatpe.lending.loanV3.revamp.dto;

import lombok.*;

@Data
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EnachModeDTO {
    private String name;
    private boolean isEnabled;

    // in case ENACH mode disabled
    private String reason;
}
