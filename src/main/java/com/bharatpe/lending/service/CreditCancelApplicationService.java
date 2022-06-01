package com.bharatpe.lending.service;

import java.util.HashMap;
import java.util.Map;

import com.bharatpe.lending.service.merchant.dto.BasicDetailsDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


import com.bharatpe.lending.common.dao.CreditApplicationDao;
import com.bharatpe.lending.common.dao.CreditApplicationTransitionDao;
import com.bharatpe.lending.common.entity.CreditApplication;
import com.bharatpe.lending.common.entity.CreditApplicationTransition;
 
 

@Service
public class CreditCancelApplicationService {

		private Logger logger = LoggerFactory.getLogger(CancelApplicationService.class);
	
	@Autowired
	CreditApplicationDao creditApplicationDao;
	
	@Autowired
	CreditApplicationTransitionDao creditApplicationTransitionDao;

	public Map<String, Boolean> cancelApplication(BasicDetailsDto merchant, Long applicationId) {
		Map<String, Boolean> resp = new HashMap<> ();
		CreditApplication creditApplication = creditApplicationDao.findByIdAndMerchantIdAndStatus(applicationId, merchant.getId(), "draft");

		if(creditApplication == null) {
			logger.info("CancelApplicationService credit application not found with application id {}", applicationId);
			resp.put("success",false);
			return resp;
		}
		creditApplication.setStatus("deleted");
		creditApplicationDao.save(creditApplication);

		logger.info("CancelApplicationService application status update success for applicationId : {} and merchantId : {}", applicationId, merchant.getId());
		CreditApplicationTransition creditApplicationTransition = new CreditApplicationTransition();
		creditApplicationTransition.setApplicationId(applicationId);
		creditApplicationTransition.setFromStatus("draft");
		creditApplicationTransition.setToStatus("deleted");
		creditApplicationTransitionDao.save(creditApplicationTransition);
		resp.put("success",true);
		logger.info("CancelApplicationService lending_audit_trail success insert for applicationId : {} and merchantId : {}", applicationId, merchant.getId());
		return resp;
	}
}
