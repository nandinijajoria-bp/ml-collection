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
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.*;
import com.bharatpe.lending.lendingplatform.nbfc.dto.request.KYCDocumentUploadRequest;
import com.bharatpe.lending.lendingplatform.nbfc.enums.KycDocType;
import com.bharatpe.lending.lendingplatform.nbfc.service.builder.pojo.*;
import com.bharatpe.lending.loanV3.dto.CKycResponseDto;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Slf4j
public class KYCDocumentUploadRequestBuilder {
    @Autowired
    private ApplicationDetailsBuilder applicationDetailsBuilder;
    @Autowired
    private CustomerAdditionalDataBuilder customerAdditionalDataBuilder;
    @Autowired
    private KYCDocumentsBuilder kycDocumentsBuilder;
    @Autowired
    private LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;
    @Autowired
    private MerchantService merchantService;
    @Autowired
    private LendingRiskVariablesSnapshotDao lendingRiskVariablesSnapshotDao;
    @Autowired
    private CustomerPersonalDetailsBuilder customerPersonalDetailsBuilder;
    @Autowired
    private CustomerAddressDetailsBuilder customerAddressDetailsBuilder;
    @Autowired
    private CSCustomerAddressDataBuilder csCustomerAddressDataBuilder;
    @Autowired
    LendingApplicationKycDetailsDao lendingApplicationKycDetailsDao;
    @Autowired
    KycUtils kycUtils;


    public KYCDocumentUploadRequest buildRequest(LendingApplication lendingApplication) {
        LendingApplicationLenderDetails lendingApplicationLenderDetails =
                lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(
                        lendingApplication.getId(), Status.ACTIVE.name(), lendingApplication.getLender());
        LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot =
                lendingRiskVariablesSnapshotDao.findByApplicationId(lendingApplication.getId());
        BasicDetailsDto basicDetailsDto =
                merchantService.fetchMerchantBasicDetails(lendingApplication.getMerchantId()).orElse(null);

        CKycResponseDto cKycResponseDto = kycUtils.getKycData(lendingApplication.getMerchantId());

        ApplicationDetails applicationDetails = applicationDetailsBuilder.buildApplicationDetails(lendingApplication, lendingApplicationLenderDetails);
        CustomerAdditionalData customerAdditionalData = customerAdditionalDataBuilder.buildCustomerAdditionalData(lendingApplication, lendingRiskVariablesSnapshot, basicDetailsDto);
        Map<KycDocType, KYCDocuments> kycDocuments = kycDocumentsBuilder.buildKYCDocuments(lendingApplication);
        CustomerPersonalDetails customerPersonalDetails = customerPersonalDetailsBuilder.buildCustomerPersonalDetails(lendingApplication, basicDetailsDto);
        CustomerAddressDetails customerAddressDetails = customerAddressDetailsBuilder.buildCustomerAddressDetails(lendingApplication);
        LinkedHashMap<String, Object> identifiers = new LinkedHashMap<>();
        CSCustomerAdditionalData csCustomerAdditionalData = csCustomerAddressDataBuilder.buildCSCustomerAdditipnalData(cKycResponseDto, Long.valueOf(applicationDetails.getApplicationId()), lendingApplication.getMerchantId());
        String base64Xml = Base64.getEncoder().encodeToString(cKycResponseDto.getPoaString().getBytes(StandardCharsets.UTF_8));
        identifiers.put("adharXML", base64Xml);
        identifiers.put("selfie", cKycResponseDto.getSelfieString());
        identifiers.put("partnerLoanId", lendingApplication.getExternalLoanId());

        return KYCDocumentUploadRequest.builder()
                .applicationDetails(applicationDetails)
                .customerAdditionalData(customerAdditionalData)
                .kycDocuments(kycDocuments)
                .customerPersonalDetails(customerPersonalDetails)
                .customerAddressDetails(customerAddressDetails)
                .csCustomerAdditionalData(csCustomerAdditionalData)
                .identifier(identifiers)
                .build();
    }

}
