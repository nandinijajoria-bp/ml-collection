package com.bharatpe.lending.lendingplatform.nbfc.dto.request;

import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.ApplicationDetails;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.CustomerAdditionalData;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.CustomerAddressDetails;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.CustomerPersonalDetails;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.LoanRiskVariables;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.NachDetails;
import lombok.Builder;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
@Builder
public class UpdateLeadRequest {
    @NotNull
    private ApplicationDetails applicationDetails;
    @NotNull
    private LoanRiskVariables loanRiskVariables;
    @NotNull
    private CustomerAdditionalData customerAdditionalData;
    @NotNull
    private CustomerPersonalDetails customerPersonalDetails;
    @NotNull
    private CustomerAddressDetails customerAddressDetails;
    @NotNull
    private NachDetails nachDetails;
}
