package com.bharatpe.lending.lendingplatform.nbfc.service.builder.pojo;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.dto.KycDoc;
import com.bharatpe.lending.handlers.KycHandler;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.KYCDocuments;
import com.bharatpe.lending.lendingplatform.nbfc.enums.KycDocStatus;
import com.bharatpe.lending.lendingplatform.nbfc.enums.KycDocType;
import com.bharatpe.lending.lendingplatform.nbfc.util.BuildersUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.bharatpe.lending.lendingplatform.nbfc.enums.DocType.DIGILOCKER_AADHAAR_XML;
import static com.bharatpe.lending.lendingplatform.nbfc.enums.DocType.SELFIE;

@Service
@Slf4j
public class KYCDocumentsBuilder {

	@Autowired
	KycHandler kycHandler;

	public Map<KycDocType, KYCDocuments> buildKYCDocuments(LendingApplication lendingApplication) {
		log.info("Fetching Bank Details for merchant: {}", lendingApplication.getMerchantId());

		List<KycDoc> kycDocs = kycHandler.getKycDoc(lendingApplication.getMerchantId(), false, true, "POA,PAN_NO,SELFIE");
		if (ObjectUtils.isEmpty(kycDocs)) {
			log.error("KycDocs not found for merchantId: {}", lendingApplication.getMerchantId());
			return null;
		}

		Map<KycDocType, KYCDocuments> kycDocuments = new HashMap<>();
		for (KycDoc doc : kycDocs) {
			switch (doc.getDocType()) {
				case POA:
					kycDocuments.put(KycDocType.POA, getPoaDocument(doc));
					break;
				case PAN_NO:
					kycDocuments.put(KycDocType.PAN_NO, getPanDocument(doc));
					break;
				case SELFIE:
					kycDocuments.put(KycDocType.SELFIE, getSelfieDocument(doc));
					break;
			}
		}

		log.debug("KYC Documents for merchant: {} are: {}", lendingApplication.getMerchantId(), kycDocuments);
		return kycDocuments;
	}

	private KYCDocuments getPoaDocument(KycDoc doc) {
		return KYCDocuments.builder()
				.pincode(Integer.parseInt(doc.getPincode()))
				.docIdentifier(doc.getDocIdentifier())
				.dob(doc.getDob())
				.name(doc.getName())
				.gender(doc.getGender())
				.state(doc.getState())
				.city(doc.getCity())
				.address(doc.getAddress())
				.base64(BuildersUtil.convertXmlToBase64String(
						!ObjectUtils.isEmpty(doc.getXml()) ?
								BuildersUtil.convertXmlToBase64String(doc.getXml()) :
								BuildersUtil.convertXmlToBase64String(doc.getDigioXml())))
				.docType(DIGILOCKER_AADHAAR_XML)
				.nameMatchPer(BigDecimal.valueOf(doc.getNameMatchPer()))
				.status(KycDocStatus.valueOf(doc.getStatus().name()))
				.xml(BuildersUtil.convertXmlToString(
						!ObjectUtils.isEmpty(doc.getXml()) ?
								BuildersUtil.convertXmlToString(doc.getXml()) :
								BuildersUtil.convertXmlToString(doc.getDigioXml())))
				.build();
	}

	private KYCDocuments getPanDocument(KycDoc doc) {
		return KYCDocuments.builder()
				.docIdentifier(doc.getDocIdentifier())
				.dob(doc.getDob())
				.firstName(doc.getFirstName())
				.middleName(doc.getMiddleName())
				.lastName(doc.getLastName())
				.name(doc.getName())
				.nameMatchPer(BigDecimal.valueOf(doc.getNameMatchPer()))
				.status(KycDocStatus.valueOf(doc.getStatus().name()))
				.build();
	}

	private KYCDocuments getSelfieDocument(KycDoc doc) {
		return KYCDocuments.builder()
				.status(KycDocStatus.valueOf(doc.getStatus().name()))
				.docType(SELFIE)
				.url(doc.getDocFrontImageUrl())
				.base64(BuildersUtil.convertPreSignedUrlToBase64String(doc.getDocFrontImageUrl()).orElse(null))
				.livelinessScore(BigDecimal.valueOf(doc.getLivelinessScore()))
				.faceMatchPer(ObjectUtils.isEmpty(doc.getFaceMatchPer()) ?
						null : BigDecimal.valueOf(BuildersUtil.getParsedFaceMatchPer(doc.getFaceMatchPer())))
				.docFrontImageUrl(doc.getDocFrontImageUrl())
				.build();
	}

}