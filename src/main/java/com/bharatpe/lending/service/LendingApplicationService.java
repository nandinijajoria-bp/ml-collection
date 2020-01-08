package com.bharatpe.lending.service;

import com.bharatpe.common.dao.AvailableLoanDao;
import com.bharatpe.common.dao.MerchantSummaryDao;
import com.bharatpe.common.dao.MerchantSummarySnapshotDao;
import com.bharatpe.common.entities.*;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingAuditTrialDao;
import com.bharatpe.lending.dao.LendingCategoryDao;
import com.bharatpe.lending.dto.LendingApplicationRequestDTO;
import com.bharatpe.lending.dto.LendingApplicationResponseDTO;
import com.bharatpe.lending.dto.RequestDTO;
import com.bharatpe.lending.util.LoanCalculationUtil;
import com.bharatpe.lending.util.LoanCalculationUtil.LoanBreakupDetail;
import com.bharatpe.lending.util.LoanUtil;

import java.math.BigDecimal;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LendingApplicationService {
	private Logger logger = LoggerFactory.getLogger(LendingApplicationService.class);
	
	@Autowired
	LendingApplicationDao lendingApplicationDao;
	
	@Autowired
	AvailableLoanDao availableLoanDao;
	
	@Autowired
	LendingCategoryDao lendingCategoryDao;

	@Autowired
	LendingAuditTrialDao lendingAuditTrialDao;
	
	@Autowired
	MerchantSummarySnapshotDao merchantSummarySnapshotDao;
	
	@Autowired
	MerchantSummaryDao merchantSummaryDao;

	public LendingApplicationResponseDTO createApplication(Merchant merchant, RequestDTO<LendingApplicationRequestDTO> requestDTO) {
		LendingApplicationResponseDTO lendingApplicationResponse;
		LendingApplication lendingApplication;
		Long merchantId = merchant.getId();
		LendingApplicationRequestDTO lendingApplicationRequest = requestDTO.getPayload();

		if(lendingApplicationRequest.getApplicationId() != null && lendingApplicationRequest.getApplicationId() > 0) {
			lendingApplication = lendingApplicationDao.findByIdAndMerchantAndStatus(lendingApplicationRequest.getApplicationId(), merchant, "draft");
			if(lendingApplication == null) {
				logger.info("No application found in draft status for given application id {}", lendingApplicationRequest.getApplicationId());
				lendingApplicationResponse = new LendingApplicationResponseDTO();
				lendingApplicationResponse.setSuccess(false);
				return lendingApplicationResponse;
			}
			lendingApplication = updateApplication(lendingApplication, lendingApplicationRequest);
			lendingApplicationDao.save(lendingApplication);
		}else {
			AvailableLoan availableLoan = availableLoanDao.findByMerchantIdAndCategory(merchantId, lendingApplicationRequest.getCategory());
			if(availableLoan == null) {
				logger.info("No loan available for Merchant {} and category", merchantId, lendingApplicationRequest.getCategory());
				lendingApplicationResponse = new LendingApplicationResponseDTO();
				lendingApplicationResponse.setSuccess(false);
				return lendingApplicationResponse;
			}
			lendingApplication = createApplication(merchant, availableLoan, lendingApplicationRequest);
			lendingApplication.setLatitude(requestDTO.getMeta().getLatitude());
			lendingApplication.setLongitude(requestDTO.getMeta().getLongitude());
			lendingApplication.setIp(requestDTO.getMeta().getIp());
			lendingApplicationDao.save(lendingApplication);
			createMerchantSummarySnapshot(merchant, lendingApplication, null);
			createStatusAuditTrail(lendingApplication);
		}

		logger.info("Loan Application saved : {}",lendingApplication);
		return prepareAPIResponse(lendingApplication);
	}
	
	private LendingApplication updateApplication(LendingApplication lendingApplication, LendingApplicationRequestDTO lendingApplicationRequest) {
		lendingApplication.setBusinessName(lendingApplicationRequest.getBusinessName());
		lendingApplication.setShopNumber(lendingApplicationRequest.getShopNumber());
		lendingApplication.setStreetAddress(lendingApplicationRequest.getStreetAddress());
		lendingApplication.setArea(lendingApplicationRequest.getArea());
		lendingApplication.setLandmark(lendingApplicationRequest.getLandmark());
		lendingApplication.setPincode(lendingApplicationRequest.getPincode());
		lendingApplication.setCity(lendingApplicationRequest.getCity());
		lendingApplication.setState(lendingApplicationRequest.getState());
		return lendingApplication;
	}
	
	private LendingApplication createApplication(Merchant merchant, AvailableLoan availableLoan, LendingApplicationRequestDTO lendingApplicationRequest) {
		LendingApplication lendingApplication = new LendingApplication();
		LendingCategories lendingCategory = lendingCategoryDao.findByCategory(availableLoan.getCategory()).get(0);
		
		LoanBreakupDetail breakupDetail = LoanCalculationUtil.getLoanBreakup(availableLoan, lendingCategory);
		
		lendingApplication.setEdi(Double.valueOf(breakupDetail.getEdi()));
		lendingApplication.setIoEdi(Double.valueOf(breakupDetail.getIoEdi()));
		lendingApplication.setRepayment(Double.valueOf(breakupDetail.getRepayment()));
		lendingApplication.setInterestRate(breakupDetail.getEffectiveInterestRate());
		lendingApplication.setProcessingFee(Double.valueOf(breakupDetail.getProcessingFee()));
		lendingApplication.setDisbursalAmount(Double.valueOf(breakupDetail.getDisbursementAmount()));
		lendingApplication.setStatus("draft");
		lendingApplication.setMode("AUTO");
		lendingApplication.setMerchant(merchant);
		lendingApplication.setLoanAmount(availableLoan.getAmount());
		lendingApplication.setCategory(availableLoan.getCategory());
		lendingApplication.setTenure(lendingCategory.getPayableConverter());
		lendingApplication.setTenureInMonths(lendingCategory.getTenureMonths().intValue());
		lendingApplication.setPayableDays(Long.valueOf(lendingCategory.getPayableDays()));
		lendingApplication.setEdiFreeDays(lendingCategory.getEdiFreeDays());
		lendingApplication.setIoPayableDays(lendingCategory.getIoPayableDays());
		lendingApplication.setLoanConstruct(availableLoan.getLoanConstruct());

		lendingApplication = updateApplication(lendingApplication, lendingApplicationRequest);

		return lendingApplication;
	}

	private void createStatusAuditTrail(LendingApplication lendingApplication) {
		LendingAuditTrial lendingAuditTrial = new LendingAuditTrial();
		lendingAuditTrial.setMerchantId(lendingApplication.getMerchant().getId());
		lendingAuditTrial.setApplicationId(lendingApplication.getId());
		lendingAuditTrial.setLoanId("");
		lendingAuditTrial.setUserId(Long.parseLong("0"));
		lendingAuditTrial.setNewStatus("draft");
		lendingAuditTrial.setType("APP_STATUS");
		lendingAuditTrialDao.save(lendingAuditTrial);
	}
	
	private LendingApplicationResponseDTO prepareAPIResponse(LendingApplication lendingApplication) {
		LendingApplicationResponseDTO lendingApplicationResponse = new LendingApplicationResponseDTO();
		LendingApplicationResponseDTO.LoanApplication loanApplication = lendingApplicationResponse.new LoanApplication();

		loanApplication.setApplicationId(lendingApplication.getId());
		loanApplication.setApplicationStatus(lendingApplication.getStatus());
		loanApplication.setSelectedLoan(LoanUtil.prepareSelectedLoanForClient(lendingApplication));
		loanApplication.setShopDetails(LoanUtil.prepareShopDetailsForClient(lendingApplication));
		lendingApplicationResponse.setLoanApplication(loanApplication);
		lendingApplicationResponse.setSuccess(true);

		return lendingApplicationResponse;
	}
	
	public void createMerchantSummarySnapshot(Merchant merchant, LendingApplication application, MerchantSummary summary) {
		try {
			if(summary == null)
				summary = merchantSummaryDao.getByMerchantId(merchant.getId());
			
			MerchantSummarySnapshot snapshot = new MerchantSummarySnapshot();
			List<Object[]> data = availableLoanDao.getMaxEligibilityDataForMerchant(merchant.getId());
			
			snapshot.setApplication(application);
			snapshot.setMerchant(merchant);
			snapshot.setLastTransactionDate(summary.getLastTransactionDate());
			snapshot.setTotalTxnCount(summary.getDailyTxnCount());
			snapshot.setTotalTxnAmount(summary.getDailyTxnAmount());
			snapshot.setCategory(summary.getCategory());
			snapshot.setAvgTpv(summary.getAvgTpv());
			snapshot.setMaxEligibleLoanAmount(((BigDecimal) data.get(0)[0]).doubleValue());
			snapshot.setEligibleLoanCategories((String) data.get(0)[1]);
			snapshot.setLoanType(summary.getLoanType());
			snapshot.setTpv1Mon(summary.getTpv1Mon());
			snapshot.setTpv2Mon(summary.getTpv2Mon());
			snapshot.setTpv3Mon(summary.getTpv3Mon());
			snapshot.setTxnDayCount1Mon(summary.getTxnDayCount1Mon());
			snapshot.setTxnDayCount2Mon(summary.getTxnDayCount2Mon());
			snapshot.setTxnDayCount3Mon(summary.getTxnDayCount3Mon());
			snapshot.setTotalTxns1Month(summary.getTotalTxns1Month());
			snapshot.setTotalTxns2Month(summary.getTotalTxns2Month());
			snapshot.setTotalTxns3Month(summary.getTotalTxns3Month());
			snapshot.setTotalLoansCount(summary.getTotalLoansCount());
			
			merchantSummarySnapshotDao.save(snapshot);
		} catch(Exception ex) {
			logger.error("Exception while creating merchant summary snapshot for merchant id {} and application id {}, Exception is {}", merchant.getId(), application.getId(), ex);
		}
	}
}
