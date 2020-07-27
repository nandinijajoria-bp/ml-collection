package com.bharatpe.lending.service;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import com.bharatpe.common.dao.MerchantBankDetailDao;
import com.bharatpe.common.dao.MerchantFcmTokenDao;
import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.MerchantBankDetail;
import com.bharatpe.common.entities.MerchantFcmToken;
import com.bharatpe.common.handlers.PushNotificationHandler;
import com.bharatpe.common.service.delayedqueue.DelayedMessagePublisher;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dto.AppliedApplicationNotificationDto;
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
			AppliedApplicationNotificationDto notificationDto=new AppliedApplicationNotificationDto();
			notificationDto.setApplicationId(lendingApplication.getId());
			notificationDto.setMerchantId(merchantId);
			MerchantBankDetail bankDetail=merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchantId, "ACTIVE");
			String bankName=bankDetail!=null?bankDetail.getBankName():"";
			notificationDto.setMessage("Transfer Rs."+lendingApplication.getLoanAmount()+" to your "+bankName+" A/c Now!\n" + 
					"Just 2 minutes to complete your loan application");
			String messageString30min=objectMapper.writeValueAsString(notificationDto);
			delayedMessagePublisher.publish("application_applied", "partitionKey", messageString30min, "applied_application_30min_"+lendingApplication.getId(), 30*60);
			notificationDto.setMessage("Just 2 minutes away from Rs."+lendingApplication.getLoanAmount()+" Loan \n" + 
					"Complete application now and get money in your "+bankName+" A/c");
			String messageString1day=objectMapper.writeValueAsString(notificationDto);
			delayedMessagePublisher.publish("application_applied", "partitionKey", messageString1day, "applied_application_1day_"+lendingApplication.getId(), DateTimeUtil.getSecondsTillTime(13, 1));
			notificationDto.setMessage("Complete your Loan Application in 2 Minutes! \n" + 
					"Get Rs.<Loan Amount> in your <Bank Name> A/c  Now & Grow your Business.");
			String messageString3day=objectMapper.writeValueAsString(notificationDto);
			delayedMessagePublisher.publish("application_applied", "partitionKey", messageString3day,"applied_application_3day_"+lendingApplication.getId(), DateTimeUtil.getSecondsTillTime(13, 3));
			notificationDto.setMessage("Complete your Loan Application in 2 Minutes! \n" + 
					"Get Rs.<Loan Amount> in your <Bank Name> A/c  Now & Grow your Business.");
			String messageString5day=objectMapper.writeValueAsString(notificationDto);
			delayedMessagePublisher.publish("application_applied", "partitionKey", messageString5day, "applied_application_5day_"+lendingApplication.getId(), DateTimeUtil.getSecondsTillTime(13, 5));
		} catch (Exception e) {
			logger.error("Error occured while sending notification {}",e);
		}
	}
	
}
