package com.bharatpe.lending.service;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.bharatpe.common.constants.ResponseCode;
import com.bharatpe.common.entities.Merchant;
import com.bharatpe.common.entities.NotifyEligible;
import com.bharatpe.lending.dao.NotifyEligibleDao;

@Service
public class NotifyEligibleService {
	private Logger logger = LoggerFactory.getLogger(NotifyEligibleService.class);

	@Autowired
	NotifyEligibleDao notifyEligibleDao;
	
	public Map<String, Boolean> notifyEligible(Merchant merchant, HttpServletResponse response, String type) {
		Map<String, Boolean> resp = new LinkedHashMap<> ();
		Long merchantId = merchant.getId();
		
		NotifyEligible notifyEligible = new NotifyEligible();

		notifyEligible.setMerchantId(merchantId);
		notifyEligible.setStatus("PENDING");
		notifyEligible.setType(type);
		notifyEligibleDao.save(notifyEligible);
		
		if(notifyEligible.getId() > 0) {
			logger.info("NotifyEligibleService success to insert for merchantId : {}",merchantId);
			resp.put("success", true);
		}else {
			response.setStatus(Integer.parseInt(ResponseCode.SOMETHING_WENT_WRONG));
			logger.info("NotifyEligibleService failed to insert for merchantId : {}",merchantId);
			resp.put("success", false);
		}
		
		return resp;
	}
}
