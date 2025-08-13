package com.bharatpe.lending.lendingplatform.nbfc.service.builder.request;

import com.bharatpe.common.entities.LendingApplication;
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
import com.bharatpe.lending.lendingplatform.nbfc.dto.request.VKYCRequest;
import com.bharatpe.lending.lendingplatform.nbfc.service.builder.pojo.ApplicationDetailsBuilder;
import com.bharatpe.lending.lendingplatform.nbfc.service.builder.pojo.CustomerAdditionalDataBuilder;
import com.bharatpe.lending.lendingplatform.nbfc.service.builder.pojo.CustomerPersonalDetailsBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class VKYCRequestBuilder {
    private final ApplicationDetailsBuilder applicationDetailsBuilder;
    private final CustomerPersonalDetailsBuilder customerPersonalDetailsBuilder;
    private final CustomerAdditionalDataBuilder customerAdditionalDataBuilder;
    private final LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;
    private final MerchantService merchantService;
    private final LendingRiskVariablesSnapshotDao lendingRiskVariablesSnapshotDao;


    public VKYCRequest buildRequest(LendingApplication lendingApplication) {
        LendingApplicationLenderDetails lendingApplicationLenderDetails =
                lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(
                        lendingApplication.getId(), Status.ACTIVE.name(), lendingApplication.getLender());

        BasicDetailsDto basicDetailsDto =
                merchantService.fetchMerchantBasicDetails(lendingApplication.getMerchantId()).orElse(null);

        LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot =
                lendingRiskVariablesSnapshotDao.findByApplicationId(lendingApplication.getId());

        ApplicationDetails applicationDetails = applicationDetailsBuilder.buildApplicationDetails(lendingApplication, lendingApplicationLenderDetails);
        CustomerPersonalDetails customerPersonalDetails = customerPersonalDetailsBuilder.buildCustomerPersonalDetails(lendingApplication, basicDetailsDto);
        CustomerAdditionalData customerAdditionalData = customerAdditionalDataBuilder.buildCustomerAdditionalData(lendingApplication, lendingRiskVariablesSnapshot, basicDetailsDto);
        return VKYCRequest.builder()
                .applicationDetails(applicationDetails)
                .customerAdditionalData(customerAdditionalData)
                .customerPersonalDetails(customerPersonalDetails)
                .build();
    }
}
