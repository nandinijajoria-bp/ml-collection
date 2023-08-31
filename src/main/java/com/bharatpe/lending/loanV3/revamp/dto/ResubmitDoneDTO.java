package com.bharatpe.lending.loanV3.revamp.dto;

import lombok.*;

@Data
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ResubmitDoneDTO {
    private boolean success;
    private String errorString;
    private boolean resubmitDone;
}
