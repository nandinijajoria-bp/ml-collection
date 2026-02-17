package com.bharatpe.lending.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
@NoArgsConstructor
public class AutoPayUPIAltMandateRegisterRequest {
    @NotNull
    private Long applicationId;
    @NotBlank
    private String accountName;
    @NotBlank
    private String bankName;
    @NotBlank
    private String accountNumber;
    @NotBlank
    private String ifsc;
}
