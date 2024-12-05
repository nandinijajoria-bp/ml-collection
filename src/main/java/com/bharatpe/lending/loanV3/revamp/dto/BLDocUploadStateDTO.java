package com.bharatpe.lending.loanV3.revamp.dto;

import lombok.*;

@Data
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BLDocUploadStateDTO {
    private Long applicationId;
    private String deeplink;
}
