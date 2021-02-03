package com.bharatpe.lending.service;

import com.bharatpe.common.dao.ExperianDao;
import com.bharatpe.common.dao.OrderStickerDao;
import com.bharatpe.common.entities.*;
import com.bharatpe.lending.common.dao.CreditLineMerchantDao;
import com.bharatpe.lending.common.dao.LendingCoolOffDao;
import com.bharatpe.lending.common.dao.LendingEkycDao;
import com.bharatpe.lending.common.entity.CreditLineMerchant;
import com.bharatpe.lending.common.entity.LendingCoolOff;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.dao.BPEnachDao;
import com.bharatpe.lending.dto.CommonResponse;
import com.bharatpe.lending.dto.CoolOffRequestDTO;
import com.bharatpe.lending.dto.CoolOffResponseDTO;
import com.bharatpe.lending.util.LoanUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.bharatpe.lending.common.dao.LendingBharatswipeOffersDao;
import com.bharatpe.lending.common.entity.LendingBharatswipeOffers;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dto.LendingOffersResponseDTO;

import java.util.Date;


@Service
public class LendingOffersService {

	private Logger logger = LoggerFactory.getLogger(LendingOffersService.class);
	
    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;

	@Autowired
    LendingApplicationDao lendingApplicationDao;

	@Autowired
	LendingBharatswipeOffersDao lendingBharatswipeOffersDao;

	@Autowired
	CreditLineMerchantDao creditLineMerchantDao;

	@Autowired
	LendingCoolOffDao lendingCoolOffDao;

	@Autowired
	OrderStickerDao orderStickerDao;

	@Autowired
	ExperianDao experianDao;

	@Autowired
	BPEnachDao bpEnachDao;

	@Autowired
	LendingEkycDao lendingEkycDao;

	public LendingOffersResponseDTO getOffers(Long merchantId) {
		LendingOffersResponseDTO responseDTO = new LendingOffersResponseDTO();
		LendingBharatswipeOffers lendingOffer = lendingBharatswipeOffersDao.findByMerchantId(merchantId);
		LendingPaymentSchedule activeLoan = lendingPaymentScheduleDao.findByMerchantIdAndStatus(merchantId, "ACTIVE");
		CreditLineMerchant creditLineMerchant = creditLineMerchantDao.findByMerchantId(merchantId);
		if (creditLineMerchant != null || lendingOffer == null || lendingOffer.getTpv() == null || lendingOffer.getTpv() <= 0D || isOfferExpired(lendingOffer)) {
			responseDTO.setSuccess(false);
			responseDTO.setMessage("No Offer found");
			return responseDTO;
		}
		if(activeLoan != null && activeLoan.getLoanApplication() != null && activeLoan.getLoanApplication().getLoanType().equals("BHARAT_SWIPE")) {
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
		LendingApplication lendingApplication = lendingApplicationDao.findByMerchantIdAndNotLoanTypeAndNotStatus(merchantId, "BHARAT_SWIPE", "deleted");
		if (lendingApplication != null) {
			responseDTO.setSuccess(false);
			responseDTO.setMessage("Active loan application found");
			return responseDTO;
		}
		LendingApplication previousApplication = lendingApplicationDao.findByMerchantIdAndLoanTypeAndNotStatus(merchantId, "BHARAT_SWIPE", "deleted");
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
		responseDTO.setTpv(lendingOffer.getTpv());
		responseDTO.setMessage("Fetched available bharat swipe lending offer");
		return responseDTO;
	}

	private Boolean isOfferExpired(LendingBharatswipeOffers offer) {
		if(offer!=null && offer.getExpiryDate()!=null) {
			return offer.getExpiryDate().compareTo(new Date())<=0;
		}
		return true;
	}

	public CommonResponse checkCoolOffPeriod(Merchant merchant, CoolOffRequestDTO requestDTO) {
		try {
			Experian experian = experianDao.getByMerchantId(merchant.getId());
			boolean diy = (merchant.getMerchantType() != null && "DIY".equals(merchant.getMerchantType())) || merchant.getReferalCode() == null;
			if (experian != null) {
				logger.info("Pancard already exist for merchant:{}", merchant.getId());
				CoolOffResponseDTO responseDTO = new CoolOffResponseDTO(true, false, null, null);
				return new CommonResponse(true, "success", responseDTO);
			}
			LendingCoolOff lendingCoolOff = lendingCoolOffDao.findByMerchantId(merchant.getId());
			OrderSticker orderSticker = orderStickerDao.findByMerchantId(merchant.getId());
			boolean showOrderQr = orderSticker == null;
			if (lendingCoolOff != null) {
				logger.info("lending_cool_off entry already exist for merchant:{}", merchant.getId());
				if (!diy && !lendingCoolOff.isEligible()) {
					lendingCoolOff.setEligible(true);
					lendingCoolOffDao.save(lendingCoolOff);
				}
				CoolOffResponseDTO responseDTO = new CoolOffResponseDTO(lendingCoolOff.isEligible(), showOrderQr, lendingCoolOff.getPancard(), lendingCoolOff.getPincode());
				return new CommonResponse(true, "success", responseDTO);
			}
			if (requestDTO.getPanCard() == null) {
				logger.info("First time merchant:{}, returning null pancard", merchant.getId());
				CoolOffResponseDTO responseDTO = new CoolOffResponseDTO(false, false, null, null);
				return new CommonResponse(true, "success", responseDTO);
			}
			boolean isEligible = false;
			if (!diy || LoanUtil.getDateDiffInDays(merchant.getCreatedAt(), new Date()) > 1) {
				logger.info("Merchant:{} not DIY/created before 1 day, so eligible for cool off period", merchant.getId());
				isEligible = true;
			} else {
				logger.info("Merchant:{} not eligible for cool off period", merchant.getId());
			}
			lendingCoolOff = new LendingCoolOff(merchant.getId(), requestDTO.getPanCard(), requestDTO.getPincode(), isEligible);
			lendingCoolOff.setCreatedAt(merchant.getCreatedAt());
			lendingCoolOffDao.save(lendingCoolOff);
			CoolOffResponseDTO responseDTO = new CoolOffResponseDTO(lendingCoolOff.isEligible(), showOrderQr, lendingCoolOff.getPancard(), lendingCoolOff.getPincode());
			return new CommonResponse(true, "success", responseDTO);
		} catch (Exception e) {
			logger.error("Exception while checking cool off period for merchant:{}", merchant.getId(), e);
		}
		return new CommonResponse(false, "Something went wrong");
	}

	public void makeMeFresh(Merchant merchant) {
		experianDao.deleteByMerchantId(merchant.getId());
		lendingApplicationDao.deleteByMerchantId(merchant.getId());
		bpEnachDao.deleteByMerchantId(merchant.getId());
		lendingEkycDao.deleteByMerchantId(merchant.getId());
	}
}
