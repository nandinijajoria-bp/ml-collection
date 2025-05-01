package com.bharatpe.lending.lendingplatform.lms.dto.request;

import com.bharatpe.lending.lendingplatform.lms.enums.DebitFrequency;
import com.bharatpe.lending.lendingplatform.lms.enums.DebitType;
import com.bharatpe.lending.lendingplatform.lms.enums.MandateType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.Date;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EmandateRequest {

    @NotBlank
    private String bpLoanId;


    private int customerBankId;

    @NotNull
    @Valid
    private MandateDetails mandateDetails;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class MandateDetails {
        @NotBlank
        private String mandateAmount;

        @NotNull
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private Date mandateEndDate;

        @NotNull
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private Date mandateStartDate;

        private String mandateId;

        @NotBlank
        private String umrn;

        @NotNull
        private DebitFrequency debitFrequency;

        @NotNull
        private DebitType debitType;

        @NotNull
        private MandateType mandateType;

        @NotBlank
        private String bankIFSCCode;

        @NotBlank
        private String bankName; // Map with bank code from Master
    }
}
