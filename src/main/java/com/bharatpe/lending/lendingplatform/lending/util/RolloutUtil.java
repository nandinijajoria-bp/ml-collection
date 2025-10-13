package com.bharatpe.lending.lendingplatform.lending.util;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.Constants.AutoPayStatusEnum;
import com.bharatpe.lending.common.dao.AutoPayUPIDao;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.dao.LmsLoanStatusDao;
import com.bharatpe.lending.common.entity.AutoPayUPI;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.entity.LmsLoanStatus;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.lendingplatform.nbfc.enums.Lender;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;


@Component
@Slf4j
@RequiredArgsConstructor
public class RolloutUtil {

	private List<String> topupLoans = Arrays.asList(LoanType.TOPUP.name(), LoanType.HALF_TOPUP.name(), LoanType.IO_TOPUP.name());

    // --- Feature: NBFC Flow ---
    @Value("${lending.platform.nbfc.eligible.lenders:TRILLIONLOANS,OXYZO}")
    private List<String> eligibleLenders;
    @Value("${lending.platform.nbfc.eligible.merchants:20000100}")
    private List<Long> eligibleMerchants;
    @Value("${lending.platform.nbfc.enable:false}")
    private boolean nbfcFlowEnable;

    // --- Feature: Underwriting Flow ---
    @Value("${lending.platform.underwriting.flow.enabled:false}")
    private boolean isNewUnderwritingFlowEnabled;
    @Value("${lending.platform.underwriting.flow.eligible.merchants:20000100}")
    private List<Long> eligibleUnderwritingMerchants;
    @Value("${lending.platform.underwriting.flow.merchant.rollout:0}")
    private int eligibleUnderwritingMerchantRollout;

    // --- Feature: OneLMS Flow ---
    @Value("${new.flow.eligible.lenders.onelms:TRILLIONLOANS}")
    private List<String> eligibleLendersForOneLms;
    @Value("${lending.platform.lms.eligible.merchants:20000100}")
    private List<Long> lmsEligibleMerchants;
    @Value("${lending.platform.lms.application.limit:2}")
    private int lendingPlatformLmsApplicationLimit;
    @Value("${lending.platform.lms.merchant.rollout:0}")
    private int eligibleLendingPlatformLmsMerchantRollout;
    @Value("${new.oneLms.flow.enable:false}")
    private boolean oneLmsFlowEnable;

    // --- Feature: Autopay ---
    @Value("${lending.platform.autopay.enable:false}")
    private boolean isAutoPayEnabled;
    @Value("${lending.platform.lms.autopay.upi.eligible.merchants:}")
    private List<Long> autoPayEligibleMerchants;
    @Value("${lending.platform.lms.autopay.upi.eligible.lenders:TRILLIONLOANS}")
    private List<String> lmsAutoPayEligibleLenders;
    @Value("${lending.platform.lms.autopay.upi.application.limit:0}")
    private int lmsAutoPayApplicationLimit;

    // --- Lender: CreditSaison ---
    @Value("${lending.platform.cs.nbfc.enable:false}")
    private boolean lendingPlatformCsNbfcFlowEnable;
    @Value("${lending.platform.cs.nbfc.merchant.rollout:0}")
    private int eligibleLendingPlatformCsNbfcMerchantRollout;
    @Value("${lending.platform.cs.nbfc.application.limit:2}")
    private int lendingPlatformCsNbfcApplicationLimit;
    @Value("${lending.platform.cs.vkyc.application.percent:1}")
    private int lendingPlatformCsVkycApplicationPercent;
    @Value("${lending.platform.cs.vkyc.eligible.merchants:20000100}")
    private List<Long> eligibleMerchantsForVkyc;
    @Value("${lending.platform.cs.vkyc.enable:false}")
    private boolean lendingPlatformCsVkycFlowEnable;

    // --- Lender: Oxyzo ---
    @Value("${lending.platform.oxyzo.enable:false}")
    private boolean lendingPlatformOxyzoEnable;
    @Value("${lending.platform.oxyzo.merchant.rollout:0}")
    private int eligibleLendingPlatformOxyzoMerchantRollout;
    @Value("${lending.platform.oxyzo.application.limit:0}")
    private int lendingPlatformOxyzoApplicationLimit;

    // --- Lender: Trillionloans ---
    @Value("${lending.platform.trillion.enable:false}")
    private boolean lendingPlatformTrillionloansEnable;
    @Value("${lending.platform.trillion.merchant.rollout:0}")
    private int eligibleLendingPlatformTrillionloansMerchantRollout;
    @Value("${lending.platform.trillion.application.limit:0}")
    private int lendingPlatformTrillionloansApplicationLimit;
    @Value("${lending.platform.lms.topup.eligibility:0}")
    private int oneLmsTopupRolloutPercent;


    @Value("${oxyzo.lending.platform.lms.application.limit:2}")
    private int oxyzoLendingPlatformLmsApplicationLimit;

    private final LmsLoanStatusDao lmsLoanStatusDao;
    private final LendingApplicationLenderDetailsDao laldDao;
    private final LendingApplicationDao lendingApplicationDao;
    private final KycUtils kycUtils;
    private final AutoPayUPIDao autoPayUPIDao;
    private final LendingPaymentScheduleDao lendingPaymentScheduleDao;

    public boolean lendingPlatformUnderwritingFLowApplicable(Long merchantId) {
        //config for internal merchants
        if (eligibleUnderwritingMerchants.contains(merchantId)){
            return true;
        }
        if (!isNewUnderwritingFlowEnabled) {
            log.info("Underwriting merchant is not eligible due to flag config: {}", merchantId);
            return false;
        }
        int merchantIdPercentage = merchantId.intValue() % 100;
        if (merchantIdPercentage > eligibleUnderwritingMerchantRollout) {
            log.info("Underwriting merchant is not eligible due to rollout percent: {}", merchantId);
            return false;
        }
        return true;
    }

	public boolean lendingPlatformNbfcFlowApplicable(Long merchantId) {

		LendingApplication lendingApplication = lendingApplicationDao.findBymerchantId(merchantId);

		if (ObjectUtils.isEmpty(lendingApplication)) {
			log.warn("Application not exist for merchantId: {}", merchantId);
			return false;
		}

		LendingApplicationLenderDetails lendingApplicationLenderDetails =
				laldDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(
						lendingApplication.getId(), Status.ACTIVE.name(), lendingApplication.getLender());

		if(!(ObjectUtils.isEmpty(lendingApplicationLenderDetails))) {
			log.info("Application: {} and rearch flow: {}", lendingApplication.getId(), lendingApplicationLenderDetails.getRearchFlow());
			return Boolean.TRUE.equals(lendingApplicationLenderDetails.getRearchFlow());
		}

		if (topupLoans.contains(lendingApplication.getLoanType())) {
			log.info("Application: {} not eligible for new flow due to Topup loan", lendingApplication.getId());
			return false;
		}

		if (!eligibleLenders.contains(lendingApplication.getLender())) {
			log.info("Lender not applicable for new flow applicationId: {}", lendingApplication.getId());
			return false;
		}

		if (!eligibleMerchants.isEmpty() && eligibleMerchants.contains(merchantId)) {
			log.info("Application Rolled out to New flow for applicationId: {}", lendingApplication.getId());
			return true;
		}
        if (!nbfcFlowEnable) {
            log.info("Merchant not eligible for new flow due to flag config, application: {}", lendingApplication.getId());
            return false;
        }
        if (Boolean.TRUE.equals(kycUtils.isEligibleForLenderKyc(
                lendingApplication.getLender(), lendingApplication.getMerchantId(), false))) {
            log.info("New flow for Merchant:{} is not eligible due to EKYC", merchantId);
            return false;
        }
        return checkLenderSpecificEligibility(lendingApplication, merchantId);
	}

    private boolean checkLenderSpecificEligibility(LendingApplication lendingApplication, Long merchantId) {
        String lender = lendingApplication.getLender();
        if (ObjectUtils.isEmpty(lender)) {
            log.info("Lender is null for applicationId: {}", lendingApplication.getId());
            return false;
        }

        switch (Lender.valueOf(lender)) {
            case CREDITSAISON:
                return isEligibleForCreditSaison(lendingApplication, merchantId);
            case OXYZO:
                return isEligibleForOxyzo(lendingApplication, merchantId);
            case TRILLIONLOANS:
                return isEligibleForTrillionloans(lendingApplication, merchantId);
            default:
                log.info("Lender not applicable for new flow: {}", lendingApplication.getId());
                return false;
        }
    }

    public boolean checkEligibilityForOneLmsLoans(String bpLoanId) {
        LendingApplication lendingApplication = lendingApplicationDao.findByExternalLoanId(bpLoanId);
        LmsLoanStatus lmsLoanStatus = lmsLoanStatusDao.findLoanByBpLoanIdAndStatus(lendingApplication.getExternalLoanId(), "FAILED");
        Long lmsLoanStatusCount = lmsLoanStatusDao.countAllLoanStatusRecords();
        Long oxyzoLmsLoanStatusCount = lendingPaymentScheduleDao.countOxyzoLoansOn1LMS();

         // Commenting at the moment as it might be required later.
//        AutoPayUPI autoPayUPI = autoPayUPIDao.findTop1ByApplicationIdAndStatusOrderByIdDesc(lendingApplication.getId(),
//                lendingApplication.getLender(), Arrays.asList(AutoPayStatusEnum.ACTIVE.name()));

        if (ObjectUtils.isEmpty(lendingApplication)) {
            return false;
        }
        if (topupLoans.contains(lendingApplication.getLoanType())) {
            log.info("Application: {} not eligible for new lms flow due to Topup loan", lendingApplication.getId());
            return false;
        }
        if (!ObjectUtils.isEmpty(lmsLoanStatus)) {
            return false;
        }
        if (!eligibleLendersForOneLms.contains(lendingApplication.getLender())) {
            log.info("Lender not eligible for new flow {}", lendingApplication.getLender());
            return false;
        }

        if (lmsEligibleMerchants.contains(lendingApplication.getMerchantId())) {
            log.info("Internal Merchant eligible for new flow");
            return true;
        }
        if (!oneLmsFlowEnable) {
            return false;
        }

        // Commenting at the moment as it might be required later.
//       if(!ObjectUtils.isEmpty(autoPayUPI)){
//            return autoPayEligibleForOneLmsFlow(autoPayUPI, lendingApplication);
//       }

        int merchantIdPercentage = lendingApplication.getMerchantId().intValue() % 100;
        if (merchantIdPercentage > eligibleLendingPlatformLmsMerchantRollout) {
            log.info("New flow for Merchant:{} is not eligible due to rollout percent", lendingApplication.getMerchantId());
            return false;
        }

        if("TRILLIONLOANS".equalsIgnoreCase(lendingApplication.getLender())) {
            if(lendingPlatformLmsApplicationLimit!=-1 && lmsLoanStatusCount>=lendingPlatformLmsApplicationLimit){
                log.info("New flow for bpLoanId:{} is not eligible due to application limit for TL", bpLoanId);
                return false;
            }
        }
        if("UGRO".equalsIgnoreCase(lendingApplication.getLender())) {
            if(lendingPlatformLmsApplicationLimit!=-1 && lmsLoanStatusCount>=lendingPlatformLmsApplicationLimit){
                log.info("New flow for bpLoanId:{} is not eligible due to application limit for UGRO", bpLoanId);
                return false;
            }
        }
        if("OXYZO".equalsIgnoreCase(lendingApplication.getLender())) {
            if(oxyzoLendingPlatformLmsApplicationLimit!=-1 && oxyzoLmsLoanStatusCount>=oxyzoLendingPlatformLmsApplicationLimit){
                log.info("New flow for bpLoanId:{} is not eligible due to application limit for OXYZO", bpLoanId);
                return false;
            }
        }

        log.info("New flow applicable for LMS");
        return true;
    }

    private boolean isEligibleForCreditSaison(LendingApplication lendingApplication, Long merchantId) {

        if (!lendingPlatformCsNbfcFlowEnable) {
            log.info("Merchant not eligible for new flow due to cs flag config, application: {}", lendingApplication.getId());
            return false;
        }

        int merchantIdPercentage = merchantId.intValue() % 100;
        if (merchantIdPercentage > eligibleLendingPlatformCsNbfcMerchantRollout) {
            log.info("CreditSaison New flow for Merchant:{} is not eligible due to rollout percent", merchantId);
            return false;
        }

        if (lendingPlatformCsNbfcApplicationLimit != -1) {
            LocalDate localDate = LocalDate.parse("2025-06-23");
            Date date = Date.from(localDate.atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant());
            long count = laldDao.countLendingApplicationLenderDetailsByRearchFlowAndCreatedAtGreaterThanAndLender(
                    true, date, "CREDITSAISON");
            log.info("CreditSaison New flow for Merchant:{} has count: {}", merchantId, count);
            if (count >= lendingPlatformCsNbfcApplicationLimit) {
                log.info("CreditSaison New flow for Merchant:{} is not eligible due to application limit", merchantId);
                return false;
            }
        }

        log.info("CreditSaison Application Rolled out to New flow for applicationId: {}", lendingApplication.getId());
        return true;
    }
    private boolean isEligibleForOxyzo(LendingApplication lendingApplication, Long merchantId) {
        if (!lendingPlatformOxyzoEnable) {
            log.info("Merchant not eligible for Oxyzo new flow due to flag config, application: {}", lendingApplication.getId());
            return false;
        }
        int merchantIdPercentage = merchantId.intValue() % 100;
        if (merchantIdPercentage > eligibleLendingPlatformOxyzoMerchantRollout) {
            log.info("Oxyzo New flow for Merchant:{} is not eligible due to rollout percent", merchantId);
            return false;
        }

        if (lendingPlatformOxyzoApplicationLimit != -1) {
            LocalDate localDate = LocalDate.parse("2025-06-23");
            Date date = Date.from(localDate.atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant());
            long count = laldDao.countLendingApplicationLenderDetailsByRearchFlowAndCreatedAtGreaterThanAndLender(
                    true, date, "OXYZO");
            log.info("Oxyzo New flow for Merchant:{} has count: {}", merchantId, count);
            if (count >= lendingPlatformOxyzoApplicationLimit) {
                log.info("Oxyzo New flow for Merchant:{} is not eligible due to application limit", merchantId);
                return false;
            }
        }

        log.info("Oxyzo Application Rolled out to New flow for applicationId: {}", lendingApplication.getId());
        return true;
    }
    private boolean isEligibleForTrillionloans(LendingApplication lendingApplication, Long merchantId) {
        if (!lendingPlatformTrillionloansEnable) {
            log.info("Merchant not eligible for Trillionloans new flow due to flag config, application: {}", lendingApplication.getId());
            return false;
        }

        int merchantIdPercentage = merchantId.intValue() % 100;
        if (merchantIdPercentage > eligibleLendingPlatformTrillionloansMerchantRollout) {
            log.info("Trillionloans New flow for Merchant:{} is not eligible due to rollout percent", merchantId);
            return false;
        }

        if (lendingPlatformTrillionloansApplicationLimit != -1) {
            LocalDate localDate = LocalDate.parse("2025-06-23");
            Date date = Date.from(localDate.atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant());
            long count = laldDao.countLendingApplicationLenderDetailsByRearchFlowAndCreatedAtGreaterThanAndLender(
                    true, date, "TRILLIONLOANS");
            log.info("Trillionloans New flow for Merchant:{} has count: {}", merchantId, count);
            if (count >= lendingPlatformTrillionloansApplicationLimit) {
                log.info("Trillionloans New flow for Merchant:{} is not eligible due to application limit", merchantId);
                return false;
            }
        }

        log.info("Trillionloans Application Rolled out to New flow for applicationId: {}", lendingApplication.getId());
        return true;
    }


    private boolean autoPayEligibleForOneLmsFlow(AutoPayUPI autoPayUPI, LendingApplication lendingApplication){
        if(isAutoPayEnabled && lmsAutoPayEligibleLenders.contains(lendingApplication.getLender())){
            long count  = lendingPaymentScheduleDao.countLmsAutoPayApplication();
            if(count<=lmsAutoPayApplicationLimit){
                return true;
            }
            return false;
        }
        return false;
    }

    public boolean isEligibleForCreditSaisonVkyc(Long merchantId) {
        log.info("CreditSaison vKYC: Checking eligibility for merchantId: {}", merchantId);

        if (!lendingPlatformCsVkycFlowEnable) {
            log.info("CreditSaison vKYC: is disabled completely");
            return false;
        }

        if (!lendingPlatformNbfcFlowApplicable(merchantId)) {
            log.info("CreditSaison vKYC: MerchantId {} is not eligible for vKYC as new NBFC flow is not applicable", merchantId);
            return false;
        }

        // Level 2: VKYC rollout check (independent of level 1's % logic)
        boolean isEligibleForVkyc = isEligibleByHashedRollout(merchantId, lendingPlatformCsVkycApplicationPercent, "CS_VKYC");

        log.info("CreditSaison vKYC: MerchantId {} VKYC rollout result: {}", merchantId, isEligibleForVkyc);
        return isEligibleForVkyc;
    }

    private boolean isEligibleByHashedRollout(Long merchantId, int percent, String rolloutKey) {
        if (percent <= 0) return false;
        if (percent >= 100) return true;

        int hash = Math.abs(Objects.hash(merchantId, rolloutKey));
        int bucket = hash % 100;

        return bucket < percent;
    }



    public boolean isEligibleForTopupLoan(Long merchantId) {
        return merchantId % 100 <= oneLmsTopupRolloutPercent;
    }
}
