package com.bharatpe.lending.lendingplatform.nbfc.service.builder.pojo;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.service.merchant.dto.MerchantDetailsDto;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.BankDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class BankDetailsBuilder {

	public BankDetails buildBankDetails(LendingApplication lendingApplication, MerchantDetailsDto merchantDetails) {
		log.info("Fetching Bank Details for merchant: {}", lendingApplication.getMerchantId());

		BankDetails bankDetails = BankDetails.builder()
				.beneficiaryName(merchantDetails.getBankDetail().getBeneficiaryName())
				.accountNumber(merchantDetails.getBankDetail().getAccountNumber())
				.ifsc(merchantDetails.getBankDetail().getIfsc())
				.bankLogo(merchantDetails.getBankDetail().getBankLogo())
				.ifscLogo(merchantDetails.getBankDetail().getIfscLogo())
				.bankName(merchantDetails.getBankDetail().getBankName())
				.signInType(merchantDetails.getBankDetail().getSignInType())
				.bankCode(merchantDetails.getBankDetail().getBankCode())
				.accountType(merchantDetails.getBankDetail().getAccountType())
				.build();

		log.debug("Bank Details for merchant: {} is {}", lendingApplication.getMerchantId(), bankDetails);
		return bankDetails;
	}

}
