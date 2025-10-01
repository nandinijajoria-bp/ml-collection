package com.bharatpe.lending.service.helper;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.Handler.EnachHandler;
import com.bharatpe.lending.common.dao.AutoPayUPIDao;
import com.bharatpe.lending.dao.AutoPayUPIMerchantsDao;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.dao.LendingRiskVariablesSnapshotDao;
import com.bharatpe.lending.common.dao.LoanDpdDao;
import com.bharatpe.lending.common.dto.LendingNachBankResponseDTO;
import com.bharatpe.lending.common.dto.MerchantNachDetailsResponseDTO;
import com.bharatpe.lending.common.entity.AutoPayUPI;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.enums.MandateType;
import com.bharatpe.lending.common.entity.LendingRiskVariablesSnapshot;
import com.bharatpe.lending.common.entity.LoanDpd;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.common.query.entity.LendingPaymentScheduleSlave;
import com.bharatpe.lending.common.service.merchant.dto.BankDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.dao.AutopayUpiConfigDao;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.enums.EnachMode;
import com.bharatpe.lending.entity.AutopayUPIConfig;
import com.bharatpe.lending.enums.LoanStatus;
import com.bharatpe.lending.lendingplatform.lms.constant.Constants;
import lombok.RequiredArgsConstructor;
import com.bharatpe.lending.loanV3.revamp.util.DateUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.BooleanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class MandateRegistrationHelper {
    private final EnachHandler enachHandler;
    private final MerchantService merchantService;
    private final LendingApplicationDao lendingApplicationDao;
    private final LendingApplicationDetailsDao lendingApplicationDetailsDao;
    private final AutoPayUPIDao autoPayUPIDao;
    private final AutoPayUPIMerchantsDao autoPayUPIMerchantsDao;
    private final AutopayUpiConfigDao autopayUpiConfigDao;
    private final LendingRiskVariablesSnapshotDao lendingRiskVariablesSnapshotDao;
    private final LoanDpdDao loanDpdDao;

//    @Value("${active.loan.autopay.eligible.merchant.ids:}")
//    private List<Long> upiAutoPayEligibleMerchantIds;
    @Value("${active.loan.autopay.eligible.merchant.ids:}")
    private List<Long> upiAutoPayEligibleMerchantIds;
    @Value("${autopay.upi.cutoff.date:}")
    String autopayUpiCutoffDate;
    @Value("${autopay.upi.dpd.threshold:90}")
    Integer autoPayUpiDpdThreshold;

    public boolean isDigioUpiCase(LendingApplicationDetails lendingApplicationDetails){
        if(Objects.nonNull(lendingApplicationDetails) && MandateType.DIGIO_UPI.equals(lendingApplicationDetails.getMandateType())){
            log.info("eligible for digio upiautopay");
            return true;
        }
        return false;
    }

    public boolean isDigioUpiCase(LendingApplication lendingApplication){
        LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(lendingApplication.getId());
        return isDigioUpiCase(lendingApplicationDetails);
    }

    public boolean isDigioUpiDone(Long merchantId, Long applicationId) {
        MerchantNachDetailsResponseDTO nachDetail = enachHandler.findSuccessEnach(merchantId, applicationId);
        log.info("nach details fetched for applicationId:{}, is: {}", applicationId, nachDetail);
        if(Objects.isNull(nachDetail)){
            return false;
        }
        if(EnachMode.UPI.name().equals(nachDetail.getMode())){
            log.info("found upimode nach for applicationId: {}", applicationId);
            return true;
        }
        return false;
    }

    public boolean isMerchantNachableForMode(long merchantId, String nachMode) {
        final Optional<BankDetailsDto> bankDetailsDtoOptional = merchantService.fetchMerchantBankDetails(merchantId);
        BankDetailsDto merchantBankDetail = null;
        if (bankDetailsDtoOptional.isPresent())
            merchantBankDetail = bankDetailsDtoOptional.get();
        if (merchantBankDetail == null) return true;
        LendingNachBankResponseDTO lendingNachBank = enachHandler.findByIfscAndMode(merchantBankDetail.getIfsc().substring(0, 4), nachMode);
        return lendingNachBank != null && BooleanUtils.isTrue(lendingNachBank.getUpiMandate());
    }

    public boolean isAutopayRequiredForActiveApplication(LendingPaymentScheduleSlave lps) {
        log.info("Checks for autopay required of merchant: {}", lps.getMerchantId());
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

        AutoPayUPI autoPayUPI = autoPayUPIDao.findByApplicationIdAndStatus(applicationId, Status.ACTIVE.name());
        LendingApplication lendingApplication = activeApplication.get();

        LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findByApplicationId(applicationId);
        if (ObjectUtils.isEmpty(lendingApplicationDetails)) {
            log.error("LendingApplicationDetails is null for applicationId: {}", applicationId);
            return false;
        }

        boolean loanDpdChecksPassed = loanDpdChecks(lps, lendingApplication);
        log.info("loanDpdChecks result for {}: {}", lendingApplication.getMerchantId(), loanDpdChecksPassed);
        boolean baseCondition = Constants.DISBURSED_LOAN.equals(lendingApplication.getLoanDisbursalStatus())
                && ObjectUtils.isEmpty(autoPayUPI);

        return loanDpdChecksPassed
                ? baseCondition
                : baseCondition && autoPayUPIMerchantsDao.existsByMerchantId(activeApplication.get().getMerchantId());

    }


    public boolean loanDpdChecks(LendingPaymentScheduleSlave lps, LendingApplication lendingApplication) {
        LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = lendingRiskVariablesSnapshotDao.findByApplicationId(lps.getApplicationId());
        if (ObjectUtils.isEmpty(lendingRiskVariablesSnapshot)) {
            log.error("LendingRiskVariablesSnapshot is null for applicationId: {}", lps.getApplicationId());
            return false;
        }
        AutopayUPIConfig autopayUPIConfig = autopayUpiConfigDao.findAutoPayUpiConfigByLenderAndLoanSegmentAndStatus(lendingApplication.getLender(), lendingRiskVariablesSnapshot.getLoanSegment(), "ACTIVE");
        if(ObjectUtils.isEmpty(autopayUPIConfig)) {
            log.error("No entry found in autopay_upi_config for lender: {} & segment: {}", lendingApplication.getLender(), lendingRiskVariablesSnapshot.getLoanSegment());
            return false;
        }
        Optional<LoanDpd> loanDpdOptional = loanDpdDao.findTop1ByLoanIdOrderByIdDesc(lps.getId());
        int dpd = 0;
        if (!loanDpdOptional.isPresent()) {
            log.error("No entry in loan_dpd for loanId: {}", lps.getId());
        }else{
            dpd = loanDpdOptional.get().getDpd();
        }

        Date cutoffDate = DateUtils.parseDate(autopayUpiCutoffDate);
        Date tentativeClosing = lps.getTentativeClosingDate();

        if (cutoffDate == null || tentativeClosing == null) {
            log.error("Tentative closing date or cut-off date is null for application {}", lps.getApplicationId());
            return false;
        }
        Date calculatedDate = Date.from(tentativeClosing.toInstant().plus(dpd, ChronoUnit.DAYS));
        log.info("tentative closing date: {}, dpd: {}, threshold date: {}", tentativeClosing, dpd, calculatedDate);
        return lendingApplication.getLender().equalsIgnoreCase(autopayUPIConfig.getLender())
                && LoanStatus.ACTIVE.name().equals(lps.getStatus())
                && dpd < autoPayUpiDpdThreshold
                && calculatedDate.compareTo(cutoffDate) >= 0
                && lendingRiskVariablesSnapshot.getLoanSegment().equalsIgnoreCase(autopayUPIConfig.getLoanSegment());
    }

}
