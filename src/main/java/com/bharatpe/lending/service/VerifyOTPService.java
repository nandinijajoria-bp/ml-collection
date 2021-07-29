package com.bharatpe.lending.service;

import com.bharatpe.common.dao.*;
import com.bharatpe.common.entities.*;
import com.bharatpe.common.enums.NotificationProvider;
import com.bharatpe.common.enums.Status;
import com.bharatpe.common.handlers.SmsServiceHandler;
import com.bharatpe.common.objects.CommonAPIRequest;
import com.bharatpe.common.objects.Meta;
import com.bharatpe.lending.common.dao.LendingShopDocumentsDao;
import com.bharatpe.lending.common.dto.NotificationPayloadDto;
import com.bharatpe.lending.common.entity.BpEnach;
import com.bharatpe.lending.common.entity.LendingShopDocuments;
import com.bharatpe.lending.common.service.LendingNotificationService;
import com.bharatpe.lending.constant.ExperianConstants;
import com.bharatpe.lending.dao.*;
import com.bharatpe.lending.dto.MetaDTO;
import com.bharatpe.lending.entity.LendingPrebookTarget;
import com.bharatpe.lending.enums.KycStatus;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.handlers.BharatPeOtpHandler;
import com.bharatpe.lending.handlers.KycHandler;
import com.bharatpe.lending.loanV2.dto.KycStatusDTO;
import com.bharatpe.lending.util.LoanCalculationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


@Service
public class VerifyOTPService {
	private Logger logger = LoggerFactory.getLogger(VerifyOTPService.class);
	
	@Autowired
	LendingApplicationDao lendingApplicationDao;

	@Autowired
	BharatPeOtpHandler bharatPeOtpHandler;
	
	@Autowired
	LendingAuditTrialDao lendingAuditTrialDao;

	@Autowired
	MerchantBankDetailDao merchantBankDetailDao;
	
	@Autowired
	SmsServiceHandler smsServiceHandler;

	@Autowired
	LendingLedgerDao lendingLedgerDao;

	@Autowired
	BankListDao bankListDao;

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
	
	ExecutorService notificationExecutor = Executors.newFixedThreadPool(10);

	@Autowired
	LendingPrebookTargetDao lendingPrebookTargetDao;

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

	@Autowired
	BPEnachDao bpEnachDao;

	@Autowired
	APIGatewayService apiGatewayService;

	@Autowired
	LendingNotificationService lendingNotificationService;

	@Autowired
	KycHandler kycHandler;

    @Autowired
    DocumentsIdProofDao documentsIdProofDao;

    @Autowired
    LendingShopDocumentsDao lendingShopDocumentsDao;

	List<Long> exemptMerchant = Arrays.asList(2411647L, 1210933L, 4340760L, 2097359L, 7090157L, 6518986L, 1141505L, 3L, 3543643L, 9319451L, 8891247L, 2078363L);

	public Map<String, Boolean> verifyOTP(Merchant merchant, CommonAPIRequest commonAPIRequest) {
		Map<String, Boolean> finalResponse = new LinkedHashMap<>();
		finalResponse.put("success",false);
		finalResponse.put("agreement_verified",false);
		
		Long applicationId =  commonAPIRequest.getPayload().get("application_id") != null ? Long.parseLong(commonAPIRequest.getPayload().get("application_id").toString()) : null;
		String otp =  commonAPIRequest.getPayload().get("otp") != null ? commonAPIRequest.getPayload().get("otp").toString() : null;
		String uuid =  commonAPIRequest.getPayload().get("uuid") != null ? commonAPIRequest.getPayload().get("uuid").toString() : null;

		if(applicationId == null || applicationId <= 0 || StringUtils.isEmpty(otp)) {
			logger.info("No application found in draft status for given application id {}", applicationId);
			return finalResponse;
		}
		LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchantAndStatus(applicationId, merchant, "draft");
		if(lendingApplication == null) {
			logger.info("No application found in draft status for given application id {}", applicationId);
			return finalResponse;
		}


		return verifyOTP(otp, uuid, merchant, lendingApplication, commonAPIRequest.getMeta());
	}
	
	private Map<String, Boolean> verifyOTP(String otp, String uuid, Merchant merchant, LendingApplication lendingApplication, Meta meta) {
		Map<String, Boolean> finalResponse = new LinkedHashMap<>();
		finalResponse.put("success",false);
		finalResponse.put("agreement_verified",false);
		logger.info("Mobile length: {}", merchant.getMobile().length());
		if(merchant.getMobile().length() == 12) {
			Boolean isOTPVerified = bharatPeOtpHandler.verifyOtp(merchant.getMobile(), otp, uuid);
			if(isOTPVerified) {
				finalResponse = updateApplicationStatusAndSuccessSms(merchant, lendingApplication, meta);
				//createPrebookTarget(lendingApplication, merchant);
			}
		}
		return finalResponse;
	}
	
	private Map<String, Boolean> updateApplicationStatusAndSuccessSms(Merchant merchant, LendingApplication lendingApplication, Meta meta) {
		Map<String, Boolean> finalResponse = new LinkedHashMap<>();
		List<String> topupLoans = Arrays.asList(LoanType.TOPUP.name(), LoanType.HALF_TOPUP.name(), LoanType.IO_TOPUP.name());
		finalResponse.put("success",false);
		finalResponse.put("agreement_verified",false);
		LendingApplication openApplication = lendingApplicationDao.findOpenApplication(merchant.getId());
		LendingPaymentSchedule activeLoan = lendingPaymentScheduleDao.getOldestActiveLoan(merchant.getId());
		Integer repeatLoan = lendingPaymentScheduleDao.getRepeatLoan(merchant.getId());
		if (!topupLoans.contains(lendingApplication.getLoanType()) && (openApplication != null || activeLoan != null)) {
			logger.info("duplicate application for merchant:{} and applicationId:{}", merchant.getId(), lendingApplication.getId());
			lendingApplication.setStatus("deleted");
			lendingApplicationDao.save(lendingApplication);
			notificationExecutor.execute(() -> apiGatewayService.globalLimitTxn(lendingApplication.getMerchant().getId(), "CREDIT",lendingApplication.getLoanAmount()));
			return finalResponse;
		}
		if("TOPUP".equalsIgnoreCase(lendingApplication.getLoanType())){
			LendingApplication checkDupe = lendingApplicationDao.findOpenApplication(lendingApplication.getMerchant().getId());
			if(checkDupe != null){
				logger.info("duplicate application for Topup Loan For MerchantId:{} and applicationId:{}", merchant.getId(), lendingApplication.getId());
				lendingApplication.setStatus("deleted");
				lendingApplicationDao.save(lendingApplication);
				notificationExecutor.execute(() -> apiGatewayService.globalLimitTxn(lendingApplication.getMerchant().getId(), "CREDIT",lendingApplication.getLoanAmount()));
				return finalResponse;
			}
		}
        if (!topupLoans.contains(lendingApplication.getLoanType())) {
            List<DocumentsIdProof> documentsIdProofList = documentsIdProofDao.findByMerchantAndLendingApplication(merchant, lendingApplication);
            List<LendingShopDocuments> shopDocuments = lendingShopDocumentsDao.findByMerchantIdAndApplicationId(merchant.getId(), lendingApplication.getId());
            if (documentsIdProofList == null || documentsIdProofList.size() == 0 || shopDocuments.isEmpty()) {
                logger.error("documents not found for application:{}", lendingApplication.getId());
                return finalResponse;
            }
        }

		if(lendingApplication.getProcessingFee() > 0 && apiGatewayService.eligibleForProcessingFee(lendingApplication.getMerchant().getId())){
			logger.info("Merchant is BP CLUB member, so making processing fee zero for applicationID:{}", lendingApplication.getId());
			lendingApplication.setDisbursalAmount(lendingApplication.getDisbursalAmount() + lendingApplication.getProcessingFee());
			lendingApplication.setProcessingFee(0D);
		}

		BpEnach enachSuccess = bpEnachDao.findSuccessEnach(merchant.getId());
		MerchantBankDetail merchantBankDetail = merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchant.getId(), "ACTIVE");
		if (enachSuccess != null && merchantBankDetail != null && enachSuccess.getAccountNumber() != null && !enachSuccess.getAccountNumber().equals(merchantBankDetail.getAccountNumber())) {
			enachSuccess = null;
		}
		DateFormat df = new SimpleDateFormat("ddMMyy");
		Date dateobj = new Date();
		String loanId = "BPL" + df.format(dateobj) + lendingApplication.getId();
		lendingApplication.setAgreementAt(new Date());
		lendingApplication.setAgreement(1);
		if (meta != null && meta.getLatitude() != null && !meta.getLatitude().equalsIgnoreCase("undefined") && !meta.getLatitude().trim().equalsIgnoreCase("")) {
			lendingApplication.setLatitude(meta.getLatitude());
		}
		if (meta != null && !StringUtils.isEmpty(meta.getLongitude()) && !meta.getLongitude().equalsIgnoreCase("undefined") && !meta.getLongitude().equalsIgnoreCase("null") && !meta.getLongitude().trim().equalsIgnoreCase("")) {
			lendingApplication.setLongitude(meta.getLongitude());
		}
		lendingApplication.setExternalLoanId(loanId);
		if (enachSuccess != null) {
			lendingApplication.setNachType("ENACH");
			lendingApplication.setNachLender("BHARATPE");
			lendingApplication.setNachReferenceNumber(enachSuccess.getReferenceNumber());
			lendingApplication.setNachStatus("APPROVED");
		}
		if(topupLoans.contains(lendingApplication.getLoanType())){
			logger.info("TOPUP loan submitted for merchant {}", merchant.getId());
			updateDocuments(lendingApplication, meta);
			topUpLoans(lendingApplication);
        }
        lendingApplication.setStatus("pending_verification");
        lendingApplicationDao.save(lendingApplication);
		redisNotificationService.sendPendingEnachNotification(merchant, lendingApplication);

		LendingAuditTrial lendingAuditTrial = new LendingAuditTrial();
		lendingAuditTrial.setApplicationId(lendingApplication.getId());
		lendingAuditTrial.setMerchantId(merchant.getId());
		lendingAuditTrial.setLoanId(loanId);
		lendingAuditTrial.setUserId(Long.parseLong("0"));
		lendingAuditTrial.setOldStatus("draft");
        lendingAuditTrial.setNewStatus("pending_verification");
		lendingAuditTrial.setType("APP_STATUS");

		lendingAuditTrialDao.save(lendingAuditTrial);
		notificationExecutor.submit(() -> sendNotification(merchant, lendingApplication));
		if (!StringUtils.isEmpty(lendingApplication.getCkycId())) {
			logger.info("Checking kyc status for new flow application:{}", lendingApplication.getId());
			updateKycStatus(lendingApplication);
		}

		sendPennyDrop(merchant.getId(),lendingApplication.getId());
		sendLatLong(merchant.getId(),lendingApplication.getId());

		if(repeatLoan == 0 && !topupLoans.contains(lendingApplication.getLoanType())){
			sendDetailsForContactsVerification(merchant.getId(), lendingApplication.getId());
			if (lendingApplication.getLoanAmount() <= 200000)
				sendDetailsForKycVerification(merchant.getId(),lendingApplication.getId(),false);
		}

		sendDuplicatePancardCheck(merchant.getId(), lendingApplication.getId());

		finalResponse.put("success",true);
		finalResponse.put("agreement_verified",true);
		return finalResponse;
	}

	private void updateKycStatus(LendingApplication lendingApplication) {
		try {
			KycStatusDTO kycStatus = kycHandler.getKycStatus(lendingApplication.getMerchant().getId());
			logger.info("kyc status:{} for application:{}", kycStatus, lendingApplication.getId());
			if (kycStatus.getKycStatus().equals(KycStatus.APPROVED)) {
				lendingApplication.setCkycStatus(KycStatus.APPROVED.name());
				lendingApplication.setCkycDate(new Date());
				lendingApplicationDao.save(lendingApplication);
			} else if (kycStatus.getKycStatus().equals(KycStatus.REJECTED)) {
				lendingApplication.setCkycStatus(KycStatus.REJECTED.name());
				lendingApplication.setCkycRejectionReason(kycStatus.getRemarks());
				lendingApplication.setCkycDate(new Date());
				lendingApplication.setStatus(KycStatus.REJECTED.name());
				lendingApplicationDao.save(lendingApplication);
			} else if (kycStatus.getKycStatus().equals(KycStatus.PENDING)) {
				lendingApplication.setCkycStatus(KycStatus.PENDING.name());
				lendingApplication.setCkycDate(new Date());
				lendingApplicationDao.save(lendingApplication);
			} else {
				logger.error("Unable to update kycStatus:{} for application:{}", kycStatus, lendingApplication.getId());
			}
		} catch (Exception e) {
			logger.error("Exception in updateKycStatus for application:{}", lendingApplication.getId());
		}
	}

	public void sendLatLong(Long merchantId,Long applicationId){
		try {
			Map<String,Long> detailMap=new HashMap<String, Long>(){{
				put("merchantId", merchantId);
				put("applicationId",applicationId);
			}};
			kafkaTemplate.send("find_lat_long", merchantId.toString(), detailMap);
			logger.info("Pushed "+detailMap+" to topic find_lat_long");
		}
		catch(Exception e) {
			logger.error("Error occured while pushing to topic find_lat_long",e);
		}
	}

	private Boolean topUpLoans(LendingApplication lendingApplication){
		try{
			LendingPaymentSchedule activeLoan = lendingPaymentScheduleDao.findByMerchantIdAndStatus(lendingApplication.getMerchant().getId(),"ACTIVE");
			if(activeLoan == null){
				return false;
			}
			Double previousAmount = activeLoan.getLoanAmount() - activeLoan.getPaidPrinciple() + activeLoan.getDueInterest();
			LendingLedger lendingLedger = new LendingLedger();
			lendingLedger.setMerchant(activeLoan.getMerchant());
			lendingLedger.setLendingPaymentSchedule(activeLoan);
			lendingLedger.setTxnType("EDI");
			lendingLedger.setAmount(previousAmount);
			lendingLedger.setDate(new Date());
			lendingLedger.setDescription("TOPUP LOAN ADJUSTMENT");
			lendingLedger.setPrinciple(previousAmount-activeLoan.getDueInterest());
			lendingLedger.setInterest(activeLoan.getDueInterest());
			lendingLedger.setAdjustmentMode(lendingApplication.getLoanType());
			lendingLedgerDao.save(lendingLedger);

			LendingLedger negativeEntry = new LendingLedger();
			negativeEntry.setMerchant(activeLoan.getMerchant());
			negativeEntry.setLendingPaymentSchedule(activeLoan);
			negativeEntry.setTxnType("EDI");
			negativeEntry.setAmount(-(previousAmount-activeLoan.getDueAmount()));
			negativeEntry.setDate(new Date());
			negativeEntry.setDescription("TOPUP LOAN ADJUSTMENT");
			negativeEntry.setPrinciple(-(previousAmount-activeLoan.getDueAmount()));
			negativeEntry.setInterest(0D);
			negativeEntry.setAdjustmentMode(lendingApplication.getLoanType());
			lendingLedgerDao.save(negativeEntry);

			activeLoan.setStatus("CLOSED");
			activeLoan.setClosingDate(new Date());
			activeLoan.setPaidAmount(activeLoan.getPaidAmount()+previousAmount);
			activeLoan.setPaidPrinciple(activeLoan.getPaidPrinciple()+previousAmount-activeLoan.getDueInterest());
			activeLoan.setPaidInterest(activeLoan.getPaidInterest()+activeLoan.getDueInterest());
			activeLoan.setDueAmount(0D);
			activeLoan.setDuePrinciple(0D);
			activeLoan.setDueInterest(0D);
			lendingPaymentScheduleDao.save(activeLoan);

			lendingApplication.setDisbursalAmount(lendingApplication.getLoanAmount()-previousAmount);
			lendingApplicationDao.save(lendingApplication);
			if ("TOPUP".equalsIgnoreCase(lendingApplication.getLoanType())) {
				notificationExecutor.execute(() -> apiGatewayService.globalLimitTxn(lendingApplication.getMerchant().getId(), "CREDIT", previousAmount));
			}

		}catch(Exception ex){
			logger.error("Exception IN TOPUP LOANS Ledger:{}",ex);
		}

		return true;
	}

	public void sendPennyDrop(Long merchantId,Long applicationId){
		try {
			Map<String,Long> detailMap = new HashMap<String, Long>(){{
				put("merchantId", merchantId);
				put("applicationId",applicationId);
			}};
			kafkaTemplate.send("check_pennydrop", merchantId.toString(), detailMap);
			logger.info("Pushed "+detailMap+" to topic check_pennydrop");
		}
		catch(Exception e) {
			logger.error("Error occured while pushing to topic check_pennydrop",e);
		}
	}
	
	public void sendDetailsForKycVerification(Long merchantId, Long applicationId, boolean isCreditLine) {
		if (exemptMerchant.contains(merchantId)) {
			return;
		}
		try {
			Map<String,Long> detailMap=new HashMap<String, Long>(){{
				put("merchantId", merchantId);
				put("applicationId",applicationId);
				put("isCreditLine",isCreditLine?1L:0L);
			}};
			kafkaTemplate.send("auto_kyc", merchantId.toString(), detailMap);
			logger.info("Pushed "+detailMap+" to topic auto_kyc");
		}
		catch(Exception e) {
			logger.error("Error occured while pushing to toipc auto_kyc",e);
		}
	}

	public void sendDetailsForContactsVerification(Long merchantId, Long applicationId) {
		if (exemptMerchant.contains(merchantId)) {
			return;
		}
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

		String identifier = "LENDING_APPLICATION_RECEIVED_PUSH";
		Map<String,Object> templateParams = new HashMap<>();
		templateParams.put("loan_amount",loanAmount.intValue());
		templateParams.put("external_loan_id",lendingApplication.getExternalLoanId());
		NotificationPayloadDto notificationPayloadDto = new NotificationPayloadDto();
		notificationPayloadDto.setTemplateIdentifier(identifier);
		notificationPayloadDto.setTemplateParams(templateParams);
		notificationPayloadDto.setMobile(merchant.getMobile());
		notificationPayloadDto.setPushDeepLink("dynamic?key=loan");
		notificationPayloadDto.setPushTitle("BHARATPE");
		notificationPayloadDto.setClientName("LENDING");
		lendingNotificationService.notify(notificationPayloadDto);

		String whatsappContent = "Hi  " + merchantBankDetail.getBeneficiaryName() + ",\n" +
				"\n" +
				"Your loan application for INR " + loanAmount.intValue() + " has been received successfully.\n" +
				"Your Application ID is " + lendingApplication.getExternalLoanId() + ".";
//		whatsappNotificationService.send(merchant, null, whatsappContent, mobiles, null);

		if (isPaymentBank(merchant, merchantBankDetail)) {
			identifier = "LENDING_APPLICATION_RECEIVED_2_PUSH";
			notificationPayloadDto = new NotificationPayloadDto();
			notificationPayloadDto.setTemplateIdentifier(identifier);
			notificationPayloadDto.setMobile(merchant.getMobile());
			notificationPayloadDto.setPushDeepLink("dynamic?key=loan");
			notificationPayloadDto.setPushTitle("BHARATPE");
			notificationPayloadDto.setClientName("LENDING");
			notificationPayloadDto.setTemplateParams(templateParams);
			lendingNotificationService.notify(notificationPayloadDto);
//			whatsappNotificationService.send(merchant, null, sms, mobiles, null);
		}

		identifier = "LENDING_AGENT_SMS";
		notificationPayloadDto.setTemplateIdentifier(identifier);
		lendingNotificationService.notify(notificationPayloadDto);
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

	public void sendDuplicatePancardCheck(Long merchantId,Long applicationId){
		try {
			Map<String,Long> detailMap=new HashMap<String, Long>(){{
				put("merchantId", merchantId);
				put("applicationId",applicationId);
			}};
			kafkaTemplate.send("check_duplicate_pancard", merchantId.toString(), detailMap);
			logger.info("Pushed "+detailMap+" to topic check_duplicate_pancard");
		}
		catch(Exception e) {
			logger.error("Error occured while pushing to topic check_duplicate_pancard",e);
		}
	}
}
