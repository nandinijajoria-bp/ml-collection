package com.bharatpe.lending.service;

import com.amazonaws.services.dynamodbv2.xspec.L;
import com.bharatpe.common.constants.ResponseCode;
import com.bharatpe.common.dao.AvailableLoanDao;
import com.bharatpe.common.entities.AvailableLoan;
import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingCategories;
import com.bharatpe.common.entities.Merchant;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingCategoryDao;
import com.bharatpe.lending.dto.LendingApplicationRequestDTO;
import com.bharatpe.lending.dto.LendingApplicationResponse;
import com.bharatpe.lending.dto.RequestDTO;
import com.bharatpe.lending.util.LoanCalculationUtil;
import com.bharatpe.lending.util.LoanCalculationUtil.LoanBreakupDetail;
import com.bharatpe.lending.util.LoanUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class LendingApplicationService {
	private Logger logger = LoggerFactory.getLogger(LendingApplicationService.class);
	
	@Autowired
	LendingApplicationDao lendingApplicationDao;
	
	@Autowired
	AvailableLoanDao availableLoanDao;
	
	@Autowired
	LendingCategoryDao lendingCategoryDao;
	
	public LendingApplicationResponse createApplication(Merchant merchant, HttpServletResponse response, RequestDTO<LendingApplicationRequestDTO> requestDTO) {
		LendingApplicationResponse resp;
		LendingApplication lendingApplication;
		
		Long merchantId = merchant.getId();
		LendingApplicationRequestDTO lendingApplicationRequestDTO = requestDTO.getPayload();


		if(lendingApplicationRequestDTO != null) {
			if(lendingApplicationRequestDTO.getApplicationId() != null && lendingApplicationRequestDTO.getApplicationId() > 0) {
				lendingApplication = lendingApplicationDao.fetchApplicationByIdAndStatus(lendingApplicationRequestDTO.getApplicationId(), merchantId);
				lendingApplication = updateShopDetail(lendingApplication, lendingApplicationRequestDTO);
			}else {
				AvailableLoan availableLoan = availableLoanDao.findByMerchantIdAndCategory(merchantId, lendingApplicationRequestDTO.getCategory());
				if(availableLoan == null) {
					logger.info("No loan available for Merchant {} and category", merchantId, lendingApplicationRequestDTO.getCategory());
					response.setStatus(Integer.parseInt(ResponseCode.NOT_FOUND));
					resp = new LendingApplicationResponse();
					resp.setSuccess(false);
					return resp;
				}
				lendingApplication = updateShopDetail(availableLoan, lendingApplicationRequestDTO);
			}
			lendingApplication.setLatitude(requestDTO.getMeta().getLatitude());
			lendingApplication.setLongitude(requestDTO.getMeta().getLongitude());
			lendingApplication.setIp(requestDTO.getMeta().getIp());
			lendingApplicationDao.save(lendingApplication);
			resp = prepareAPIResponse(lendingApplication);
			logger.info("LendingUploadSerivce saved to lending_application : {}",lendingApplication);
		}else {
			logger.info("LendingUploadSerivce invalid request parameters : {}", requestDTO);
			response.setStatus(Integer.parseInt(ResponseCode.BAD_REQUEST));
			resp = new LendingApplicationResponse();
			resp.setSuccess(false);
		}
		return resp;
	}
	
	private LendingApplication updateShopDetail(LendingApplication lendingApplication, LendingApplicationRequestDTO lendingApplicationRequestDTO) {
		lendingApplication.setBusinessName(lendingApplicationRequestDTO.getBusinessName());
		lendingApplication.setShopNumber(lendingApplicationRequestDTO.getShopNumber());
		lendingApplication.setStreetAddress(lendingApplicationRequestDTO.getStreetAddress());
		lendingApplication.setArea(lendingApplicationRequestDTO.getArea());
		lendingApplication.setLandmark(lendingApplicationRequestDTO.getLandmark());
		lendingApplication.setPincode(lendingApplicationRequestDTO.getPincode());
		lendingApplication.setCity(lendingApplicationRequestDTO.getCity());
		lendingApplication.setState(lendingApplicationRequestDTO.getState());
		return lendingApplication;
	}
	
	private LendingApplication updateShopDetail(AvailableLoan availableLoan, LendingApplicationRequestDTO lendingApplicationRequestDTO) {
		LendingApplication lendingApplication = new LendingApplication();
		LendingCategories lendingCategory = lendingCategoryDao.findByCategory(availableLoan.getCategory()).get(0);
		
		LoanBreakupDetail breakupDetail = LoanCalculationUtil.getLoanBreakup(availableLoan, lendingCategory);
		
		lendingApplication.setEdi(Double.valueOf(breakupDetail.getEdi()));
		lendingApplication.setIoEdi(Double.valueOf(breakupDetail.getIoEdi()));
		lendingApplication.setRepayment(Double.valueOf(breakupDetail.getRepayment()));
		lendingApplication.setInterestRate(breakupDetail.getEffectiveInterestRate());
		lendingApplication.setProcessingFee(Double.valueOf(breakupDetail.getProcessingFee()));
		lendingApplication.setStatus("draft");
		lendingApplication.setMerchantId(availableLoan.getMerchantId());
		lendingApplication.setLoanAmount(availableLoan.getAmount());
		lendingApplication.setCategory(availableLoan.getCategory());
		lendingApplication.setTenure(lendingCategory.getPayableConverter());
		lendingApplication.setTenureInMonths(lendingCategory.getTenureMonths().intValue());
		lendingApplication.setPayableDays(Long.valueOf(lendingCategory.getPayableDays()));
		lendingApplication.setEdiFreeDays(lendingCategory.getEdiFreeDays());
		lendingApplication.setIoPayableDays(lendingCategory.getIoPayableDays());
		lendingApplication.setLoanConstruct(availableLoan.getLoanConstruct());

		lendingApplication = updateShopDetail(lendingApplication, lendingApplicationRequestDTO);

		return lendingApplication;
	}
	
	private LendingApplicationResponse prepareAPIResponse(LendingApplication lendingApplication) {
		Map<String, Object> response = new LinkedHashMap<>();
		Map<String, Object> loanApplication1 = new LinkedHashMap<>();
		LendingApplicationResponse lendingApplicationResponse = new LendingApplicationResponse();
		LendingApplicationResponse.LoanApplication loanApplication = lendingApplicationResponse.new LoanApplication();

		loanApplication.setApplicationId(lendingApplication.getApplicationId());
		loanApplication.setApplicationStatus(lendingApplication.getStatus());

		loanApplication.setSelectedLoan(LoanUtil.prepareSelectedLoanForClient(lendingApplication));
		loanApplication1.put("selected_loan", LoanUtil.prepareSelectedLoanForClient(lendingApplication));

		loanApplication.setShopDetails(LoanUtil.prepareShopDetailsForClient(lendingApplication));
		loanApplication1.put("shop_details",LoanUtil.prepareShopDetailsForClient(lendingApplication));

		lendingApplicationResponse.setLoanApplication(loanApplication);
		response.put("loan_application", loanApplication1);

		lendingApplicationResponse.setSuccess(true);
		response.put("success", true);
		
		return lendingApplicationResponse;
	}
	
}
