package com.bharatpe.lending.loanV3.dto.trillions;

import com.bharatpe.lending.loanV3.dto.ForeclosureRequestDto;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TrillionForeclosureRequestDto {

    private String lender;

    private String productName;

    private Long applicationId;

    private TrillionForeclosureRequestDto.Payload payload;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Payload {

        private String loanAccounts;

        private String note = "Foreclosure";

        private Integer preClosureReasonId = 192;

        private String transactionAmount;

        @JsonFormat(shape=JsonFormat.Shape.STRING, pattern= "dd-MM-yyyy", timezone = "Asia/Kolkata")
        private Date transactionDate;

        private Integer paymentTypeId;

        private Double interestWaiverAmount = 0.0;

        private String receiptNumber;

        private List<Object> chargeDiscountDetails = new ArrayList<>();

        private List<Object> waiveCharges = new ArrayList<>();

    }

}