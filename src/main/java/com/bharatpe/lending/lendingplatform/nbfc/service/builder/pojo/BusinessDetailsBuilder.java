package com.bharatpe.lending.lendingplatform.nbfc.service.builder.pojo;


import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.AddressDetails;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.BusinessDetails;
import com.bharatpe.lending.loanV3.enums.StateMapping;
import com.bharatpe.lending.loanV3.utils.ConverterUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Objects;

@Service
@Slf4j
public class BusinessDetailsBuilder {

	@Autowired
	private ConverterUtils converterUtils;

	public BusinessDetails buildBusinessDetails(LendingApplication lendingApplication, BasicDetailsDto basicDetailsDto) {
		log.info("Fetching Business Details for merchant: {}", lendingApplication.getMerchantId());

		if (ObjectUtils.isEmpty(basicDetailsDto)) {
			log.error("Merchant Details not found for merchantId: {}", lendingApplication.getMerchantId());
			return null;
		}
		return populateBusinessDetails(lendingApplication, basicDetailsDto);
	}

	private BusinessDetails populateBusinessDetails(LendingApplication lendingApplication, BasicDetailsDto basicDetails) {

		AddressDetails addressDetails = createAddressDetails(basicDetails, lendingApplication);
		BusinessDetails businessDetails = BusinessDetails.builder()
				.name(basicDetails.getBussinessName())
				.category(basicDetails.getBussinessCategory())
				.subCategory(basicDetails.getSubCategory())
				.addressDetails(addressDetails)
				.mobile(Long.parseLong(basicDetails.getMobile()))
				.email(basicDetails.getEmail())
				.pan(basicDetails.getPanNumber())
				.gst(basicDetails.getGstn())
				.shopType(basicDetails.getShopType())
				.companyType(basicDetails.getCompanyType())
				.kycType(basicDetails.getKycType())
				.status(basicDetails.getStatus())
				.createdAt(basicDetails.getCreatedAt())
				.build();

		log.debug("Business Details for merchant:{} is {}", lendingApplication.getMerchantId(), businessDetails);

		return businessDetails;
	}

	private AddressDetails createAddressDetails(BasicDetailsDto basicDetails, LendingApplication lendingApplication) {
		String address = converterUtils.parseData(basicDetails.getAddress());
		int addressSize = address.length();
		String address1, address2 = "", address3 = "";
		if (addressSize <= 40) {
			address1 = address;
		} else if (addressSize <= 80) {
			address1 = address.substring(0, 40);
			address2 = address.substring(40, addressSize);
		} else {
			address1 = address.substring(0, 40);
			address2 = address.substring(40, 80);
			address3 = address.substring(80, addressSize);
		}
		return AddressDetails.builder()
				.address1(address1)
				.address2(address2)
				.address3(address3)
				.city(basicDetails.getCity())
				.state(basicDetails.getState())
				.pincode(lendingApplication.getPincode())
				.landmark(lendingApplication.getLandmark())
				.latitude(basicDetails.getLatitude().floatValue())
				.longitude(basicDetails.getLongitude().floatValue())
				.stateCode(Objects.nonNull(StateMapping.getStateEnum(basicDetails.getState())) ?
						StateMapping.getStateEnum(basicDetails.getState()).name() : null)
				.build();
	}
}