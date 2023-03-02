package com.bharatpe.lending.loanV3.dto;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
@Data
public class ModifyLenderDto {
    Long id;
    String status;
}