package com.bharatpe.lending.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class FosTaskStatusDto {
    String status;
    String loanType;
    String stage;
    String message;
    String eligibleForPayout;
    Long applicationId;
    EmiFosTaskStatusDto emiTaskDetails;

    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @ToString
    @Builder
    public static class EmiFosTaskStatusDto {
        Long applicationId;
        String status;
        String eligibleForPayout;
        String message;

    }
}
