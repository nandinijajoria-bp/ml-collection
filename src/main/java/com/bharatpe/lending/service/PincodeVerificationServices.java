package com.bharatpe.lending.service;

import com.bharatpe.common.dao.LendingRedCitiesDao;
import com.bharatpe.common.entities.LendingRedCities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.bharatpe.common.dao.LendingCitiesDao;
import com.bharatpe.common.dao.PincodeCityStateMappingDao;
import com.bharatpe.common.entities.LendingCities;
import com.bharatpe.common.entities.PincodeCityStateMapping;
import com.bharatpe.lending.dto.PincodeVerifyDTO;

@Service
public class PincodeVerificationServices {

	@Autowired
	LendingCitiesDao lendingCityDao;

	Logger logger = LoggerFactory.getLogger(PincodeVerificationServices.class);	

	@Autowired
	PincodeCityStateMappingDao pincodeCityStateMappingDao;

	@Autowired
	LendingRedCitiesDao lendingRedCitiesDao;
	
	public PincodeVerifyDTO checkPincodeValidity(Integer pincode) {

		PincodeVerifyDTO cityDetails = getTheCityDetails(pincode);
		try {
			logger.info("Checking pincode for loan eligibility");
			LendingCities lendingCity = lendingCityDao.findActiveCityByPincode(pincode);
			LendingRedCities redCity = lendingRedCitiesDao.findByPincode(pincode);
			if (cityDetails.getCity() == null || "".equalsIgnoreCase(cityDetails.getCity().trim())) {
				logger.info("Pincode is not eligible for the loan");
				return cityDetails;
			}
			if ((lendingCity == null || lendingCity.getPincode() == null) && redCity != null) {
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
			PincodeCityStateMapping cityDetails = pincodeCityStateMappingDao.findByPincode(pincode);
			if (cityDetails == null) {
				logger.error("No entry found for the pincode {}", pincode);
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
