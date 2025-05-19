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
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.CustomerAddressDetails;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.CustomerPersonalDetails;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.LoanRiskVariables;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.NachDetails;
import com.bharatpe.lending.lendingplatform.nbfc.dto.request.UpdateLeadRequest;
import com.bharatpe.lending.lendingplatform.nbfc.service.builder.pojo.ApplicationDetailsBuilder;
import com.bharatpe.lending.lendingplatform.nbfc.service.builder.pojo.CustomerAdditionalDataBuilder;
import com.bharatpe.lending.lendingplatform.nbfc.service.builder.pojo.CustomerAddressDetailsBuilder;
import com.bharatpe.lending.lendingplatform.nbfc.service.builder.pojo.CustomerPersonalDetailsBuilder;
import com.bharatpe.lending.lendingplatform.nbfc.service.builder.pojo.LoanRiskVariablesBuilder;
import com.bharatpe.lending.lendingplatform.nbfc.service.builder.pojo.NachDetailsBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UpdateLeadRequestBuilder {
    @Autowired
    private ApplicationDetailsBuilder applicationDetailsBuilder;
    @Autowired
    private LoanRiskVariablesBuilder loanRiskVariablesBuilder;
    @Autowired
    private CustomerAdditionalDataBuilder customerAdditionalDataBuilder;
    @Autowired
    private CustomerPersonalDetailsBuilder customerPersonalDetailsBuilder;
    @Autowired
    private CustomerAddressDetailsBuilder customerAddressDetailsBuilder;
    @Autowired
    private NachDetailsBuilder nachDetailsBuilder;
    @Autowired
    private LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;
    @Autowired
    private MerchantService merchantService;
    @Autowired
    private LendingRiskVariablesSnapshotDao lendingRiskVariablesSnapshotDao;

    public UpdateLeadRequest buildRequest(LendingApplication lendingApplication) {
        LendingApplicationLenderDetails lendingApplicationLenderDetails =
                lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(
                        lendingApplication.getId(), Status.ACTIVE.name(), lendingApplication.getLender());
        LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot =
                lendingRiskVariablesSnapshotDao.findByApplicationId(lendingApplication.getId());
        BasicDetailsDto basicDetailsDto =
                merchantService.fetchMerchantBasicDetails(lendingApplication.getMerchantId()).orElse(null);

        ApplicationDetails applicationDetails = applicationDetailsBuilder.buildApplicationDetails(lendingApplication, lendingApplicationLenderDetails);
        LoanRiskVariables loanRiskVariables = loanRiskVariablesBuilder.buildLoanRiskVariables(lendingApplication, lendingRiskVariablesSnapshot);
        CustomerAdditionalData customerAdditionalData = customerAdditionalDataBuilder.buildCustomerAdditionalData(lendingApplication, lendingRiskVariablesSnapshot, basicDetailsDto);
        CustomerPersonalDetails customerPersonalDetails = customerPersonalDetailsBuilder.buildCustomerPersonalDetails(lendingApplication, basicDetailsDto);
        CustomerAddressDetails customerAddressDetails = customerAddressDetailsBuilder.buildCustomerAddressDetails(lendingApplication);
        NachDetails nachDetails = nachDetailsBuilder.buildNachDetails(lendingApplication);
        return UpdateLeadRequest.builder()
                .applicationDetails(applicationDetails)
                .loanRiskVariables(loanRiskVariables)
                .customerAdditionalData(customerAdditionalData)
                .customerPersonalDetails(customerPersonalDetails)
                .customerAddressDetails(customerAddressDetails)
                .nachDetails(nachDetails)
                .build();
    }
}
