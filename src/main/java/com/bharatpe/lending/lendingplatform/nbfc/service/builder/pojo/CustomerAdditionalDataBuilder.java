package com.bharatpe.lending.lendingplatform.nbfc.service.builder.pojo;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.entity.LendingRiskVariablesSnapshot;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.dao.LendingKfsDao;
import com.bharatpe.lending.dao.MerchantAggregateDataDao;
import com.bharatpe.lending.entity.LendingKfs;
import com.bharatpe.lending.entity.MerchantAggregateData;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.CustomerAdditionalData;
import com.bharatpe.lending.loanV3.utils.DocUploadUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

@Service
@Slf4j
public class CustomerAdditionalDataBuilder {

	@Autowired
	private MerchantAggregateDataDao merchantAggregateDataDao;

	@Autowired
	private ObjectMapper objectMapper;
	@Autowired
	DocUploadUtils docUploadUtils;
	@Autowired
	LendingKfsDao lendingKfsDao;

	public CustomerAdditionalData buildCustomerAdditionalData(
			LendingApplication lendingApplication,
			LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot,
			BasicDetailsDto basicDetailsDto) {
		log.info("Fetching Customer Additional Data for merchant with applicationId: {}", lendingApplication.getId());

		CustomerAdditionalData customerAdditionalData = new CustomerAdditionalData();
		Long applicationId = lendingApplication.getId();
		LendingKfs lendingKfs = lendingKfsDao.findTop1ByApplicationIdAndLenderOrderByIdDesc(applicationId, lendingApplication.getLender());
		customerAdditionalData.setIp(lendingApplication.getIp());
		if (Objects.nonNull(lendingKfs)) {
			customerAdditionalData.setLoanAgreement(getLoanDocsMergedUrl(lendingKfs, applicationId));
		}
		if (!ObjectUtils.isEmpty(lendingApplication.getLatitude()) && !ObjectUtils.isEmpty(lendingApplication.getLongitude())) {
			customerAdditionalData.setMobileLatitude(Float.parseFloat(lendingApplication.getLatitude()));
			customerAdditionalData.setMobileLongitude(Float.parseFloat(lendingApplication.getLongitude()));
		}
		populateMerchantAggregatedData(lendingApplication, lendingRiskVariablesSnapshot, customerAdditionalData);

		if (!ObjectUtils.isEmpty(basicDetailsDto)) {
			customerAdditionalData.setSettlementType(basicDetailsDto.getSettlementType());
			customerAdditionalData.setSettlementLevel(basicDetailsDto.getSettlementLevel());
		}

		log.debug("Customer Additional Data for merchant: {} is {}", lendingApplication.getMerchantId(), customerAdditionalData);

		return customerAdditionalData;
	}

	private void populateMerchantAggregatedData(
			LendingApplication lendingApplication,
			LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot,
			CustomerAdditionalData customerAdditionalData) {

		if (!ObjectUtils.isEmpty(lendingRiskVariablesSnapshot)) {
			MerchantAggregateData merchantAggregateData =
					merchantAggregateDataDao.findByMerchantIdAndAggregateId
							(lendingApplication.getMerchantId(), lendingRiskVariablesSnapshot.getAggregateId());
			if (!ObjectUtils.isEmpty(merchantAggregateData)) {
				customerAdditionalData.setApplicationType(merchantAggregateData.getApplicationType());
				customerAdditionalData.setAggregatedId(merchantAggregateData.getAggregateId());

				try {
					customerAdditionalData.setSources(objectMapper.readTree(merchantAggregateData.getSources()));
					customerAdditionalData.setScienapticProperties(objectMapper.readTree(merchantAggregateData.getScienapticProperties()));
				} catch (IOException exception) {
					log.error("Error occured while parsing merchant aggregate data for merchantId: {}",
							lendingApplication.getMerchantId(), exception);
				}

			}
		}
	}

	private String getLoanDocsMergedUrl(LendingKfs lendingKfs, Long applicationId){
		try{
			String kfsDocFile = lendingKfs.getKfsDocFile();
			String loanAgreementDocFile = lendingKfs.getSanctionLoanAgreementDocFile();
			return docUploadUtils.mergeUnsignedDocs(applicationId, kfsDocFile, loanAgreementDocFile);
		} catch(Exception e) {
			log.warn("Exception while creating merged loan docs of OXYZO for applicationId: {}, {}, {}", applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));

		}
		return null;
	}
}
