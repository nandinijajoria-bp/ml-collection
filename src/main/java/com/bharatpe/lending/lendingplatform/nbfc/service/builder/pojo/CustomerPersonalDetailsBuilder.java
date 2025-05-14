package com.bharatpe.lending.lendingplatform.nbfc.service.builder.pojo;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.dao.LendingPancardDetailsDao;
import com.bharatpe.lending.dto.KycDoc;
import com.bharatpe.lending.entity.LendingPancardDetails;
import com.bharatpe.lending.handlers.KycHandler;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.CustomerPersonalDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;
import java.util.List;

@Service
@Slf4j
public class CustomerPersonalDetailsBuilder {

	@Autowired
	KycHandler kycHandler;

	@Autowired
	LendingPancardDetailsDao lendingPancardDetailsDao;

	public static String getFatherName(String careOf) {
		try {
			if (ObjectUtils.isEmpty(careOf) || !careOf.contains("S/O")) {
				return null;
			}
			careOf = careOf.toUpperCase();
			careOf = careOf.replaceAll("S/O", "").replaceAll("\\.", "").replaceAll(":", "").replaceFirst(" ", "");
			return careOf.substring(0, careOf.indexOf(","));
		} catch (Exception e) {
			log.info("Exception in fetching father name from kyc address {}", Arrays.asList(e.getStackTrace()));
		}
		return null;
	}

	public CustomerPersonalDetails buildCustomerPersonalDetails(LendingApplication lendingApplication, BasicDetailsDto basicDetails) {
		log.info("Fetching Customer Personal Details for merchant : {}", lendingApplication.getMerchantId());

		if (ObjectUtils.isEmpty(basicDetails)) {
			log.error("Merchant Details not found for merchantId: {}", lendingApplication.getMerchantId());
			return null;
		}
		return populatePersonalDetails(lendingApplication, basicDetails);
	}

	private CustomerPersonalDetails populatePersonalDetails(LendingApplication lendingApplication, BasicDetailsDto basicDetails) {
		CustomerPersonalDetails customerPersonalDetails = populateAadhaarAndPanDetails(lendingApplication);
		if (!ObjectUtils.isEmpty(customerPersonalDetails)) {
			customerPersonalDetails.setCustomerId(basicDetails.getId());
			customerPersonalDetails.setEmail(basicDetails.getEmail());
			customerPersonalDetails.setCreatedAt(basicDetails.getCreatedAt());
			customerPersonalDetails.setMobile(Long.parseLong(basicDetails.getMobile().substring(2)));
			customerPersonalDetails.setMerchantType(basicDetails.getMerchantType());
			customerPersonalDetails.setMid(basicDetails.getMid());
		}
		log.debug("Customer Personal Details for merchant: {} is {}", lendingApplication.getMerchantId(), customerPersonalDetails);

		return customerPersonalDetails;
	}

	private CustomerPersonalDetails populateAadhaarAndPanDetails(LendingApplication lendingApplication) {
		List<KycDoc> kycDocs = kycHandler.getKycDoc(lendingApplication.getMerchantId(), false, true, "PAN_NO,POA");
		if (ObjectUtils.isEmpty(kycDocs)) {
			log.error("KycDocs not found for merchantId: {}", lendingApplication.getMerchantId());
			return null;
		}
		CustomerPersonalDetails.AadhaarDetails aadhaarDetails = null;
		CustomerPersonalDetails.PanDetails panDetails = null;
		for (KycDoc doc : kycDocs) {
			try {
				if ("POA".equalsIgnoreCase(doc.getDocType().name())) {
					aadhaarDetails = fetchAadhaarDetails(doc);
				} else if ("PAN_NO".equalsIgnoreCase(doc.getDocType().name())) {
					panDetails = fetchPanDetails(doc, lendingApplication.getMerchantId());
				}
			} catch (Exception e) {
				log.info("error in processing kyc doc {} {}", e.getMessage(), Arrays.asList(e.getStackTrace()));
			}
		}
		return CustomerPersonalDetails.builder()
				.aadhaarDetails(aadhaarDetails)
				.panDetails(panDetails)
				.build();
	}

	private CustomerPersonalDetails.PanDetails fetchPanDetails(KycDoc doc, long merchantId) {
		return CustomerPersonalDetails.PanDetails.builder()
				.pan(doc.getDocIdentifier())
				.nameDobDetails(getPanNameDobDetails(doc, merchantId))
				.gender(doc.getGender())
				.build();
	}

	private CustomerPersonalDetails.AadhaarDetails fetchAadhaarDetails(KycDoc doc) {
		return CustomerPersonalDetails.AadhaarDetails.builder()
				.aadhaar(doc.getDocIdentifier())
				.gender(doc.getGender())
				.fatherName(getFatherName(doc.getAddress()))
				.nameDobDetails(getAadhaarNameDobDetails(doc))
				.build();
	}

	private CustomerPersonalDetails.NameDobDetails getPanNameDobDetails(KycDoc doc, long merchantId) {

		CustomerPersonalDetails.NameDobDetails nameDobDetails = new CustomerPersonalDetails.NameDobDetails();
		String fullname = doc.getName();
		String firstName = doc.getFirstName();
		String middleName = doc.getMiddleName();
		String lastName = doc.getLastName();
		if (!ObjectUtils.isEmpty(doc.getDob())) {
			nameDobDetails.setDob(DateTimeUtil.parseDate(doc.getDob(), "dd/MM/yyyy"));
			LendingPancardDetails lendingPancardDetails = lendingPancardDetailsDao.findTop1ByMerchantIdOrderByIdDesc(merchantId);
			if (!ObjectUtils.isEmpty(lendingPancardDetails)
					&& !ObjectUtils.isEmpty(lendingPancardDetails.getName())
			) {
				fullname = lendingPancardDetails.getName().trim();
			}
		}
		if (!ObjectUtils.isEmpty(fullname)) {
			if (fullname.contains(" ")) {
				String temp = fullname;
				firstName = temp.substring(0, fullname.indexOf(" ")).trim();
				temp = temp.substring(firstName.length()).trim();
				middleName = "";
				if (temp.contains(" ")) {
					middleName = temp.substring(0, temp.indexOf(" ")).trim();
				}
				temp = temp.substring(middleName.length()).trim();
				lastName = temp;
			} else {
				firstName = fullname;
			}
		}
		nameDobDetails.setFirstName(firstName);
		nameDobDetails.setMiddleName(middleName);
		nameDobDetails.setLastName(lastName);
		return nameDobDetails;
	}

	private CustomerPersonalDetails.NameDobDetails getAadhaarNameDobDetails(KycDoc doc) {

		CustomerPersonalDetails.NameDobDetails nameDobDetails = new CustomerPersonalDetails.NameDobDetails();
		String fullname = doc.getName();
		String firstName = "";
		String middleName = "";
		String lastName = "";
		nameDobDetails.setDob(DateTimeUtil.parseDate(doc.getDob(), "dd/MM/yyyy"));
		if (!ObjectUtils.isEmpty(fullname)) {
			if (fullname.contains(" ")) {
				String temp = fullname;
				firstName = temp.substring(0, fullname.indexOf(" ")).trim();
				temp = temp.substring(firstName.length()).trim();
				middleName = "";
				if (temp.contains(" ")) {
					middleName = temp.substring(0, temp.indexOf(" ")).trim();
				}
				temp = temp.substring(middleName.length()).trim();
				lastName = temp;
			} else {
				firstName = fullname;
			}
		}
		nameDobDetails.setFirstName(firstName);
		nameDobDetails.setMiddleName(middleName);
		nameDobDetails.setLastName(lastName);
		return nameDobDetails;
	}
}
