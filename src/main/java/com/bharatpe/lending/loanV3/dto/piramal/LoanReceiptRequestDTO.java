package com.bharatpe.lending.loanV3.dto.piramal;

import com.bharatpe.lending.enums.PaymentType;
import com.bharatpe.lending.loanV3.enums.piramal.FeeType;
import com.bharatpe.lending.loanV3.enums.piramal.PaymentMode;
import com.bharatpe.lending.loanV3.enums.piramal.PaymentRequestType;
import com.bharatpe.lending.loanV3.enums.piramal.PaymentTypePiramal;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoanReceiptRequestDTO {

    private String loanAccountNumber;

    private BigDecimal paymentAmount;

    private Long ledgerId;

    private PaymentMode paymentMode;

    private PaymentTypePiramal paymentType;

    private Boolean isPartCancellation;

    private PaymentRequestType paymentRequestType;

    private PaymentReceiptData paymentReceiptData;

    private List<AllocationDetail> allocationDetails;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PaymentReceiptData {

        private String transactionReference;

        private String receivedDate;

        private String remarks;

    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AllocationDetail {

        private String allocationItem;

        private FeeType feeType;

        private Number paidAmount;

        private Number waivedAmount;

    }

}