package com.bharatpe.lending.lendingplatform.nbfc.service.builder.request;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationKycDetailsDao;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.dao.LendingRiskVariablesSnapshotDao;
import com.bharatpe.lending.common.entity.LendingApplicationKycDetails;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.entity.LendingRiskVariablesSnapshot;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.common.service.merchant.constants.Constants;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.dto.MerchantDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.ApplicationDetails;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.BankDetails;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.BusinessDetails;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.CustomerAdditionalData;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.CustomerPersonalDetails;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.CustomerShopDetails;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.KYCDocuments;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.LoanRiskVariables;
import com.bharatpe.lending.lendingplatform.nbfc.dto.request.BRERequest;
import com.bharatpe.lending.lendingplatform.nbfc.enums.KycDocType;
import com.bharatpe.lending.lendingplatform.nbfc.service.builder.pojo.ApplicationDetailsBuilder;
import com.bharatpe.lending.lendingplatform.nbfc.service.builder.pojo.BankDetailsBuilder;
import com.bharatpe.lending.lendingplatform.nbfc.service.builder.pojo.BusinessDetailsBuilder;
import com.bharatpe.lending.lendingplatform.nbfc.service.builder.pojo.CustomerAdditionalDataBuilder;
import com.bharatpe.lending.lendingplatform.nbfc.service.builder.pojo.CustomerPersonalDetailsBuilder;
import com.bharatpe.lending.lendingplatform.nbfc.service.builder.pojo.CustomerShopDetailsBuilder;
import com.bharatpe.lending.lendingplatform.nbfc.service.builder.pojo.KYCDocumentsBuilder;
import com.bharatpe.lending.lendingplatform.nbfc.service.builder.pojo.LoanRiskVariablesBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Map;

@Service
public class BRERequestBuilder {
    @Autowired
    private ApplicationDetailsBuilder applicationDetailsBuilder;
    @Autowired
    private LoanRiskVariablesBuilder loanRiskVariablesBuilder;
    @Autowired
    private CustomerAdditionalDataBuilder customerAdditionalDataBuilder;
    @Autowired
    private CustomerPersonalDetailsBuilder customerPersonalDetailsBuilder;
    @Autowired
    private KYCDocumentsBuilder kycDocumentsBuilder;
    @Autowired
    private CustomerShopDetailsBuilder customerShopDetailsBuilder;
    @Autowired
    private BankDetailsBuilder bankDetailsBuilder;
    @Autowired
    private BusinessDetailsBuilder businessDetailsBuilder;
    @Autowired
    private LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;
    @Autowired
    private MerchantService merchantService;
    @Autowired
    private LendingRiskVariablesSnapshotDao lendingRiskVariablesSnapshotDao;

    public BRERequest buildRequest(LendingApplication lendingApplication) {

        LendingApplicationLenderDetails lendingApplicationLenderDetails =
                lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(
                        lendingApplication.getId(), Status.ACTIVE.name(), lendingApplication.getLender());
        LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot =
                lendingRiskVariablesSnapshotDao.findByApplicationId(lendingApplication.getId());
        BasicDetailsDto basicDetailsDto =
                merchantService.fetchMerchantBasicDetails(lendingApplication.getMerchantId()).orElse(null);
        final MerchantDetailsDto merchantDetails =
                merchantService.fetchMerchantDetails(lendingApplication.getMerchantId(), Arrays.asList(
                        Constants.MerchantUtil.Scope.BANK_DETAIL,
                        Constants.MerchantUtil.Scope.MERCHANT_USER
                ));

        ApplicationDetails applicationDetails = applicationDetailsBuilder.buildApplicationDetails(lendingApplication, lendingApplicationLenderDetails);
        LoanRiskVariables loanRiskVariables = loanRiskVariablesBuilder.buildLoanRiskVariables(lendingApplication, lendingRiskVariablesSnapshot);
        CustomerAdditionalData customerAdditionalData = customerAdditionalDataBuilder.buildCustomerAdditionalData(lendingApplication, lendingRiskVariablesSnapshot, basicDetailsDto);
        CustomerPersonalDetails customerPersonalDetails = customerPersonalDetailsBuilder.buildCustomerPersonalDetails(lendingApplication, basicDetailsDto);
        Map<KycDocType, KYCDocuments> kycDocuments = kycDocumentsBuilder.buildKYCDocuments(lendingApplication);
        CustomerShopDetails customerShopDetails = customerShopDetailsBuilder.buildCustomerShopDetails(lendingApplication);
        BankDetails bankDetails = bankDetailsBuilder.buildBankDetails(lendingApplication, merchantDetails);
        BusinessDetails businessDetails = businessDetailsBuilder.buildBusinessDetails(lendingApplication, basicDetailsDto);
        return BRERequest.builder()
                .applicationDetails(applicationDetails)
                .loanRiskVariables(loanRiskVariables)
                .customerAdditionalData(customerAdditionalData)
                .customerPersonalDetails(customerPersonalDetails)
                .kycDocuments(kycDocuments)
                .customerShopDetails(customerShopDetails)
                .bankDetails(bankDetails)
                .businessDetails(businessDetails)
                .build();
    }
}
