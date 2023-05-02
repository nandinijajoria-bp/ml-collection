package com.bharatpe.lending.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class PgStatusResponseMandate {
    private String statusCode;
    private String message;
    private PgPaymentCallbackMandateDTO data;


}
