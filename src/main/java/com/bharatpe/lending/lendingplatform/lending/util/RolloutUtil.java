package com.bharatpe.lending.lendingplatform.lending.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class RolloutUtil {
	@Value("${lending.platform.underwriting.flow.enabled:false}")
	private boolean isNewUnderwritingFlowEnabled;
	@Value("${lending.platform.underwriting.flow.eligible.merchants:20000100}")
	private List<Long> eligibleUnderwritingMerchants;
	@Value("${lending.platform.underwriting.flow.merchant.rollout:0}")
	private int eligibleUnderwritingMerchantRollout;

	public boolean underwritingNewFLowApplicable(Long merchantId){
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
}
