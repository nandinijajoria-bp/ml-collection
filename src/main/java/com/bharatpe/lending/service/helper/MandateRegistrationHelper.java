package com.bharatpe.lending.service.helper;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.Handler.EnachHandler;
import com.bharatpe.lending.common.dao.AutoPayUPIDao;
import com.bharatpe.lending.dao.AutoPayUPIMerchantsDao;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.dto.LendingNachBankResponseDTO;
import com.bharatpe.lending.common.entity.AutoPayUPI;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.common.query.entity.LendingPaymentScheduleSlave;
import com.bharatpe.lending.common.service.merchant.dto.BankDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.enums.LoanStatus;
import com.bharatpe.lending.lendingplatform.lms.constant.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.List;
import java.util.Optional;

@Component
@Slf4j
public class MandateRegistrationHelper {
    private final EnachHandler enachHandler;
    private final MerchantService merchantService;
    private final LendingApplicationDao lendingApplicationDao;
    private final LendingApplicationDetailsDao lendingApplicationDetailsDao;
    private final AutoPayUPIDao autoPayUPIDao;
    private final AutoPayUPIMerchantsDao autoPayUPIMerchantsDao;

//    @Value("${active.loan.autopay.eligible.merchant.ids:}")
//    private List<Long> upiAutoPayEligibleMerchantIds;

    public MandateRegistrationHelper(EnachHandler enachHandler, MerchantService merchantService, LendingApplicationDao lendingApplicationDao, LendingApplicationDetailsDao lendingApplicationDetailsDao, AutoPayUPIDao autoPayUPIDao, AutoPayUPIMerchantsDao autoPayUPIMerchantsDao) {
        this.enachHandler = enachHandler;
        this.merchantService = merchantService;
        this.lendingApplicationDao = lendingApplicationDao;
        this.lendingApplicationDetailsDao = lendingApplicationDetailsDao;
        this.autoPayUPIDao = autoPayUPIDao;
        this.autoPayUPIMerchantsDao = autoPayUPIMerchantsDao;
    }

    public boolean isMerchantNachableForMode(long merchantId, String nachMode) {
        final Optional<BankDetailsDto> bankDetailsDtoOptional = merchantService.fetchMerchantBankDetails(merchantId);
        BankDetailsDto merchantBankDetail = null;
        if (bankDetailsDtoOptional.isPresent())
            merchantBankDetail = bankDetailsDtoOptional.get();
        if (merchantBankDetail == null) return true;
        LendingNachBankResponseDTO lendingNachBank = enachHandler.findByIfscAndMode(merchantBankDetail.getIfsc().substring(0, 4), nachMode);
        return lendingNachBank != null;
    }

    public boolean isAutopayRequiredForActiveApplication(LendingPaymentScheduleSlave lps) {
        if (ObjectUtils.isEmpty(lps)) {
            log.error("LendingPaymentScheduleSlave is null");
            return false;
        }
        long applicationId = lps.getApplicationId();

        Optional<LendingApplication> activeApplication = lendingApplicationDao.findById(applicationId);
        if (!activeApplication.isPresent()) {
            log.error("No active application found for applicationId: {}", applicationId);
            return false;
        }
//        if(!upiAutoPayEligibleMerchantIds.contains(activeApplication.get().getMerchantId())){
//            return false;
//        }
        if (!autoPayUPIMerchantsDao.existsByMerchantId(activeApplication.get().getMerchantId())) {
            return false;
        }

        LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findByApplicationId(applicationId);
        if (ObjectUtils.isEmpty(lendingApplicationDetails)) {
            log.error("LendingApplicationDetails is null for applicationId: {}", applicationId);
            return false;
        }

        AutoPayUPI autoPayUPI = autoPayUPIDao.findByApplicationIdAndStatus(applicationId, Status.ACTIVE.name());
        LendingApplication lendingApplication = activeApplication.get();
        return Constants.DISBURSED_LOAN.equals(lendingApplication.getLoanDisbursalStatus())
                && LoanStatus.ACTIVE.name().equals(lps.getStatus())
                && ObjectUtils.isEmpty(autoPayUPI);

    }

}
