package com.bharatpe.lending.lendingplatform.lms.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.Date;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PaymentSynchronousRequest {

    @NotBlank
    private String bpLoanId;


    @NotBlank
    private String paymentId;

    @NotBlank
    private String transactionReferenceId;

    @NotNull
    private Date date;

    @NotBlank
    private String paymentStatus;
}
