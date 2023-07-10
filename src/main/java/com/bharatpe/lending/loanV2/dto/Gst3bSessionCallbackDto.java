package com.bharatpe.lending.loanV2.dto;

import com.bharatpe.lending.common.enums.Gst3bSessionStatus;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

@Data
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class Gst3bSessionCallbackDto {
    String sessionId;
    Gst3bSessionStatus status;
    Boolean correctTenure;
    String gstin;
    String message;
}
