package com.bharatpe.lending.lendingplatform.nbfc.service.builder.pojo;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingGstDetail;
import com.bharatpe.lending.common.dao.LendingShopDocumentsDao;
import com.bharatpe.lending.common.entity.LendingShopDocuments;
import com.bharatpe.lending.dao.LendingGstDao;
import com.bharatpe.lending.dto.DSMainResponse;
import com.bharatpe.lending.dto.KycDoc;
import com.bharatpe.lending.handlers.DsHandler;
import com.bharatpe.lending.handlers.KycHandler;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.AddressDetails;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.CustomerAddressDetails;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.Location;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.enums.StateMapping;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@Slf4j
public class CustomerAddressDetailsBuilder {

	@Autowired
	KycHandler kycHandler;

	@Autowired
	LendingGstDao lendingGstDao;

	@Autowired
	LendingShopDocumentsDao lendingShopDocumentsDao;

	@Autowired
	DsHandler dsHandler;


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

		Location location =  getLocation(lendingApplication);
		log.info("Location of application id: {} {}", lendingApplication.getId(), location);
		addressDetails.setLatitude(location.getLatitude());
		addressDetails.setLongitude(location.getLongitude());
		addressDetails.setLandmark(lendingApplication.getLandmark());

		return addressDetails;
	}

	private Location getLocation(LendingApplication lendingApplication) {
		Location location = new Location();
		try{
			if (!ObjectUtils.isEmpty(lendingApplication.getLatitude()) && !ObjectUtils.isEmpty(lendingApplication.getLongitude())) {
				location.setLatitude(Float.parseFloat(lendingApplication.getLatitude()));
				location.setLongitude(Float.parseFloat(lendingApplication.getLongitude()));
				return location;
			}
			LendingShopDocuments lendingShopDocument = lendingShopDocumentsDao.findTop1ByMerchantIdAndApplicationId(lendingApplication.getMerchantId(), lendingApplication.getId());
			if (!ObjectUtils.isEmpty(lendingShopDocument)) {
				location.setLatitude(Float.parseFloat(lendingShopDocument.getLatitude()));
				location.setLongitude(Float.parseFloat(lendingShopDocument.getLongitude()));
				return location;
			}
			DSMainResponse dsMainResponse = dsHandler.fetchDSMainVariables(lendingApplication.getMerchantId(),
					lendingApplication.getId());

			if (!ObjectUtils.isEmpty(dsMainResponse) && !ObjectUtils.isEmpty(dsMainResponse.getLocation())
					&& !ObjectUtils.isEmpty(dsMainResponse.getLocation().getInferredLat()) &&
					!ObjectUtils.isEmpty(dsMainResponse.getLocation().getInferredLon())) {
				location.setLatitude(Float.parseFloat(dsMainResponse.getLocation().getInferredLat()));
				location.setLongitude(Float.parseFloat(dsMainResponse.getLocation().getInferredLon()));
				return location;
			}
		} catch (Exception e) {
			log.warn("error while getting latitude and longitude for application Id {} and merchantId {}, {}, {}", lendingApplication.getId(), lendingApplication.getMerchantId(), e, Arrays.asList(e.getStackTrace()));
		}
		return location;
	}

}