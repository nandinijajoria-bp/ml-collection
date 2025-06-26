package com.bharatpe.lending.lendingplatform.nbfc.service.builder.request;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationKycDetailsDao;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.dao.LendingRiskVariablesSnapshotDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.entity.LendingRiskVariablesSnapshot;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.ApplicationDetails;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.CustomerAdditionalData;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.CustomerPersonalDetails;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.KYCDocuments;
import com.bharatpe.lending.lendingplatform.nbfc.dto.request.KYCRequest;
import com.bharatpe.lending.lendingplatform.nbfc.enums.KycDocType;
import com.bharatpe.lending.lendingplatform.nbfc.service.builder.pojo.ApplicationDetailsBuilder;
import com.bharatpe.lending.lendingplatform.nbfc.service.builder.pojo.CustomerAdditionalDataBuilder;
import com.bharatpe.lending.lendingplatform.nbfc.service.builder.pojo.CustomerPersonalDetailsBuilder;
import com.bharatpe.lending.lendingplatform.nbfc.service.builder.pojo.KYCDocumentsBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class KYCRequestBuilder {
    @Autowired
    private ApplicationDetailsBuilder applicationDetailsBuilder;
    @Autowired
    private CustomerAdditionalDataBuilder customerAdditionalDataBuilder;
    @Autowired
    private CustomerPersonalDetailsBuilder customerPersonalDetailsBuilder;
    @Autowired
    private KYCDocumentsBuilder kycDocumentsBuilder;
    @Autowired
    private LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;
    @Autowired
    private MerchantService merchantService;
    @Autowired
    private LendingRiskVariablesSnapshotDao lendingRiskVariablesSnapshotDao;

    public KYCRequest buildRequest(LendingApplication lendingApplication) {
        LendingApplicationLenderDetails lendingApplicationLenderDetails =
                lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(
                        lendingApplication.getId(), Status.ACTIVE.name(), lendingApplication.getLender());
        LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot =
                lendingRiskVariablesSnapshotDao.findByApplicationId(lendingApplication.getId());
        BasicDetailsDto basicDetailsDto =
                merchantService.fetchMerchantBasicDetails(lendingApplication.getMerchantId()).orElse(null);

        ApplicationDetails applicationDetails = applicationDetailsBuilder.buildApplicationDetails(lendingApplication, lendingApplicationLenderDetails);
        CustomerAdditionalData customerAdditionalData = customerAdditionalDataBuilder.buildCustomerAdditionalData(lendingApplication, lendingRiskVariablesSnapshot, basicDetailsDto);
        CustomerPersonalDetails customerPersonalDetails = customerPersonalDetailsBuilder.buildCustomerPersonalDetails(lendingApplication, basicDetailsDto);
        Map<KycDocType, KYCDocuments> kycDocuments = kycDocumentsBuilder.buildKYCDocuments(lendingApplication);
        return KYCRequest.builder()
                .applicationDetails(applicationDetails)
                .customerAdditionalData(customerAdditionalData)
                .customerPersonalDetails(customerPersonalDetails)
                .kycDocuments(kycDocuments)
                .build();
    }
}
