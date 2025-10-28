package com.bharatpe.lending.dto;

import lombok.Data;

@Data
public class QrStatusEventDTO {
    private String merchantId;
    private String eventType;
    private String clientIdentifier;
}