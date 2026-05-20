package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanPaymentAttemptItemDTO {

    private String date;
    private String paymentStatus;
    private String amount;
    private String transactionId;
    private String typeOfPayment;
    private String modeOfPayment;

}