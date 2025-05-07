package com.bharatpe.lending.lendingplatform.nbfc.service.builder.pojo;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.dao.LendingKfsDao;
import com.bharatpe.lending.entity.LendingKfs;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.LoanDocument;
import com.bharatpe.lending.lendingplatform.nbfc.enums.DocType;
import com.bharatpe.lending.lendingplatform.nbfc.util.BuildersUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class LoanDocumentBuilder {

	@Autowired
	LendingKfsDao lendingKfsDao;

	@Autowired
	BuildersUtil buildersUtil;

	@Value("${aws.s3.bucket:loan-document}")
	private String bucket;

	public Map<DocType, LoanDocument> buildLoanDocuments(LendingApplication lendingApplication) {
		log.info("Fetching Bank Details for merchant: {}", lendingApplication.getMerchantId());

		LendingKfs lendingKfs = lendingKfsDao.findTop1ByApplicationIdAndLenderOrderByIdDesc(
				lendingApplication.getId(), lendingApplication.getLender());

		if (ObjectUtils.isEmpty(lendingKfs)) {
			return null;
		}
		Map<DocType, LoanDocument> loanDocuments = new HashMap<>();

		loanDocuments.put(DocType.KEY_FACT_STATEMENT, getKFSDocument(lendingKfs));
		loanDocuments.put(DocType.KEY_FACT_STATEMENT_NEW, getKFSDocument(lendingKfs));
		loanDocuments.put(DocType.LOAN_AGREEMENT, getLoanAgreementDocument(lendingKfs));
		loanDocuments.put(DocType.LOAN_AGREEMENT_NEW, getLoanAgreementDocument(lendingKfs));
//		loanDocuments.put(DocType.MITC, getMITC(lendingKfs));
//		loanDocuments.put(DocType.GTC, getGTC(lendingKfs));
//		loanDocuments.put(DocType.LOA, getLOA(lendingKfs));

		log.info("Loan Documents for merchant: {} is {}", lendingApplication.getMerchantId(), loanDocuments);
		return loanDocuments;
	}

	private LoanDocument getKFSDocument(LendingKfs lendingKfs) {
		if (ObjectUtils.isEmpty(lendingKfs.getKfsDocFile())) {
			return null;
		}
		String presignedUrl = buildersUtil.getS3PresignedUrlFromKey(lendingKfs.getKfsDocFile(), bucket);
		return LoanDocument.builder()
				.name(DocType.KEY_FACT_STATEMENT.name())
//				.data(BuildersUtil.convertPreSignedUrlToBase64String(presignedUrl).orElse(null))
				.url(presignedUrl)
				.type(DocType.KEY_FACT_STATEMENT)
				.build();
	}

	private LoanDocument getLoanAgreementDocument(LendingKfs lendingKfs) {
		if (ObjectUtils.isEmpty(lendingKfs.getSanctionLoanAgreementDocFile())) {
			return null;
		}
		String presignedUrl = buildersUtil.getS3PresignedUrlFromKey(lendingKfs.getSanctionLoanAgreementDocFile(), bucket);
		return LoanDocument.builder()
				.name(DocType.LOAN_AGREEMENT.name())
//				.data(BuildersUtil.convertPreSignedUrlToBase64String(presignedUrl).orElse(null))
				.url(presignedUrl)
				.type(DocType.LOAN_AGREEMENT)
				.build();
	}

	private LoanDocument getLOA(LendingKfs lendingKfs) {
		if (ObjectUtils.isEmpty(lendingKfs.getLoaDocFile())) {
			return null;
		}
		String presignedUrl = buildersUtil.getS3PresignedUrlFromKey(lendingKfs.getLoaDocFile(), bucket);
		return LoanDocument.builder()
				.name(DocType.LOA.name())
				.data(BuildersUtil.convertPreSignedUrlToBase64String(presignedUrl).orElse(null))
				.url(presignedUrl)
				.type(DocType.LOA)
				.build();
	}

	private LoanDocument getGTC(LendingKfs lendingKfs) {
		if (ObjectUtils.isEmpty(lendingKfs.getGtcDocFile())) {
			return null;
		}
		String presignedUrl = buildersUtil.getS3PresignedUrlFromKey(lendingKfs.getGtcDocFile(), bucket);
		return LoanDocument.builder()
				.name(DocType.GTC.name())
				.data(BuildersUtil.convertPreSignedUrlToBase64String(presignedUrl).orElse(null))
				.url(presignedUrl)
				.type(DocType.GTC)
				.build();
	}

	private LoanDocument getMITC(LendingKfs lendingKfs) {
		if (ObjectUtils.isEmpty(lendingKfs.getMitcDocFile())) {
			return null;
		}
		String presignedUrl = buildersUtil.getS3PresignedUrlFromKey(lendingKfs.getMitcDocFile(), bucket);
		return LoanDocument.builder()
				.name(DocType.MITC.name())
				.data(BuildersUtil.convertPreSignedUrlToBase64String(presignedUrl).orElse(null))
				.url(presignedUrl)
				.type(DocType.MITC)
				.build();
	}

}
