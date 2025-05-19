package com.bharatpe.lending.lendingplatform.nbfc.service.builder.pojo;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingGstDetail;
import com.bharatpe.lending.dao.LendingGstDao;
import com.bharatpe.lending.dto.KycDoc;
import com.bharatpe.lending.handlers.KycHandler;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.AddressDetails;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.CustomerAddressDetails;
import com.bharatpe.lending.loanV3.enums.StateMapping;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
public class CustomerAddressDetailsBuilder {

	@Autowired
	KycHandler kycHandler;

	@Autowired
	LendingGstDao lendingGstDao;

	public CustomerAddressDetails buildCustomerAddressDetails(LendingApplication lendingApplication) {
		log.info("Fetching Customer Address Details for merchant: {}", lendingApplication.getMerchantId());

		CustomerAddressDetails customerAddressDetails = new CustomerAddressDetails();
		AddressDetails addressDetails = fetchAddressDetails(lendingApplication);
		customerAddressDetails.setAddressDetails(addressDetails);

		LendingGstDetail lendingGstDetail = lendingGstDao.findByApplicationId(lendingApplication.getId());
		if (!ObjectUtils.isEmpty(lendingGstDetail)) {
			customerAddressDetails.setType(lendingGstDetail.getAddressType());
		}

		log.info("Customer Address Details for merchant: {} is {}", lendingApplication.getMerchantId(), customerAddressDetails);
		return customerAddressDetails;
	}

	private AddressDetails fetchAddressDetails(LendingApplication lendingApplication) {

		List<KycDoc> kycDocs = kycHandler.getKycDoc(lendingApplication.getMerchantId(), false, true, "POA");
		if (ObjectUtils.isEmpty(kycDocs)) {
			log.error("KycDocs not found for merchantId: {}", lendingApplication.getMerchantId());
			return null;
		}
		AddressDetails addressDetails = new AddressDetails();
		for (KycDoc doc : kycDocs) {
			try {
				if ("POA".equalsIgnoreCase(doc.getDocType().name())) {
					addressDetails.setAddress1(doc.getAddress());
					addressDetails.setCity(doc.getCity());
					addressDetails.setPincode(Long.parseLong(doc.getPincode()));
					addressDetails.setState(doc.getState());
					addressDetails.setStateCode(Objects.nonNull(StateMapping.getStateEnum(doc.getState())) ?
							StateMapping.getStateEnum(doc.getState()).name() : null);
				}
			} catch (Exception e) {
				log.info("error in processing kyc doc {} {}", e.getMessage(), Arrays.asList(e.getStackTrace()));
			}
		}

		if (!ObjectUtils.isEmpty(lendingApplication.getLatitude()) && !ObjectUtils.isEmpty(lendingApplication.getLongitude())) {
			addressDetails.setLatitude(Float.parseFloat(lendingApplication.getLatitude()));
			addressDetails.setLongitude(Float.parseFloat(lendingApplication.getLongitude()));
		}

		addressDetails.setLandmark(lendingApplication.getLandmark());

		return addressDetails;
	}

}