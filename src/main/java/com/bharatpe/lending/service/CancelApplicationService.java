package com.bharatpe.lending.service;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.bharatpe.common.constants.ResponseCode;
import com.bharatpe.common.entities.LendingAuditTrial;
import com.bharatpe.common.objects.CommonAPIRequest;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingAuditTrialDao;

@Service
public class CancelApplicationService {
	private Logger logger = LoggerFactory.getLogger(CancelApplicationService.class);
	
	@Autowired
	LendingApplicationDao lendingApplicationDao;
	
	@Autowired
	LendingAuditTrialDao lendingAuditTrialDao;

	public Map<String, Boolean> runService(HttpServletRequest request, HttpServletResponse response, CommonAPIRequest commonAPIRequest) {
		
		Long merchantId = Long.parseLong(request.getAttribute("merchantId").toString());
		Long applicationId =  commonAPIRequest.getPayload().get("application_id") != null ? Long.parseLong(commonAPIRequest.getPayload().get("application_id").toString()) : null;
		Boolean reapply =  commonAPIRequest.getPayload().get("reapply") != null ? (boolean) commonAPIRequest.getPayload().get("reapply") : false;
		
		
		Map<String, Boolean> resp = new HashMap<> ();
		
		if(applicationId != null) {
			String currentStatus = null;
			String newStatus = null;
			if(reapply == true) {
				currentStatus = "rejected";
				newStatus = "deleted";
			}else {
				currentStatus = "draft";
				newStatus = "closed";
			}
			int updatedColumn = lendingApplicationDao.updateApplicationStatus(newStatus, applicationId, merchantId, currentStatus);
			if(updatedColumn != 0) {
				logger.info("CancelApplicationService application status update success for applicationId : {} and merchantId : {}", applicationId, merchantId);
				LendingAuditTrial lendingAuditTrial = new LendingAuditTrial();
				lendingAuditTrial.setApplicationId(applicationId);
				lendingAuditTrial.setMerchantId(merchantId);
				lendingAuditTrial.setLoanId("");
				lendingAuditTrial.setUserId(Long.parseLong("0"));
				lendingAuditTrial.setOldStatus("draft");
				lendingAuditTrial.setNewStatus(newStatus);
				lendingAuditTrial.setType("APP_STATUS");
				lendingAuditTrialDao.save(lendingAuditTrial);
				resp.put("success",true);
				logger.info("CancelApplicationService lending_audit_trail success insert for applicationId : {} and merchantId : {}", applicationId, merchantId);
			}else {
				response.setStatus(Integer.parseInt(ResponseCode.SOMETHING_WENT_WRONG));
				logger.info("CancelApplicationService status update failed for applicationId : {} and merchantId : {}", applicationId, merchantId);
				resp.put("success",false);
			}
		}else {
			logger.info("CancelApplicationService invalid applicationId");
			response.setStatus(Integer.parseInt(ResponseCode.BAD_REQUEST));
			resp.put("success",false);
		}
		
		return resp;
	}

}
