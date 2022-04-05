package com.bharatpe.lending.loanV2.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Data
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BusinessDetailsDTO {
    String businessName;
    String businessCategory;
    String businessSubCategory;
    Long merchantId;
    Boolean isEdit;
}
