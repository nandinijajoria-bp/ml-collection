package com.bharatpe.lending.loanV3.services.associationsV2.wrapper;


import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.services.associationsV2.AssociationServiceUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;
import java.util.Optional;

@Slf4j
@Service
public class RetryCallbackWrapperService {

    @Autowired
    AssociationServiceUtil associationServiceUtil;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Value("${lender.change.enabled:false}")
    Boolean enableLenderChange;

    public void retryCallback(NBFCResponseDTO nbfcResponse) {
        try {
            log.info("Retry callback received {}", nbfcResponse);
            LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest = createRecord(Long.valueOf(nbfcResponse.getApplicationId()));
            if(ObjectUtils.isEmpty(lenderAssociationDetailsRequest)) {
                log.info("lender association details not found for retry callback request {}", nbfcResponse);
                return;
            }
            associationServiceUtil.handleRetryCallback(nbfcResponse.getLender(), lenderAssociationDetailsRequest, nbfcResponse);
        } catch (Exception e) {
            log.error("Exception in consuming retry callback of {} for applicationId {}  {} {}", nbfcResponse.getLender(), nbfcResponse.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
    }

    private LenderAssociationDetailsRequestDto createRecord(Long applicationId) {
        LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest = new LenderAssociationDetailsRequestDto();
        lenderAssociationDetailsRequest.setApplicationId(applicationId);
        Optional<LendingApplication> lendingApplicationOptional = lendingApplicationDao.findById(applicationId);
        if (!lendingApplicationOptional.isPresent()) {
            log.info("No application found for application id : {}", applicationId);
            return null;
        }
        LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(applicationId, Status.ACTIVE.name(), lendingApplicationOptional.get().getLender());
        if (ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
            log.info("No lender association details found for lender {} of application id : {}", lendingApplicationOptional.get().getLender(), applicationId);
            return null;
        }
        LendingApplication lendingApplication = lendingApplicationOptional.get();
        lenderAssociationDetailsRequest.setLendingApplication(lendingApplication);
        lenderAssociationDetailsRequest.setLendingApplicationLenderDetails(lendingApplicationLenderDetails);
        lenderAssociationDetailsRequest.setMerchantId(lendingApplication.getMerchantId());
        lenderAssociationDetailsRequest.setManageState(Boolean.TRUE);
        lenderAssociationDetailsRequest.setModifyLender(enableLenderChange);
        return lenderAssociationDetailsRequest;
    }
}
