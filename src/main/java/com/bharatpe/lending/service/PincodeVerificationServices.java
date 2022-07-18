package com.bharatpe.lending.service;

import com.bharatpe.lending.common.dao.LendingPincodesDao;
import com.bharatpe.lending.common.entity.LendingPincodes;
import com.bharatpe.lending.common.enums.PincodeColor;
import com.bharatpe.lending.common.service.merchant.dto.PincodeCityStateMappingDTO;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.dto.PincodeVerifyDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PincodeVerificationServices {

	Logger logger = LoggerFactory.getLogger(PincodeVerificationServices.class);

	@Autowired
	MerchantService merchantService;

	@Autowired
    LendingPincodesDao lendingPincodesDao;
	
	public PincodeVerifyDTO checkPincodeValidity(Integer pincode) {

		PincodeVerifyDTO cityDetails = getTheCityDetails(pincode);
		try {
			logger.info("Checking pincode for loan eligibility");
            LendingPincodes lendingPincodes = lendingPincodesDao.findByPincode(pincode);
			if (cityDetails.getCity() == null || "".equalsIgnoreCase(cityDetails.getCity().trim())) {
				logger.info("Pincode is not eligible for the loan");
				return cityDetails;
			}
			if (lendingPincodes == null || lendingPincodes.getColor().equals(PincodeColor.RED)) {
				logger.info("Pincode is not eligible for the loan");
				return cityDetails;
			}
			logger.info("Pincode is eligible for the loan");
			cityDetails.setEligible(true);
		} catch (Exception e) {
			logger.error("Error occured while fetching lending city for pin code {}", pincode);
		}
		return cityDetails;
	}
	
	public PincodeVerifyDTO getTheCityDetails(Integer pincode) {
		PincodeVerifyDTO pincodeVerify = new PincodeVerifyDTO();
		pincodeVerify.setEligible(false);
		try {
			logger.info("Fetching city details from table pincode_citystate_mapping for the pincode {}", pincode);
			PincodeCityStateMappingDTO cityDetails = merchantService.findByPincode(pincode);
			if (cityDetails == null) {
				logger.info("No entry found for the pincode {}", pincode);
				return pincodeVerify;
			}
			pincodeVerify.setCity(cityDetails.getCity());
			pincodeVerify.setState(cityDetails.getState());

		} catch (Exception e) {
			logger.error("Error occured while fetching details for the pincode {}", pincode);
		}
		return pincodeVerify;
	}
}
