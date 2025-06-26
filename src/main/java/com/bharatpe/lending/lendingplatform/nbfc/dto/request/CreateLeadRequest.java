package com.bharatpe.lending.lendingplatform.nbfc.dto.request;

import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.ApplicationDetails;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.BankDetails;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.CustomerAddressDetails;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.CustomerPersonalDetails;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.CustomerShopDetails;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.KYCDocuments;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.LoanRiskVariables;
import com.bharatpe.lending.lendingplatform.nbfc.enums.KycDocType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateLeadRequest {
    @NotNull
    private ApplicationDetails applicationDetails;
    @NotNull
    private BankDetails bankDetails;
    @NotNull
    private CustomerAddressDetails customerAddressDetails;
    @NotNull
    private CustomerPersonalDetails customerPersonalDetails;
    @NotNull
    private Map<KycDocType, KYCDocuments> kycDocuments;
    private LoanRiskVariables loanRiskVariables;
    private CustomerShopDetails customerShopDetails;
}
