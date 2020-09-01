package com.bharatpe.lending.controller;

import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.bharatpe.common.dao.ExperianDao;
import com.bharatpe.common.dao.MerchantDao;
import com.bharatpe.common.entities.Merchant;
import com.bharatpe.lending.dto.IneligibleRequestDTO;
import com.bharatpe.lending.dto.RequestDTO;
import com.bharatpe.lending.service.LoanDetailsService;

@Service
public class CallLoanDetailService {

	private final Logger logger = LoggerFactory.getLogger(CallLoanDetailService.class);
	
	@Autowired
	ExperianDao experianDao;
	
	@Autowired
	MerchantDao merchantDao;
	
	@Autowired
	LoanDetailsService loanDetailsService;
	
	public void callLoanDetail() {
		long offset = 0;
		boolean lastBatchProcessed = false;
		logger.info("Loan Details Script Started");
		while (!lastBatchProcessed) {
			try {
				List<Integer> merchantIdList = experianDao.getMerchantList(offset);//query returns integer in merchant_id
				logger.info("Processing loan details batch starting at offset: {}", offset);
				offset += 1000;
				if (merchantIdList.size() < 1000) {
					lastBatchProcessed = true;
				}
				List<Long> merchantIdList2 = new LinkedList<>();
				merchantIdList.forEach(id -> merchantIdList2.add((long) id));
				Iterable<Merchant> merchantList = merchantDao.findAllById(merchantIdList2);
				merchantList.forEach(this::callLoanDetailFunction);
			} catch (Exception e) {
				logger.error("Exception---", e);
			}
		}
		logger.info("Loan Details Script Ended");
	}
	
	public void callLoanDetailFunction(Merchant merchant) {
		try {
			RequestDTO<IneligibleRequestDTO> requestDTO = new RequestDTO<>();
			requestDTO.setPayload(new IneligibleRequestDTO());
			requestDTO.getPayload().setSkip(false);
			logger.info("Calling loan details for merchant:{}", merchant.getId());
			loanDetailsService.fetchLoanDetails(merchant, requestDTO, null);
		} catch (Exception e) {
			logger.error("Exception---", e);
		}
	}
}


