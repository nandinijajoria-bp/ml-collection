package com.bharatpe.lending.lendingplatform.nbfc.dto.request;


import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.ApplicationDetails;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.BankDetails;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.BusinessDetails;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.CustomerAdditionalData;
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
public class BRERequest {
    @NotNull
    private ApplicationDetails applicationDetails;
    @NotNull
    private LoanRiskVariables loanRiskVariables;
    @NotNull
    private CustomerAdditionalData customerAdditionalData;
    @NotNull
    private CustomerPersonalDetails customerPersonalDetails;
    @NotNull
    private Map<KycDocType,KYCDocuments> kycDocuments;
    @NotNull
    private CustomerShopDetails customerShopDetails;
    @NotNull
    private BankDetails bankDetails;
    @NotNull
    private BusinessDetails businessDetails;
}