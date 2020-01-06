package com.bharatpe.lending.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.bharatpe.common.constants.ResponseCode;
import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.common.entities.LendingNbfscs;
import com.bharatpe.common.entities.Merchant;
import com.bharatpe.common.objects.CommonAPIRequest;
import com.bharatpe.lending.dao.LendingNbfscsDao;

@Service
public class LendingAgreementService {
	private Logger logger = LoggerFactory.getLogger(LendingAgreementService.class);
	
	@Autowired
	LendingApplicationDao lendingApplicationDao;
	
	@Autowired
	LendingNbfscsDao lendingNbfscsDao;
	
	public Map<String, Object> fetchLendingAgreement(Merchant merchant, HttpServletResponse response, CommonAPIRequest commonAPIRequest) {
		Long id = null;
		String lenderText = "";
		Map<String, Object> resp = new LinkedHashMap<> ();
		
//		Integer applicationId = (Integer) commonAPIRequest.getPayload().get("application_id");
//		if(applicationId == null || applicationId == 0) {
//			logger.error("Application ID not preset/zero, returning failure.");
//			resp.put("success",false);
//			response.setStatus(Integer.parseInt(ResponseCode.BAD_REQUEST));
//			return resp;
//		}
		

//		LendingApplication application = lendingApplicationDao.findByIdAndMerchant(applicationId.longValue(), merchant);
		
//		if(application == null) {
//			logger.info("LendingAgreementService application present");
//			response.setStatus(Integer.parseInt(ResponseCode.BAD_REQUEST));
//			resp.put("success",false);
//			return resp;
//		}

//		Double loanAmount = application.getLoanAmount();
//		String tenure = application.getTenure();
//		
//		if(loanAmount == 5000 && tenure.equals("1 Month")) {
//			id = (long) 4;
//		} else {
			id = (long) 3;
			lenderText = "BharatPe is the processing partner for this loan. <br /> Loan is in books of BharatPe Partner NBFCs and will be disbursed from their account.";
//		}
		
		Optional<LendingNbfscs> optionalObj = lendingNbfscsDao.findById(id);
		
		if(optionalObj.isPresent() == true) {
			LendingNbfscs lendingNbfscs = optionalObj.orElse(null);
			Map<String, String> lenderInfo = new LinkedHashMap<> ();
			
			lenderInfo.put("lender_name","");
			lenderInfo.put("lender_logo","");
			lenderInfo.put("lender_tnc_url",lendingNbfscs.getTermsAndConditionURL());
			lenderInfo.put("lender_text",lenderText);
			
			resp.put("success",true);
			resp.put("data", lenderInfo);
		} else {
			logger.info("LendingAgreementService no lender info present");
			response.setStatus(Integer.parseInt(ResponseCode.SOMETHING_WENT_WRONG));
			resp.put("success",false);
		}
		return resp;
	}
}
