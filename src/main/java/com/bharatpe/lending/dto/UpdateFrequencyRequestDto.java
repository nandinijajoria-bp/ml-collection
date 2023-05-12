package com.bharatpe.lending.dto;

import lombok.Data;

@Data
public class UpdateFrequencyRequestDto {

    private Long loanId;
    private int frequency;

}
