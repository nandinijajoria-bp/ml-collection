package com.bharatpe.lending.lendingplatform.nbfc.service.builder.pojo;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.entity.LendingRiskVariablesSnapshot;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.LoanRiskVariables;
import com.bharatpe.lending.lendingplatform.nbfc.enums.PincodeColor;
import com.bharatpe.lending.lendingplatform.nbfc.enums.RiskDecision;
import com.bharatpe.lending.lendingplatform.nbfc.enums.RiskSegment;
import com.bharatpe.lending.lendingplatform.nbfc.enums.ShopStructure;
import com.bharatpe.lending.util.LoanUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.math.BigDecimal;
import java.util.Date;

@Service
@Slf4j
public class LoanRiskVariablesBuilder {

	@Autowired
	LoanUtil loanUtil;

	@Autowired
	LendingPaymentScheduleDao lendingPaymentScheduleDao;

	public LoanRiskVariables buildLoanRiskVariables(
			LendingApplication lendingApplication, LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot) {
		log.info("Fetching Loan Risk Variables for merchant: {}", lendingApplication.getMerchantId());

		LendingPaymentSchedule lastLoan = lendingPaymentScheduleDao.findTop1ByMerchantIdAndStatusAndCreditLoanOrderByIdDesc(lendingApplication.getMerchantId(), "CLOSED", false);
		LendingPaymentSchedule currentLoan = lendingPaymentScheduleDao.findTop1ByMerchantIdAndStatusAndCreditLoanOrderByIdDesc(lendingApplication.getMerchantId(), "ACTIVE", false);
		int maxDpdInLastLoan = loanUtil.getMaxDpdInLastLoan(lendingApplication.getMerchantId(), lastLoan);
		int maxDpdInCurrentLoan = loanUtil.getMaxDpdInLastLoan(lendingApplication.getMerchantId(), currentLoan);

		LoanRiskVariables loanRiskVariables = LoanRiskVariables.builder()
				.bpScore(!ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getBpScore()) ? BigDecimal.valueOf(lendingRiskVariablesSnapshot.getBpScore()) : BigDecimal.ZERO)
				.bbs(!ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getBbs()) ? BigDecimal.valueOf(lendingRiskVariablesSnapshot.getBbs()) : BigDecimal.ZERO)
				.bbs2(!ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getBbs2()) ? BigDecimal.valueOf(lendingRiskVariablesSnapshot.getBbs2()) : BigDecimal.ZERO)
				.bureauScore(!ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getBureauScore()) ? BigDecimal.valueOf(lendingRiskVariablesSnapshot.getBureauScore()) : BigDecimal.ZERO)
				.riskSegment(!ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getRiskSegment()) ? RiskSegment.valueOf(lendingRiskVariablesSnapshot.getRiskSegment().name()) : null)
				.decisionId(!ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getDecisionId()) ? RiskDecision.valueOf(lendingRiskVariablesSnapshot.getDecisionId().name()) : null)
				.pincode(!ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getPincode()) ? lendingRiskVariablesSnapshot.getPincode() : 0)
				.pincodeColor(!ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getPincodeColor()) ? PincodeColor.valueOf(lendingRiskVariablesSnapshot.getPincodeColor().name()) : null)
				.gstOffer(!ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getGstOffer()) ? BigDecimal.valueOf(lendingRiskVariablesSnapshot.getGstOffer()) : BigDecimal.ZERO)
				.finalOffer(!ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getFinalOffer()) ? BigDecimal.valueOf(lendingRiskVariablesSnapshot.getFinalOffer()) : BigDecimal.ZERO)
				.loanType(!ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getLoanType()) ? lendingRiskVariablesSnapshot.getLoanType() : "")
				.experianRejection(!ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getExperianRejection()) ? lendingRiskVariablesSnapshot.getExperianRejection() : "")
				.riskGroup(!ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getRiskGroup()) ? lendingRiskVariablesSnapshot.getRiskGroup() : "")
				.loanSegment(!ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getLoanSegment()) ? lendingRiskVariablesSnapshot.getLoanSegment() : "")
				.drsScore(!ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getDrsScore()) ? BigDecimal.valueOf(lendingRiskVariablesSnapshot.getDrsScore()) : BigDecimal.ZERO)
				.monthlyNfi(!ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getMonthlyNfi()) ? BigDecimal.valueOf(lendingRiskVariablesSnapshot.getMonthlyNfi()) : BigDecimal.ZERO)
				.monthlyTpv(!ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getMonthlyTpv()) ? BigDecimal.valueOf(lendingRiskVariablesSnapshot.getMonthlyTpv()) : BigDecimal.ZERO)
				.monthlyIncome(!ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getMonthlyIncome()) ? BigDecimal.valueOf(lendingRiskVariablesSnapshot.getMonthlyIncome()) : BigDecimal.ZERO)
				.vintage(!ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getVintage()) ? lendingRiskVariablesSnapshot.getVintage() : 0L)
				.referenceCount(!ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getReferenceCount()) ? lendingRiskVariablesSnapshot.getReferenceCount() : 0L)
				.tenure(!ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getTenure()) ? lendingRiskVariablesSnapshot.getTenure() : 0)
				.summaryTpv(!ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getSummaryTpv()) ? BigDecimal.valueOf(lendingRiskVariablesSnapshot.getSummaryTpv()) : BigDecimal.ZERO)
				.uniqueCustomer1mon(!ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getUniqueCustomer1mon()) ? lendingRiskVariablesSnapshot.getUniqueCustomer1mon() : 0)
				.gstAffectedOffer(!ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getGstAffectedOffer()) ? lendingRiskVariablesSnapshot.getGstAffectedOffer() : false)
				.shopStructure(!ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getShopStructure()) ? ShopStructure.valueOf(lendingRiskVariablesSnapshot.getShopStructure().name()) : null)
				.pilotIdentifier(!ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getPilotIdentifier()) ? lendingRiskVariablesSnapshot.getPilotIdentifier() : "")
				.bankBasedOffer(!ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getBankBasedOffer()) ? BigDecimal.valueOf(lendingRiskVariablesSnapshot.getBankBasedOffer()) : BigDecimal.ZERO)
				.gst3bBasedOffer(!ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getGst3bBasedOffer()) ? BigDecimal.valueOf(lendingRiskVariablesSnapshot.getGst3bBasedOffer()) : BigDecimal.ZERO)
				.aggregateId(!ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getAggregateId()) ? lendingRiskVariablesSnapshot.getAggregateId() : "")
				.aaBasedOffer(!ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getAaBasedOffer()) ? BigDecimal.valueOf(lendingRiskVariablesSnapshot.getAaBasedOffer()) : BigDecimal.ZERO)
				.gst3bBasedAffectedOffer(!ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getGst3bBasedAffectedOffer()) ? lendingRiskVariablesSnapshot.getGst3bBasedAffectedOffer() : false)
				.aaBasedAffectedOffer(!ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getAaBasedAffectedOffer()) ? lendingRiskVariablesSnapshot.getAaBasedAffectedOffer() : false)
				.bankBasedAffectedOffer(!ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getBankBasedAffectedOffer()) ? lendingRiskVariablesSnapshot.getBankBasedAffectedOffer() : false)
				.newContactReferenceLogic(!ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getNewContactReferenceLogic()) ? lendingRiskVariablesSnapshot.getNewContactReferenceLogic() : false)
				.loanCategory(!ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getLoanCategory()) ? lendingRiskVariablesSnapshot.getLoanCategory() : "")
				.metaData(!ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getMetaData()) ? lendingRiskVariablesSnapshot.getMetaData() : null)
				.maxDpdLastLoan(!ObjectUtils.isEmpty(maxDpdInLastLoan) ? maxDpdInLastLoan : 0)
				.maxDpdCurrentLoan(!ObjectUtils.isEmpty(maxDpdInCurrentLoan) ? maxDpdInCurrentLoan : 0)
				.updatedAt(!ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getUpdatedAt()) ? lendingRiskVariablesSnapshot.getUpdatedAt() : new Date())
				.build();

		log.debug("Loan Risk Variables for merchant with applicationId: {} is {}", lendingApplication.getId(), loanRiskVariables);

		return loanRiskVariables;
	}

}
