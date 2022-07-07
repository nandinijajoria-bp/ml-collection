package com.bharatpe.lending.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.bharatpe.lending.common.bpnewmaster.dao.DocKycDetailsDaoMaster;
import com.bharatpe.lending.common.bpnewmaster.dao.DocumentsIdProofDaoMaster;
import com.bharatpe.lending.common.bpnewmaster.entity.DocKycDetailsMaster;
import com.bharatpe.lending.common.dao.LendingEkycDao;
import com.bharatpe.lending.common.entity.LendingEkyc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.bharatpe.common.objects.CommonAPIRequest;

@Service
public class SaveAddressProofDetailsService {
	
	@Autowired
	DocKycDetailsDaoMaster docKycDetailsDaoMaster;
	
	@Autowired
	DocumentsIdProofDaoMaster documentsIdProofDaoMaster;

	@Autowired
	LendingEkycDao lendingEkycDao;
	
//	public Map<String, String> saveAddressProofDetails(CommonAPIRequest commonAPIRequest) {
//		Map<String, String> finalResponse = new LinkedHashMap<>();
//
//		Long applicationId =  Long.parseLong(commonAPIRequest.getPayload().get("application_id").toString());
//		Long merchantId =  Long.parseLong(commonAPIRequest.getPayload().get("merchant_id").toString());
//		Map<String, String> addressProofDetails = (Map<String, String>)commonAPIRequest.getPayload().get("address_proof_details");
//
//		DocKycDetailsMaster docKycDetails = docKycDetailsDaoMaster.fetchLatestAddressDetails(merchantId, applicationId);
//
//		saveAddressProofDetails(docKycDetails, addressProofDetails, applicationId, merchantId);
//
//		finalResponse.put("response", "success");
//		finalResponse.put("message", "Address Proof Details Updated Successfully!");
//
//		return finalResponse;
//	}
	
//	private void saveAddressProofDetails(DocKycDetailsMaster docKycDetails, Map<String, String> addressProofDetails, Long applicationId, Long merchantId) {
//		DocKycDetailsMaster toSave = new DocKycDetailsMaster();
//		if(docKycDetails != null) {
//			toSave.setId(docKycDetails.getId());
////			toSave.setDocId(docKycDetails.getDocId());
//			toSave.setQr(docKycDetails.getQr());
//			toSave.setGender(docKycDetails.getGender());
//			toSave.setMotherName(docKycDetails.getMotherName());
//			toSave.setCountryCode(docKycDetails.getCountryCode());
//			toSave.setResponse(docKycDetails.getResponse());
//			toSave.setStatus(docKycDetails.getStatus());
//		}else {
//			Long docId = documentsIdProofDaoMaster.fetchLatestAddressProofDocId(merchantId, applicationId, "LENDING");
////			toSave.setDocId(docId);
//		}
////		toSave.setMerchantId(merchantId);
//		LendingEkyc lendingEkyc = lendingEkycDao.fetchEkycByMerchantId(merchantId);
//		if("eaadhar".equalsIgnoreCase(addressProofDetails.get("doc_type")) && Objects.nonNull(lendingEkyc)) {
//			toSave.setDocNo(lendingEkyc.getMaskedAadhar());
//		}
//		else {
//			toSave.setDocNo(addressProofDetails.get("doc_no"));
//		}
//		toSave.setFatherName(addressProofDetails.get("father_name"));
//		toSave.setPersonName(addressProofDetails.get("person_name"));
//		toSave.setDob(addressProofDetails.get("dob"));
//		toSave.setMode("MANUAL");
//		toSave.setModule("LENDING");
//		if(addressProofDetails.get("doc_type") != null && !addressProofDetails.get("doc_type").isEmpty()) {
//			toSave.setDocType(addressProofDetails.get("doc_type"));
//		}
//		if(addressProofDetails.get("address") != null && !addressProofDetails.get("address").isEmpty()) {
//			toSave.setAddress(addressProofDetails.get("address"));
//		}
//		if(addressProofDetails.get("city") != null && !addressProofDetails.get("city").isEmpty()) {
//			toSave.setCity(addressProofDetails.get("city"));
//		}
//		if(addressProofDetails.get("state") != null && !addressProofDetails.get("state").isEmpty()) {
//			toSave.setState(addressProofDetails.get("state"));
//		}
//		if(addressProofDetails.get("pin_code") != null && !addressProofDetails.get("pin_code").isEmpty()) {
//			toSave.setPincode(addressProofDetails.get("pin_code"));
//		}
//		if(addressProofDetails.get("date_of_issue") != null && !addressProofDetails.get("date_of_issue").isEmpty()) {
//			toSave.setDoi(addressProofDetails.get("date_of_issue"));
//		}
//		docKycDetailsDaoMaster.save(toSave);
//	}
}
