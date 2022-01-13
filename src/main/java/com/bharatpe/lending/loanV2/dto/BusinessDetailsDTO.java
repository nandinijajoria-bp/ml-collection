package com.bharatpe.lending.loanV2.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

@Data
@ToString
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BusinessDetailsDTO {
    String businessName;
    String businessCategory;
    String businessSubCategory;
}
