package com.bharatpe.lending.service;

import com.bharatpe.common.dao.ExperianDao;
import com.bharatpe.common.dao.LendingPancardDao;
import com.bharatpe.common.entities.Experian;
import com.bharatpe.common.entities.LendingPancard;
import com.bharatpe.lending.common.bpnewmaster.dao.DocKycDetailsDaoMaster;
import com.bharatpe.lending.common.bpnewmaster.entity.DocKycDetailsMaster;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.dto.VerifyPanCardDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VerifyDocService {

	Logger logger=LoggerFactory.getLogger(VerifyDocService.class);

	@Autowired
	LoanEligibleService loanEligibleService;

	@Autowired
	DocKycDetailsDaoMaster docKycDetailsDaoMaster;

	@Autowired
	LendingPancardDao lendingPancardDao;

	@Autowired
	ExperianDao experianDao;

	public VerifyPanCardDto verifyPanCard(BasicDetailsDto merchant, String  panCard) {
		try {
			LendingPancard lendingPancard = lendingPancardDao.findByMerchantId(merchant.getId());
			Experian experian =experianDao.getByPancardNumber(panCard, merchant.getId());
			if(experian != null && !merchant.getId().equals(experian.getMerchantId())){
				logger.info("Already Experian  Pull On this Pancard :{}",panCard);
				return new VerifyPanCardDto(true,"PAN already exists, Please enter a different PAN Number",false);
			}
			if(lendingPancard == null || (lendingPancard.getPancardNumber()!=null && !lendingPancard.getPancardNumber().equalsIgnoreCase(panCard))||(lendingPancard.getPancardNumber()==null || lendingPancard.getPancardNumber().isEmpty())) {
				lendingPancard=loanEligibleService.fetchPanName(panCard,merchant.getId());
			}
			if(lendingPancard!=null && lendingPancard.getName()!=null && !lendingPancard.getName().isEmpty()) {
				if(!isUsingOthersPancard(merchant.getId(),panCard)) {
					return new VerifyPanCardDto(true , "", true);
				}
				else {
					return new VerifyPanCardDto(true,"PAN already exists, Please enter a different PAN Number",false);
				}
			}
			return new VerifyPanCardDto(true , "Please Enter Valid PAN Number", false);
		}
		catch(Exception e) {
			logger.error("Error occured while verifying pancard {} for merchant {}",panCard,merchant.getId(),e);
			return new VerifyPanCardDto(true, "", true);
		}
	}

	public boolean isUsingOthersPancard(Long merchantId, String proofNo) {
		List<DocKycDetailsMaster> docOcrList=docKycDetailsDaoMaster.findByDocTypeAndDocNo("pancard", proofNo);
		for(DocKycDetailsMaster docOcr:docOcrList) {
			if(!docOcr.getMerchantId().equals(merchantId)) {
				logger.info("merchant:{} using others pancard", merchantId);
				return true;
			}
		}
		return false;
	}
}
