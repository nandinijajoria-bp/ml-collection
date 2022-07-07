package com.bharatpe.lending.service;

import java.util.LinkedHashMap;
import java.util.Map;

import com.bharatpe.lending.common.bpnewmaster.dao.DocKycDetailsDaoMaster;
import com.bharatpe.lending.common.bpnewmaster.dao.DocumentsIdProofDaoMaster;
import com.bharatpe.lending.common.bpnewmaster.entity.DocKycDetailsMaster;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.bharatpe.common.objects.CommonAPIRequest;

@Service
public class SavePanCardDetailsService {
	
//	@Autowired
//	DocKycDetailsDaoMaster docKycDetailsDaoMaster;
	
//	public Map<String, String> savePanCardDetails(CommonAPIRequest commonAPIRequest) {
//		Map<String, String> finalResponse = new LinkedHashMap<>();
//
//		Long applicationId =  Long.parseLong(commonAPIRequest.getPayload().get("application_id").toString());
//		Long merchantId =  Long.parseLong(commonAPIRequest.getPayload().get("merchant_id").toString());
//		Map<String, String> panCardDetails = (Map<String, String>)commonAPIRequest.getPayload().get("pancard_details");
//
//		DocKycDetailsMaster docKycDetails = docKycDetailsDaoMaster.fetchLatestPanCardDetails(merchantId, applicationId);
//
//		saveAddressProofDetails(docKycDetails, panCardDetails, applicationId, merchantId);
//
//		finalResponse.put("response", "success");
//		finalResponse.put("message", "Address Proof Details Updated Successfully!");
//
//		return finalResponse;
//	}
	
//	private void saveAddressProofDetails(DocKycDetailsMaster docKycDetails, Map<String, String> pancardDetails, Long applicationId, Long merchantId) {
//		DocKycDetailsMaster toSave = new DocKycDetailsMaster();
//		if(docKycDetails != null) {
//			String doi = null;
//			if(pancardDetails.get("date_of_issue") != null && !pancardDetails.get("date_of_issue").isEmpty()) {
//				doi = pancardDetails.get("date_of_issue");
//			}
//			String mode = "MANUAL";
////			docKycDetailsDao.updateKycPanCardDetails(pancardDetails.get("doc_no"),pancardDetails.get("father_name"),pancardDetails.get("person_name"),pancardDetails.get("dob"),doi,mode,merchantId,docKycDetails.getDocId(),"LENDING");
//		}else {
////			toSave.setDocId(docId);
////			toSave.setMerchantId(merchantId);
//			toSave.setDocNo(pancardDetails.get("doc_no"));
//			toSave.setFatherName(pancardDetails.get("father_name"));
//			toSave.setPersonName(pancardDetails.get("person_name"));
//			toSave.setDob(pancardDetails.get("dob"));
//			toSave.setDocType("pancard");
//			toSave.setMode("MANUAL");
//			toSave.setModule("LENDING");
//			if(pancardDetails.get("date_of_issue") != null && !pancardDetails.get("date_of_issue").isEmpty()) {
//				toSave.setDoi(pancardDetails.get("date_of_issue"));
//			}
//			docKycDetailsDaoMaster.save(toSave);
//		}
//	}
}
