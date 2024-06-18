package com.bharatpe.lending.service;

import java.util.Objects;
import java.util.Optional;

import com.bharatpe.cache.DTO.AddCacheDto;
import com.bharatpe.cache.service.LendingCache;
import com.bharatpe.common.dao.EligibleLoanDao;
import com.bharatpe.common.entities.EligibleLoan;
import com.bharatpe.lending.common.service.merchant.dto.BankDetailsDto;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.entity.CreditAccount;
import com.bharatpe.lending.common.entity.CreditApplication;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.dto.InstantNotificationDto;

@Service
public class RedisNotificationService {
	
	@Autowired
	LendingDelayedMessagePublisher lendingDelayedMessagePublisher;

	@Autowired
	LendingCache lendingCache;
//
//	@Autowired
//	SmsServiceHandler smsServiceHandler;

	@Autowired
	EligibleLoanDao eligibleLoanDao;

	@Autowired
	MerchantService merchantService;
	
	Logger logger=LoggerFactory.getLogger(RedisNotificationService.class);
	
	public void sendNotificationForAppliedApplication(Long merchantId, LendingApplication lendingApplication) {
		try {
			logger.info("Pushing notification of application applied but not confirmend for merchant {} and application {}",merchantId,lendingApplication);
			InstantNotificationDto notificationDto=new InstantNotificationDto();
			notificationDto.setApplicationId(lendingApplication.getId());
			notificationDto.setMerchantId(merchantId);
			notificationDto.setMessageCategory("APPLIED_APPLICATION");
			final Optional<BankDetailsDto> bankDetailsDtoOptional = merchantService.fetchMerchantBankDetails(lendingApplication.getMerchantId());
			BankDetailsDto bankDetail = null;
			if (bankDetailsDtoOptional.isPresent())
				bankDetail = bankDetailsDtoOptional.get();
			String bankName=bankDetail!=null?bankDetail.getBankName():"";
			notificationDto.setMessage("Your Loan is Waiting for you!\n" +
					"Complete your application to get instant money in your " + bankName + " A/c");
			//String messageString30min=objectMapper.writeValueAsString(notificationDto);
			lendingDelayedMessagePublisher.publish("lending_notify", merchantId.toString(), notificationDto, "applied_application_5min_"+lendingApplication.getId(), 5*60);
		} catch (Exception e) {
			logger.error("Error occured while sending notification",e);
		}
	}
	
	public void sendNotificationForSeenOffer(Long merchantId) {
		try {
			EligibleLoan eligibleLoan = eligibleLoanDao.findMaxLoan(merchantId);
			if (eligibleLoan == null) {
				return;
			}
			logger.info("Pushing notification of seen offer for merchant {}",merchantId);
			String key = "CREDIT_SCORE_MESSAGE_"+ merchantId;
			Object cache_data = lendingCache.get(key);
			logger.info("get key: {}, cache_data : {}",key, cache_data);
			if(Objects.isNull(cache_data)) {
				InstantNotificationDto notificationDto = new InstantNotificationDto();
				notificationDto.setMerchantId(merchantId);
				notificationDto.setMessageCategory("ELIGIBLE");
				final Optional<BankDetailsDto> bankDetailsDtoOptional = merchantService.fetchMerchantBankDetails(merchantId);
				BankDetailsDto bankDetail = null;
				if (bankDetailsDtoOptional.isPresent())
					bankDetail = bankDetailsDtoOptional.get();
				notificationDto.setMessage("Dear " + bankDetail.getBeneficiaryName() + ". Rs. " + eligibleLoan.getAmount() + " quick loan is ready to be disbursed to your " + bankDetail.getBankName() + " A/C.\n" +
						" Daily repayment of only Rs." + eligibleLoan.getEdi() + " \n");
				lendingDelayedMessagePublisher.publish("lending_notify", merchantId.toString(), notificationDto, "eligible_30_min_" + merchantId, 5 * 60);

				AddCacheDto addCacheDto = new AddCacheDto();
				addCacheDto.setKey(key);
				addCacheDto.setTtl(24);
				addCacheDto.setValue(true);
				lendingCache.add(addCacheDto);
				logger.info("add key: {}, cache_data : {}",key, cache_data);
			}
		}
		catch(Exception e) {
			logger.error("Error occured while sending notification",e);
		}
	}

	public void sendNotificationForPostDisbursalInstruction(LendingApplication lendingApplication) {
		try {
			logger.info("Pushing notification of Post Disbursal Instruction");

			String sms = "Most convenient way to repay is by accepting payments through BharatPe QR. Your EDI will be deducted from the QR settlement amount at the end of the day.\nClick here: bharatpe.in/loanqr\n\n-BharatPe";
//			smsServiceHandler.sendSMS(new ArrayList<String>(){{add(lendingApplication.getMerchant().getMobile());}}, sms, NotificationProvider.SMS.GUPSHUP);
			InstantNotificationDto notificationDto = new InstantNotificationDto();
			notificationDto.setMerchantId(lendingApplication.getMerchantId());
			notificationDto.setMessageCategory("DISBURSAL_INSTRUCTION");
			notificationDto.setMessage(sms);
			lendingDelayedMessagePublisher.publish("lending_notify", lendingApplication.getMerchantId().toString(), notificationDto, "disbursal_instruction_" + lendingApplication.getMerchantId(), 15 * 60);

		}
		catch(Exception e) {
			logger.error("Error occurred while sending Post Disbursal Instruction",e);
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
	
	public void sendEnachNotificationForCreditLine(BasicDetailsDto merchant, CreditApplication creditApplication) {
		if (true) return;
		try {
			logger.info("Sending enach notification got merchant {}",merchant);
			InstantNotificationDto notificationDto=new InstantNotificationDto();
			notificationDto.setApplicationId(creditApplication.getId());
			notificationDto.setMerchantId(merchant.getId());
			notificationDto.setMessageCategory("CREDIT_LINE_ENACH");
			String message= "Instantly Activate Loans Balance by Registering eNACH.\nClick Here: ";
			notificationDto.setMessage(message);
			lendingDelayedMessagePublisher.publish("lending_notify", merchant.getId().toString(), notificationDto, "enach_"+merchant.getId().toString(), DateTimeUtil.getSecondsTillTime(11, 1));
			
		}
		catch(Exception e) {
			logger.error("Error occured while sending redis based enach notification for merchant {}",merchant,e);
		}
	}
	
	public void sendPromotionalNotificationForCreditLine(Long merchantId, CreditAccount creditAccount) {
		try {
			if (true) return;
			logger.info("Sending promotional notification got merchant {}",merchantId);
			final Optional<BankDetailsDto> bankDetailsDtoOptional = merchantService.fetchMerchantBankDetails(merchantId);
			BankDetailsDto bankDetail = null;
			if (bankDetailsDtoOptional.isPresent())
				bankDetail = bankDetailsDtoOptional.get();
			InstantNotificationDto notificationDto=new InstantNotificationDto();
			notificationDto.setApplicationId(creditAccount.getId());
			notificationDto.setMerchantId(merchantId);
			notificationDto.setMessageCategory("CREDIT_LINE_PROMOTIONAL");
			String message= "Hi "+bankDetail.getBeneficiaryName()+",\n" +
					"BharatPe Loan Balance of "+creditAccount.getAvailableBalance()+" is now ACTIVE. Utilize your Loan Balance as per requirement and pay interest only on amount used at low rate of 0.1% / day. Repay with complete flexibility.\n";
			notificationDto.setMessage(message);
			lendingDelayedMessagePublisher.publish("lending_notify", merchantId.toString(), notificationDto,
			"promotional_"+merchantId.toString(), DateTimeUtil.getSecondsTillTime(12, 3));
		}
		catch(Exception e) {
			logger.error("Error occured while sending redis based promotional notification for merchant {}",merchantId,e);
			
		}
	}
	
	public void sendPendingEnachNotification(BasicDetailsDto merchant, LendingApplication lendingApplication) {
		try {
			logger.info("Sending pending enach notification for merchant {}",merchant);
			final Optional<BankDetailsDto> bankDetailsDtoOptional = merchantService.fetchMerchantBankDetails(merchant.getId());
			BankDetailsDto merchantBankDetail = null;
			if (bankDetailsDtoOptional.isPresent())
				merchantBankDetail = bankDetailsDtoOptional.get();
			String message = "Register eNACH and get Rs." + lendingApplication.getLoanAmount() + " Loan in your " + merchantBankDetail.getBankName() + " A/c Now!";
		    InstantNotificationDto notificationDto=new InstantNotificationDto();
			notificationDto.setApplicationId(lendingApplication.getId());
			notificationDto.setMerchantId(merchant.getId());
			notificationDto.setMessageCategory("LENDING_PENDING_ENACH");
			notificationDto.setMessage(message);
			lendingDelayedMessagePublisher.publish("lending_notify", merchant.getId().toString(), notificationDto, "pending_enach_"+merchant.getId().toString(), 15*60);
		}
		catch(Exception e ) {
			logger.error("Error occured while sending redis based pending enach notification for merchant {}",merchant,e);
		}
	}

	public void sendRepaymentNudge(Long merchantId, Double processingFee) {
		try {
			logger.info("Sending PF repayment nudge for merchant {}", merchantId);
			final Optional<BankDetailsDto> bankDetailsDtoOptional = merchantService.fetchMerchantBankDetails(merchantId);
			BankDetailsDto merchantBankDetail = null;
			if (bankDetailsDtoOptional.isPresent())
				merchantBankDetail = bankDetailsDtoOptional.get();
			String message = "Hi " + merchantBankDetail.getBeneficiaryName() + "\nSpecial offer on repaying your BharatPe Loan through QR Transactions - Get Processing Fees Charges of Rs." + processingFee + " refunded. Start accepting payments through BharatPe QR and repay your loan easily.";
			InstantNotificationDto notificationDto=new InstantNotificationDto();
			notificationDto.setMerchantId(merchantId);
			notificationDto.setMessageCategory("LENDING_PF_NUDGE");
			notificationDto.setMessage(message);
			lendingDelayedMessagePublisher.publish("lending_notify", merchantId.toString(), notificationDto, "pf_nudge_"+merchantId.toString(), 5*60);
		}
		catch(Exception e ) {
			logger.error("Error occured while sending pf nudge for merchant {}", merchantId, e);
		}
	}
}
