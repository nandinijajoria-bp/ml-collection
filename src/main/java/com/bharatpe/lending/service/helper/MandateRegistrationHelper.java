package com.bharatpe.lending.service.helper;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.Handler.EnachHandler;
import com.bharatpe.lending.common.dao.AutoPayUPIDao;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.dao.LendingRiskVariablesSnapshotDao;
import com.bharatpe.lending.common.dao.LoanDpdDao;
import com.bharatpe.lending.common.dto.LendingNachBankResponseDTO;
import com.bharatpe.lending.common.entity.AutoPayUPI;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.entity.LendingRiskVariablesSnapshot;
import com.bharatpe.lending.common.entity.LoanDpd;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.common.query.entity.LendingPaymentScheduleSlave;
import com.bharatpe.lending.common.service.merchant.dto.BankDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.dao.AutopayUpiConfigDao;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.entity.AutopayUPIConfig;
import com.bharatpe.lending.enums.LoanStatus;
import com.bharatpe.lending.lendingplatform.lms.constant.Constants;
import com.bharatpe.lending.util.LoanUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.Date;
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
    private final AutopayUpiConfigDao autopayUpiConfigDao;
    private final LendingRiskVariablesSnapshotDao lendingRiskVariablesSnapshotDao;
    private final LoanDpdDao loanDpdDao;
    private final LoanUtil loanUtil;
    private final DateTimeUtil dateTimeUtil;

    @Value("${active.loan.autopay.eligible.merchant.ids:}")
    private List<Long> upiAutoPayEligibleMerchantIds;
    @Value("${autopay.upi.cutoff.date:}")
    String autopayUpiCutoffDate;
    @Value("${autopay.upi.dpd.threshold:90}")
    Integer autoPayUpiDpdThreshold;

    public MandateRegistrationHelper(EnachHandler enachHandler, MerchantService merchantService, LendingApplicationDao lendingApplicationDao, LendingApplicationDetailsDao lendingApplicationDetailsDao, AutoPayUPIDao autoPayUPIDao,
                                     AutopayUpiConfigDao autopayUpiConfigDao,
                                     LendingRiskVariablesSnapshotDao lendingRiskVariablesSnapshotDao,
                                     LoanDpdDao loanDpdDao,
                                     LoanUtil loanUtil,
                                     DateTimeUtil dateTimeUtil) {
        this.enachHandler = enachHandler;
        this.merchantService = merchantService;
        this.lendingApplicationDao = lendingApplicationDao;
        this.lendingApplicationDetailsDao = lendingApplicationDetailsDao;
        this.autoPayUPIDao = autoPayUPIDao;
        this.autopayUpiConfigDao = autopayUpiConfigDao;
        this.lendingRiskVariablesSnapshotDao = lendingRiskVariablesSnapshotDao;
        this.loanDpdDao = loanDpdDao;
        this.loanUtil = loanUtil;
        this.dateTimeUtil = dateTimeUtil;
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
        if(!upiAutoPayEligibleMerchantIds.contains(activeApplication.get().getMerchantId())){
            return false;
        }

        LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findByApplicationId(applicationId);
        if (ObjectUtils.isEmpty(lendingApplicationDetails)) {
            log.error("LendingApplicationDetails is null for applicationId: {}", applicationId);
            return false;
        }

        LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = lendingRiskVariablesSnapshotDao.findByApplicationId(applicationId);
        if (ObjectUtils.isEmpty(lendingRiskVariablesSnapshot)) {
            log.error("LendingRiskVariablesSnapshot is null for applicationId: {}", applicationId);
            return false;
        }

        AutoPayUPI autoPayUPI = autoPayUPIDao.findByApplicationIdAndStatus(applicationId, Status.ACTIVE.name());
        LendingApplication lendingApplication = activeApplication.get();
        AutopayUPIConfig autopayUPIConfig = autopayUpiConfigDao.findAutoPayUpiConfigByLenderAndLoanSegment(lendingApplication.getLender(), lendingRiskVariablesSnapshot.getLoanSegment());lendingApplication.getLender().equals(autopayUPIConfig.getLender());
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

        Date cutoffDate = loanUtil.parseDate(autopayUpiCutoffDate);
        Date tentativeClosing = lps.getTentativeClosingDate();

        if (cutoffDate == null || tentativeClosing == null) {
            log.error("Tentative closing date or cut-off date is null for application {}", lendingApplication.getId());
            return false;
        }

        Date calculatedDate = dateTimeUtil.getDatePlusDays(tentativeClosing, dpd * 60);
        log.info("tentative closing date: {}, dpd: {}, threshold date: {}", tentativeClosing, dpd, calculatedDate);
        return Constants.DISBURSED_LOAN.equals(lendingApplication.getLoanDisbursalStatus())
                && LoanStatus.ACTIVE.name().equals(lps.getStatus())
                && ObjectUtils.isEmpty(autoPayUPI)
                && lendingApplication.getLender().equalsIgnoreCase(autopayUPIConfig.getLender())
                && dpd < autoPayUpiDpdThreshold
                && calculatedDate.compareTo(cutoffDate) >= 0
                && lendingRiskVariablesSnapshot.getLoanSegment().equalsIgnoreCase(autopayUPIConfig.getLoanSegment());
    }
}
