package com.bharatpe.lending.loanV2.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

@Data
@ToString
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProfessionalDetails {
    private String profession;
    private String gstNumber;
    private String experience;
    private String salary;
    private String companyName;
    private String shopType;
    private String addressType;
}
