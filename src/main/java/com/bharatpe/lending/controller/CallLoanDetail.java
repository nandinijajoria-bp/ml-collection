package com.bharatpe.lending.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.bharatpe.common.dao.ExperianDao;
import com.bharatpe.common.dao.MerchantDao;
import com.bharatpe.common.entities.Merchant;
import com.bharatpe.lending.dto.IneligibleRequestDTO;
import com.bharatpe.lending.dto.RequestDTO;
import com.bharatpe.lending.service.LoanDetailsService;

@Service
public class CallLoanDetail {
	
	@Autowired
	ExperianDao experianDao;
	
	@Autowired
	MerchantDao merchantDao;
	
	@Autowired
	LoanDetailsService loanDetailsService;
	
	public void callLoanDetail() {
		List<Long> merchantIdList=experianDao.getMerchantList();
		Iterable<Merchant> merchantList=merchantDao.findAllById(merchantIdList);
		merchantList.forEach(merchant->callLoanDetailFunction(merchant));
	}
	
	public void callLoanDetailFunction(Merchant merchant) {
		RequestDTO<IneligibleRequestDTO> requestDTO=new RequestDTO<>();
		requestDTO.setPayload(new IneligibleRequestDTO());
		requestDTO.getPayload().setSkip(false);
		loanDetailsService.fetchLoanDetails(merchant, requestDTO, null);
	}
}


