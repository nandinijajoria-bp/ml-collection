package com.bharatpe.lending.lendingplatform.nbfc.service.builder.pojo;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.dto.CommonResponse;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.ApplicationDetails;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import com.bharatpe.lending.service.LendingEdiScheduleService;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;

@Service
@Slf4j
public class ApplicationDetailsBuilder {

	@Autowired
	KycUtils kycUtils;
	@Autowired
	LendingEdiScheduleService lendingEdiScheduleService;

	public ApplicationDetails buildApplicationDetails(
			LendingApplication lendingApplication, LendingApplicationLenderDetails lendingApplicationLenderDetails) {
		log.info("Fetching Application Details with applicationId: {}", lendingApplication.getId());

		return populateApplicationDetails(lendingApplication, lendingApplicationLenderDetails);
	}

	private ApplicationDetails populateApplicationDetails(
			LendingApplication lendingApplication, LendingApplicationLenderDetails lendingApplicationLenderDetails) {

		Date loanDisbursalDate = null;
		Date loanClosureDate = null;
		if (!ObjectUtils.isEmpty(lendingApplication.getDisburseTimestamp())) {
			loanDisbursalDate = lendingApplication.getDisburseTimestamp();
			loanClosureDate = DateTimeUtil.addDays(loanDisbursalDate, lendingApplication.getPayableDays().intValue());
		}

		Map<String, String> businessCategoryAndSubCategoryMap = kycUtils.getBusinessCategoryAndSubCategory(lendingApplication.getMerchantId());
		String merchantCategory = ObjectUtils.isEmpty(businessCategoryAndSubCategoryMap.get("businessCategory"))?"DEFAULT BUSINESS CATEGORY":businessCategoryAndSubCategoryMap.get("businessCategory");
		CommonResponse ediScheduleResponse = lendingEdiScheduleService.getEdiScheduleV2(lendingApplication.getMerchantId(), lendingApplication.getId(), null);
		log.info("Edi Schedule Response for merchantId: {} is {}", lendingApplication.getMerchantId(), ediScheduleResponse);
		ApplicationDetails applicationDetails = ApplicationDetails.builder()
				.applicationId(lendingApplication.getId().toString())
				.customerId(lendingApplication.getMerchantId().toString())
				.loanAmount(BigDecimal.valueOf(lendingApplication.getLoanAmount()))
				.disbursalAmount(BigDecimal.valueOf(lendingApplication.getDisbursalAmount()))
				.processingFee(BigDecimal.valueOf(lendingApplication.getProcessingFee()))
				.edi(BigDecimal.valueOf(lendingApplication.getEdi()))
				.monthlyInterest(BigDecimal.valueOf(lendingApplication.getInterestRate()))
				.tenureInMonths(lendingApplication.getTenureInMonths())
				.tenureInDays(lendingApplication.getPayableDays().intValue())
				.lender(lendingApplication.getLender())
				.bpLoanId(lendingApplication.getExternalLoanId())
				.accountType(lendingApplication.getAccountType())
				.loanDisbursalDate(loanDisbursalDate)
				.loanClosureDate(loanClosureDate)
				.loanType(lendingApplication.getLoanType())
				.ckycStatus(lendingApplication.getCkycStatus())
				.category(lendingApplication.getCategory())
				.createdAt(lendingApplication.getCreatedAt())
				.agreementAt(lendingApplication.getAgreementAt())
				.merchantCategory(merchantCategory)
				.ediScheduleResponse(ediScheduleResponse)
				.build();

		if (!ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
			applicationDetails.setAnnualInterest(BigDecimal.valueOf(lendingApplicationLenderDetails.getAnnualRoi()));
			applicationDetails.setClientId(lendingApplicationLenderDetails.getCccId());
			applicationDetails.setLoanAccountNumber(lendingApplicationLenderDetails.getLan());
			applicationDetails.setLoanCreationTimestamp(lendingApplicationLenderDetails.getLoanCreationTimestamp());
			applicationDetails.setTxnId(lendingApplicationLenderDetails.getTxnId());
			applicationDetails.setAccountId(lendingApplicationLenderDetails.getAccountId());
			applicationDetails.setLoanId(lendingApplicationLenderDetails.getLoanId());
			applicationDetails.setLeadId(lendingApplicationLenderDetails.getLeadId());
			applicationDetails.setAgreementOtp(lendingApplicationLenderDetails.getAgreementOtp());
			applicationDetails.setPennyDropAccountNumber(lendingApplicationLenderDetails.getPennyDropAccountNumber());
			applicationDetails.setBreStatus(lendingApplicationLenderDetails.getBreStatus());
			applicationDetails.setDealId(lendingApplicationLenderDetails.getDealId());
			applicationDetails.setDealNo(lendingApplicationLenderDetails.getDealNo());
			applicationDetails.setSmbId(lendingApplicationLenderDetails.getSmbId());
			applicationDetails.setOfferId(lendingApplicationLenderDetails.getOfferId());
		}

		log.debug("Application Details for merchant: {} is {}", lendingApplication.getMerchantId(), applicationDetails);

		return applicationDetails;
	}
}
