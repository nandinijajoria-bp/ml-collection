package com.bharatpe.lending.lendingplatform.nbfc.service.builder.pojo;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.Handler.EnachHandler;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.dto.MerchantNachDetailsResponseDTO;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.NachDetails;
import com.bharatpe.lending.util.LoanUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.math.BigDecimal;

@Service
@Slf4j
public class NachDetailsBuilder {
	@Autowired
	EnachHandler enachHandler;

	@Autowired
	LoanUtil loanUtil;

	@Autowired
	LendingApplicationDetailsDao lendingApplicationDetailsDao;

	public NachDetails buildNachDetails(LendingApplication lendingApplication) {
		log.info("Fetching Nach Details for merchant: {}", lendingApplication.getMerchantId());
		LendingApplicationDetails lendingApplicationDetails =
				lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(lendingApplication.getId());
		Long nachApplicationId = lendingApplicationDetails.getIsNachSkip() ? null : lendingApplication.getId();
		MerchantNachDetailsResponseDTO merchantNachDetailsResponse =
				enachHandler.findByMerchantIdAndApplicationIdAndLender(
						lendingApplication.getMerchantId(),
						nachApplicationId,
						loanUtil.enachServiceLenderMapper(lendingApplication.getLender()));

		if (ObjectUtils.isEmpty(merchantNachDetailsResponse)) {
			log.error("Nach details not found for merchant: {}", lendingApplication.getMerchantId());
			return null;
		}
		return populateNachDetails(lendingApplication, merchantNachDetailsResponse);
	}

	private NachDetails populateNachDetails(LendingApplication lendingApplication, MerchantNachDetailsResponseDTO merchantNachDetailsResponse) {

		NachDetails nachDetails = NachDetails.builder()
				.mandateId(merchantNachDetailsResponse.getMandateId())
				.beneficiaryName(merchantNachDetailsResponse.getBeneficiaryName())
				.branchName(merchantNachDetailsResponse.getBranchName())
				.status(merchantNachDetailsResponse.getStatus())
				.type(merchantNachDetailsResponse.getInternalNachType())
				.bankName(merchantNachDetailsResponse.getBankName())
				.ifsc(merchantNachDetailsResponse.getIfscCode())
				.accountNumber(merchantNachDetailsResponse.getAccountNumber())
				.accountType(merchantNachDetailsResponse.getAccountType())
				.startDate(merchantNachDetailsResponse.getStartDate())
				.endDate(merchantNachDetailsResponse.getEndDate())
				.nachAmount(BigDecimal.valueOf(merchantNachDetailsResponse.getNachAmount()))
				.mode(merchantNachDetailsResponse.getNachMode())
				.referenceNumber(merchantNachDetailsResponse.getReferenceNumber())
				.txnId(merchantNachDetailsResponse.getNpciTxnId())
				.txnDate(merchantNachDetailsResponse.getCreatedAt())
				.lender(loanUtil.enachServiceLenderMapper(lendingApplication.getLender()))
				.micr(merchantNachDetailsResponse.getMicrNumber())
				.umrn(merchantNachDetailsResponse.getProviderUmrn())
				.build();

		log.info("Nach details populated for merchant: {} are :{}", lendingApplication.getMerchantId(), nachDetails);
		return nachDetails;
	}
}
