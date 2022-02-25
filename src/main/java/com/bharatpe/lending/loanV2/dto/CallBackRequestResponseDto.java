package com.bharatpe.lending.loanV2.dto;

import lombok.Builder;
import lombok.Data;
import lombok.ToString;

@Data
@Builder
@ToString
public class CallBackRequestResponseDto {
    private String callbackStatus;
}
