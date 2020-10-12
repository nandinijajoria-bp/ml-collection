package com.bharatpe.lending.service;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.bharatpe.common.dao.*;
import com.bharatpe.common.entities.*;
import com.bharatpe.common.enums.LoyaltyTransactionType;
import com.bharatpe.common.enums.Status;
import com.bharatpe.common.objects.LoyaltyServiceRequest;
import com.bharatpe.common.service.LoyaltyService;
import com.bharatpe.lending.constant.ExperianConstants;
import com.bharatpe.lending.dao.*;
import com.bharatpe.lending.dto.MetaDTO;
import com.bharatpe.lending.entity.LendingPrebookTarget;
import com.bharatpe.lending.entity.OglLoans;
import com.bharatpe.lending.util.LoanCalculationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.bharatpe.common.enums.NotificationProvider;
import com.bharatpe.common.handlers.PushNotificationHandler;
import com.bharatpe.common.handlers.SmsServiceHandler;
import com.bharatpe.common.objects.CommonAPIRequest;
import com.bharatpe.common.objects.Meta;
import com.bharatpe.common.service.WhatsappNotificationService;
import com.bharatpe.lending.handlers.GupShupOTPHandler;


@Service
public class VerifyOTPService {
	private Logger logger = LoggerFactory.getLogger(VerifyOTPService.class);
	
	@Autowired
	LendingApplicationDao lendingApplicationDao;
	
	@Autowired
	LendingAuditTrialDao lendingAuditTrialDao;

	@Autowired
	MerchantBankDetailDao merchantBankDetailDao;
	
	@Autowired
	MerchantFcmTokenDao merchantFcmTokenDao;
	
	@Autowired
	PushNotificationHandler pushNotificationHandler;
	
	@Autowired
	SmsServiceHandler smsServiceHandler;
	
	@Autowired
	GupShupOTPHandler gupShupOTPHandler;
	
	@Autowired
	WhatsappNotificationService whatsappNotificationService;

	@Autowired
	BankListDao bankListDao;

	@Autowired
	OglLoansDao oglLoansDao;

	@Autowired
	MerchantSummaryLendingDao merchantSummaryLendingDao;

	@Autowired
	MerchantSummaryDao merchantSummaryDao;

	@Autowired
	LendingCategoryDao lendingCategoryDao;

	@Autowired
	PaymentTransactionNewDao paymentTransactionNewDao;

	@Autowired
	LendingPrebookLoansDao lendingPrebookLoansDao;

	@Autowired
	ENachService eNachService;
	
	ExecutorService notificationExecutor = Executors.newFixedThreadPool(5);

	ExecutorService preBookExecutor = Executors.newFixedThreadPool(5);

	@Autowired
	LoyaltyService loyaltyService;

	@Autowired
	LendingPrebookTargetDao lendingPrebookTargetDao;

	@Autowired
	LendingEnachDao lendingEnachDao;

	@Autowired
	LendingCitiesDao lendingCitiesDao;

	@Autowired
	DocumentsIdProofDao documentsIdProofdao;

	@Autowired
	SignAgreementService signAgreementService;

	@Autowired
	LendingPaymentScheduleDao lendingPaymentScheduleDao;
	
	@Autowired
	RedisNotificationService redisNotificationService;
	
	@Autowired
	KafkaTemplate<String, Object> kafkaTemplate;

	public Map<String, Boolean> verifyOTP(Merchant merchant, CommonAPIRequest commonAPIRequest) {
		Map<String, Boolean> finalResponse = new LinkedHashMap<>();
		finalResponse.put("success",false);
		finalResponse.put("agreement_verified",false);
		
		Long applicationId =  commonAPIRequest.getPayload().get("application_id") != null ? Long.parseLong(commonAPIRequest.getPayload().get("application_id").toString()) : null;
		String otp =  commonAPIRequest.getPayload().get("otp") != null ? commonAPIRequest.getPayload().get("otp").toString() : null;

		if(applicationId == null || applicationId <= 0 || StringUtils.isEmpty(otp)) {
			logger.info("No application found in draft status for given application id {}", applicationId);
			return finalResponse;
		}
		LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchantAndStatus(applicationId, merchant, "draft");
		if(lendingApplication == null) {
			logger.info("No application found in draft status for given application id {}", applicationId);
			return finalResponse;
		}

		return verifyOTP(otp, merchant, lendingApplication, commonAPIRequest.getMeta());
	}
	
	private Map<String, Boolean> verifyOTP(String otp, Merchant merchant, LendingApplication lendingApplication, Meta meta) {
		Map<String, Boolean> finalResponse = new LinkedHashMap<>();
		finalResponse.put("success",false);
		finalResponse.put("agreement_verified",false);
		
		if(merchant.getMobile().length() == 12) {
			Boolean isOTPVerified = gupShupOTPHandler.verifyOTP(merchant.getMobile(), otp);
			if(isOTPVerified) {
				finalResponse = updateApplicationStatusAndSuccessSms(merchant, lendingApplication, meta);
				//createPrebookTarget(lendingApplication, merchant);
			}
		}
		return finalResponse;
	}
	
	private Map<String, Boolean> updateApplicationStatusAndSuccessSms(Merchant merchant, LendingApplication lendingApplication, Meta meta) {
		OglLoans oglLoans = oglLoansDao.findByMerchantIdAndExternalLoanId(merchant.getId(), lendingApplication.getExternalLoanId());
		LendingEnach enachSuccess = lendingEnachDao.findSuccessEnach(merchant.getId());
		LendingCities lendingCities = null;
		if (lendingApplication.getPincode() != null) {
			lendingCities = lendingCitiesDao.findActiveCityByPincode(lendingApplication.getPincode().intValue());
		}
		boolean cpvMandatory = lendingCities != null && lendingCities.getCpvMandatory();
		Map<String, Boolean> finalResponse = new LinkedHashMap<>();
		DateFormat df = new SimpleDateFormat("ddMMyy");
		Date dateobj = new Date();
		String loanId = "BPL" + df.format(dateobj) + lendingApplication.getId();
		lendingApplication.setAgreementAt(new Date());
		lendingApplication.setAgreement(1);
		if (meta != null && meta.getLatitude() != null && !meta.getLatitude().equalsIgnoreCase("undefined")) {
			lendingApplication.setLatitude(meta.getLatitude());
			lendingApplication.setLongitude(meta.getLongitude());
			lendingApplication.setIp(meta.getIp());
		}
		lendingApplication.setExternalLoanId(loanId);
		if (enachSuccess != null && !"LIQUILOANS".equalsIgnoreCase(enachSuccess.getIdentifier())) {
			lendingApplication.setNachType("ENACH");
			lendingApplication.setNachLender("BHARATPE");
			lendingApplication.setNachReferenceNumber(enachSuccess.getMid());
			lendingApplication.setNachStatus("APPROVED");
		}
		if (oglLoans != null) {
			logger.info("Found OGL merchant: {}", merchant.getId());
			lendingApplication.setStatus("approved");
			lendingApplication.setManualKyc("APPROVED");
			lendingApplication.setManualCibil("APPROVED");
			lendingApplication.setPhysicalVerificationStatus("APPROVED");
			lendingApplication.setLender("LIQUILOANS");
		} else if("TOPUP".equalsIgnoreCase(lendingApplication.getLoanType())){
			logger.info("TOPUP loan submitted for merchant {}", merchant.getId());
			if (!cpvMandatory && (enachSuccess != null && lendingApplication.getLoanAmount() < 300000)) {
				lendingApplication.setPhysicalVerificationStatus("APPROVED");
				lendingApplication.setPhysicalApprovedDate(lendingApplication.getAgreementAt());
				lendingApplication.setAssignedAt(lendingApplication.getAgreementAt());
				lendingApplication.setCpvSubmitTimestamp(lendingApplication.getAgreementAt());
				lendingApplication.setCpvCloseDate(lendingApplication.getAgreementAt());
				lendingApplication.setStatus("approved");
			} else {
				sendTopupSms(merchant, lendingApplication);
				lendingApplication.setStatus("pending_verification");
			}
			updateDocuments(lendingApplication, meta);
			lendingApplication.setVerifyOcr("yes");
			lendingApplication.setVerifyPan("yes");
			lendingApplication.setManualKyc("APPROVED");
			lendingApplication.setKycApprovedDate(lendingApplication.getAgreementAt());
			lendingApplication.setKycAssignedAt(lendingApplication.getAgreementAt());
			lendingApplication.setManualCibil("APPROVED");
			lendingApplication.setCibilApprovedDate(lendingApplication.getAgreementAt());
			lendingApplication.setLender("LIQUILOANS");
		} else {
			lendingApplication.setStatus("pending_verification");
		}
		
		finalResponse.put("success",false);
		finalResponse.put("agreement_verified",false);
		lendingApplicationDao.save(lendingApplication);
		if(lendingApplication.getLoanType().equalsIgnoreCase("NTB")) {
			redisNotificationService.sendPendingEnachNotification(merchant, lendingApplication);	
			sendDetailsForContactsVerification(merchant.getId(), lendingApplication.getId());
		}
		LoyaltyServiceRequest requestBean = new LoyaltyServiceRequest.LoyaltyServiceRequestBuilder(merchant.getId(), LoyaltyTransactionType.PRE_BOOK_LOAN)
				.amount(0D)
				.merchantStoreId(null)
				.transactionId(lendingApplication.getId())
				.build();
		loyaltyService.pushToKafka(requestBean);


		LendingAuditTrial lendingAuditTrial = new LendingAuditTrial();
		lendingAuditTrial.setApplicationId(lendingApplication.getId());
		lendingAuditTrial.setMerchantId(merchant.getId());
		lendingAuditTrial.setLoanId(loanId);
		lendingAuditTrial.setUserId(Long.parseLong("0"));
		lendingAuditTrial.setOldStatus("draft");
		if (oglLoans != null || (lendingApplication.getNachStatus() != null && (lendingApplication.getNachStatus().equalsIgnoreCase("initiated") || lendingApplication.getNachStatus().equalsIgnoreCase("approved")))) {
			lendingAuditTrial.setNewStatus("approved");
		} else {
			lendingAuditTrial.setNewStatus("pending_verification");
		}
		lendingAuditTrial.setType("APP_STATUS");

		lendingAuditTrialDao.save(lendingAuditTrial);
		notificationExecutor.submit(() -> sendNotification(merchant, lendingApplication));
		if (lendingApplication.getLoanAmount() <= 50000 && !lendingApplication.getLoanType().equalsIgnoreCase("NTB"))
			sendDetailsForKycVerification(merchant.getId(),lendingApplication.getId(),false);
		finalResponse.put("success",true);
		finalResponse.put("agreement_verified",true);
		return finalResponse;
	}
	
	public void sendDetailsForKycVerification(Long merchantId, Long applicationId, boolean isCreditLine) {
		try {
			Map<String,Long> detailMap=new HashMap<String, Long>(){{
				put("merchantId", merchantId);
				put("applicationId",applicationId);
				put("isCreditLine",isCreditLine?1L:0L);
			}};
			kafkaTemplate.send("verify_kyc_details", merchantId.toString(), detailMap);
			logger.info("Pushed "+detailMap+" to topic verify_kyc_details");
		}
		catch(Exception e) {
			logger.error("Error occured while pushing to toipc verify_kyc_details",e);
		}
	}

	public void sendDetailsForContactsVerification(Long merchantId, Long applicationId) {
		try {
			Map<String, Long> detailMap = new HashMap<>();
			detailMap.put("merchantId", merchantId);
			detailMap.put("applicationId", applicationId);
			kafkaTemplate.send("verify_contacts_for_application", merchantId.toString(), detailMap);
			logger.info("Pushed {} to topic verify_contacts_for_application", detailMap);
		} catch (Exception e) {
			logger.error("Error occured while pushing to topic verify_contacts_for_application", e);
		}
	}

	private void updateDocuments(LendingApplication lendingApplication, Meta meta) {
		try {
			List<LendingPaymentSchedule> lendingPaymentScheduleList = lendingPaymentScheduleDao.findByMerchantIdAndCreditLoanOrderByIdDesc(lendingApplication.getMerchant().getId(),false);
			LendingPaymentSchedule activeLoan = getActiveLoan(lendingPaymentScheduleList);
			if(lendingPaymentScheduleList == null || lendingPaymentScheduleList.isEmpty() || activeLoan == null || activeLoan.getLoanAmount() <= 5000) {
				logger.info("No previous loan/active loan for merchant ID {}", lendingApplication.getMerchant().getId());
				return;
			}
			LendingApplication prevApplication = lendingApplicationDao.findByIdAndMerchant(activeLoan.getApplicationId(), lendingApplication.getMerchant());
			signAgreementService.replicateDocumentsForNewApplication(prevApplication, lendingApplication, lendingApplication.getMerchant(), new MetaDTO(meta));
		} catch (Exception e) {
			logger.error("Exception replicating documents for topup---", e);
		}
	}

	private void sendTopupSms(Merchant merchant, LendingApplication lendingApplication) {
		try {
			MerchantBankDetail merchantBankDetail = merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchant.getId(), "ACTIVE");
			if (merchantBankDetail == null) {
				return;
			}
			Long docId = documentsIdProofdao.fetchLatestAddressProofDocId(merchant.getId(), lendingApplication.getId(), "LENDING");
			String proof = "";
			if (docId != null) {
				Optional<DocumentsIdProof> documentsIdProof = documentsIdProofdao.findById(docId);
				if (documentsIdProof.isPresent()) {
					proof = documentsIdProof.get().getProofType();
				}
			}
			String sms = "Hi " + merchantBankDetail.getBeneficiaryName() + ",a BharatPe agent will visit you in 72 hrs. to collect the following:\n- PAN\n- Address Proof (" + proof + ")\n- Cheque (" + merchantBankDetail.getIfscCode() + ", " + merchantBankDetail.getAccountNumber() + ")\n- Shop Ownership Doc\n- Business Ownership Proof";
			smsServiceHandler.sendSMS(new ArrayList<String>() {{
				add(merchant.getMobile());
			}}, sms, NotificationProvider.SMS.GUPSHUP);
		} catch (Exception e) {
			logger.error("Exception while sending topup sms---", e);
		}
	}

	private void checkPreBook(Merchant merchant, LendingApplication lendingApplication) {
		LendingPrebookLoans lendingPrebookLoans = lendingPrebookLoansDao.findByMerchantId(merchant.getId());
		if (lendingPrebookLoans != null) {
			logger.info("Prebook loan already exists for merchant: {}", merchant.getId());
			notificationExecutor.submit(() -> sendNotification(merchant, lendingApplication));
			return;
		}
		MerchantSummaryLending merchantSummaryLending = merchantSummaryLendingDao.findByMerchantId(merchant.getId());
		MerchantSummary merchantSummary = merchantSummaryDao.getByMerchantId(merchant.getId());
		LendingCategories lendingCategories = lendingCategoryDao.getByCategory(lendingApplication.getCategory());
		List<String> preBookCategories = Arrays.asList("Grocery","Medical","Dairy");
		List<String> etcCategories = Arrays.asList("S1LG","S1DG","S2LG","S2DG");
		List<String> cities = Arrays.asList("Bangalore", "Hyderabad", "Pune", "Delhi");
		if (preBookCategories.contains(merchant.getBusinessCategory()) && merchantSummaryLending != null && merchantSummaryLending.getSegment().equalsIgnoreCase("2") && merchantSummary.getBpScore() > 10 && lendingCategories.getMasterCategory() != null && etcCategories.contains(lendingCategories.getMasterCategory()) && cities.contains(lendingApplication.getCity())) {
			Calendar c = Calendar.getInstance();
			c.setTime(lendingApplication.getAgreementAt());
			c.add(Calendar.DATE, -9);
			Date startDate = c.getTime();
			List<Object[]> transactions = paymentTransactionNewDao.getCountForPreBook(merchant.getId(), startDate, lendingApplication.getAgreementAt());
			if (transactions != null && transactions.size() >= 8) {
				Double previousLoanAmount = lendingApplication.getLoanAmount();
				AvailableLoan availableLoan = new AvailableLoan();
				availableLoan.setAmount(100000D);
				LoanCalculationUtil.LoanBreakupDetail breakup = LoanCalculationUtil.getLoanBreakup(availableLoan, lendingCategories, null);
				lendingApplication.setEdi(Double.valueOf(breakup.getEdi()));
				lendingApplication.setIoEdi(Double.valueOf(breakup.getIoEdi()));
				lendingApplication.setRepayment(Double.valueOf(breakup.getRepayment()));
				lendingApplication.setDisbursalAmount((double)breakup.getLoanAmount());
				lendingApplication.setLoanAmount((double)breakup.getLoanAmount());
				lendingApplicationDao.save(lendingApplication);
				lendingPrebookLoansDao.save(new LendingPrebookLoans(merchant.getId(), lendingApplication.getId(), previousLoanAmount));
				logger.info("Updated loan amount to 100000 for merchant: {} with applicationId: {}", merchant.getId(), lendingApplication.getId());
			}
		}
		notificationExecutor.submit(() -> sendNotification(merchant, lendingApplication));
	}

	private void sendNotification(Merchant merchant, LendingApplication lendingApplication) {
		
		MerchantBankDetail merchantBankDetail = merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchant.getId(),"ACTIVE");
		if(merchantBankDetail == null) {
			return;
		}

		List<String> mobiles = new ArrayList<> ();
		mobiles.add(merchant.getMobile());
		Double loanAmount = lendingApplication.getLoanAmount();
		
		if (!StringUtils.isEmpty(lendingApplication.getLoanType()) && "PREBOOK".equalsIgnoreCase(lendingApplication.getLoanType())) {
			String sms = "Hi "+merchantBankDetail.getBeneficiaryName()+",\nYou have successfully Applied for Rs."+loanAmount.intValue()+" Loan with BharatPe which you will get in your " + merchantBankDetail.getBankName() + " A/c in next 10 days post verification.\nYou have scored 10 Runs which you can use to get Rewards on BharatPe App.";
			smsServiceHandler.sendSMS(mobiles, sms, NotificationProvider.SMS.GUPSHUP);
		}else if(!StringUtils.isEmpty(lendingApplication.getLoanType()) && "BHARAT_SWIPE".equalsIgnoreCase(lendingApplication.getLoanType())){
			String sms =  "Hi  " + merchantBankDetail.getBeneficiaryName() + ",\nYour Cash Advance application for INR " + loanAmount.intValue() + " has been received successfully." + "Your Application ID is " + lendingApplication.getExternalLoanId() + ". It should be processed in the next 24-48 hours.";
			smsServiceHandler.sendSMS(mobiles, sms, NotificationProvider.SMS.GUPSHUP);
		} else {
			String smsContent = "Hi "+merchantBankDetail.getBeneficiaryName()+",\n\nYour loan application for INR "+loanAmount.intValue()+" has been received successfully.\n\nYour Application ID is "+lendingApplication.getExternalLoanId()+" and this should get processed in the next 7 days.\nNote: Due to necessary precautions for coronavirus, there may be a delay in processing your application. We'll keep you posted.";
			smsServiceHandler.sendSMS(mobiles, smsContent, NotificationProvider.SMS.GUPSHUP);
		}
		String whatsappContent = "Hi  " + merchantBankDetail.getBeneficiaryName() + ",\n" +
				"\n" +
				"Your loan application for INR " + loanAmount.intValue() + " has been received successfully.\n" +
				"Your Application ID is " + lendingApplication.getExternalLoanId() + ".";
		whatsappNotificationService.send(merchant, null, whatsappContent, mobiles, null);
		MerchantFcmToken merchantFcmToken = merchantFcmTokenDao.findByMerchantId(merchant.getId());
		
		if(merchantFcmToken != null) {
			if (mobiles.isEmpty()) {
				logger.info("mobile list is empty");
				mobiles.add(merchant.getMobile());
			}
			String pushContent = "Dear "+merchantBankDetail.getBeneficiaryName()+", Your loan application for INR "+loanAmount.intValue()+" has been received successfully.";
			pushNotificationHandler.sendPushNotification(merchantFcmToken.getFcmToken(), merchantFcmToken.getPlatform(), pushContent, "dynamic?key=loan");
			if (isPaymentBank(merchant, merchantBankDetail)) {
				String pushNotification = "Hi  " + merchantBankDetail.getBeneficiaryName() + ",\n" +
						"\n" +
						"We have received your Loan Application of Rs." + loanAmount.intValue() + ".Our lending partners do not support disbursal in Payment Banks. Please change your registered account with us to a non-payment bank to get Rs." + loanAmount.intValue() + " NOW!";
				pushNotificationHandler.sendPushNotification(merchantFcmToken.getFcmToken(), merchantFcmToken.getPlatform(), pushNotification, "dynamic?key=change-acc");
				String sms = "Dear "+merchantBankDetail.getBeneficiaryName()+",\nYour loan application for Rs."+loanAmount.intValue()+" has been successfully received.. Our lending partners do not support disbursal to Payment Banks.\nPlease change your registered account with us to a Non-payment bank to get the amount now.\nClick here: https://bharatpe.in/acchange to change bank.";
				boolean smsSent = smsServiceHandler.sendSMS(mobiles, sms, NotificationProvider.SMS.GUPSHUP);
				whatsappNotificationService.send(merchant, null, sms, mobiles, null);
				if (smsSent) {
					logger.info("Change bank account sms sent to merchant:{}", merchant.getId());
				} else {
					logger.info("Change bank account sms not sent to merchant:{}", merchant.getId());
				}
			}
		}
	}

	private boolean isPaymentBank(Merchant merchant, MerchantBankDetail merchantBankDetail) {
		try {
			if(merchantBankDetail == null) {
				logger.error("No merchnat bank detail found for merchant id {}", merchant.getId());
				return true;
			}

			if(StringUtils.isEmpty(merchantBankDetail.getIfscCode())) {
				logger.error("IFSC is empty for merchant bank detail id {} and merchant ID {}", merchantBankDetail.getId(), merchant.getId());
				return true;
			}

			List<BankList> nonPaymentBankList = bankListDao.fetchNonPaymentBankList(merchantBankDetail.getIfscCode().substring(0,4));

			if (nonPaymentBankList == null || nonPaymentBankList.size() == 0) {
				return false;
			} else {
				logger.info("IFSC {} is of Payment bank, returning true", merchantBankDetail.getIfscCode());
				return true;
			}
		} catch(Exception ex) {
			logger.error("Exception while checking if merchant's bank is payment bank with merchant id {}, Exception is {}", merchant.getId(), ex);
		}
		return true;
	}

	public void createPrebookTarget(LendingApplication lendingApplication, Merchant merchant) {
		try {
			if (ExperianConstants.LOCKDOWN && lendingApplication.getLoanType() != null && lendingApplication.getLoanType().equalsIgnoreCase("PREBOOK")) {
				MerchantSummaryLending merchantSummaryLending = merchantSummaryLendingDao.findByMerchantId(merchant.getId());
				if (merchantSummaryLending != null && merchantSummaryLending.getTpv() > 0D) {
					double tpv = merchantSummaryLending.getSegment().equalsIgnoreCase("1") ? merchantSummaryLending.getTpv() * 0.25 : merchantSummaryLending.getTpv() * 0.15;
					Date lockdownEndDate = new SimpleDateFormat("yyyy-MM-dd").parse("2020-05-17");
					Date targetAchieveDate = new SimpleDateFormat("yyyy-MM-dd").parse("2020-05-27");
					if (lendingApplication.getPincode() != null) {
						LendingCities lendingCities = lendingCitiesDao.findActiveCityByPincode(lendingApplication.getPincode().intValue());
						if (lendingCities != null && lendingCities.getLockdownEndDate() != null) {
							lockdownEndDate = new SimpleDateFormat("MM/dd/yyyy").parse(lendingCities.getLockdownEndDate());
							Calendar c = Calendar.getInstance();
							c.setTime(lockdownEndDate);
							c.add(Calendar.DATE, 10);
							targetAchieveDate = c.getTime();
						}
					}
					if (lendingApplication.getAgreementAt().after(lockdownEndDate)) {
						lockdownEndDate = lendingApplication.getAgreementAt();
						Calendar c = Calendar.getInstance();
						c.setTime(lockdownEndDate);
						c.add(Calendar.DATE, 10);
						targetAchieveDate = c.getTime();
					}
					lendingPrebookTargetDao.save(new LendingPrebookTarget(merchant.getId(), merchantSummaryLending.getSegment(), lendingApplication.getId(), lendingApplication.getPincode(), tpv, lockdownEndDate, targetAchieveDate));
				}
			}
		} catch (Exception e) {
			logger.error("Exception while inserting lending_prebook_target for merchant: {}", merchant.getId());
			logger.error("Exception---", e);
		}
	}

	private LendingPaymentSchedule getActiveLoan(List<LendingPaymentSchedule> lendingPaymentScheduleList) {
		if(lendingPaymentScheduleList == null || lendingPaymentScheduleList.size() == 0) {
			return null;
		}
		for(LendingPaymentSchedule schedule : lendingPaymentScheduleList) {
			if(Status.LendingStatus.ACTIVE.toString().equals(schedule.getStatus())) {
				return schedule;
			}
		}
		return null;
	}
}
