package com.bharatpe.lending.loanV3.revamp.dto;


import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Data
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ActiveLoanStateDTO {
    Boolean isActiveLoan;
}
