package com.bharatpe.lending.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.bharatpe.common.dao.MerchantBankDetailDao;
import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.Merchant;
import com.bharatpe.common.entities.MerchantBankDetail;
import com.bharatpe.common.service.delayedqueue.DelayedMessagePublisher;
import com.bharatpe.lending.common.entity.CreditAccount;
import com.bharatpe.lending.common.entity.CreditApplication;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.dto.InstantNotificationDto;
import com.bharatpe.lending.dto.LoanEligibilityDTO;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class RedisNotificationService {
	
	@Autowired
	DelayedMessagePublisher delayedMessagePublisher;
	
	@Autowired
	MerchantBankDetailDao merchantBankDetailDao;
	
	Logger logger=LoggerFactory.getLogger(RedisNotificationService.class);
	
	public void sendNotificationForAppliedApplication(Long merchantId, LendingApplication lendingApplication) {
		try {
			logger.info("Pushing notification of application applied but not confirmend for merchant {} and application {}",merchantId,lendingApplication);
			InstantNotificationDto notificationDto=new InstantNotificationDto();
			notificationDto.setApplicationId(lendingApplication.getId());
			notificationDto.setMerchantId(merchantId);
			notificationDto.setMessageCategory("APPLIED_APPLICATION");
			MerchantBankDetail bankDetail=merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchantId, "ACTIVE");
			String bankName=bankDetail!=null?bankDetail.getBankName():"";
			notificationDto.setMessage("Your Loan is Waiting for you!\n" +
					"Complete your application to get instant money in your " + bankName + " A/c");
			//String messageString30min=objectMapper.writeValueAsString(notificationDto);
			delayedMessagePublisher.publish("lending_notify", merchantId.toString(), notificationDto, "applied_application_5min_"+lendingApplication.getId(), 5*60);
		} catch (Exception e) {
			logger.error("Error occured while sending notification",e);
		}
	}
	
	public void sendNotificationForSeenOffer(Long merchantId, List<LoanEligibilityDTO> eligibleLoan) {
		if (true) return;
		try {
			logger.info("Pushing notification of seen offer for merchant {}",merchantId);
			if(eligibleLoan!=null && !eligibleLoan.isEmpty()) {
				LoanEligibilityDTO highestLoan=eligibleLoan.get(0);
				InstantNotificationDto notificationDto=new InstantNotificationDto();
				notificationDto.setMerchantId(merchantId);
				notificationDto.setMessageCategory("ELIGIBLE");
				MerchantBankDetail bankDetail=merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchantId, "ACTIVE");
				String bankName=bankDetail!=null?bankDetail.getBankName():"";
				
				notificationDto.setMessage("Rs. "+highestLoan.getAmount()+" is ready to be transferred to your "+bankName+" A/c\n" + 
						"Quick Disbursal. Pay only Rs."+highestLoan.getEdi()+" Daily Instalment\n");
				//String messageString30min=objectMapper.writeValueAsString(notificationDto);
				delayedMessagePublisher.publish("lending_notify", merchantId.toString(), notificationDto, "eligible_30_min_"+merchantId, 5*60);
				
//				notificationDto.setMessage("Transfer Rs. "+highestLoan.getAmount()+" to your "+bankName+" A/c Now\n" + 
//						"Pay only Rs. "+highestLoan.getEdi()+" Daily from your QR Settlement");
//				//String messageString1Day=objectMapper.writeValueAsString(notificationDto);
//				delayedMessagePublisher.publish("lending_notify", merchantId.toString(), notificationDto, "eligible_1day_"+merchantId, DateTimeUtil.getSecondsTillTime(12, 1));
//				
//				notificationDto.setMessage("Rs. "+highestLoan.getAmount()+" is waiting to be transferred to your "+bankName+" A/c\n" + 
//						"Pay only Rs. "+highestLoan.getEdi()+". Daily from your QR Settlement.");
//				//String messageString3days=objectMapper.writeValueAsString(notificationDto);
//				delayedMessagePublisher.publish("lending_notify", merchantId.toString(), notificationDto, "eligible_3day_"+merchantId, DateTimeUtil.getSecondsTillTime(12, 3));
//				//message of day 3 and day 5 are same
//				delayedMessagePublisher.publish("lending_notify", merchantId.toString(), notificationDto, "eligible_5day_"+merchantId, DateTimeUtil.getSecondsTillTime(12, 5));
			}
		}
		catch(Exception e) {
			logger.error("Error occured while sending notification",e);
		}
	}
	
//	public void sendEligibleNotificationForCreditLine(Merchant merchant, List<LoanEligibilityDTO> eligibleLoan) {
//		try {
//			if(eligibleLoan!=null && !eligibleLoan.isEmpty()) {
//				logger.info("Sending eligible notification for merchant {}",merchant);
//				LoanEligibilityDTO highestLoan=eligibleLoan.get(0);
//				InstantNotificationDto notificationDto=new InstantNotificationDto();
//				notificationDto.setMerchantId(merchant.getId());
//				notificationDto.setMessageCategory("CREDIT_LINE_ELIGIBLE");
//				String message = "You are pre - approved for business loan up to Rs."+highestLoan.getAmount()+" from BharatPe. Enjoy repayment flexibility along with interest rate as low as 0.1% per day. \n" +
//						"Get Now: ";
//				notificationDto.setMessage(message);
//				delayedMessagePublisher.publish("lending_notify", merchant.getId().toString(), notificationDto, "credit_eligible_2day_"+merchant.getId().toString(), DateTimeUtil.getSecondsTillTime(11, 2));
//				delayedMessagePublisher.publish("lending_notify", merchant.getId().toString(), notificationDto, "credit_eligible_4day_"+merchant.getId().toString(), DateTimeUtil.getSecondsTillTime(11, 4));
//				delayedMessagePublisher.publish("lending_notify", merchant.getId().toString(), notificationDto, "credit_eligible_6day_"+merchant.getId().toString(), DateTimeUtil.getSecondsTillTime(11, 6));
//			}
//		}
//		catch(Exception e) {
//			logger.error("Error occured while sending redis based notification for merchant {}",merchant,e);
//		}
//	}
	
//	public void sendDraftNotificationForCreditLine(Merchant merchant, CreditApplication creditApplication) {
//		try {
//			logger.info("Sending notification for application in draft state got merchant {}",merchant);
//			InstantNotificationDto notificationDto=new InstantNotificationDto();
//			notificationDto.setApplicationId(creditApplication.getId());
//			notificationDto.setMerchantId(merchant.getId());
//			notificationDto.setMessageCategory("CREDIT_LINE_APPLIED_APPLICATION");
//			String message= "You are 1 step away from activating your Rs."+creditApplication.getAmount()+" BharatPe Loan Balance. Pay interest only on amount used at low rate of 0.1% / day. Repay with complete flexibility. \n" +
//					"Get Now: ";
//			notificationDto.setMessage(message);
//			delayedMessagePublisher.publish("lending_notify", merchant.getId().toString(), notificationDto, "credit_draft_2day_"+merchant.getId().toString(), DateTimeUtil.getSecondsTillTime(11, 2));
//			delayedMessagePublisher.publish("lending_notify", merchant.getId().toString(), notificationDto, "credit_draft_4day_"+merchant.getId().toString(), DateTimeUtil.getSecondsTillTime(11, 4));
//			delayedMessagePublisher.publish("lending_notify", merchant.getId().toString(), notificationDto, "credit_draft_6day_"+merchant.getId().toString(), DateTimeUtil.getSecondsTillTime(11, 6));
//		}
//		catch(Exception e) {
//			logger.error("Error occured while sending redis based notification for merchant {}",merchant,e);		
//		}
//	}
	
	public void sendEnachNotificationForCreditLine(Merchant merchant, CreditApplication creditApplication) {
		if (true) return;
		try {
			logger.info("Sending enach notification got merchant {}",merchant);
			InstantNotificationDto notificationDto=new InstantNotificationDto();
			notificationDto.setApplicationId(creditApplication.getId());
			notificationDto.setMerchantId(merchant.getId());
			notificationDto.setMessageCategory("CREDIT_LINE_ENACH");
			String message= "Instantly Activate Loans Balance by Registering eNACH.\nClick Here: ";
			notificationDto.setMessage(message);
			delayedMessagePublisher.publish("lending_notify", merchant.getId().toString(), notificationDto, "enach_"+merchant.getId().toString(), DateTimeUtil.getSecondsTillTime(11, 1));
			
		}
		catch(Exception e) {
			logger.error("Error occured while sending redis based enach notification for merchant {}",merchant,e);
		}
	}
	
	public void sendPromotionalNotificationForCreditLine(Merchant merchant, CreditAccount creditAccount) {
		try {
			logger.info("Sending promotional notification got merchant {}",merchant);
			MerchantBankDetail bankDetail=merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchant.getId(), "ACTIVE");
			InstantNotificationDto notificationDto=new InstantNotificationDto();
			notificationDto.setApplicationId(creditAccount.getId());
			notificationDto.setMerchantId(merchant.getId());
			notificationDto.setMessageCategory("CREDIT_LINE_PROMOTIONAL");
			String message= "Hi "+bankDetail.getBeneficiaryName()+",\n" +
					"BharatPe Loan Balance of "+creditAccount.getAvailableBalance()+" is now ACTIVE. Utilize your Loan Balance as per requirement and pay interest only on amount used at low rate of 0.1% / day. Repay with complete flexibility.\n";
			notificationDto.setMessage(message);
			delayedMessagePublisher.publish("lending_notify", merchant.getId().toString(), notificationDto, "promotional_"+merchant.getId().toString(), DateTimeUtil.getSecondsTillTime(12, 3));
		}
		catch(Exception e) {
			logger.error("Error occured while sending redis based promotional notification for merchant {}",merchant,e);
			
		}
	}
	
	public void sendPendingEnachNotification(Merchant merchant, LendingApplication lendingApplication) {
		if (true) return;
		try {
			logger.info("Sending pending enach notification for merchant {}",merchant);
			MerchantBankDetail merchantBankDetail = merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(lendingApplication.getMerchant().getId(), "ACTIVE");
		    String message = "Register eNACH and get Rs." + lendingApplication.getLoanAmount() + " Loan in your " + merchantBankDetail.getBankName() + " A/c Now!";
		    InstantNotificationDto notificationDto=new InstantNotificationDto();
			notificationDto.setApplicationId(lendingApplication.getId());
			notificationDto.setMerchantId(merchant.getId());
			notificationDto.setMessageCategory("LENDING_PENDING_ENACH");
			notificationDto.setMessage(message);
			delayedMessagePublisher.publish("lending_notify", merchant.getId().toString(), notificationDto, "pending_enach_"+merchant.getId().toString(), 15*60);
		}
		catch(Exception e ) {
			logger.error("Error occured while sending redis based pending enach notification for merchant {}",merchant,e);
		}
	}

	public void sendRepaymentNudge(Merchant merchant, Double processingFee) {
		try {
			logger.info("Sending PF repayment nudge for merchant {}", merchant.getId());
			MerchantBankDetail merchantBankDetail = merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchant.getId(), "ACTIVE");
			String message = "Hi " + merchantBankDetail.getBeneficiaryName() + "\nSpecial offer on repaying your BharatPe Loan through QR Transactions - Get Processing Fees Charges of Rs." + processingFee + " refunded. Start accepting payments through BharatPe QR and repay your loan easily.";
			InstantNotificationDto notificationDto=new InstantNotificationDto();
			notificationDto.setMerchantId(merchant.getId());
			notificationDto.setMessageCategory("LENDING_PF_NUDGE");
			notificationDto.setMessage(message);
			delayedMessagePublisher.publish("lending_notify", merchant.getId().toString(), notificationDto, "pf_nudge_"+merchant.getId().toString(), 5*60);
		}
		catch(Exception e ) {
			logger.error("Error occured while sending pf nudge for merchant {}", merchant.getId(), e);
		}
	}
}
