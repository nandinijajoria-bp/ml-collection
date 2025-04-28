package com.bharatpe.lending.lendingplatform.nbfc.dto.request;

import com.bharatpe.lending.lendingplatform.nbfc.enums.ApiName;
import com.bharatpe.lending.lendingplatform.nbfc.enums.Lender;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class StatusCheckRequest {
    @NotNull
    private String leadId;
    @NotNull
    private String bpLoanId;
    private String clientId;
    private String digiId;
    @NotNull
    private Lender lender;
    @NotNull
    private ApiName apiName;
    @NotNull
    private int ttl;
    @NotNull
    private String topic;
}

