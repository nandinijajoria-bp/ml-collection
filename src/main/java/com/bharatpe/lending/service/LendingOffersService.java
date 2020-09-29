package com.bharatpe.lending.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.dao.LendingBharatswipeOffersDao;
import com.bharatpe.lending.common.entity.LendingBharatswipeOffers;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dto.LendingOffersResponseDTO;
 
 

@Service
public class LendingOffersService {

	private Logger logger = LoggerFactory.getLogger(LendingOffersService.class);
	
    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;

	@Autowired
    LendingApplicationDao lendingApplicationDao;

	@Autowired
	LendingBharatswipeOffersDao lendingBharatswipeOffersDao;

	public LendingOffersResponseDTO getOffers(Long merchantId) {
		LendingOffersResponseDTO responseDTO = new LendingOffersResponseDTO();
		LendingBharatswipeOffers lendingOffer = lendingBharatswipeOffersDao.findByMerchantId(merchantId);
		LendingPaymentSchedule activeLoan = lendingPaymentScheduleDao.findByMerchantIdAndStatus(merchantId, "ACTIVE");
		if (lendingOffer == null) {
			responseDTO.setSuccess(false);
			responseDTO.setMessage("No Offer found");
			return responseDTO;
		}
		if(activeLoan != null && activeLoan.getLoanApplication() != null && activeLoan.getLoanApplication().getLoanType().equals("BHARATSWIPE")) {
			logger.info("LendingPaymentSchedule active loan found loanId: {}", activeLoan.getId());
			responseDTO.setApplicationStatus("approved");
			responseDTO.setSuccess(true);
			responseDTO.setOfferAmount(activeLoan.getLoanApplication().getLoanAmount());
			responseDTO.setActiveLoan(true);
			responseDTO.setTenure(activeLoan.getLoanApplication().getTenureInMonths());
			responseDTO.setMessage("Active Bharat Swipe Loan found");
			return responseDTO;
		} else if (activeLoan != null) {
			responseDTO.setSuccess(false);
			responseDTO.setMessage("Active loan found");
			return responseDTO;
		}
		LendingApplication previousApplication = lendingApplicationDao.findByMerchantIdAndLoanTypeAndNotStatus(merchantId, "BHARATSWIPE", "deleted");
		if(previousApplication != null) {
			logger.info("LendingApplication found applicationId: {}", previousApplication.getId());
			responseDTO.setApplicationStatus(previousApplication.getStatus());
			responseDTO.setSuccess(true);
			responseDTO.setOfferAmount(previousApplication.getLoanAmount());
			responseDTO.setActiveLoan(false);
			responseDTO.setTenure(previousApplication.getTenureInMonths());
			responseDTO.setMessage("Bharat Swipe Loan application found");
			return responseDTO;
		}
		logger.info("LendingBharatswipeOffer found with id: {}", lendingOffer.getId());
		responseDTO.setApplicationStatus(null);
		responseDTO.setSuccess(true);
		responseDTO.setOfferAmount(lendingOffer.getLoanAmount());
		responseDTO.setActiveLoan(false);
		responseDTO.setTenure(lendingOffer.getTenureMonths());
		responseDTO.setMessage("Fetched available bharat swipe lending offer");
		return responseDTO;
	}
}
