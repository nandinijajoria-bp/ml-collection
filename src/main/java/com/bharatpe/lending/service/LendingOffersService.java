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
		LendingPaymentSchedule activeLoan = lendingPaymentScheduleDao.findByMerchantIdAndStatus(merchantId, "ACTIVE");
		if(activeLoan != null) {
			logger.info("LendingPaymentSchedule active loan found loanId: {}", activeLoan.getId());
			responseDTO.setApplicationStatus("approved");
			responseDTO.setSuccess(true);
			responseDTO.setOfferAmount(0.0);
			responseDTO.setActiveLoan(true);
			responseDTO.setTenure(activeLoan.getLoanApplication().getTenureInMonths());
			responseDTO.setMessage("Previously active loan found");
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
			responseDTO.setMessage("Previously active loan application found");
			return responseDTO;
		}
		LendingBharatswipeOffers lendingOffer = lendingBharatswipeOffersDao.findByMerchantId(merchantId);
		if(lendingOffer != null) {
			logger.info("LendingBharatswipeOffer found with id: {}", lendingOffer.getId());
			responseDTO.setApplicationStatus(null);
			responseDTO.setSuccess(true);
			responseDTO.setOfferAmount(lendingOffer.getLoanAmount());
			responseDTO.setActiveLoan(false);
			responseDTO.setTenure(lendingOffer.getTenureMonths());
			responseDTO.setMessage("Fetched available lending offer");
			return responseDTO;
		}
		responseDTO.setApplicationStatus(null);
		responseDTO.setSuccess(true);
		responseDTO.setOfferAmount(0.0);
		responseDTO.setActiveLoan(false);
		responseDTO.setTenure(0);
		responseDTO.setMessage("No offers available!");
		return responseDTO;
	}
}
