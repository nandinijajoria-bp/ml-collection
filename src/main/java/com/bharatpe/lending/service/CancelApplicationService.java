package com.bharatpe.lending.service;

import com.bharatpe.cache.service.LendingCache;
import com.bharatpe.common.constants.ResponseCode;
import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingAuditTrial;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingAuditTrialDao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class CancelApplicationService {
	private Logger logger = LoggerFactory.getLogger(CancelApplicationService.class);
	
	@Autowired
	LendingApplicationDao lendingApplicationDao;
	
	@Autowired
	LendingAuditTrialDao lendingAuditTrialDao;

	@Autowired
	APIGatewayService apiGatewayService;

	public Map<String, Boolean> cancelApplication(BasicDetailsDto merchant, Long applicationId, String reason) {

		Map<String, Boolean> resp = new HashMap<> ();
		LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchantIdAndStatus(applicationId,
		merchant.getId(), "draft");

		if(lendingApplication == null) {
			logger.info("CancelApplicationService lending application not found with application id {}", applicationId);
			resp.put("success",false);
			return resp;
		}

		final boolean isTxnSuccess = apiGatewayService.globalLimitTxn(lendingApplication.getMerchantId(), "CREDIT", lendingApplication.getLoanAmount());

		if (!isTxnSuccess) {
			logger.error("credit txn failed for applicationId {}", applicationId);
			resp.put("success",false);
			return resp;
		}

		lendingApplication.setStatus("deleted");
		lendingApplication.setResponseCode(reason);
		lendingApplicationDao.save(lendingApplication);

		logger.info("CancelApplicationService application status update success for applicationId : {} and merchantId : {}", applicationId, merchant.getId());
		LendingAuditTrial lendingAuditTrial = new LendingAuditTrial();
		lendingAuditTrial.setApplicationId(applicationId);
		lendingAuditTrial.setMerchantId(merchant.getId());
		lendingAuditTrial.setLoanId("");
		lendingAuditTrial.setUserId(Long.parseLong("0"));
		lendingAuditTrial.setOldStatus("draft");
		lendingAuditTrial.setNewStatus("deleted");
		lendingAuditTrial.setType("APP_STATUS");
		lendingAuditTrialDao.save(lendingAuditTrial);
		resp.put("success",true);
		logger.info("CancelApplicationService lending_audit_trail success insert for applicationId : {} and merchantId : {}", applicationId, merchant.getId());
		return resp;
	}
	
	

}
