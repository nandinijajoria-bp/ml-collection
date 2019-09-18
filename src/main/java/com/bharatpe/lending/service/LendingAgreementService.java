package com.bharatpe.lending.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.bharatpe.common.constants.ResponseCode;
//import com.bharatpe.common.entities.LendingApplication;
//import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.common.entities.LendingNbfscs;
import com.bharatpe.lending.dao.LendingNbfscsDao;

@Service
public class LendingAgreementService {
	private Logger logger = LoggerFactory.getLogger(LendingAgreementService.class);
	
//	@Autowired
//	LendingApplicationDao lendingApplicationDao;
	
	@Autowired
	LendingNbfscsDao lendingNbfscsDao;
	
	public Map<String, Object> runService(HttpServletRequest request, HttpServletResponse response) {
		Map<String, Object> resp = new LinkedHashMap<> ();
		
		Long merchantId = Long.parseLong(request.getAttribute("merchantId").toString());
		
//		LendingApplication lendingApplication = lendingApplicationDao.findTop1ByMerchantIdOrderByApplicationIdDesc(merchantId);
		
		Long id = (long) 1;
		
		Optional<LendingNbfscs> optionalObj = lendingNbfscsDao.findById(id);
		if(optionalObj.isPresent() == true) {
			LendingNbfscs lendingNbfscs = optionalObj.orElse(null);
			Map<String, String> lenderInfo = new LinkedHashMap<> ();
			String lenderText = "BharatPe is the processing partner for this loan. <br /> Loan is in books of "+lendingNbfscs.getDisplayName()+" <img src='"+ lendingNbfscs.getImageURL() +"'/> and will be disbursed from their account.";
			
			resp.put("success",true);
			
			lenderInfo.put("lender_name",lendingNbfscs.getDisplayName());
			lenderInfo.put("lender_logo",lendingNbfscs.getImageURL());
			lenderInfo.put("lender_tnc_url",lendingNbfscs.getTermsAndConditionURL());
			lenderInfo.put("lender_text",lenderText);
			
			resp.put("data", lenderInfo);
		}else {
			logger.info("LendingAgreementService no lender info present");
			response.setStatus(Integer.parseInt(ResponseCode.SOMETHING_WENT_WRONG));
			resp.put("success",false);
		}
		return resp;
	}
}
