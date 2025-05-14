package com.bharatpe.lending.lendingplatform.nbfc.service.builder.request;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.common.service.merchant.constants.Constants;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.dto.MerchantDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.ApplicationDetails;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.BankDetails;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.CustomerAddressDetails;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.CustomerPersonalDetails;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.KYCDocuments;
import com.bharatpe.lending.lendingplatform.nbfc.dto.request.CreateLeadRequest;
import com.bharatpe.lending.lendingplatform.nbfc.enums.KycDocType;
import com.bharatpe.lending.lendingplatform.nbfc.service.builder.pojo.ApplicationDetailsBuilder;
import com.bharatpe.lending.lendingplatform.nbfc.service.builder.pojo.BankDetailsBuilder;
import com.bharatpe.lending.lendingplatform.nbfc.service.builder.pojo.CustomerAddressDetailsBuilder;
import com.bharatpe.lending.lendingplatform.nbfc.service.builder.pojo.CustomerPersonalDetailsBuilder;
import com.bharatpe.lending.lendingplatform.nbfc.service.builder.pojo.KYCDocumentsBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Map;

@Service
public class CreateLeadRequestBuilder {
    @Autowired
    private ApplicationDetailsBuilder applicationDetailsBuilder;
    @Autowired
    private BankDetailsBuilder bankDetailsBuilder;
    @Autowired
    private CustomerAddressDetailsBuilder customerAddressDetailsBuilder;
    @Autowired
    private CustomerPersonalDetailsBuilder customerPersonalDetailsBuilder;
    @Autowired
    private KYCDocumentsBuilder kycDocumentsBuilder;
    @Autowired
    private LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;
    @Autowired
    private MerchantService merchantService;

    public CreateLeadRequest buildRequest(LendingApplication lendingApplication) {
        LendingApplicationLenderDetails lendingApplicationLenderDetails =
                lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(
                        lendingApplication.getId(), Status.ACTIVE.name(), lendingApplication.getLender());
        final MerchantDetailsDto merchantDetails =
                merchantService.fetchMerchantDetails(lendingApplication.getMerchantId(), Arrays.asList(
                        Constants.MerchantUtil.Scope.BANK_DETAIL,
                        Constants.MerchantUtil.Scope.MERCHANT_USER
                ));
        BasicDetailsDto basicDetailsDto =
                merchantService.fetchMerchantBasicDetails(lendingApplication.getMerchantId()).orElse(null);
        ApplicationDetails applicationDetails = applicationDetailsBuilder.buildApplicationDetails(lendingApplication, lendingApplicationLenderDetails);
        BankDetails bankDetails = bankDetailsBuilder.buildBankDetails(lendingApplication, merchantDetails);
        CustomerAddressDetails customerAddressDetails = customerAddressDetailsBuilder.buildCustomerAddressDetails(lendingApplication);
        CustomerPersonalDetails customerPersonalDetails = customerPersonalDetailsBuilder.buildCustomerPersonalDetails(lendingApplication, basicDetailsDto);
        Map<KycDocType, KYCDocuments> kycDocuments = kycDocumentsBuilder.buildKYCDocuments(lendingApplication);
        return CreateLeadRequest.builder()
                .applicationDetails(applicationDetails)
                .bankDetails(bankDetails)
                .customerAddressDetails(customerAddressDetails)
                .customerPersonalDetails(customerPersonalDetails)
                .kycDocuments(kycDocuments)
                .build();
    }
}
