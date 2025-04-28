package com.bharatpe.lending.lendingplatform.nbfc.dto.request;

import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.ApplicationDetails;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.BankDetails;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.NachDetails;
import lombok.Builder;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
@Builder
public class NachRegistrationRequest {
    @NotNull
    private ApplicationDetails applicationDetails;
    @NotNull
    private NachDetails nachDetails;
    @NotNull
    private BankDetails bankDetails;
}
