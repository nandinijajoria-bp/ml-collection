package com.bharatpe.lending.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.bharatpe.common.dao.DocKycDetailsDao;
import com.bharatpe.common.entities.DocKycDetails;
import com.bharatpe.common.entities.LendingPancard;
import com.bharatpe.common.entities.Merchant;
import com.bharatpe.lending.common.entity.MerchantDocumentProofOcr;
import com.bharatpe.lending.dto.VerifyPanCardDto;
import com.bharatpe.lending.dto.VerifyPanRequestDto;

@Service
public class VerifyDocService {

	Logger logger=LoggerFactory.getLogger(VerifyDocService.class);
	
	@Autowired
	LoanEligibleService loanEligibleService;
	
	@Autowired
	DocKycDetailsDao docKycDetailsDao;
	
	public VerifyPanCardDto verifyPanCard(Merchant merchant, VerifyPanRequestDto requestDto) {
		try {
			LendingPancard lendingPancard=loanEligibleService.fetchNameFromSignzy(requestDto.getPanCard(),merchant.getId());
			if(lendingPancard!=null && lendingPancard.getName()!=null && !lendingPancard.getName().isEmpty()) {
				if(!isUsingOthersPancard(merchant.getId(),requestDto.getPanCard())) {
					return new VerifyPanCardDto(true , "", true);
				}
			}
			return new VerifyPanCardDto(true , "", false);
		}
		catch(Exception e) {
			logger.error("Error occured while verifying pancard {} for merchant {}",requestDto.getPanCard(),merchant.getId(),e);
			return new VerifyPanCardDto(false, "Error occured while verifying pancard", null);
		}
	}
	
	public boolean isUsingOthersPancard(Long merchantId, String proofNo) {
		List<DocKycDetails> docOcrList=docKycDetailsDao.findByDocTypeAndDocNo("pancard", proofNo);
		for(DocKycDetails docOcr:docOcrList) {
			if(!docOcr.getMerchant().getId().equals(merchantId)) {
				logger.info("merchant:{} using others pancard", merchantId);
				return true;
			}
		}
		return false;
	}
}
