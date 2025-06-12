package com.bharatpe.lending.lendingplatform.nbfc.service.builder.pojo;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.AddressDetails;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.CustomerShopDetails;
import com.bharatpe.lending.loanV3.enums.StateMapping;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Objects;

@Service
@Slf4j
public class CustomerShopDetailsBuilder {

	public CustomerShopDetails buildCustomerShopDetails(LendingApplication lendingApplication) {
		log.info("Fetching Customer Shop Details for merchant: {}", lendingApplication.getMerchantId());

		AddressDetails addressDetails = fetchShopAddress(lendingApplication);

		CustomerShopDetails customerShopDetails = CustomerShopDetails.builder()
				.addressDetails(addressDetails)
				.pincode(lendingApplication.getPincode().intValue())
				.businessName(lendingApplication.getBusinessName())
				.build();

		log.debug("Customer Shop Details for merchant: {} is {}", lendingApplication.getMerchantId(), customerShopDetails);
		return customerShopDetails;
	}

	private AddressDetails fetchShopAddress(LendingApplication lendingApplication) {


		AddressDetails addressDetails = AddressDetails.builder()
				.address1(lendingApplication.getShopNumber())
				.address2(lendingApplication.getStreetAddress())
				.landmark(lendingApplication.getLandmark())
				.pincode(lendingApplication.getPincode())
				.city(lendingApplication.getCity())
				.state(lendingApplication.getState())
				.stateCode(Objects.nonNull(StateMapping.getStateEnum(lendingApplication.getState())) ?
						StateMapping.getStateEnum(lendingApplication.getState()).name() : null)
				.build();

		if (!ObjectUtils.isEmpty(lendingApplication.getLatitude()) && !ObjectUtils.isEmpty(lendingApplication.getLongitude())) {
			addressDetails.setLatitude(Float.parseFloat(lendingApplication.getLatitude()));
			addressDetails.setLongitude(Float.parseFloat(lendingApplication.getLongitude()));
		}

		return addressDetails;
	}

}
