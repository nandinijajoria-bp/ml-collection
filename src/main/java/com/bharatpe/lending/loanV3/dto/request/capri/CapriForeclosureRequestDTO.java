package com.bharatpe.lending.loanV3.dto.request.capri;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CapriForeclosureRequestDTO {
    String note;
    String dateFormat;
    Double transactionAmount;
    String transactionDate;
    Long paymentTypeId;
    Double interestWaiverAmount;
    String receiptNumber;
    String locale;
    List<Object> chargeDiscountDetails;
    List<Object> waiveCharges;
}
