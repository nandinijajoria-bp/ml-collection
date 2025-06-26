package com.bharatpe.lending.lendingplatform.nbfc.service.builder.request;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.dao.LendingRiskVariablesSnapshotDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.ApplicationDetails;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.LoanDocument;
import com.bharatpe.lending.lendingplatform.nbfc.dto.request.LoanDocumentDownloadRequest;
import com.bharatpe.lending.lendingplatform.nbfc.enums.DocType;
import com.bharatpe.lending.lendingplatform.nbfc.service.builder.pojo.ApplicationDetailsBuilder;
import com.bharatpe.lending.lendingplatform.nbfc.service.builder.pojo.CustomerAdditionalDataBuilder;
import com.bharatpe.lending.lendingplatform.nbfc.service.builder.pojo.LoanDocumentBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class LoanDocumentDownloadRequestBuilder {
    @Autowired
    private ApplicationDetailsBuilder applicationDetailsBuilder;
    @Autowired
    private LoanDocumentBuilder loanDocumentsBuilder;
    @Autowired
    private CustomerAdditionalDataBuilder customerAdditionalDataBuilder;
    @Autowired
    private LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;
    @Autowired
    private MerchantService merchantService;
    @Autowired
    private LendingRiskVariablesSnapshotDao lendingRiskVariablesSnapshotDao;

    public LoanDocumentDownloadRequest buildRequest(LendingApplication lendingApplication) {

        LendingApplicationLenderDetails lendingApplicationLenderDetails =
                lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(
                        lendingApplication.getId(), Status.ACTIVE.name(), lendingApplication.getLender());
        ApplicationDetails applicationDetails = applicationDetailsBuilder.buildApplicationDetails(lendingApplication, lendingApplicationLenderDetails);
        Map<DocType, LoanDocument> loanDocuments = loanDocumentsBuilder.buildLoanDocuments(lendingApplication);
        return LoanDocumentDownloadRequest.builder()
                .applicationDetails(applicationDetails)
                .loanDocuments(loanDocuments)
                .build();
    }
}
