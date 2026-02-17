package com.bharatpe.lending.loanV3.services.associationsV2.muthoot.impl;

import com.bharatpe.lending.common.dao.LendingLoanInsuranceDao;
import com.bharatpe.lending.common.entity.LendingLoanInsurance;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.loanV2.service.InsuranceService;
import com.bharatpe.lending.loanV3.dto.response.muthoot.MFDisbursalCallbackDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.util.Date;

import static com.bharatpe.lending.constant.InsuranceConstant.SELECTED;

@Slf4j
@Service
@RequiredArgsConstructor
public class MFInsuranceDocAsyncService {

    private  final LendingLoanInsuranceDao lendingLoanInsuranceDao;
    private final InsuranceService insuranceService;

    private static final String INSURANCE_CERTIFICATE = "INSURANCE_CERTIFICATE";

    @Async("lenderPoolTaskExecutor")
    public void addInsurancePolicyDocInDB(MFDisbursalCallbackDTO.Insurance insurance, String applicationId) {
        log.info("Adding insurance policy document for applicationId: {}", applicationId);
        try {
            MDC.put("requestId", applicationId);

            if(CollectionUtils.isEmpty(insurance.getDocuments()) || !insurance.getDocuments().get(0).getDocName().equalsIgnoreCase(INSURANCE_CERTIFICATE)) {
                log.info("No insurance documents found in the callback for applicationId: {}", applicationId);
                return;
            }

            LendingLoanInsurance lendingLoanInsurance = lendingLoanInsuranceDao.findByApplicationIdAndLenderAndStatus(
                    Long.valueOf(applicationId), Lender.MUTHOOT.name(), SELECTED);

            if(ObjectUtils.isEmpty(lendingLoanInsurance)) {
                log.info("No lending loan insurance record found for applicationId: {}", applicationId);
                return;
            }

            insuranceService.downloadInsuranceDocDetails(insurance.getDocuments().get(0).getUrl(), new Date(), new Date(), Long.valueOf(applicationId), Lender.MUTHOOT.name(), lendingLoanInsurance);
            log.info("Successfully added insurance policy document for applicationId: {}", applicationId);
        } catch (Exception e) {
            log.error("Exception occurred while updating insurance policy document for applicationId: {}, error: {}", applicationId, e.getMessage());
        }finally {
            MDC.clear();
        }
    }
}
