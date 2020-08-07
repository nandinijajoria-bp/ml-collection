package com.bharatpe.lending.service;


import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import com.bharatpe.common.dao.MerchantBankDetailDao;
import com.bharatpe.common.entities.MerchantBankDetail;
import com.bharatpe.lending.common.dao.*;
import com.bharatpe.lending.common.entity.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.bharatpe.common.dao.ExperianDao;
import com.bharatpe.common.dao.MerchantDao;
import com.bharatpe.common.dao.MerchantFcmTokenDao;
import com.bharatpe.common.entities.Experian;
import com.bharatpe.common.entities.Merchant;
import com.bharatpe.common.entities.MerchantFcmToken;
import com.bharatpe.common.enums.NotificationProvider;
import com.bharatpe.common.handlers.PushNotificationHandler;
import com.bharatpe.common.handlers.SmsServiceHandler;
import com.bharatpe.common.service.WhatsappNotificationService;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.constant.CreditConstants;
import com.bharatpe.lending.dto.CreditApplicationStatusUpdationRequestDto;
import com.bharatpe.lending.dto.ResponseDTO;

@Service
public class CreditApplicationStatusChange {
	
	Logger logger=LoggerFactory.getLogger(CreditApplicationStatusChange.class);
	
	@Autowired
	CreditApplicationDao creditApplicationDao;
	
	@Autowired
	CreditApplicationTransitionDao creditApplicationTransitionDao;
	
	@Autowired
	ExperianDao experianDao;
	
	@Autowired
	CreditAccountDao creditAccountDao;
	
	@Autowired
	LendingCaBalanceDetailDao lendingCaBalanceDetailDao;
	
	@Autowired
	SmsServiceHandler smsServiceHandler;
	
	@Autowired
	WhatsappNotificationService whatsappNotificationService;
	
	@Autowired
	PushNotificationHandler pushNotificationHandler;
	
	@Autowired
	MerchantDao merchantDao;
	
	@Autowired
	MerchantFcmTokenDao merchantFcmTokenDao;
	
	@Autowired
	CreditLineCategoriesDao creditLineCategoriesDao;

	@Autowired
	MerchantBankDetailDao merchantBankDetailDao;
	
	@Autowired
	CreditLineService creditLineService;
	
	private final DecimalFormat df = new DecimalFormat("#.##");
	
	public ResponseDTO changeApplicationStatus(CreditApplicationStatusUpdationRequestDto applicationStatus){
		try {
			logger.info("updating application status for application id {}",applicationStatus.getApplicationId());
			ResponseDTO response=new ResponseDTO();
			
			if(applicationStatus!=null && applicationStatus.getApplicationId()!=null && applicationStatus.getMerchantId()!=null){
				CreditApplication creditApplication=creditApplicationDao.findByIdAndMerchantId(applicationStatus.getApplicationId(), applicationStatus.getMerchantId());
				if(creditApplication==null) {
					return getErrorMessage("Application not found for the given merchant and application id");
				}
				if(changeCreditApplicationStatus(creditApplication, applicationStatus)){
					response.setSuccess(true);
					response.setMessage("");
					return response;
				}
				else {
					return getErrorMessage("Error occured while updating status");
				}
			}
			else {
				return getErrorMessage("Bad request");
			}
			
		}
		catch(Exception e) {
			logger.error("Error occured while updating application status",e);
			return getErrorMessage("Error occured while updating application status");
		}
	}
	
	private void sendApprovalNotification(CreditApplication  creditApplication) {
		
		Optional<Merchant> merchantOptional=merchantDao.findById(creditApplication.getMerchantId());
		if(merchantOptional!=null && merchantOptional.isPresent()) {
			Merchant merchant=merchantOptional.get();
			List<String> mobiles = new ArrayList<> ();
			mobiles.add(merchant.getMobile());
			String message="BharatPe Loan Approved. ACTIVATION PENDING!\n" + 
					"You have Rs."+Double.valueOf(df.format(creditApplication.getAmount()))+" Loan Approved, which you can use for Bank transfers, Sending money, Paying Bills, Shopping etc.\n" + 
					"Activate Now by collecting Rs.500 more from your customers through BharatPe QR Code \n" + 
					"Check Status. ";
			smsServiceHandler.sendSMS(mobiles, message+CreditConstants.MESSAGE_NOTIFICATION_LINK, NotificationProvider.SMS.GUPSHUP);
			whatsappNotificationService.send(merchant, null, message+CreditConstants.MESSAGE_NOTIFICATION_LINK, mobiles, null);
			MerchantFcmToken merchantFcmToken = merchantFcmTokenDao.getByMerchantId(merchant.getId());
			if(merchantFcmToken != null) {
				pushNotificationHandler.sendPushNotification(merchantFcmToken.getFcmToken(), merchantFcmToken.getPlatform(), message, "bharatpe://dynamic?key=credit-line");
			}
		}
	}
	
	private void sendRejectionNotification(CreditApplication  creditApplication) {
		Optional<Merchant> merchantOptional=merchantDao.findById(creditApplication.getMerchantId());
		if(merchantOptional!=null && merchantOptional.isPresent()) {
			Merchant merchant=merchantOptional.get();
			MerchantBankDetail merchantBankDetail = merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchant.getId(),"ACTIVE");
			List<String> mobiles = new ArrayList<> ();
			mobiles.add(merchant.getMobile());
			String message="Hi "+merchantBankDetail.getBeneficiaryName()+",\nAs per your submitted documents and credit history, we are unable to activate BharatPe Loan Balance at this point. Please call at 088825 55444 to learn how to improve your eligibility. Transact more on BharatPe QR over next 1 month and then re-apply.";
			smsServiceHandler.sendSMS(mobiles, message, NotificationProvider.SMS.GUPSHUP);
			whatsappNotificationService.send(merchant, null, message, mobiles, null);
		}
	}
	
	public Boolean changeCreditApplicationStatus(CreditApplication creditApplication,CreditApplicationStatusUpdationRequestDto applicationStatus) {
		try {
			
			if(applicationStatus.getStatus().equalsIgnoreCase("rejected")){
				sendRejectionNotification(creditApplication);
				creditApplication.setStatus("rejected");
				if(applicationStatus.getState().equalsIgnoreCase("kyc")){
					creditApplication.setManualKyc("REJECTED");
				} else if(applicationStatus.getState().equalsIgnoreCase("cpv")) {
					creditApplication.setPhysicalVerificationStatus("REJECTED");
				}
			}
			else if(applicationStatus.getStatus().equalsIgnoreCase("approved")) {
				if(applicationStatus.getState().equalsIgnoreCase("kyc")){
					creditApplication.setStatus("cpv");
					creditApplication.setManualKyc("APPROVED");
				}	
				else if(applicationStatus.getState().equalsIgnoreCase("cpv")) {
					creditApplication.setStatus("approved");
					creditApplication.setPhysicalVerificationStatus("APPROVED");
					creditApplication.setManualKyc("APPROVED");
					CreditLineCategories creditLineCategories=creditLineCategoriesDao.findTop1ByCategoryOrderByMaxCreditLimitDesc(creditApplication.getCategory());
					if(creditLineCategories!=null && creditLineCategories.getActivationFee()!=0) {
						sendApprovalNotification(creditApplication);
					}
					//creating credit account
					if(!createCreditAccount(creditApplication, applicationStatus.getMerchantId())){
						return false;
					}
					creditApplication.setAccountCreated(true);
				}
				else {
					logger.error("Invalid state {}",applicationStatus.getState());
					return false;
				}
			} else if(applicationStatus.getStatus().equalsIgnoreCase("pending")) {
				if(applicationStatus.getState().equalsIgnoreCase("kyc")){
					creditApplication.setManualKyc("PENDING");
				} else if(applicationStatus.getState().equalsIgnoreCase("cpv")) {
					creditApplication.setPhysicalVerificationStatus("PENDING");
				}
			} else {
				logger.error("Invalid status "+applicationStatus.getStatus());
				return false;
			}
			creditApplicationDao.save(creditApplication);
			return true;
		}
		catch(Exception e) {
			logger.error("Error occured while updation credit application",e);
			return false;
		}
	}
	
	public Boolean insertUpdatedStatusInTransitionTable(CreditApplicationStatusUpdationRequestDto applicationStatusChange){
		try{
			CreditApplicationTransition transition=new CreditApplicationTransition();
			transition.setApplicationId(applicationStatusChange.getApplicationId());
			
			if(applicationStatusChange.getState().equalsIgnoreCase("kyc")){
				transition.setFromStatus("kyc");
				if(applicationStatusChange.getStatus().equalsIgnoreCase("approved")){
					transition.setToStatus("cpv");
				}
				else if(applicationStatusChange.getStatus().equalsIgnoreCase("rejected")){
					transition.setToStatus("rejected");
				}
				else {
					logger.error("Invalid status {}",applicationStatusChange.getStatus());
					return false;
				}
			}
			else if(applicationStatusChange.getState().equalsIgnoreCase("cpv")) {
				transition.setFromStatus("cpv");
				if(applicationStatusChange.getStatus().equalsIgnoreCase("approved")){
					transition.setToStatus("approved");
				}
				else if(applicationStatusChange.getStatus().equalsIgnoreCase("rejected")){
					transition.setToStatus("rejected");
				}
				else {
					logger.error("Invalid status {}",applicationStatusChange.getStatus());
					return false;
				}
			}
			else {
				logger.error("Invalid state {}",applicationStatusChange.getState());
				return false;
			}
			
			transition.setComment("");
			creditApplicationTransitionDao.save(transition);
			return true;
		}
		catch(Exception e) {
			logger.error("Error occured while inserting new transition data",e);
			return false;
		}
	}
	
	public ResponseDTO getErrorMessage(String message){
		ResponseDTO response=new ResponseDTO();
		response.setSuccess(false);
		response.setMessage(message);
		return response;
	}
	
	public boolean createCreditAccount(CreditApplication creditApplication,Long merchantId){
		try {
			logger.info("Fetching segment detail from experian table");
			
			Experian experian= experianDao.getByMerchantId(merchantId);
			
			if(experian!=null && experian.getColor()!=null) {
				
				logger.info("Inserting new entry in credit_account table");
				
				CreditAccount creditAccount=new CreditAccount();
				
				creditAccount.setMerchantId(creditApplication.getMerchantId());
				creditAccount.setMerchantStoreId(creditApplication.getMerchantStoreId());
				creditAccount.setStatus("ACTIVE");
				creditAccount.setSegment(experian.getColor());
				creditAccount.setLimit(creditApplication.getAmount());
				creditAccount.setAvailableBalance(creditApplication.getAmount());
				creditAccount.setUsedBalance(0D);
				creditAccount.setPayableAmount(0D);
				creditAccount.setInterestDue(0D);
				creditAccount.setMinimumAmountDue(0D);
				creditAccount.setNextBillDate(DateTimeUtil.getStartTimeFromDateTime(DateTimeUtil.addDays(new Date(), 20)));
				creditAccount.setDueDate(DateTimeUtil.getStartTimeFromDateTime(DateTimeUtil.addDays(new Date(), 29)));
				creditAccount.setActivationDate(new Date());
				creditAccount.setCreatedAt(new Date());
				creditAccount.setUpdatedAt(new Date());
				

				creditAccount = creditAccountDao.save(creditAccount);

				LendingCaBalanceDetail lendingCaBalanceDetail = new LendingCaBalanceDetail();
				lendingCaBalanceDetail.setMerchantId(creditApplication.getMerchantId());
				lendingCaBalanceDetail.setMerchantStoreId(creditApplication.getMerchantStoreId());
				lendingCaBalanceDetail.setCreditAccountId(creditAccount.getId());
				lendingCaBalanceDetail.setAccountLimit(creditApplication.getAmount());
				lendingCaBalanceDetail.setAvailableBalance(creditApplication.getAmount());
				lendingCaBalanceDetail.setUsedBalance(0D);
				lendingCaBalanceDetail.setUsedBalanceCl(0D);
				lendingCaBalanceDetail.setUsedBalanceG1(0D);
				lendingCaBalanceDetail.setUsedBalanceG2(0D);
				lendingCaBalanceDetail.setUsedBalanceG3(0D);
				lendingCaBalanceDetail.setInterestDue(0D);
				lendingCaBalanceDetail.setCreatedAt(new Date());
				lendingCaBalanceDetail.setUpdatedAt(new Date());
				lendingCaBalanceDetailDao.save(lendingCaBalanceDetail);
				
				Optional<Merchant> merchantOptional=merchantDao.findById(merchantId);
				if(merchantOptional.isPresent()) {
					creditLineService.sendActivationNotification(creditApplication, merchantOptional.get());
				}
				
				return true;
		}
		else {
				logger.warn("Experian detail not found");
				return false;
			}
		}
		catch(Exception e) {
			logger.error("Error occured while creating credit account",e);
			return false;
		}
	}
	
}
