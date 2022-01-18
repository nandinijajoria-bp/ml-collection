package com.bharatpe.lending.service;

import com.bharatpe.common.dao.*;
import com.bharatpe.common.entities.*;
import com.bharatpe.common.enums.NotificationProvider;
import com.bharatpe.common.handlers.PushNotificationHandler;
import com.bharatpe.common.handlers.SmsServiceHandler;
import com.bharatpe.common.service.WhatsappNotificationService;
import com.bharatpe.common.utils.AesEncryption;
import com.bharatpe.common.utils.HmacCalculator;
import com.bharatpe.lending.common.dao.*;
import com.bharatpe.lending.common.entity.*;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.constant.CreditConstants;
import com.bharatpe.lending.dao.BankListDao;
import com.bharatpe.lending.dao.LendingLedgerDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.dto.CreditLineRepaymentHistoryResponseDto.Repayment;
import com.bharatpe.lending.dto.DailySettlementResponseDto.DailyRepayment;
import com.bharatpe.lending.handlers.BharatPeOtpHandler;
import com.bharatpe.lending.util.CreditUtil;
import com.bharatpe.lending.util.LoanUtil;
import com.bharatpe.lending.util.creditresponse.CrifResponseUtil;
import com.bharatpe.lending.util.creditresponse.ExperianResponseUtil;
import com.bharatpe.lending.util.creditresponse.ResponseUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.text.DecimalFormat;
import java.util.*;

@Service
public class CreditLineService {
	
	Logger logger=LoggerFactory.getLogger(CreditLineService.class);
	
	@Autowired
	CreditApplicationDao creditApplicationDao;
	
	@Autowired
	ExperianDao experianDao;
	
	@Autowired
	CreditAccountDao creditAccountDao;

	@Autowired
	LendingCaBalanceDetailDao lendingCaBalanceDetailDao;

	@Autowired
	CreditLineCategoriesDao creditLineCategoriesDao;

	@Autowired
	BharatPeOtpHandler bharatPeOtpHandler;

	@Autowired
	LendingClTransactionDao lendingClTransactionDao;

	@Autowired
	LendingClLedgerDao lendingClLedgerDao;

	@Autowired
	IfscDao ifscDao;

	@Autowired
	LendingTlDetailsDao lendingTlDetailsDao;

	@Autowired
	LendingPaymentScheduleDao lendingPaymentScheduleDao;

	@Autowired
	LendingClTransactionRequestDao lendingClTransactionRequestDao;

	@Autowired
	MerchantBankDetailDao merchantBankDetailDao;
	
	@Autowired
	CreditDayEndBalanceDao creditDayEndBalanceDao;

	@Autowired
	BankListDao bankListDao;

	@Autowired
	LendingDeeplinkDao lendingDeeplinkDao;

	@Autowired
	HmacCalculator hmacCalculator;

	@Autowired
	InternalClientDao internalClientDao;

	@Autowired
	AesEncryption aesEncryption;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	RestTemplate restTemplate;

	@Autowired
	CreditUtil creditUtil;
	
	@Autowired
	LendingLedgerDao lendingLedgerDao;
	
	@Autowired
	SmsServiceHandler smsServiceHandler;
	
	@Autowired
	WhatsappNotificationService whatsappNotificationService;

	@Value("${spring.profiles.active:dev}")
	private String activeProfile;

	@Autowired
	CreditLineMerchantDao creditLineMerchantDao;
	
	@Autowired
	PushNotificationHandler pushNotificationHandler;
	
	@Autowired
	MerchantFcmTokenDao merchantFcmTokenDao;

	@Autowired
	CreditLineTransaction creditLineTransaction;

	@Autowired
	PincodeCityStateMappingDao pincodeCityStateMappingDao;

	@Autowired
	LendingCityCreditScoreDao lendingCityCreditScoreDao;

	@Value("${cl.deeplink}")
	private String clDeeplink;
	
	private final DecimalFormat df = new DecimalFormat("#.##");

	@Autowired
	RedisNotificationService redisNotificationService;

	public ResponseDTO createCreditLineAccount(CreateCreditAccountRequestDto request, Merchant merchant){

		CreditLineMerchant creditLineMerchant = creditLineMerchantDao.findByMerchantId(merchant.getId());
		if (creditLineMerchant == null) {
			logger.error("Merchant:{} not applicable for credit line", merchant.getId());
			return new ResponseDTO(false,"Merchant not applicable for credit line", null,null);
		}
		
		if(request.getApplicationId()!=null){
			
			try {
				
				logger.info("Fetching credit application details for the application id {}",request.getApplicationId());
				
				Optional<CreditApplication> optionalCreditApplication=creditApplicationDao.findById(request.getApplicationId());
				
				if(optionalCreditApplication==null || !optionalCreditApplication.isPresent()){
					
					logger.warn("Credit application not found for the application id {}",request.getApplicationId());
					return new ResponseDTO(false,"No loan application found for the requested application id", null,null);
				
				}
				
				CreditApplication creditApplication=optionalCreditApplication.get();
				if(creditApplication.getAccountCreated()) {
					logger.info("Credit account already exists");
					return new ResponseDTO(true,"Successful",null,null);
				}
				
				logger.info("Fetching segment detail from experian table");
				
				Experian experian= experianDao.getByMerchantId(merchant.getId());
				
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
					creditAccount.setActivationDate(new Date());
					creditAccount.setCreatedAt(new Date());
					creditAccount.setUpdatedAt(new Date());
					creditAccount.setNextBillDate(DateTimeUtil.addDays(new Date(), 20));
					creditAccount.setDueDate(DateTimeUtil.addDays(new Date(), 29));
					
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
					
					creditApplication.setAccountCreated(true);
					creditApplicationDao.save(creditApplication);
					creditLineMerchant.setCreditAccountId(creditAccount.getId());
					creditLineMerchantDao.save(creditLineMerchant);
					
					sendActivationNotification(creditApplication, merchant);
					redisNotificationService.sendPromotionalNotificationForCreditLine(merchant,creditAccount);
					return new ResponseDTO(true,"Successful",null,null);
				}
				else {
					
					logger.error("Experian details or segment details not found");
					
					return new ResponseDTO(false,"Experian details not found",null,null);
				}
				
				
			}
			catch(Exception e) {
				
				logger.error("Error occured while creating new credit line account",e);
				return new ResponseDTO(false,"Eroor occured while creating credit account",null,null);
			}
			
		}
		else {
			logger.error("Application id is absent in the request body");
			return new ResponseDTO(false,"Application id missing from the request body", null,null);
		}
	}
	
	public void sendActivationNotification(CreditApplication  creditApplication,Merchant merchant) {
		List<String> mobiles = new ArrayList<> ();
		mobiles.add(merchant.getMobile());
		String message="Hi "+merchant.getBeneficiaryName()+"!\n"+"Congratulations ! Your BharatPe Loan Balance of Rs. "+creditApplication.getAmount()+" is now ACTIVE. Utilize your Loan Balance as per requirement and pay interest only on amount used at low rate of 0.1% / day. Repay with complete flexibility.\n";
		smsServiceHandler.sendSMS(mobiles, message+"Click Here : "+CreditConstants.MESSAGE_NOTIFICATION_LINK+" for more details.", NotificationProvider.SMS.GUPSHUP);
		whatsappNotificationService.send(merchant, null, message+"Click Here : "+CreditConstants.MESSAGE_NOTIFICATION_LINK+" for more details.", mobiles, null);
		MerchantFcmToken merchantFcmToken = merchantFcmTokenDao.getByMerchantId(merchant.getId());
		if(merchantFcmToken != null) {
			pushNotificationHandler.sendPushNotification(merchantFcmToken.getFcmToken(), merchantFcmToken.getPlatform(), message, "dynamic?key=credit-line");
		}
		
	}

	public CreditSpendResponseDTO getSpendDetails(Merchant merchant, Long requestId) {
		LendingClTransactionRequest paymentRequest = lendingClTransactionRequestDao.findByIdAndMerchantId(requestId, merchant.getId());
		if (paymentRequest == null) {
			return new CreditSpendResponseDTO(false, "Invalid Payment request_id");
		}
		CreditSpendRequestDTO requestDTO = new CreditSpendRequestDTO(paymentRequest.getAmount().intValue(), paymentRequest.getMode());
		if (!validateSpendDetailRequest(requestDTO)) {
			return new CreditSpendResponseDTO(false, "Invalid request");
		}
		CreditAccount creditAccount = creditAccountDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchant.getId(), "ACTIVE");
		if (creditAccount == null) {
			return new CreditSpendResponseDTO(false, "Credit Account does not exist");
		}
		LendingCaBalanceDetail lendingCaBalanceDetail = lendingCaBalanceDetailDao.findByMerchantIdAndCreditAccountId(merchant.getId(), creditAccount.getId());
		if (!CreditUtil.isSufficientBalance(creditAccount, lendingCaBalanceDetail, requestDTO.getAmount())) {
			return new CreditSpendResponseDTO(false, "Insufficient Balance");
		}
		CreditSpendResponseDTO creditSpendResponseDTO = new CreditSpendResponseDTO();
		CreditLineCategories creditLineCategories = creditLineCategoriesDao.findTop1ByCategoryOrderByMaxCreditLimitDesc(creditAccount.getSegment());
		if (CreditUtil.isSufficientCLBalance(lendingCaBalanceDetail, requestDTO.getAmount(), requestDTO.getMode(), creditLineCategories)) {
			creditSpendResponseDTO.setCl(new CreditSpendResponseDTO.CL(requestDTO.getAmount(), creditAccount.getDueDate(), creditAccount.getNextBillDate()));
		}
		else {
			creditSpendResponseDTO.setAvailableCl(CreditUtil.getAvailableClForSpecificMode(lendingCaBalanceDetail, creditLineCategories, requestDTO.getMode()));
		}
		//List<LendingTlDetails> todayLoans = lendingTlDetailsDao.findByMerchantIdAndDateBetween(merchant.getId(), DateTimeUtil.getCurrentDayStartTime(), DateTimeUtil.getEndTimeFromDateTime(new Date()));
		if (CreditUtil.isSufficientTLBalance(creditAccount, lendingCaBalanceDetail, requestDTO.getAmount(), null)) {
			creditSpendResponseDTO.setTl(fetchTl(requestDTO.getAmount()));
		}
		//send endpoint for client
		LendingDeeplink lendingDeeplink = lendingDeeplinkDao.findByClient(paymentRequest.getMode());
		if (lendingDeeplink != null) {
			creditSpendResponseDTO.setInitiateEndpoint(lendingDeeplink.getInitiateEndpoint());
			creditSpendResponseDTO.setVerifyEndpoint(lendingDeeplink.getVerifyEndpoint());
		}
		creditSpendResponseDTO.setClient(paymentRequest.getMode());
		if(paymentRequest.getMode().equalsIgnoreCase("BANK_TRANSFER")) {
			MerchantBankDetail merchantBankDetail = merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchant.getId(),"ACTIVE");
			List<Ifsc> ifscList = ifscDao.findByIfsc(merchantBankDetail.getIfscCode());
			if (!ifscList.isEmpty()) {
				BankList bankList = bankListDao.findByBankCode(ifscList.get(0).getBankCode());
				String narrationHeading = "Money will be transferred to the following Bank A/c:";
				String narration1 = "Mr " + merchantBankDetail.getBeneficiaryName();
				String narration2 = "XX-" + merchantBankDetail.getAccountNumber().substring(merchantBankDetail.getAccountNumber().length()-4) + " (" + ifscList.get(0).getBank() + ")";
				String narration3 = "Branch - " + ifscList.get(0).getBranch();
				String icon = bankList.getImageUrl();
				creditSpendResponseDTO.setDetails(new CreditSpendResponseDTO.Narration(narrationHeading, narration1, narration2, narration3, icon, merchant.getMobile().substring(2)));
			}
		} else if (paymentRequest.getNarration1() != null) {
			String narrationHeading = paymentRequest.getMode().equalsIgnoreCase("SEND_MONEY") ? "Sent to the following Mobile No:" : "Bill paid for:";
			creditSpendResponseDTO.setDetails(new CreditSpendResponseDTO.Narration(narrationHeading, paymentRequest.getNarration1(), paymentRequest.getNarration2(), paymentRequest.getNarration3(), paymentRequest.getIcon(), merchant.getMobile().substring(2)));
		}
		return creditSpendResponseDTO;
	}

	private boolean validateSpendDetailRequest(CreditSpendRequestDTO requestDTO) {
		if (requestDTO == null || requestDTO.getAmount() == null || requestDTO.getMode() == null) {
			return false;
		}
		if (requestDTO.getAmount() <= 0) {
			return false;
		}
		return CreditConstants.validSpendMode(requestDTO.getMode()) && CreditConstants.SpendGroup.containsKey(requestDTO.getMode());
	}

	private List<CreditSpendResponseDTO.TL> fetchTl(Integer amount) {
		List<CreditSpendResponseDTO.TL> tlList = new ArrayList<>();
		if (amount < 5000) {
			tlList.add(calculateTL(amount, 1));
		} else if (amount < 20000) {
			tlList.add(calculateTL(amount, 1));
			tlList.add(calculateTL(amount, 3));
		} else if (amount < 100000) {
			tlList.add(calculateTL(amount, 1));
			tlList.add(calculateTL(amount, 3));
			tlList.add(calculateTL(amount, 6));
//			tlList.add(calculateTL(amount, 9));
//			tlList.add(calculateTL(amount, 12));
		} else {
			tlList.add(calculateTL(amount, 3));
			tlList.add(calculateTL(amount, 6));
//			tlList.add(calculateTL(amount, 9));
//			tlList.add(calculateTL(amount, 12));
//			tlList.add(calculateTL(amount, 15));
		}
		tlList.sort(Comparator.comparingInt(CreditSpendResponseDTO.TL::getEdiAmount));
		return tlList;
	}

	private CreditSpendResponseDTO.TL calculateTL(Integer amount, int tenure) {
		int ediCount = LoanUtil.getEdiDays(tenure);
		int edi = (int) Math.ceil(((amount + (amount * 0.02 * tenure))) / ediCount);
		Integer repayment = Math.round(ediCount * edi);
		Integer interestAmount = repayment - amount;
		return new CreditSpendResponseDTO.TL(edi, tenure, 2D, 0, amount, interestAmount, repayment, ediCount);
	}

	public CreditSpendVerifyResponseDTO verifySpend(Merchant merchant, CreditSpendVerifyRequestDTO requestDTO) {
		LendingClTransactionRequest paymentRequest = lendingClTransactionRequestDao.findByIdAndMerchantId(requestDTO.getRequestId(), merchant.getId());
		if (paymentRequest == null || !"PENDING".equalsIgnoreCase(paymentRequest.getStatus())) {
			return new CreditSpendVerifyResponseDTO(false, "Invalid Payment request_id");
		}
		if (!validateSpendVerifyRequest(paymentRequest)) {
			return new CreditSpendVerifyResponseDTO(false, "Invalid request");
		}
		if (!bharatPeOtpHandler.verifyOtp(merchant.getMobile(), requestDTO.getOtp(), requestDTO.getUuid())) {
			return new CreditSpendVerifyResponseDTO(false, "Invalid OTP");
		}
		CreditAccount creditAccount = creditAccountDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchant.getId(), "ACTIVE");
		if (creditAccount == null) {
			return new CreditSpendVerifyResponseDTO(false, "Credit Account does not exist");
		}
		LendingCaBalanceDetail lendingCaBalanceDetail = lendingCaBalanceDetailDao.findByMerchantIdAndCreditAccountId(merchant.getId(), creditAccount.getId());
		CreditLineCategories creditLineCategories = creditLineCategoriesDao.findTop1ByCategoryOrderByMaxCreditLimitDesc(creditAccount.getSegment());
//		List<LendingTlDetails> todayLoans = lendingTlDetailsDao.findByMerchantIdAndDateBetween(merchant.getId(), DateTimeUtil.getCurrentDayStartTime(), DateTimeUtil.getEndTimeFromDateTime(new Date()));
		boolean sufficientBalance = "CL".equals(paymentRequest.getLoanType()) ? CreditUtil.isSufficientCLBalance(lendingCaBalanceDetail, paymentRequest.getAmount().intValue(), paymentRequest.getMode(), creditLineCategories)
				: CreditUtil.isSufficientTLBalance(creditAccount, lendingCaBalanceDetail, paymentRequest.getAmount().intValue(), null);
		if (!sufficientBalance) {
			return new CreditSpendVerifyResponseDTO(false, "Insufficient Balance");
		}
		int expireRequest = lendingClTransactionRequestDao.expireRequest(paymentRequest.getId(), merchant.getId());
		if (expireRequest != 1) {
			return new CreditSpendVerifyResponseDTO(false, "Unable to expire payment request");
		}
		//Starting transaction
		LendingClTransaction lendingClTransaction = creditLineTransaction.createDebitTxn(creditAccount, paymentRequest);
		creditLineTransaction.debitTxn(creditAccount, paymentRequest, lendingClTransaction);
		try {
			payout(lendingClTransaction,merchant);
		} catch (Exception e) {
			logger.error("Exception in bank transfer---", e);
			creditLineTransaction.rollbackTxn(lendingClTransaction);
		}
		return createSpendVerifyResponse(lendingClTransaction);
	}

	private CreditSpendVerifyResponseDTO createSpendVerifyResponse(LendingClTransaction lendingClTransaction) {
		CreditAccount creditAccount = creditAccountDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(lendingClTransaction.getMerchantId(), "ACTIVE");
		CreditSpendVerifyResponseDTO responseDTO = new CreditSpendVerifyResponseDTO();
		responseDTO.setTransactionId(lendingClTransaction.getId());
		responseDTO.setAmount(lendingClTransaction.getAmount());
		responseDTO.setTransferTime(new Date());
		responseDTO.setBankReferenceNo(lendingClTransaction.getBankReferenceId());
		responseDTO.setAvailableLimit(creditAccount.getAvailableBalance());
		responseDTO.setStatus(lendingClTransaction.getStatus());
		responseDTO.setNarrationHeading("Transferred to the following Bank A/c:");
		responseDTO.setDeeplink("bharatpe://dynamic?key=cl");
		if (lendingClTransaction.getOrderId() != null && lendingClTransaction.getIfscCode() != null) {
			List<Ifsc> ifscList = ifscDao.findByIfsc(lendingClTransaction.getIfscCode());
			if (!ifscList.isEmpty()) {
				BankList bankList = bankListDao.findByBankCode(ifscList.get(0).getBankCode());
				responseDTO.setNarration1("Mr " + lendingClTransaction.getBeneficiaryName());
				responseDTO.setNarration2("XX-" + lendingClTransaction.getAccountNumber().substring(lendingClTransaction.getAccountNumber().length()-4) + " (" + ifscList.get(0).getBank() + ")");
				responseDTO.setNarration3("Branch - " + ifscList.get(0).getBranch());
				responseDTO.setIcon(bankList.getImageUrl());
			}
		}
		return responseDTO;
	}

	private boolean validateSpendVerifyRequest(LendingClTransactionRequest requestDTO) {
		if (requestDTO.getAmount() == null || requestDTO.getAmount() <=0 || requestDTO.getMode() == null || requestDTO.getLoanType() == null) {
			return false;
		}
		if (!CreditConstants.validSpendMode(requestDTO.getMode()) || !CreditConstants.SpendGroup.containsKey(requestDTO.getMode()) || (!"TL".equals(requestDTO.getLoanType()) && !"CL".equals(requestDTO.getLoanType()))) {
			return false;
		}
		if ("TL".equals(requestDTO.getLoanType()) && (requestDTO.getTenure() == null || !Arrays.asList(1,3,6).contains(requestDTO.getTenure()))) {
			return false;
		}
		return true;
	}

	private void payout(LendingClTransaction lendingClTransaction, Merchant merchant) {
		BankTransferResponseDTO bankTransferResponseDTO;
		if ("prod".equalsIgnoreCase(activeProfile)) {
			bankTransferResponseDTO = callPayoutAPI(lendingClTransaction);
		} else {
			bankTransferResponseDTO = new BankTransferResponseDTO("SUCCESS", "123", "xx-1234", "Khushal", "IOBA0001612", 123L, lendingClTransaction.getId().toString());
		}
		if (bankTransferResponseDTO != null) {
			lendingClTransaction.setOrderId(bankTransferResponseDTO.getPayoutId().toString());
			lendingClTransaction.setBankReferenceId(bankTransferResponseDTO.getBankReferenceNumber());
			lendingClTransaction.setIfscCode(bankTransferResponseDTO.getIfsc());
			lendingClTransaction.setAccountNumber(bankTransferResponseDTO.getAccountNumber());
			lendingClTransaction.setBeneficiaryName(bankTransferResponseDTO.getBeneficiaryName());
			creditLineTransaction.saveTxn(lendingClTransaction);
			if(CreditConstants.PaymentStatus.FAILED.name().equalsIgnoreCase(bankTransferResponseDTO.getPaymentStatus())) {
				creditLineTransaction.rollbackTxn(lendingClTransaction);
				sendFiledTransNotification(lendingClTransaction,merchant);
				return;
			} else if (CreditConstants.PaymentStatus.SUCCESS.name().equalsIgnoreCase(bankTransferResponseDTO.getPaymentStatus())) {
				creditLineTransaction.updateTxnStatus(lendingClTransaction, CreditConstants.PaymentStatus.SUCCESS);
				if (lendingClTransaction.getType().equalsIgnoreCase("TL")) {
					creditLineTransaction.createLPS(merchant, lendingClTransaction);
				}
			}
			//send debit notification
			try {
				String message = lendingClTransaction.getType().equalsIgnoreCase("CL") ? getFlexibileNotificationMessage(lendingClTransaction, merchant) : getFixedNotificationMessage(lendingClTransaction, merchant);
				sendNotification(message, merchant);
			} catch (Exception e) {
				logger.error("Unable to send debit notification", e);
			}
		} else {
			logger.error("Exception in bank transfer for account:{}", lendingClTransaction.getCreditAccountId());
		}
	}

	public String getFlexibileNotificationMessage(LendingClTransaction lendingClTransaction,Merchant merchant) {
		MerchantBankDetail merchantBankDetail = merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchant.getId(),"ACTIVE");
		CreditAccount creditAccount = creditAccountDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchant.getId(), "ACTIVE");
//		return "Hi "+merchantBankDetail.getBeneficiaryName()+",\n" +
//				"Rs."+Double.valueOf(df.format(lendingClTransaction.getAmount()))+" Loan used for "+CreditConstants.SpendModeFrontEndFormat.getOrDefault(lendingClTransaction.getSubType(), lendingClTransaction.getSubType())+" successfully on BharatPe.\n" + 
//				"Your Available Loan Balance is Rs."+Double.valueOf(df.format(creditAccount.getAvailableBalance()))+". More details: " + CreditConstants.MESSAGE_NOTIFICATION_LINK;
		
		return "Hi "+merchantBankDetail.getBeneficiaryName()+",\n" +
				"Rs."+Double.valueOf(df.format(lendingClTransaction.getAmount()))+" from your BharatPe Loan Balance has been successsfully used towards "+CreditConstants.SpendModeFrontEndFormat.getOrDefault(lendingClTransaction.getSubType(), lendingClTransaction.getSubType())+".\n" + 
				"Available Balance now is Rs. "+Double.valueOf(df.format(creditAccount.getAvailableBalance()))+". Click Here: "+CreditConstants.MESSAGE_NOTIFICATION_LINK;
	}
	
	public String getFixedNotificationMessage(LendingClTransaction lendingClTransaction,Merchant merchant) {
		CreditAccount creditAccount = creditAccountDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchant.getId(), "ACTIVE");
		LendingTlDetails lendingTlDetails = lendingTlDetailsDao.findByLendingClTransaction(lendingClTransaction);
		MerchantBankDetail merchantBankDetail = merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchant.getId(),"ACTIVE");
		
//		return "Hi "+merchantBankDetail.getBeneficiaryName()+",\n" +
//				"Rs."+Double.valueOf(df.format(lendingClTransaction.getAmount()))+" Loan used for "+CreditConstants.SpendModeFrontEndFormat.getOrDefault(lendingClTransaction.getSubType(), lendingClTransaction.getSubType())+" successfully on BharatPe.\n" + 
//				"Your Available Loan Balance is Rs."+Double.valueOf(df.format(creditAccount.getAvailableBalance()))+
//				".\nDaily installment of Rs."+Double.valueOf(df.format(lendingTlDetails.getEdi()))+" will be deducted from your QR Settlements. \n" + 
//				"More details: " + CreditConstants.MESSAGE_NOTIFICATION_LINK;
		
		return "Hi "+merchantBankDetail.getBeneficiaryName()+",\n" +
				"Rs."+Double.valueOf(df.format(lendingClTransaction.getAmount()))+" from your BharatPe Loan Balance has been successfully used towards "+CreditConstants.SpendModeFrontEndFormat.getOrDefault(lendingClTransaction.getSubType(), lendingClTransaction.getSubType())+".\n" + 
				"EDI of Rs."+Double.valueOf(df.format(lendingTlDetails.getEdi()))+" will be deducted from your BharatPe settlement over the next "+lendingTlDetails.getPayableDays()+" days. Available Balance now is Rs. "+Double.valueOf(df.format(creditAccount.getAvailableBalance()))+". \n" + 
				"Click Here: "+CreditConstants.MESSAGE_NOTIFICATION_LINK;
		
	}
	public void sendNotification(String message, Merchant merchant) {
		List<String> mobiles=new LinkedList<>();
		mobiles.add(merchant.getMobile());
		smsServiceHandler.sendSMS(mobiles, message, NotificationProvider.SMS.GUPSHUP);
		whatsappNotificationService.send(merchant, null, message+" for more details.", mobiles, null);
	}
	
	public void sendFiledTransNotification(LendingClTransaction lendingClTransaction, Merchant merchant) {
		String message="Hi "+merchant.getBeneficiaryName()+",\n" + 
				"Your Loans Balance transaction of Rs."+lendingClTransaction.getAmount()+" has Failed. Please try again after some time.\n" + 
				"Click Here: "+CreditConstants.MESSAGE_NOTIFICATION_LINK+" for more details.";
		List<String> mobiles=new LinkedList<>();
		mobiles.add(merchant.getMobile());
		smsServiceHandler.sendSMS(mobiles, message, NotificationProvider.SMS.GUPSHUP);
		whatsappNotificationService.send(merchant, null, message, mobiles, null);
	}
	
	@SuppressWarnings("unchecked, rawtypes")
	private BankTransferResponseDTO callPayoutAPI(LendingClTransaction lendingClTransaction) {
		try {
			InternalClient internalClient = internalClientDao.findByClientNameAndStatus("LENDING", "ACTIVE");
			Map requestParams = new HashMap<>();
			requestParams.put("merchantId", lendingClTransaction.getMerchantId());
			if (lendingClTransaction.getMerchantStoreId() != null) {
				requestParams.put("merchantStoreId", lendingClTransaction.getMerchantStoreId());
			}
			requestParams.put("amount", lendingClTransaction.getAmount());
			requestParams.put("orderId", lendingClTransaction.getId());
			requestParams.put("loanType", "CL".equalsIgnoreCase(lendingClTransaction.getType()) ? "FLEXIBLE" : "FIXED");
			String hash = hmacCalculator.calculateHmac(hmacCalculator.getPayload(requestParams), aesEncryption.decrypt(internalClient.getSecret()));
			UriComponents requestUrl = UriComponentsBuilder.fromHttpUrl(CreditConstants.PAYOUT_URL).build();
			HttpHeaders headers = new HttpHeaders();
			headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
			headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
			headers.set("hash", hash);
			headers.set("clientName", "LENDING");
			HttpEntity<Object> entity = new HttpEntity<>(requestParams, headers);
			logger.info("payout request: {}", objectMapper.writeValueAsString(entity));
			long startTime = System.currentTimeMillis();
			logger.info("Successful payout api result in {} ms", System.currentTimeMillis() - startTime);
			int retryCount=0;
			while(retryCount<3) {
				try {
					ResponseEntity<Object> response = restTemplate.exchange(requestUrl.encode().toUri(), HttpMethod.POST, entity, Object.class);
					logger.info("Payout response: {}", objectMapper.writeValueAsString(response.getBody()));
					if (response.getBody() != null && "100".equalsIgnoreCase(((Map<String, Object>) response.getBody()).get("responseCode").toString())) {
						logger.info("payout success for transaction:{}", lendingClTransaction.getId());
						Map<String, Object> responseData = (Map<String, Object>) ((Map<String, Object>) response.getBody()).get("data");
						BankTransferResponseDTO bankTransferResponseDTO = new BankTransferResponseDTO();
						bankTransferResponseDTO.setPaymentStatus(responseData.get("paymentStatus").toString());
						bankTransferResponseDTO.setBankReferenceNumber(responseData.get("bankReferenceNumber") != null ? responseData.get("bankReferenceNumber").toString() : null);
						bankTransferResponseDTO.setAccountNumber(responseData.get("accountNumber").toString());
						bankTransferResponseDTO.setBeneficiaryName(responseData.get("beneficiaryName").toString());
						bankTransferResponseDTO.setIfsc(responseData.get("ifsc").toString());
						bankTransferResponseDTO.setPayoutId(((Number)responseData.get("payoutId")).longValue());
						bankTransferResponseDTO.setOrderId(responseData.get("orderId").toString());
						return bankTransferResponseDTO;
					}
				}
				catch(Exception e) {
					logger.error("Error occured while calling Payout API",e);
				}
				retryCount++;
			}
			
			
		} catch (Exception e) {
			logger.error("Exception in payout api---", e);
		}
		logger.info("Payout failed for orderId: {}", lendingClTransaction.getId());
		return null;
	}

	public CreditLineRepaymentHistoryResponseDto getRepaymentHistory(Merchant merchant) {
		
		try {
			logger.info("Fetching repayment history for merchant {}",merchant.getId());
			CreditLineRepaymentHistoryResponseDto response=new CreditLineRepaymentHistoryResponseDto();
			List<LendingClTransaction> repaymentTransactions=lendingClTransactionDao.findByMerchantIdAndModeAndTypeOrderByCreatedAtDesc(merchant.getId(), "CREDIT","PAYMENT");
			List<Repayment> repaymentList=new LinkedList<>();
			
			for(LendingClTransaction transaction:repaymentTransactions) {
				Repayment repayment=new Repayment();
				repayment.setId(transaction.getId());
				repayment.setAmount(transaction.getAmount());
				repayment.setDate(transaction.getCreatedAt());
				if(CreditConstants.SpendModeFrontEndFormat.containsKey(transaction.getSubType())) {
					repayment.setMode(CreditConstants.SpendModeFrontEndFormat.get(transaction.getSubType()));
				}
				else {
					repayment.setMode(transaction.getSubType());
				}	
				if(transaction.getStatus().equalsIgnoreCase("CANCELLED")) {
					repayment.setStatus("FAILED");
				}
				else {
					repayment.setStatus(transaction.getStatus());
				}
				Map<String,Double> partition=getClAndTlPartOfPayment(transaction);
				repayment.setClAmount(partition.getOrDefault("CL", 0D));
				repayment.setTlAmount(partition.getOrDefault("TL", 0D));
				repaymentList.add(repayment);
			}
			response.setRepayments(repaymentList);
			return response;
		}
		catch(Exception e) {
			logger.error("Error occured while fetching repayment history",e);
			return getErrorResponseForRepaymentHistory("Error occured while fetching repayment history");
		}
	}
	
	public Map<String,Double> getClAndTlPartOfPayment(LendingClTransaction lendingClTransaction){
		Map<String,Double> breakUpMap=new HashMap<String, Double>();
		List<LendingClLedger> lendingClLedgers=lendingClLedgerDao.findByClTransactionId(lendingClTransaction.getId());
		for(LendingClLedger ledger:lendingClLedgers) {
			if(ledger.getTransactionType().equalsIgnoreCase("CL")) {
				breakUpMap.put("CL",ledger.getAmount());
			}
			else if(ledger.getTransactionType().equalsIgnoreCase("TL") || ledger.getTransactionType().equalsIgnoreCase("EDI")) {
				breakUpMap.put("TL",ledger.getAmount());
			}
		}
		return breakUpMap;
	}
	
	public CreditLineRepaymentHistoryResponseDto getErrorResponseForRepaymentHistory(String message) {
		
		CreditLineRepaymentHistoryResponseDto errorResponse=new CreditLineRepaymentHistoryResponseDto();
		errorResponse.setSuccess(false);
		errorResponse.setMessage(message);
		return errorResponse;
	}

	public CreditSpendResponseDTO createSpend(Long merchantId, CreditSpendRequestDTO creditSpendRequestDTO) {
		if (!validateSpendDetailRequest(creditSpendRequestDTO)) {
			return new CreditSpendResponseDTO(false, "Invalid request");
		}
		CreditAccount creditAccount = creditAccountDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchantId, "ACTIVE");
		if (creditAccount == null) {
			return new CreditSpendResponseDTO(false, "Credit Account does not exist");
		}
		LendingCaBalanceDetail lendingCaBalanceDetail = lendingCaBalanceDetailDao.findByMerchantIdAndCreditAccountId(merchantId, creditAccount.getId());
		if (!CreditUtil.isSufficientBalance(creditAccount, lendingCaBalanceDetail, creditSpendRequestDTO.getAmount())) {
			return new CreditSpendResponseDTO(false, "Insufficient Balance");
		}
		LendingClTransactionRequest paymentRequest = new LendingClTransactionRequest(creditAccount.getMerchantId(), creditAccount.getId(), creditSpendRequestDTO.getMode(), creditSpendRequestDTO.getAmount().doubleValue());
		paymentRequest = creditLineTransaction.saveTxnRequest(paymentRequest);
		String deeplink = clDeeplink + "&wroute=order&wid=" + paymentRequest.getId();
		return new CreditSpendResponseDTO(paymentRequest.getId(), deeplink);
	}

	public CreditSpendResponseDTO initiateSpend(Merchant merchant, SpendInitiateRequestDTO requestDTO) {
		if (requestDTO.getRequestId() == null || (!"TL".equals(requestDTO.getLoanType()) && !"CL".equals(requestDTO.getLoanType()))) {
			return new CreditSpendResponseDTO(false, "Invalid request");
		}
		if ("TL".equals(requestDTO.getLoanType()) && (requestDTO.getTenure() == null || !Arrays.asList(1,3,6).contains(requestDTO.getTenure()))) {
			return new CreditSpendResponseDTO(false, "Invalid request");
		}
		CreditAccount creditAccount = creditAccountDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchant.getId(), "ACTIVE");
		if (creditAccount == null) {
			return new CreditSpendResponseDTO(false, "Credit Account does not exist");
		}
		LendingCaBalanceDetail lendingCaBalanceDetail = lendingCaBalanceDetailDao.findByMerchantIdAndCreditAccountId(merchant.getId(), creditAccount.getId());
		CreditLineCategories creditLineCategories = creditLineCategoriesDao.findTop1ByCategoryOrderByMaxCreditLimitDesc(creditAccount.getSegment());
		LendingClTransactionRequest paymentRequest = lendingClTransactionRequestDao.findByIdAndMerchantId(requestDTO.getRequestId(), merchant.getId());
		if (paymentRequest == null || !"PENDING".equalsIgnoreCase(paymentRequest.getStatus())) {
			return new CreditSpendResponseDTO(false, "Invalid Payment request_id");
		}
		boolean sufficientBalance = "CL".equals(requestDTO.getLoanType()) ? CreditUtil.isSufficientCLBalance(lendingCaBalanceDetail, paymentRequest.getAmount().intValue(), paymentRequest.getMode(), creditLineCategories)
				: CreditUtil.isSufficientTLBalance(creditAccount, lendingCaBalanceDetail, paymentRequest.getAmount().intValue(), null);
		if (!sufficientBalance) {
			return new CreditSpendResponseDTO(false, "Insufficient Balance");
		}
		lendingClTransactionRequestDao.updateLoanTypeAndTenure(requestDTO.getLoanType(), requestDTO.getTenure(), paymentRequest.getId());
		String hash = requestDTO.getAppHash() != null ? requestDTO.getAppHash() : "yltNeplA2JJ";
		String message = "<#> BharatPe: {otp} is your OTP to complete payment for Rs." + paymentRequest.getAmount() + " using BharatPe Loans. NEVER SHARE THIS OTP WITH ANYONE. " + hash;
		Map<String, Object> response = new HashMap<String, Object>();
		response= bharatPeOtpHandler.sendOtp(merchant.getMobile(), message);
		Boolean otp = (Boolean) response.get("success");
		String uuid = (String) response.get("uuid");
		logger.info("OTP sent on mobile: {} ", uuid);
		if (otp) {
			CreditSpendResponseDTO responseDTO = new CreditSpendResponseDTO();
			responseDTO.setAuthentication("OTP");
			responseDTO.setUuid(uuid);
			logger.info("verifyOTP response : {}", responseDTO);
			return responseDTO;
		} else {
			return new CreditSpendResponseDTO(false, "Unable to send OTP");
		}
	}

	public DailySettlementResponseDto fetchDailySettlementDetail(Merchant merchant) {
		try {
			
			DailySettlementResponseDto dailySettlementResponseDto=new DailySettlementResponseDto();
			List<LendingPaymentSchedule> termLoanList=lendingPaymentScheduleDao.findByMerchantIdAndStatusAndCreditLoan(merchant.getId(), "ACTIVE", true);
			double totalAmount=0D;
			double totalEdi=0D;
			double totalMad=0D;
			Date today = new Date();
			Calendar cal = Calendar.getInstance();
			cal.setTime(today);
//			if(cal.get(Calendar.DAY_OF_WEEK)==1){
//				dailySettlementResponseDto.setTotalAmount(totalAmount);
//				return dailySettlementResponseDto;
//			}
			List<DailyRepayment> repaymentList=new LinkedList<>();
			for(LendingPaymentSchedule loan:termLoanList) {
				if(DateTimeUtil.getCurrentDayStartTime().compareTo(DateTimeUtil.getStartTimeFromDateTime(loan.getCreatedAt()))==0) {					
					continue;
				}
				Double dueAmount=loan.getDueAmount();
				if(dueAmount == null || dueAmount<=0D) {
					continue;
				}
				totalAmount+=dueAmount;
				totalEdi+=dueAmount;
				DailyRepayment repayment=new DailyRepayment();
				repayment.setRepaymentAmount(dueAmount);
				repayment.setDate(loan.getCreatedAt());
				repayment.setLoanAmount(loan.getLoanAmount());
				Optional<LendingTlDetails> lendingTlDetailsOptional=lendingTlDetailsDao.findById(loan.getTlDetailsId());
				if(lendingTlDetailsOptional!=null && lendingTlDetailsOptional.isPresent()){
					LendingTlDetails lendingTlDetails=lendingTlDetailsOptional.get();
					if(lendingTlDetails.getLendingClTransaction()==null) {
						return getDailySettlementErrorResponse("Loan transaction detail not found for loan id "+lendingTlDetails.getId());
					}
					repayment.setLoanType("Fixed");
					if(CreditConstants.SpendModeFrontEndFormat.containsKey(lendingTlDetails.getLendingClTransaction().getSubType())) {
						repayment.setMode(CreditConstants.SpendModeFrontEndFormat.get(lendingTlDetails.getLendingClTransaction().getSubType()));
					}
					else {
						repayment.setMode(lendingTlDetails.getLendingClTransaction().getSubType());
					}
					repayment.setTenure(lendingTlDetails.getTenure());
					repaymentList.add(repayment);
				}
				else {
					return getDailySettlementErrorResponse("Term loan details not found");
				}
			}
			//start of code to populated dummy data for flexible loan
//			DailyRepayment repayment=new DailyRepayment();
//			repayment.setRepaymentAmount(20D);
//			repayment.setDate(new Date());
//			repayment.setLoanAmount(5000D);
//			repayment.setLoanType("Flexible");
//			repayment.setMode("Bank Transfer");
//			repaymentList.add(repayment);
//			totalMad+=20D;
//			totalAmount+=20D;
			//end of code
			dailySettlementResponseDto.setTotalMad(Math.round(totalMad*100.0)/100.0);//round of to 2 decimal place
			dailySettlementResponseDto.setTotalEdi(Math.round(totalEdi*100.0)/100.0);
			dailySettlementResponseDto.setTotalAmount(Math.round(totalAmount*100.0)/100.0);
			dailySettlementResponseDto.setRepayments(repaymentList);
			return dailySettlementResponseDto;
		}
		catch(Exception e) {
			logger.error("Error occured while fetching daily settlement details",e);
			return getDailySettlementErrorResponse("Error occured while fetching daily settlement details");
		}
	}
	
//	public Double getDueAmount(LendingPaymentSchedule lendingPaymentSchedule) {
//		try {
//			Double positiveSum=0D;
//			Double negativeSum=0D;
//			List<LendingLedger> ledgerEntry=lendingLedgerDao.findByLendingPaymentScheduleOrderByDateAscAmountAsc(lendingPaymentSchedule);
//			for(LendingLedger ledger:ledgerEntry){
//				if(!DateTimeUtil.getCurrentDayStartTime().equals(DateTimeUtil.getStartTimeFromDateTime(ledger.getDate()))) {
//					if(ledger.getAmount()<0) {
//						negativeSum+=ledger.getAmount();
//					}
//					else {
//						positiveSum+=ledger.getAmount();
//					}
//				}
//			}
//			return (negativeSum+positiveSum)>0D?0D:-1*(negativeSum+positiveSum);
//		}
//		catch(Exception e) {
//			logger.error("Error occured while calculating due amount",e);
//			return null;
//		}
//	}
	
	public DailySettlementResponseDto getDailySettlementErrorResponse(String message) {
		
		DailySettlementResponseDto errorResponse=new DailySettlementResponseDto();
		errorResponse.setSuccess(false);
		errorResponse.setMessage(message);
		return errorResponse;
	}
	
	public CreditLineRepaymentDetailResponseDto getRepaymentDetail(Long transactionId, Merchant merchant) {
		try {
			logger.info("fetching repayment detail for transaction id {}",transactionId);
			CreditLineRepaymentDetailResponseDto response=new CreditLineRepaymentDetailResponseDto();
			Optional<LendingClTransaction> lendingClTransactionOptional=lendingClTransactionDao.findById(transactionId);
			if(lendingClTransactionOptional==null || !lendingClTransactionOptional.isPresent()) {
				return getRepaymentDetailErrorMessage("Transaction details not found");		
			}
			LendingClTransaction lendingClTransaction=lendingClTransactionOptional.get();
			Calendar date = Calendar.getInstance();
			date.setTime(lendingClTransaction.getCreatedAt());
			date.set(Calendar.HOUR_OF_DAY, 0);
			date.set(Calendar.MINUTE, 0);
			date.set(Calendar.SECOND, 0);
			date.set(Calendar.MILLISECOND, 0);
			Date startTime=date.getTime();
			date.set(Calendar.HOUR_OF_DAY, 23);
			date.set(Calendar.MINUTE, 59);
			date.set(Calendar.SECOND, 59);
			Date endTime=date.getTime();
			logger.info("Fetching end day details for date {}",startTime);
			List<CreditDayEndBalance> creditDayEndBalanceList=creditDayEndBalanceDao.findByAccountIdBetweenDate(lendingClTransaction.getCreditAccountId(),startTime,endTime);
			CreditDayEndBalance creditDayEndBalance=null;
			if(!creditDayEndBalanceList.isEmpty()) {
				//only one entry should be there as fetching for only one day
				creditDayEndBalance=creditDayEndBalanceList.get(0);
				response.setAvailableBalance(creditDayEndBalance.getAvailableBalance());
			}
			if(creditDayEndBalance==null){
				CreditAccount creditAccount=creditAccountDao.findByMerchantIdForDashBoard(merchant.getId());
				response.setAvailableBalance(creditAccount.getAvailableBalance());	
			}
			response.setAmount(lendingClTransaction.getAmount());
			response.setDate(lendingClTransaction.getCreatedAt());
			if(CreditConstants.SpendModeFrontEndFormat.containsKey(lendingClTransaction.getSubType())) {
				response.setPaymentMode(CreditConstants.SpendModeFrontEndFormat.get(lendingClTransaction.getSubType()));
			}
			else {
				response.setPaymentMode(lendingClTransaction.getSubType());
			}
			
			response.setStatus(lendingClTransaction.getStatus());
			response.setTranscId(lendingClTransaction.getBankReferenceId());
			response.setIcon(lendingClTransaction.getIcon());
			if(lendingClTransaction.getSubType().equalsIgnoreCase("UPI")) {
				response.setUpiType(lendingClTransaction.getAmount()>2000?"collect_request":"intent");
			}
			return response;
		}
		catch(Exception e) {
			logger.error("Error occured while fetching repayment detail",e);
			return getRepaymentDetailErrorMessage("Error occured while fetching repayment detail");
		}
	}
	
	public CreditLineRepaymentDetailResponseDto getRepaymentDetailErrorMessage(String message) {
		
		CreditLineRepaymentDetailResponseDto errorResponse=new CreditLineRepaymentDetailResponseDto();
		errorResponse.setSuccess(false);
		errorResponse.setMessage(message);
		return errorResponse;
		
	}

	private JsonNode parseStringResponse(String response){
		if (response == null || response.isEmpty()) return null;
		try {
			return objectMapper.readTree(response);
		} catch (Exception e) {
			logger.info("Exception while parsing string response ", e);
			return null;
		}
	}

	public  CreditScoreReportDetailDTO.AverageCreditScore getAverageCreditScore(Merchant merchant, Experian experian){
		CreditScoreReportDetailDTO creditScoreReportDetailDTO = new CreditScoreReportDetailDTO();
		CreditScoreReportDetailDTO.AverageCreditScore averageCreditScore = creditScoreReportDetailDTO.new AverageCreditScore();
		try{
			PincodeCityStateMapping pincodeCityState = pincodeCityStateMappingDao.findByPincode(experian.getPincode());
			if(Objects.nonNull(pincodeCityState) && Objects.nonNull(experian.getExperianScore())) {

				Double averageCountryScore = lendingCityCreditScoreDao.getAverageCreditScoreForCountry();
				Double averageStateScore = lendingCityCreditScoreDao.getAverageCreditScoreForState(pincodeCityState.getState());
				Integer totalMerchantInState = lendingCityCreditScoreDao.getTotalMerchantInStateByPercentile(pincodeCityState.getState());

				if (Objects.isNull(averageStateScore) || totalMerchantInState < 30) {
					averageStateScore = averageCountryScore == 0 ? averageCountryScore : averageCountryScore - 1;
				}
				Double averageCityScore = lendingCityCreditScoreDao.getAverageCreditScoreForCity(pincodeCityState.getCity());
				Integer totalMerchantInCity = lendingCityCreditScoreDao.getTotalMerchantInCityByPercentile(pincodeCityState.getCity());

				if (Objects.isNull(averageCityScore) || totalMerchantInCity < 30) {
					averageCityScore = averageStateScore == 0 ? averageStateScore : averageStateScore - 1;
				}

				Double countryPercentileScore = lendingCityCreditScoreDao.getCreditScorePercentileByCountry(experian.getExperianScore());
				Double statePercentileScore = lendingCityCreditScoreDao.getCreditScorePercentileByState(pincodeCityState.getState(), experian.getExperianScore());
				if (Objects.isNull(statePercentileScore) || totalMerchantInState < 30) {
					statePercentileScore = countryPercentileScore == 0 ? countryPercentileScore : countryPercentileScore - 1;
				}
				Double cityPercentileScore = lendingCityCreditScoreDao.getCreditScorePercentileByCity(pincodeCityState.getCity(), experian.getExperianScore());
				if (Objects.isNull(cityPercentileScore) || totalMerchantInCity < 30) {
					cityPercentileScore = statePercentileScore == 0 ? statePercentileScore : statePercentileScore - 1;
				}


				averageCreditScore.setCity(pincodeCityState.getCity());
				averageCreditScore.setState(pincodeCityState.getState());
				averageCreditScore.setCountry("India");
				averageCreditScore.setCityAverageScore(averageCityScore);
				averageCreditScore.setStateAverageScore(averageStateScore);
				averageCreditScore.setCountryAverageScore(averageCountryScore);
				averageCreditScore.setCityPercentile(cityPercentileScore);
				averageCreditScore.setStatePercentile(statePercentileScore);
				averageCreditScore.setCountryPercentile(countryPercentileScore);

				return averageCreditScore;
			}
		}catch (Exception ex){
			logger.error("Error occured while fetching Average and percentile",ex);
		}
		return null;
	}

	public CreditScoreReportDetailDTO getReportDetails(Merchant merchant){
		Experian experian = experianDao.getByMerchantId(merchant.getId());
		JsonNode bureauResponse = parseStringResponse(experian.getResponse());;
		if (bureauResponse == null) {
			return new CreditScoreReportDetailDTO();
		}
		ResponseUtil responseUtil;
		if ("CRIF".equalsIgnoreCase(experian.getBureau())) {
			responseUtil = new CrifResponseUtil(experianDao);
		} else {
			responseUtil = new ExperianResponseUtil(experianDao);
		}
		CreditScoreReportDetailDTO creditScoreReportDetailDTO =  responseUtil.getCreditDetailReport(bureauResponse);
		creditScoreReportDetailDTO.setAverageCreditScore(getAverageCreditScore(merchant,experian));
		logger.info("Credit score detail_report response for merchant:{} is:{}", merchant.getId(), creditScoreReportDetailDTO);
		return creditScoreReportDetailDTO;
	}

	public LoanAndCreditCardDetailDTO getLoanAndCreditCardDetails(Merchant merchant){
		LoanAndCreditCardDetailDTO loanAndCreditCardDetailDTO = new LoanAndCreditCardDetailDTO();
		Experian experian = experianDao.getByMerchantId(merchant.getId());

		JsonNode bureauResponse = parseStringResponse(experian.getResponse());;
		if (bureauResponse == null) {
			return loanAndCreditCardDetailDTO;
		}
		ResponseUtil responseUtil;
		if ("CRIF".equalsIgnoreCase(experian.getBureau())) {
			responseUtil = new CrifResponseUtil(experianDao);
		} else {
			responseUtil = new ExperianResponseUtil(experianDao);
		}

		loanAndCreditCardDetailDTO =  responseUtil.getLoanAndCreditDetail(bureauResponse, merchant);

		if (Objects.nonNull(loanAndCreditCardDetailDTO) && Objects.nonNull(loanAndCreditCardDetailDTO.getLoanDetail())) {
			Collections.sort(loanAndCreditCardDetailDTO.getLoanDetail(),
					(o1, o2) -> Boolean.compare(o2.isStatus(), o1.isStatus()));
		}

		if (Objects.nonNull(loanAndCreditCardDetailDTO) && Objects.nonNull(loanAndCreditCardDetailDTO.getCreditCardDetail())) {
			Collections.sort(loanAndCreditCardDetailDTO.getCreditCardDetail(),
					(o1, o2) -> Boolean.compare(o2.isStatus(), o1.isStatus()));
		}

		logger.info("Credit score loan_creditcard_details response for merchant:{} is:{}", merchant.getId(), loanAndCreditCardDetailDTO);
		return loanAndCreditCardDetailDTO;
	}
}
