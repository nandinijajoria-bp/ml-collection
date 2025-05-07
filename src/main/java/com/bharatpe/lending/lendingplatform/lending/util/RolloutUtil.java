package com.bharatpe.lending.lendingplatform.lending.util;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.dao.LmsLoanStatusDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.entity.LmsLoanStatus;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.enums.LoanType;
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
import java.util.Optional;


@Component
@Slf4j
@RequiredArgsConstructor
public class RolloutUtil {

	private List<String> topupLoans = Arrays.asList(LoanType.TOPUP.name(), LoanType.HALF_TOPUP.name(), LoanType.IO_TOPUP.name());

	@Value("${lending.platform.nbfc.eligible.lenders:TRILLIONLOANS}")
	private List<String> eligibleLenders;
	@Value("${lending.platform.nbfc.eligible.merchants:20000100}")
	private List<Long> eligibleMerchants;
	@Value("${lending.platform.nbfc.enable:false}")
	private boolean nbfcFlowEnable;
	@Value("${lending.platform.nbfc.merchant.rollout:0}")
	private int eligibleLendingPlatformNbfcMerchantRollout;
	@Value("${lending.platform.nbfc.application.limit:2}")
	private int lendingPlatformNbfcApplicationLimit;
	@Value("${lending.platform.underwriting.flow.enabled:false}")
    private boolean isNewUnderwritingFlowEnabled;
    @Value("${lending.platform.underwriting.flow.eligible.merchants:20000100}")
    private List<Long> eligibleUnderwritingMerchants;
    @Value("${lending.platform.underwriting.flow.merchant.rollout:0}")
    private int eligibleUnderwritingMerchantRollout;

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
    private final LmsLoanStatusDao lmsLoanStatusDao;


	private final LendingApplicationLenderDetailsDao laldDao;
    private final LendingApplicationDao lendingApplicationDao;

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

		int merchantIdPercentage = merchantId.intValue() % 100;
		if (merchantIdPercentage > eligibleLendingPlatformNbfcMerchantRollout) {
			log.info("New flow for Merchant:{} is not eligible due to rollout percent", merchantId);
			return false;
		}

		if (lendingPlatformNbfcApplicationLimit != -1){
			LocalDate localDate = LocalDate.parse("2025-04-23");
			Date date = Date.from(localDate.atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant());
			long count = laldDao.countLendingApplicationLenderDetailsByRearchFlowAndCreatedAtGreaterThan(
					true, date);
			if (count >= lendingPlatformNbfcApplicationLimit) {
				log.info("New flow for Merchant:{} is not eligible due to application limit", merchantId);
				return false;
			}
		}

		log.info("Application Rolled out to New flow for applicationId: {}", lendingApplication.getId());
		return true;
	}

    public boolean checkEligibilityForOneLmsLoans(String bpLoanId) {
        LendingApplication lendingApplication = lendingApplicationDao.findByExternalLoanId(bpLoanId);
        LmsLoanStatus lmsLoanStatus = lmsLoanStatusDao.findLoanByBpLoanIdAndStatus(lendingApplication.getExternalLoanId(), "FAILED");
        Long lmsLoanStatusCount = lmsLoanStatusDao.countAllLoanStatusRecords();

        if (ObjectUtils.isEmpty(lendingApplication)) {
            return false;
        }
        if (topupLoans.contains(lendingApplication.getLoanType())) {
            log.info("Application: {} not eligible for new flow due to Topup loan", lendingApplication.getId());
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

        int merchantIdPercentage = lendingApplication.getMerchantId().intValue() % 100;
        if (merchantIdPercentage > eligibleLendingPlatformLmsMerchantRollout) {
            log.info("New flow for Merchant:{} is not eligible due to rollout percent", lendingApplication.getMerchantId());
            return false;
        }

        if(lendingPlatformLmsApplicationLimit!=-1 && lmsLoanStatusCount>=lendingPlatformLmsApplicationLimit){
            log.info("New flow for bpLoanId:{} is not eligible due to application limit", bpLoanId);
            return false;
        }

        log.info("New flow applicable for LMS");
        return true;
    }
}
