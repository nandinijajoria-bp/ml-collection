package com.bharatpe.lending.loanV2.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Data
@ToString
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@AllArgsConstructor
@NoArgsConstructor
public class ProfessionalDetails {
    private String profession;
    private String gstNumber;
    private String experience;
    private String salary;
    private String companyName;
    private String shopType;
    private String addressType;
    private String currentAddress;
}
