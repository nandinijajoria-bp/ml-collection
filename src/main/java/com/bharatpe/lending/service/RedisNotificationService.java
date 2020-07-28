package com.bharatpe.lending.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.bharatpe.common.dao.MerchantBankDetailDao;
import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.MerchantBankDetail;
import com.bharatpe.common.service.delayedqueue.DelayedMessagePublisher;
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
	
	@Autowired
	ObjectMapper objectMapper;
	
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
			notificationDto.setMessage("Transfer Rs."+lendingApplication.getLoanAmount()+" to your "+bankName+" A/c Now!\n" + 
					"Just 2 minutes to complete your loan application");
			String messageString30min=objectMapper.writeValueAsString(notificationDto);
			delayedMessagePublisher.publish("lending_notify", "notifyPartitionKey"+merchantId, messageString30min, "applied_application_30min_"+lendingApplication.getId(), 60);
			notificationDto.setMessage("Just 2 minutes away from Rs."+lendingApplication.getLoanAmount()+" Loan \n" + 
					"Complete application now and get money in your "+bankName+" A/c");
			String messageString1day=objectMapper.writeValueAsString(notificationDto);
			delayedMessagePublisher.publish("lending_notify", "notifyPartitionKey"+merchantId, messageString1day, "applied_application_1day_"+lendingApplication.getId(), DateTimeUtil.getSecondsTillTime(13, 1));
			notificationDto.setMessage("Complete your Loan Application in 2 Minutes! \n" + 
					"Get Rs."+lendingApplication.getLoanAmount()+" in your "+bankName+" A/c  Now & Grow your Business.");
			String messageString3day=objectMapper.writeValueAsString(notificationDto);
			delayedMessagePublisher.publish("lending_notify", "notifyPartitionKey"+merchantId, messageString3day,"applied_application_3day_"+lendingApplication.getId(), DateTimeUtil.getSecondsTillTime(13, 3));
			notificationDto.setMessage("Complete your Loan Application in 2 Minutes! \n" + 
					"Get Rs."+lendingApplication.getLoanAmount()+" in your "+bankName+" A/c  Now & Grow your Business.");
			String messageString5day=objectMapper.writeValueAsString(notificationDto);
			delayedMessagePublisher.publish("lending_notify", "notifyPartitionKey"+merchantId, messageString5day, "applied_application_5day_"+lendingApplication.getId(), DateTimeUtil.getSecondsTillTime(13, 5));
		} catch (Exception e) {
			logger.error("Error occured while sending notification {}",e);
		}
	}
	
	public void sendNotificationForSeenOffer(Long merchantId, List<LoanEligibilityDTO> eligibleLoan) {
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
				String messageString30min=objectMapper.writeValueAsString(notificationDto);
				delayedMessagePublisher.publish("lending_notify", "eligiblePartitionKey"+merchantId, messageString30min, "eligible_30_min_"+merchantId, 60);
				
				notificationDto.setMessage("Transfer Rs. "+highestLoan.getAmount()+" to your "+bankName+" A/c Now\n" + 
						"Pay only Rs. "+highestLoan.getEdi()+" Daily from your QR Settlement");
				String messageString1Day=objectMapper.writeValueAsString(notificationDto);
				delayedMessagePublisher.publish("lending_notify", "eligiblePartitionKey"+merchantId, messageString1Day, "eligible_1day_"+merchantId, DateTimeUtil.getSecondsTillTime(12, 1));
				
				notificationDto.setMessage("Rs. "+highestLoan.getAmount()+" is waiting to be transferred to your "+bankName+" A/c\n" + 
						"Pay only Rs. "+highestLoan.getEdi()+". Daily from your QR Settlement.");
				String messageString3days=objectMapper.writeValueAsString(notificationDto);
				delayedMessagePublisher.publish("lending_notify", "eligiblePartitionKey"+merchantId, messageString3days, "eligible_3day_"+merchantId, DateTimeUtil.getSecondsTillTime(12, 3));
				//message of day 3 and day 5 are same
				delayedMessagePublisher.publish("lending_notify", "eligiblePartitionKey"+merchantId, messageString3days, "eligible_5day_"+merchantId, DateTimeUtil.getSecondsTillTime(12, 5));	
			}
		}
		catch(Exception e) {
			logger.error("Error occured while sending notification {}",e);
		}
	}
}
