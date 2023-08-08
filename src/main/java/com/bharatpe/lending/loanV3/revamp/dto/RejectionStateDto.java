package com.bharatpe.lending.loanV3.revamp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Data
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RejectionStateDto {

    private String rejectionReason;
    private String rejectionMessage;
}
