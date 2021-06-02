package com.bharatpe.lending.service;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.bharatpe.common.entities.*;
import com.bharatpe.common.enums.Status;
import com.bharatpe.common.service.WhatsappNotificationService;
import com.bharatpe.lending.common.dao.LendingAdjustedEDIScheduleDao;
import com.bharatpe.lending.common.dao.LendingPayoutsDao;
import com.bharatpe.lending.common.dao.LoanDpdDao;
import com.bharatpe.lending.common.dto.NotificationPayloadDto;
import com.bharatpe.lending.common.entity.LendingAdjustedEDISchedule;
import com.bharatpe.lending.common.entity.LendingPayouts;
import com.bharatpe.lending.common.entity.LendingVirtualAccount;
import com.bharatpe.lending.common.service.LendingNotificationService;
import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.enums.LendingPayoutType;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.util.LoanUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.bharatpe.common.dao.LendingEDIScheduleDao;
import com.bharatpe.common.dao.MerchantBankDetailDao;
import com.bharatpe.common.dao.MerchantDao;
import com.bharatpe.common.enums.LoyaltyTransactionType;
import com.bharatpe.common.handlers.SmsServiceHandler;
import com.bharatpe.common.objects.LoyaltyServiceRequest;
import com.bharatpe.common.service.LoyaltyService;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.constant.CreditConstants;
import com.bharatpe.lending.dao.LendingLedgerDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dao.LoanPaymentOrderDao;
import com.bharatpe.lending.entity.LoanPaymentOrder;

@Service
public class PaymentService {

	Logger logger = LoggerFactory.getLogger(PaymentService.class);
	
	@Autowired
	LendingPaymentScheduleDao lendingPaymentScheduleDao;
	
	@Autowired
	APIGatewayService apiGatewayService;

	@Autowired
	LendingPayoutsDao lendingPayoutsDao;
	
	@Autowired
	MerchantDao merchantDao;
	
	@Autowired
	LendingLedgerDao lendingLedgerDao;
	
	@Autowired
	LoyaltyService loyaltyService;
	
	@Autowired
	MerchantBankDetailDao merchantBankDetailDao;
	
	@Autowired
	SmsServiceHandler smsServiceHandler;
	
	@Autowired
	LoanPaymentOrderDao loanPaymentOrderDao;
	
	@Autowired
	LendingEDIScheduleDao lendingEDIScheduleDao;

	@Autowired
	WhatsappNotificationService whatsappNotificationService;

	@Autowired
	RedisNotificationService redisNotificationService;

	@Autowired
	LendingAdjustedEDIScheduleDao lendingAdjustedEDIScheduleDao;

	@Autowired
	LoanDpdDao loanDpdDao;

	@Autowired
	LendingNotificationService lendingNotificationService;

	ExecutorService notificationExecutor = Executors.newFixedThreadPool(10);
	
	public PaymentDetailsResponseDTO getPaymentDetails(Merchant merchant) {
		logger.info("Received payment details request for merchant id {}", merchant.getId());
		try {
			
			LendingPaymentSchedule activeLoan = lendingPaymentScheduleDao.findByMerchantIdAndStatus(merchant.getId(), "ACTIVE");
			
			if(activeLoan == null) {
				logger.info("No active loan found for merchant id {}", merchant.getId());
				return new PaymentDetailsResponseDTO("No active loan found.");
			}
			
			Integer loanAmount = activeLoan.getLoanAmount().intValue();
			Integer overdueAmount = activeLoan.getDueAmount().intValue();
			Integer overdueDays = (activeLoan.getDueAmount().intValue()/activeLoan.getEdiAmount().intValue());
			Integer principalDueAmount = (int) Math.ceil(activeLoan.getLoanAmount() - (activeLoan.getPaidPrinciple() != null ? activeLoan.getPaidPrinciple() : 0) + (activeLoan.getDueInterest() != null ? activeLoan.getDueInterest() : 0));
			Integer ediHolidayInterestAmount = getEDIHolidayInterestAmount(activeLoan);
			
			boolean isPayable = true;
			if(overdueDays < 2) {
				isPayable = false;
			}
			if (activeLoan.getTentativeClosingDate().before(new Date())) {
				double totalPayable = activeLoan.getEdiAmount() * activeLoan.getEdiCount();
				int extraAmount = (int)Math.ceil(totalPayable - (activeLoan.getPaidAmount() + principalDueAmount));
				if (extraAmount > 0d) {
					logger.info("Need to get extra amount:{} for loanId:{}", extraAmount, activeLoan.getId());
					principalDueAmount += extraAmount;//adding extra amount in foreclosure amount
				}
			}
			
			PaymentDetailsResponseDTO.Data data= new PaymentDetailsResponseDTO.Data(loanAmount, overdueAmount, principalDueAmount + ediHolidayInterestAmount, overdueDays, isPayable);
			return new PaymentDetailsResponseDTO(data);
			
		} catch(Exception ex) {
			logger.error("Execption while fetching payment details for merchant id {}, Exception is {}", merchant.getId(), ex);
		}
		
		return new PaymentDetailsResponseDTO("Something went wrong.");
	}
	
	public InitiatePaymentResponseDTO initiatePaymentV2(Merchant merchant, RequestDTO<InitiatePaymentRequestDTO> request, String token) {
		logger.info("Received initiate payment request  for merchant {} : {}", merchant.getId(), request);
		try {
			LendingPaymentSchedule activeLoan = lendingPaymentScheduleDao.findByMerchantIdAndStatus(merchant.getId(), "ACTIVE");
			if(activeLoan == null) {
				logger.info("No active loan found for merchant id {}", merchant.getId());
				return new InitiatePaymentResponseDTO("No active loan found.");
			}
			if (request.getPayload().getType() != null && request.getPayload().getType().equals(CreditConstants.PaymentMode.BT)) {
				LendingVirtualAccount lendingVirtualAccount = apiGatewayService.createLendingVAN(merchant.getId(), activeLoan.getId());
				if (lendingVirtualAccount != null) {
					MerchantBankDetail merchantBankDetail = merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchant.getId(), "ACTIVE");
					InitiatePaymentResponseDTO.Data data = new InitiatePaymentResponseDTO.Data(null, null, null, null, null, null, lendingVirtualAccount.getAccountNumber(), lendingVirtualAccount.getIfsc(), merchantBankDetail.getBeneficiaryName());
					return new InitiatePaymentResponseDTO(data);
				}
				return new InitiatePaymentResponseDTO("Something went wrong.");
			}
			Integer amount = request.getPayload().getAmount();
			if(amount < 1 || amount > 100000) {
				logger.info("Amount not between 1-100000 for merchant id {}", merchant.getId());
				return new InitiatePaymentResponseDTO("Amount should be between 1-100000.");
			}
			LoanPaymentOrder order = new LoanPaymentOrder();
			order.setMerchant(merchant);
			order.setOwner("lending_payment_schedule");
			order.setOwnerId(activeLoan.getId());
			order.setAmount(Double.valueOf(amount));
			order.setStatus("INIT");
			if (request.getPayload().getSource() != null) {
				order.setSource(request.getPayload().getSource().name());
			}
			order = loanPaymentOrderDao.save(order);
			String orderId = "LOAN" + (10000000L + order.getId());
			order.setOrderId(orderId);
			boolean paymentSuccess = false;
			Boolean otpFlow = null;
			String authMode = null;
			String accountNumber = null;
			String ifsc = null;
			PgCreateTransactionRequestDTO pgCreateTransactionRequestDTO = new PgCreateTransactionRequestDTO();
			pgCreateTransactionRequestDTO.setOrderAmount(amount.doubleValue());
			pgCreateTransactionRequestDTO.setOrderId(orderId);
			pgCreateTransactionRequestDTO.setNarration("Payment for Order No "+orderId);
			pgCreateTransactionRequestDTO.setPaymentPageHeaderText("Select Payment Mode");
			pgCreateTransactionRequestDTO.setRedirectURI("https://payment-gateway.bharatpe.io/");
			pgCreateTransactionRequestDTO.setRedirectURIDeeplink("bharatpe://dynamic?key=loan&txnID="+orderId);
			if (LoanUtil.calculateDPD(activeLoan.getEdiAmount(), activeLoan.getDueAmount()) >= 4){
				pgCreateTransactionRequestDTO.setAllowedModes(Arrays.asList("CC", "DC","NB","BP","UPI","FP"));
			}else{
				pgCreateTransactionRequestDTO.setAllowedModes(Arrays.asList("BP","UPI","FP"));
			}

			PgCreateTransactionResponseDTO response = apiGatewayService.createPgTransaction(merchant.getId(), pgCreateTransactionRequestDTO);

			if(response != null && response.getStatusCode() != null && "200".equalsIgnoreCase(response.getStatusCode())) {
				paymentSuccess = true;
			}
			if (!paymentSuccess) {
				order.setStatus("FAILED");
				order.setDescription("Unable to initiate txn");
				loanPaymentOrderDao.save(order);
				return new InitiatePaymentResponseDTO("Something went wrong.");
			}
			order.setStatus("PENDING");
			loanPaymentOrderDao.save(order);
			InitiatePaymentResponseDTO.Data data = new InitiatePaymentResponseDTO.Data(order.getVpa(), order.getUpiIntent(), order.getShortLink(), order.getOrderId(), otpFlow, authMode, accountNumber, ifsc, null);
			data.setPaymentLink(response.getData().getPaymentURIDeeplink());
			return new InitiatePaymentResponseDTO(data);
		} catch(Exception ex) {
			logger.error("Exception while initiating payment for merchant id {}", merchant.getId(), ex);
		}
		return new InitiatePaymentResponseDTO("Something went wrong.");
	}

	public InitiatePaymentResponseDTO initiatePayment(Merchant merchant, RequestDTO<InitiatePaymentRequestDTO> request, String token) {
		logger.info("Received initiate payment request  for merchant {} : {}", merchant.getId(), request);
		try {
			LendingPaymentSchedule activeLoan = lendingPaymentScheduleDao.findByMerchantIdAndStatus(merchant.getId(), "ACTIVE");
			if(activeLoan == null) {
				logger.info("No active loan found for merchant id {}", merchant.getId());
				return new InitiatePaymentResponseDTO("No active loan found.");
			}
			if (request.getPayload().getType() != null && request.getPayload().getType().equals(CreditConstants.PaymentMode.BT)) {
				LendingVirtualAccount lendingVirtualAccount = apiGatewayService.createLendingVAN(merchant.getId(), activeLoan.getId());
				if (lendingVirtualAccount != null) {
					MerchantBankDetail merchantBankDetail = merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchant.getId(), "ACTIVE");
					InitiatePaymentResponseDTO.Data data = new InitiatePaymentResponseDTO.Data(null, null, null, null, null, null, lendingVirtualAccount.getAccountNumber(), lendingVirtualAccount.getIfsc(), merchantBankDetail.getBeneficiaryName());
					return new InitiatePaymentResponseDTO(data);
				}
				return new InitiatePaymentResponseDTO("Something went wrong.");
			}
			Integer overdueAmount = activeLoan.getDueAmount().intValue();
			Integer principalDueAmount = (int) Math.ceil(activeLoan.getLoanAmount() - (activeLoan.getPaidPrinciple() != null ? activeLoan.getPaidPrinciple() : 0) + (activeLoan.getDueInterest() != null ? activeLoan.getDueInterest() : 0));
			Integer ediHolidayInterestAmount = getEDIHolidayInterestAmount(activeLoan);
			Integer amount = 0;
			if("CUSTOM".equalsIgnoreCase(request.getPayload().getPaymentType())) {
				amount = request.getPayload().getAmount();
			} else if("PRINCIPAL".equalsIgnoreCase(request.getPayload().getPaymentType()) || "TOTAL_AMOUNT".equalsIgnoreCase(request.getPayload().getPaymentType())) {
				amount = principalDueAmount + ediHolidayInterestAmount;
			} else {
				amount = overdueAmount;
			}
			List<String> psps = Arrays.asList("com.google.android.apps.nbu.paisa.user","net.one97.paytm","in.org.npci.upiapp","com.csam.icici.bank.imobile","com.mobikwik_new","com.myairtelapp","com.phonepe.app","com.olacabs.customer");
			if(amount < 1 || amount > 100000) {
				logger.info("Amount not between 1-100000 for merchant id {}", merchant.getId());
				return new InitiatePaymentResponseDTO("Amount should be between 1-100000.");
			}
			if (amount > 2000 && request.getPayload().getVpa() == null && request.getPayload().getType() == null) {
				logger.info("VPA missing for merchant id {}", merchant.getId());
				return new InitiatePaymentResponseDTO("VPA missing");
			}

			LoanPaymentOrder order = new LoanPaymentOrder();
			order.setMerchant(merchant);
			order.setOwner("lending_payment_schedule");
			order.setOwnerId(activeLoan.getId());
			order.setAmount(Double.valueOf(amount));
			order.setStatus("INIT");
			if (request.getPayload().getSource() != null) {
				order.setSource(request.getPayload().getSource().name());
			}
			order = loanPaymentOrderDao.save(order);
			String orderId = "LOAN" + (10000000L + order.getId());
			order.setOrderId(orderId);
			boolean paymentSuccess = false;
			Boolean otpFlow = null;
			String authMode = null;
			String accountNumber = null;
			String ifsc = null;
			if (request.getPayload().getType() != null && request.getPayload().getType().equals(CreditConstants.PaymentMode.BPB)) {
				Map<String, Object> result = apiGatewayService.initiateTxn(request.getMeta(), request.getSimInfo(), Double.valueOf(amount), null, orderId, token, "BharatPe Loans", request.getPayload().getSource().name());
				paymentSuccess = result.containsKey("success") ?  (Boolean) result.get("success") : false;
				otpFlow = result.containsKey("otp_flow") ? (Boolean) result.get("otp_flow") : null;
				authMode = result.containsKey("auth_mode") ? (String) result.get("auth_mode") : null;
			} else { //UPI
				Map vpaResponse = apiGatewayService.createVPA(merchant, Double.valueOf(amount), orderId, request.getPayload().getVpa());
				if(vpaResponse != null && vpaResponse.get("status") != null && "OK".equalsIgnoreCase((String) vpaResponse.get("status"))) {
					paymentSuccess = true;
					order.setVpa((String) vpaResponse.get("bharatpeTxnId"));
					order.setShortLink((String) vpaResponse.get("paymentLink"));
					order.setUpiIntent((String) vpaResponse.get("upiString"));
					order.setMid((String) vpaResponse.get("mid"));
				}
			}
			if (!paymentSuccess) {
				order.setStatus("FAILED");
				order.setDescription("Unable to initiate txn");
				loanPaymentOrderDao.save(order);
				return new InitiatePaymentResponseDTO("Something went wrong.");
			}
			order.setStatus("PENDING");
			loanPaymentOrderDao.save(order);
			InitiatePaymentResponseDTO.Data data = new InitiatePaymentResponseDTO.Data(order.getVpa(), order.getUpiIntent(), order.getShortLink(), order.getOrderId(), otpFlow, authMode, accountNumber, ifsc, null);
			data.setPsps(psps);
			return new InitiatePaymentResponseDTO(data);
		} catch(Exception ex) {
			logger.error("Exception while initiating payment for merchant id {}", merchant.getId(), ex);
		}
		return new InitiatePaymentResponseDTO("Something went wrong.");
	}
	
	public String handleCallback(PaymentCallbackRequestDTO request) {
		logger.info("Received payment callback request for order ID {} : {}", request.getOrderId(), request);
		try {
			LoanPaymentOrder order = loanPaymentOrderDao.findByOrderId(request.getOrderId());
			if(order == null) {
				logger.error("No order for order id {}", request.getOrderId());
				return "OK";
			}
			if(!"PENDING".equalsIgnoreCase(order.getStatus())) {
				logger.info("Payment for merchant id {} and order id {} is already processed", order.getMerchant().getId(), request.getOrderId());
				return "OK";
			}
			if(request.getAmount() == null || request.getAmount() <= 0D) {
				logger.error("Invalid amount received for merchant {} and amount {}", order.getMerchant().getId(), request.getAmount());
				return "OK";
			}
			Optional<LendingPaymentSchedule> activeLoan = lendingPaymentScheduleDao.findById(order.getOwnerId());
			if(!activeLoan.isPresent()) {
				logger.error("No active loan found for id {}", order.getOwnerId());
				return "OK";
			}
			if(order.getAmount()  - request.getAmount() < -1 || order.getAmount() - request.getAmount() > 1) { 
				logger.error("Amount mismatch for the merchant {} and order id {}", order.getMerchant().getId(), request.getOrderId());
				order.setStatus("FAILED");
				order.setDescription("Amount mismatch");
				loanPaymentOrderDao.save(order);
				return "OK";
			}
			adjustLoanBalance(activeLoan.get(), request.getAmount(), request.getBankReferenceNumber(), order.getSource());
			order.setBankRefNo(request.getBankReferenceNumber());
			order.setStatus("SUCCESS");
			loanPaymentOrderDao.save(order);
		} catch(Exception ex) {
			logger.error("Exception in payment callback for order id {}", request.getOrderId(), ex);
		}
		return "OK";
	}

	public String handlePgCallback(PgPaymentCallbackDTO request) {
		logger.info("Received payment callback request for order ID {} : {}", request.getOrderId(), request);
		LoanPaymentOrder order = loanPaymentOrderDao.findByOrderId(request.getOrderId());
		try {
			if(order == null) {
				logger.error("No order for order id {}", request.getOrderId());
				return "OK";
			}

			if(!"PENDING".equalsIgnoreCase(order.getStatus())) {
				logger.info("Payment for merchant id {} and order id {} is already processed", order.getMerchant().getId(), request.getOrderId());
				return "OK";
			}

			int lockTxn = loanPaymentOrderDao.updateStatusForPendingTxn(CreditConstants.PaymentStatus.CALLBACK_RECEIVED.name(), order.getId());
			if (lockTxn != 1) {
				logger.info("Unable to take lock on loan payment order:{} ", order.getId());
				return "OK";
			}

			if(request.getPaymentAmount() == null || request.getPaymentAmount() <= 0D) {
				logger.error("Invalid amount received for merchant {} and amount {}", order.getMerchant().getId(), request.getPaymentAmount());
				return "OK";
			}
			Optional<LendingPaymentSchedule> activeLoan = lendingPaymentScheduleDao.findById(order.getOwnerId());
			if(!activeLoan.isPresent()) {
				logger.error("No active loan found for id {}", order.getOwnerId());
				return "OK";
			}
			if(order.getAmount()  - request.getPaymentAmount() < -1 || order.getAmount() - request.getPaymentAmount() > 1) {
				logger.error("Amount mismatch for the merchant {} and order id {}", order.getMerchant().getId(), request.getOrderId());
				order.setStatus("FAILED");
				order.setDescription("Amount mismatch");
				loanPaymentOrderDao.save(order);
				return "OK";
			}
			if(Objects.nonNull(request.getPayments()) && !request.getPayments().isEmpty() && Objects.nonNull(request.getPayments().get(0)) && Objects.nonNull(request.getPayments().get(0).getMode())){
				order.setSource(request.getPayments().get(0).getMode());
			}
			if (request.getPaymentStatus() != null) {
				order.setStatus(request.getPaymentStatus());
				if ("SUCCESS".equalsIgnoreCase(request.getPaymentStatus())) {
					adjustLoanBalance(activeLoan.get(), request.getPaymentAmount(), request.getPaymentRefId(), order.getSource());
					order.setBankRefNo(request.getPaymentRefId());
				}
			}
			loanPaymentOrderDao.save(order);
		} catch(Exception ex) {
			if (order != null) {
				order.setStatus("PENDING");
				loanPaymentOrderDao.save(order);
			}
			logger.error("Exception in payment callback for order id {}", request.getOrderId(), ex);
		}
		return "OK";
	}

	private void sendSMS(Merchant merchant, Double amount, boolean isLoanClosed) {
		try {
			MerchantBankDetail merchantBankDetail = merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchant.getId(),"ACTIVE");
			if(merchantBankDetail == null) {
				return;
			}

			String identifier = "LENDING_PAYMENT_SMS";
			Map<String,Object> templateParams = new HashMap<>();
			templateParams.put("beneficiary_name",getBeneficiaryName(merchantBankDetail.getBeneficiaryName()));
			templateParams.put("amount",amount.intValue());

			if(isLoanClosed) {
				identifier = "LENDING_PREPAYMENT_SMS";
			}

			NotificationPayloadDto notificationPayloadDto = new NotificationPayloadDto();
			notificationPayloadDto.setTemplateIdentifier(identifier);
			notificationPayloadDto.setMobile(merchant.getMobile());
			notificationPayloadDto.setClientName("LENDING");
			notificationPayloadDto.setTemplateParams(templateParams);
			lendingNotificationService.notify(notificationPayloadDto);
		} catch(Exception ex) {
			logger.error("Exception while sending payment SMS to merchant {}, Exception is {}");
		}
	}
	
	private String getDescription(String bankRRN, boolean preclosure) {
		return preclosure ? "PRECLOSER_UPI : " + bankRRN : "PREPAYMENT : " + bankRRN;
	}
	
	private void createLendingLedger(LendingPaymentSchedule lendingPaymentSchedule, Double amount, Double principle, Double interest, String description, String source) {
        if(amount == 0) {
            return;
        }
        
        LendingLedger lendingLedger = new LendingLedger();
        lendingLedger.setMerchant(lendingPaymentSchedule.getMerchant());
        if(lendingPaymentSchedule.getMerchantStoreId() != null && lendingPaymentSchedule.getMerchantStoreId() > 0) {
        	lendingLedger.setMerchantStoreId(lendingPaymentSchedule.getMerchantStoreId());
        }
        
        lendingLedger.setLendingPaymentSchedule(lendingPaymentSchedule);
        lendingLedger.setDate(getCurrenntDate());
        lendingLedger.setTxnType("EDI");
        lendingLedger.setAmount(amount);
        lendingLedger.setInterest(interest);
        lendingLedger.setOtherCharges(0D);
        lendingLedger.setPenalty(0D);
        lendingLedger.setPrinciple(principle);
        if (source != null) {
			lendingLedger.setAdjustmentMode(source);
		} else {
			lendingLedger.setAdjustmentMode("UPI");
		}

        lendingLedger.setDescription(description);
        
        lendingLedgerDao.save(lendingLedger);

        if(amount > 0 && principle > 0) {
        	logger.info("Credit principle:{} in lending global limit for merchant:{}", principle, lendingLedger.getMerchant().getId());
			notificationExecutor.execute(() -> apiGatewayService.globalLimitTxn(lendingLedger.getMerchant().getId(), "CREDIT", principle));
		}

    }
	
	private Date getCurrenntDate() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        return cal.getTime();
	}
	
	private Integer getEDIHolidayInterestAmount(LendingPaymentSchedule lps) {
		try {
			List<LendingEDISchedule> lendingEDISchedules = lendingEDIScheduleDao.getByLoanIdAndEdiType(lps.getId(), "EDIHOLIDAY");
			if (lendingEDISchedules != null && !lendingEDISchedules.isEmpty()) {
				return lendingEDISchedules.stream().mapToInt(LendingEDISchedule::getTotalEdi).sum();
			}
		} catch(Exception ex) {
			logger.error("Exception in getEDIHolidayInterestAmount for Loan ID {}, Exception is {}", lps.getId(), ex);
		}
		return 0;
	}

	public PaymentStatusResponseDTO getStatus(String orderId, Merchant merchant) {
		logger.info("Received status check request for orderId:{}", orderId);
		try {
			LoanPaymentOrder order = loanPaymentOrderDao.findByOrderId(orderId);
			if (order == null || !order.getMerchant().getId().equals(merchant.getId())) {
				logger.info("No order found for orderId:{}", orderId);
				return new PaymentStatusResponseDTO(false, "Order not found");
			}
			return new PaymentStatusResponseDTO(order.getStatus(), orderId, order.getAmount(), order.getBankRefNo(), order.getUpdatedAt());
		} catch (Exception e) {
			logger.error("Exception in payment status check", e);
			return new PaymentStatusResponseDTO(false, "Something went wrong");
		}
	}

	public PaymentStatusResponseDTO getStatusV2(String orderId, Merchant merchant) {
		logger.info("Received status check request for orderId:{}", orderId);
		try {
			LoanPaymentOrder order = loanPaymentOrderDao.findByOrderId(orderId);
			if (order == null || !order.getMerchant().getId().equals(merchant.getId())) {
				logger.info("No order found for orderId:{}", orderId);
				return new PaymentStatusResponseDTO(false, "Order not found");
			}
			if("PENDING".equalsIgnoreCase(order.getStatus())) {
				logger.info("pg status check for merchant id {} and order id {}", order.getMerchant().getId(), order.getOrderId());
				PgStatusResponse response = apiGatewayService.checkPgStatus(order.getOrderId());
				if (response != null && response.getStatusCode() != null && "200".equalsIgnoreCase(response.getStatusCode()) && Objects.nonNull(response.getData()) && "SUCCESS".equalsIgnoreCase(response.getData().getPaymentStatus())) {
					logger.info("Pg txn Status SUCCESS for orderId:{}", order.getOrderId());
					handlePgCallback(response.getData());
					order = loanPaymentOrderDao.findByOrderId(orderId);
				} else if (response != null && response.getStatusCode() != null && "200".equalsIgnoreCase(response.getStatusCode()) && Objects.nonNull(response.getData()) && (Status.TransactionStatus.FAILED.name().equalsIgnoreCase(response.getData().getPaymentStatus()) || Status.TransactionStatus.CANCELLED.name().equalsIgnoreCase(response.getData().getPaymentStatus()))) {
					order.setStatus(response.getData().getPaymentStatus());
					loanPaymentOrderDao.save(order);
					logger.info("Pg txn Status FAILED/CANCELLED for orderId:{}", order.getOrderId());
				}
			}

			return new PaymentStatusResponseDTO(order.getStatus(), orderId, order.getAmount(), order.getBankRefNo(), order.getUpdatedAt());
		} catch (Exception e) {
			logger.error("Exception in payment status check", e);
			return new PaymentStatusResponseDTO(false, "Something went wrong");
		}
	}

	public ResponseDTO getPaymentModes(RequestDTO<CreditSpendRequestDTO> requestDTO, String token) {
		if (requestDTO.getPayload().getAmount() > 100000) {
			ResponseDTO responseDTO = new ResponseDTO();
			List<PaymentDetailDto> paymentDetails = new ArrayList<PaymentDetailDto>(){{add(getBankTransferMode());}};
			responseDTO.setSuccess(true);
			responseDTO.setData(paymentDetails);
			return responseDTO;
		}
		List<PaymentDetailDto> paymentDetails = apiGatewayService.getPaymentModes(requestDTO, token);
		for (PaymentDetailDto paymentDetail : paymentDetails) {
			if (paymentDetail.getPsps() != null && !paymentDetail.getPsps().isEmpty()) {
				paymentDetail.getPsps().removeIf(psps -> psps.equalsIgnoreCase("com.phonepe.app"));
			}
		}
		paymentDetails.add(getBankTransferMode());
		paymentDetails.add(getGPAYMode());
		paymentDetails.add(getPhonePeMode());
		ResponseDTO responseDTO = new ResponseDTO();
		paymentDetails.removeIf(paymentDetailDto -> (paymentDetailDto.getBalance() != null && paymentDetailDto.getBalance() < requestDTO.getPayload().getAmount()));
		if (paymentDetails.isEmpty()) {
			responseDTO.setSuccess(false);
			responseDTO.setMessage("No Payment Mode Found");
		} else {
			responseDTO.setSuccess(true);
			responseDTO.setData(paymentDetails);
		}
		return responseDTO;
	}

	private PaymentDetailDto getBankTransferMode() {
		PaymentDetailDto paymentDetailDto = new PaymentDetailDto();
		paymentDetailDto.setName("Pay by Account Transfer");
		paymentDetailDto.setType("BT");
		paymentDetailDto.setFundSource("BT");
		paymentDetailDto.setAuthRequired(false);
		paymentDetailDto.setEnable(true);
		paymentDetailDto.setInitiate_sb(false);
		paymentDetailDto.setDefault(false);
		return paymentDetailDto;
	}

	private PaymentDetailDto getGPAYMode() {
		PaymentDetailDto paymentDetailDto = new PaymentDetailDto();
		paymentDetailDto.setName("Pay Using Google Pay");
		paymentDetailDto.setType("UPI");
		paymentDetailDto.setFundSource("UPI");
		paymentDetailDto.setAmountLimit(100000D);
		paymentDetailDto.setAuthRequired(false);
		paymentDetailDto.setEnable(true);
		paymentDetailDto.setInitiate_sb(false);
		paymentDetailDto.setDefault(false);
		return paymentDetailDto;
	}

	private PaymentDetailDto getPhonePeMode() {
		PaymentDetailDto paymentDetailDto = new PaymentDetailDto();
		paymentDetailDto.setName("Pay Using PhonePe");
		paymentDetailDto.setType("UPI");
		paymentDetailDto.setFundSource("UPI");
		paymentDetailDto.setAmountLimit(100000D);
		paymentDetailDto.setAuthRequired(false);
		paymentDetailDto.setEnable(true);
		paymentDetailDto.setInitiate_sb(false);
		paymentDetailDto.setDefault(false);
		return paymentDetailDto;
	}

	public ResponseDTO resendOTP(RequestDTO<PaymentResendOTP> requestDTO, Merchant merchant, String token) {
		LoanPaymentOrder loanPaymentOrder = loanPaymentOrderDao.findByOrderId(requestDTO.getPayload().getOrderId());
		if (loanPaymentOrder == null) {
			return new ResponseDTO(false, "Order not found");
		}
		Map<String, Object> result = apiGatewayService.sendOTP(requestDTO, token);
		Boolean success = (Boolean) result.get("success");
		if (success) {
			return new ResponseDTO(true, "success");
		}
		return new ResponseDTO(false, "Unable to resend otp");
	}

	private void adjustLoanBalance(LendingPaymentSchedule activeLoan, Double amount, String bankRefNo, String source) {
		logger.info("Adjusting Balance for loanId:{} and amount:{}", activeLoan.getId(), amount);
		Integer principalDueAmount = (int) Math.ceil(activeLoan.getLoanAmount() - (activeLoan.getPaidPrinciple() != null ? activeLoan.getPaidPrinciple() : 0) + (activeLoan.getDueInterest() != null ? activeLoan.getDueInterest() : 0));
		Integer ediHolidayInterestAmount = getEDIHolidayInterestAmount(activeLoan);

		Double paidInterestAmount = 0D;
		Double paidPrincipalAmount = 0D;
		boolean preclosure = false;
		logger.info("Preclosure amount for loanId:{} is:{}", activeLoan.getId(), (principalDueAmount + ediHolidayInterestAmount));
		if(principalDueAmount + ediHolidayInterestAmount - amount <= 1D) {
			logger.info("Received pre closure amount:{} for loan:{}", amount, activeLoan.getId());
			paidInterestAmount = (activeLoan.getDueInterest() != null ? activeLoan.getDueInterest() : 0) + ediHolidayInterestAmount;
			paidPrincipalAmount = amount - paidInterestAmount;
			double extraPrinciple = (activeLoan.getPaidPrinciple() + paidPrincipalAmount) - activeLoan.getLoanAmount();
			if (extraPrinciple > 0) {
				logger.info("Extra principle received for loanId:{} and extra amount:{}", activeLoan.getId(), extraPrinciple);
				paidPrincipalAmount -= extraPrinciple;
				paidInterestAmount += extraPrinciple;
			}
			logger.info("Adjusted breakup amount for loan:{} is principle:{} and interest:{}", activeLoan.getId(), paidPrincipalAmount, paidInterestAmount);
			if(activeLoan.getDueAmount() >= 0) {
				createLendingLedger(activeLoan, -1 * Math.abs(amount - activeLoan.getDueAmount()) , -1 * Math.abs(amount - activeLoan.getDueAmount() - ediHolidayInterestAmount), Double.valueOf(ediHolidayInterestAmount), "PREPAYMENT", source);
			} else {
				createLendingLedger(activeLoan, -1 * amount , -1 * amount - ediHolidayInterestAmount, Double.valueOf(ediHolidayInterestAmount), "PREPAYMENT", source);
			}

			activeLoan.setPaidAmount(activeLoan.getPaidAmount() + amount);
			activeLoan.setPaidInterest((activeLoan.getPaidInterest() != null ? activeLoan.getPaidInterest() : 0) + paidInterestAmount);
			activeLoan.setPaidPrinciple((activeLoan.getPaidPrinciple() != null ? activeLoan.getPaidPrinciple() : 0) + paidPrincipalAmount);

			activeLoan.setDueAmount(0D);
			activeLoan.setDueInterest(0D);
			activeLoan.setDuePrinciple(0D);

			activeLoan.setStatus("CLOSED");
			activeLoan.setClosingDate(new Date());
			preclosure = true;
		} else {
			double balance=amount;
			if(balance>0D && activeLoan.getDueOtherCharges()!=null && activeLoan.getDueOtherCharges()>0D) {
				Double paidAmount=balance>=activeLoan.getDueOtherCharges()?activeLoan.getDueOtherCharges():balance;
				activeLoan.setDueOtherCharges(activeLoan.getDueOtherCharges()-paidAmount);
				activeLoan.setDueAmount(activeLoan.getDueAmount()-paidAmount);
				activeLoan.setPaidAmount(activeLoan.getPaidAmount()+paidAmount);
				activeLoan.setPaidOtherCharges(activeLoan.getPaidOtherCharges()+paidAmount);
				balance-=paidAmount;
				logger.info("Adjusted due other charges of amount:{} for loan:{}", paidAmount, activeLoan.getId());
			}
			if(balance>0D && activeLoan.getDuePenalty()!=null && activeLoan.getDuePenalty()>0D) {
				Double paidAmount=balance>=activeLoan.getDuePenalty()?activeLoan.getDuePenalty():balance;
				activeLoan.setDuePenalty(activeLoan.getDuePenalty()-paidAmount);
				activeLoan.setDueAmount(activeLoan.getDueAmount()-paidAmount);
				activeLoan.setPaidAmount(activeLoan.getPaidAmount()+paidAmount);
				activeLoan.setPaidPenalty(activeLoan.getPaidPenalty()+paidAmount);
				balance-=paidAmount;
				logger.info("Adjusted due penalty of amount:{} for loan:{}", paidAmount, activeLoan.getId());
			}
			if(balance>0D && activeLoan.getDueInterest()!=null && activeLoan.getDueInterest()>0D) {
				Double paidAmount=balance>=activeLoan.getDueInterest()?activeLoan.getDueInterest():balance;
				activeLoan.setDueInterest(activeLoan.getDueInterest()-paidAmount);
				activeLoan.setDueAmount(activeLoan.getDueAmount()-paidAmount);
				activeLoan.setPaidInterest((activeLoan.getPaidInterest() != null ? activeLoan.getPaidInterest() : 0)+paidAmount);
				activeLoan.setPaidAmount(activeLoan.getPaidAmount()+paidAmount);
				paidInterestAmount+=paidAmount;
				balance-=paidAmount;
				logger.info("Adjusted due interest of amount:{} for loan:{}", paidAmount, activeLoan.getId());
			}
			if(balance>0D && activeLoan.getDuePrinciple()!=null && activeLoan.getDuePrinciple()>0D) {
				Double paidAmount=balance>=activeLoan.getDuePrinciple()?activeLoan.getDuePrinciple():balance;
				activeLoan.setDuePrinciple(activeLoan.getDuePrinciple()-paidAmount);
				activeLoan.setDueAmount(activeLoan.getDueAmount()-paidAmount);
				activeLoan.setPaidPrinciple((activeLoan.getPaidPrinciple() != null ? activeLoan.getPaidPrinciple() : 0)+paidAmount);
				activeLoan.setPaidAmount(activeLoan.getPaidAmount()+paidAmount);
				paidPrincipalAmount+=paidAmount;
				balance-=paidAmount;
				logger.info("Adjusted due principle of amount:{} for loan:{}", paidAmount, activeLoan.getId());
			}
			if(balance > 0D) {
				logger.info("Adjusting extra amount:{} for loan:{}", balance, activeLoan.getId());
				int adjustedEdiCount = 0;
				int adjustedIOEdiCount = 0;
				double extraAmount = 0d;
				double principle = 0d;
				double interest = 0d;
				List<LendingAdjustedEDISchedule> lendingAdjustedEDISchedules = lendingAdjustedEDIScheduleDao.findByLoanId(activeLoan.getId());
				if (!lendingAdjustedEDISchedules.isEmpty()) {
					logger.info("Found adjusted edi schedule for loan:{}", activeLoan.getId());
					lendingAdjustedEDISchedules.sort(Comparator.comparing(LendingAdjustedEDISchedule::getInstallmentNumber));
					int ediPaidCount = lendingAdjustedEDISchedules.size() - activeLoan.getEdiRemainingCount();
					logger.info("Edi Paid count:{} for loan:{}", ediPaidCount, activeLoan.getId());
					for (LendingAdjustedEDISchedule ediSchedule : lendingAdjustedEDISchedules) {
						if (balance <= 0d) {
							break;
						}
						if (ediSchedule.getInstallmentNumber() <= ediPaidCount) {
							continue;
						}
						if (balance >= ediSchedule.getTotalEdi()) {
							balance -= ediSchedule.getTotalEdi();
							principle += ediSchedule.getPrinciple();
							interest += ediSchedule.getInterest();
							adjustedEdiCount++;
						} else if (balance <= ediSchedule.getInterest()) {
							interest += balance;
							extraAmount += balance;
							balance = 0d;
						} else {
							interest += ediSchedule.getInterest();
							principle += balance - ediSchedule.getInterest();
							extraAmount += balance;
							balance = 0d;
						}
					}
				} else {
					List<LendingEDISchedule> ediSchedules = lendingEDIScheduleDao.findByLendingPaymentSchedule(activeLoan);
					ediSchedules.sort(Comparator.comparing(LendingEDISchedule::getInstallmentNumber));
					int ediPaidCount = activeLoan.getEdiCount() - activeLoan.getEdiRemainingCount();
					if (activeLoan.getInterestOnlyEdiCount() != null && activeLoan.getInterestOnlyEdiCount() > 0 && activeLoan.getRemainingInterestOnlyEdiCount() != null) {
						ediPaidCount = (activeLoan.getInterestOnlyEdiCount() + activeLoan.getEdiCount()) - (activeLoan.getRemainingInterestOnlyEdiCount() + activeLoan.getEdiRemainingCount());
					}
					logger.info("Edi Paid count:{} for loan:{}", ediPaidCount, activeLoan.getId());
					for (LendingEDISchedule ediSchedule : ediSchedules) {
						if (balance <= 0d) {
							break;
						}
						if (ediSchedule.getInstallmentNumber() <= ediPaidCount) {
							continue;
						}
						if (balance >= ediSchedule.getTotalEdi()) {
							balance -= ediSchedule.getTotalEdi();
							principle += ediSchedule.getPrinciple();
							interest += ediSchedule.getInterest();
							if (ediSchedule.getEdiType() != null && ediSchedule.getEdiType().equalsIgnoreCase("Principal Morat")) {
								adjustedIOEdiCount++;
							} else {
								adjustedEdiCount++;
							}
						} else if (balance <= ediSchedule.getInterest()) {
							interest += balance;
							extraAmount += balance;
							balance = 0d;
						} else {
							interest += ediSchedule.getInterest();
							principle += balance - ediSchedule.getInterest();
							extraAmount += balance;
							balance = 0d;
						}
					}
				}
				logger.info("Adjusted principle:{} and interest:{} for loan:{}", principle, interest, activeLoan.getId());
				paidPrincipalAmount += principle;
				paidInterestAmount += interest;

				if (amount > (paidPrincipalAmount + paidInterestAmount)) {
					double remainingAmount = amount - (paidPrincipalAmount + paidInterestAmount);
					logger.info("Balance remaining:{} for loan:{} after adjustment, adjusting this in principle", remainingAmount, activeLoan.getId());
					principle += remainingAmount;
					paidPrincipalAmount += remainingAmount;
				}
				if (activeLoan.getRemainingInterestOnlyEdiCount() != null && adjustedIOEdiCount > 0) {
					activeLoan.setRemainingInterestOnlyEdiCount(activeLoan.getRemainingInterestOnlyEdiCount() - adjustedIOEdiCount);
				}
				activeLoan.setEdiRemainingCount(activeLoan.getEdiRemainingCount() - adjustedEdiCount);
				activeLoan.setAdjustedPaidAmount(activeLoan.getAdjustedPaidAmount() != null ? activeLoan.getAdjustedPaidAmount() + extraAmount : extraAmount);
				activeLoan.setPaidAmount(activeLoan.getPaidAmount() + principle + interest);
				activeLoan.setPaidPrinciple(activeLoan.getPaidPrinciple() + principle);
				activeLoan.setPaidInterest(activeLoan.getPaidInterest() + interest);
				createLendingLedger(activeLoan, -1*(principle + interest), -1*principle, -1*interest, "PREPAYMENT", source);
				int extraEdiCount = activeLoan.getAdjustedPaidAmount() != null ? (int) (activeLoan.getAdjustedPaidAmount()/activeLoan.getEdiAmount()) : 0;
				if (extraEdiCount > 0) {
					activeLoan.setEdiRemainingCount(activeLoan.getEdiRemainingCount() - extraEdiCount);
					activeLoan.setAdjustedPaidAmount(activeLoan.getAdjustedPaidAmount() % activeLoan.getEdiAmount());
				}
				if (activeLoan.getEdiRemainingCount() == 0 && activeLoan.getAdjustedDueAmount() != null && activeLoan.getAdjustedDueAmount() > 0D) {
					int newScheduleCount = createAdjustedSchedule(activeLoan, activeLoan.getAdjustedDueAmount());
					activeLoan.setEdiRemainingCount(activeLoan.getEdiRemainingCount() + newScheduleCount);
					activeLoan.setAdjustedDueAmount(0D);
				}
			}
		}
		logger.info("Adjusted breakup amount for loan:{} is principle:{} and interest:{}", activeLoan.getId(), paidPrincipalAmount, paidInterestAmount);
		createLendingLedger(activeLoan, amount, paidPrincipalAmount, paidInterestAmount,  getDescription(bankRefNo, preclosure), source);
		lendingPaymentScheduleDao.save(activeLoan);
		if (activeLoan.getLoanApplication() != null && activeLoan.getLoanApplication().getProcessingFee() != null && activeLoan.getLoanApplication().getProcessingFee() > 0) {
			redisNotificationService.sendRepaymentNudge(activeLoan.getMerchant(), activeLoan.getLoanApplication().getProcessingFee());
		}
		boolean isLoanClosed = "CLOSED".equalsIgnoreCase(activeLoan.getStatus());

		notificationExecutor.execute(() -> sendSMS(activeLoan.getMerchant(), amount, isLoanClosed));

		if(isLoanClosed) {
			List<String> topupLoans = Arrays.asList(LoanType.TOPUP.name(), LoanType.HALF_TOPUP.name(), LoanType.IO_TOPUP.name());
			notificationExecutor.execute(() -> {
				LoyaltyServiceRequest requestBean = new LoyaltyServiceRequest.LoyaltyServiceRequestBuilder(activeLoan.getMerchant().getId(), LoyaltyTransactionType.PRE_LOAN_CLOSURE)
						.amount(amount)
						.merchantStoreId(null)
						.transactionId(activeLoan.getId())
						.build();
				loyaltyService.pushToKafka(requestBean);
				boolean eligible = apiGatewayService.sendCommunicationForNewOffer(activeLoan);
				if(topupLoans.contains(activeLoan.getLoanApplication().getLoanType())){
					LendingPaymentSchedule topupLoan = lendingPaymentScheduleDao.findTopupLoan(activeLoan.getMerchant().getId());
					if(topupLoan != null) {
						refundProcessingFee(topupLoan,eligible);
					}
				}else{
					refundProcessingFee(activeLoan, eligible);
				}
				if (activeLoan.getDueAmount() < 0) {
					logger.info("Extra amount:{} received for loanId:{}, initiating refund", activeLoan.getDueAmount(), activeLoan.getId());
					refundExtraAmount(activeLoan);
				}
			});
		}
	}

	private void refundExtraAmount(LendingPaymentSchedule lendingPaymentSchedule) {
		try {
			logger.info("Refund due amount:{} for loan:{}", lendingPaymentSchedule.getDueAmount(), lendingPaymentSchedule.getId());
			String orderId = "ECOLLECT_REFUND" + System.currentTimeMillis();
			Double refundAmount = -1 * lendingPaymentSchedule.getDueAmount();
			Double principle = -1 * lendingPaymentSchedule.getDuePrinciple();
			Double interest = -1 * lendingPaymentSchedule.getDueInterest();
			LendingPayoutRequest lendingPayoutRequest = new LendingPayoutRequest(lendingPaymentSchedule.getId(), orderId, refundAmount, LendingPayoutType.LENDING_ECOLLECT_REFUND, lendingPaymentSchedule.getMerchant().getId(), "ECOLLECT_REFUND");
			LendingPayoutResponse lendingPayoutResponse = apiGatewayService.lendingPayout(lendingPayoutRequest);
			if (lendingPayoutResponse != null) {
				String bankRefNo = lendingPayoutResponse.getData() != null ? lendingPayoutResponse.getData().getBankReferenceNo() : null;
				createRefundLedger(lendingPaymentSchedule, DateTimeUtil.getCurrentDayStartTime(), "LOAN_REFUND", -refundAmount, -principle, -interest, 0D, 0D, bankRefNo, "REFUND");
				lendingPaymentSchedule.setDueAmount(lendingPaymentSchedule.getDueAmount() + refundAmount);
				lendingPaymentSchedule.setDueInterest(lendingPaymentSchedule.getDueInterest() + interest);
				lendingPaymentSchedule.setDuePrinciple(lendingPaymentSchedule.getDuePrinciple() + principle);
				lendingPaymentScheduleDao.save(lendingPaymentSchedule);
				MerchantBankDetail merchantBankDetail = merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(lendingPaymentSchedule.getMerchant().getId(),"ACTIVE");

				String identifier = "LENDING_REFUND_2_SMS";
				Map<String,Object> templateParams = new HashMap<>();
				templateParams.put("beneficiary_name",getBeneficiaryName(merchantBankDetail.getBeneficiaryName()));
				templateParams.put("refund_amount",refundAmount);
				templateParams.put("bank_name",merchantBankDetail.getBankName());
				NotificationPayloadDto notificationPayloadDto = new NotificationPayloadDto();
				notificationPayloadDto.setTemplateIdentifier(identifier);
				notificationPayloadDto.setMobile(lendingPaymentSchedule.getMerchant().getMobile());
				notificationPayloadDto.setClientName("LENDING");
				notificationPayloadDto.setTemplateParams(templateParams);
				lendingNotificationService.notify(notificationPayloadDto);
			}
		} catch (Exception e) {
			logger.error("Exception in ECOLLECT Refund for loanId:{}", lendingPaymentSchedule.getId(), e);
		}
	}

	public void createRefundLedger(LendingPaymentSchedule lendingPaymentSchedule, Date date, String txnType, Double amount, Double principle, Double interest, Double otherCharges, Double penalty, String description, String adjustmentMode) {
		LendingLedger lendingLedger = new LendingLedger();
		lendingLedger.setMerchant(lendingPaymentSchedule.getMerchant());
		if(lendingPaymentSchedule.getMerchantStoreId() != null && lendingPaymentSchedule.getMerchantStoreId() > 0){
			lendingLedger.setMerchantStoreId(lendingPaymentSchedule.getMerchantStoreId());
		}
		lendingLedger.setLendingPaymentSchedule(lendingPaymentSchedule);
		lendingLedger.setDate(date);
		lendingLedger.setTxnType(txnType);
		lendingLedger.setAmount(amount);
		lendingLedger.setInterest(interest);
		lendingLedger.setOtherCharges(otherCharges);
		lendingLedger.setPenalty(penalty);
		lendingLedger.setPrinciple(principle);
		lendingLedger.setDescription(description);
		lendingLedger.setAdjustmentMode(adjustmentMode);
		lendingLedgerDao.save(lendingLedger);
	}

	public void refundProcessingFee(LendingPaymentSchedule lendingPaymentSchedule, boolean eligible) {
		try {
			LendingPayouts checkRefunded = lendingPayoutsDao.findTopByMerchantIdAndOwnerIdAndStatusAndOrderIdLikeOrderByIdDesc(lendingPaymentSchedule.getMerchant().getId(),lendingPaymentSchedule.getId());
			if(checkRefunded != null){
				return;
			}
			if (lendingPaymentSchedule.getStatus().equals("CLOSED") && lendingPaymentSchedule.getLoanApplication() != null && lendingPaymentSchedule.getLoanApplication().getProcessingFee() != null && lendingPaymentSchedule.getLoanApplication().getProcessingFee() > 0D) {
				BigInteger maxDpd = loanDpdDao.findMaxDpd(lendingPaymentSchedule.getId());
				long dpd = LoanUtil.getDateDiffInDays(lendingPaymentSchedule.getTentativeClosingDate(), lendingPaymentSchedule.getClosingDate());
				LendingLedger lendingLedger = lendingLedgerDao.getForClosedLedger(lendingPaymentSchedule.getId());
				if (maxDpd.intValue() <= 5 &&  dpd <= 5 && (dpd >= -5 || Objects.isNull(lendingLedger))) {
					logger.info("Closing dpd is between 5 days for loanId:{}, processing fee refund for amount:{}", lendingPaymentSchedule.getId(), lendingPaymentSchedule.getLoanApplication().getProcessingFee());
					Double cashbackAmount = lendingPaymentSchedule.getLoanApplication().getProcessingFee();
					String orderId = "PF_CASHBACK" + System.currentTimeMillis();
					LendingPayoutRequest lendingPayoutRequest = new LendingPayoutRequest(lendingPaymentSchedule.getId(), orderId, cashbackAmount, LendingPayoutType.LENDING_INCENTIVE, lendingPaymentSchedule.getMerchant().getId(), "PF_CASHBACK");
					LendingPayoutResponse lendingPayoutResponse = apiGatewayService.lendingPayout(lendingPayoutRequest);
					if (lendingPayoutResponse != null) {
						MerchantBankDetail merchantBankDetail = merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(lendingPaymentSchedule.getMerchant().getId(),"ACTIVE");

						String identifier = "LENDING_ARRANGER_REFUND_2_SMS";
						Map<String,Object> templateParams = new HashMap<>();
						templateParams.put("beneficiary_name",getBeneficiaryName(merchantBankDetail.getBeneficiaryName()));
						templateParams.put("cashback_amount",merchantBankDetail.getBeneficiaryName());
						templateParams.put("bank_name",merchantBankDetail.getBeneficiaryName());

						if (eligible) {
							identifier = "LENDING_ARRANGER_REFUND_SMS";
						}

						NotificationPayloadDto notificationPayloadDto = new NotificationPayloadDto();
						notificationPayloadDto.setTemplateIdentifier(identifier);
						notificationPayloadDto.setMobile(lendingPaymentSchedule.getMerchant().getMobile());
						notificationPayloadDto.setClientName("LENDING");
						notificationPayloadDto.setTemplateParams(templateParams);
						lendingNotificationService.notify(notificationPayloadDto);
					}
				}
			}
		} catch (Exception e) {
			logger.error("Exception in PF Refund for loanId:{}", lendingPaymentSchedule.getId(), e);
		}
	}

	public ResponseDTO verifyPayment(RequestDTO<PaymentResendOTP> requestDTO, Merchant merchant, String token) {
		LoanPaymentOrder loanPaymentOrder = loanPaymentOrderDao.findByOrderId(requestDTO.getPayload().getOrderId());
		if (loanPaymentOrder == null) {
			return new ResponseDTO(false, "Order not found");
		}
		if(!"PENDING".equalsIgnoreCase(loanPaymentOrder.getStatus())) {
			logger.info("Payment for merchant id {} and order id {} is already processed", loanPaymentOrder.getMerchant().getId(), loanPaymentOrder.getOrderId());
			return new ResponseDTO(false, "Duplicate request");
		}
		Optional<LendingPaymentSchedule> activeLoan = lendingPaymentScheduleDao.findById(loanPaymentOrder.getOwnerId());
		if(!activeLoan.isPresent()) {
			logger.error("No active loan found for id {}", loanPaymentOrder.getOwnerId());
			return new ResponseDTO(false, "Active Loan not found");
		}
		try {
			Map<String, Object> result = apiGatewayService.verifyTxn(requestDTO, token);
			Boolean success = (Boolean) result.get("success");
			if (success) {
				Double paymentAmount = (Double) result.get("amount");
				String paymentStatus = (String) result.get("status");
				String orderId = (String) result.get("order_id");
				if (CreditConstants.PaymentStatus.FAILED.name().equals(paymentStatus) || !orderId.equals(loanPaymentOrder.getOrderId()) || !loanPaymentOrder.getAmount().equals(paymentAmount)) {
					loanPaymentOrder.setStatus("FAILED");
					loanPaymentOrderDao.save(loanPaymentOrder);
				} else if (CreditConstants.PaymentStatus.SUCCESS.name().equals(paymentStatus)) {
					adjustLoanBalance(activeLoan.get(), loanPaymentOrder.getAmount(), null, loanPaymentOrder.getSource());
					loanPaymentOrder.setStatus("SUCCESS");
					loanPaymentOrderDao.save(loanPaymentOrder);
				}
				return new ResponseDTO(true, "success");
			} else {
				logger.info("BPB verification failed for loan payment order:{}", loanPaymentOrder.getOrderId());
				loanPaymentOrder.setStatus("FAILED");
				loanPaymentOrderDao.save(loanPaymentOrder);
			}
		} catch (Exception e) {
			logger.error("Exception in payment verify", e);
		}
		return new ResponseDTO(false, "Payment verification Failed");
	}

	public int createAdjustedSchedule(LendingPaymentSchedule lendingPaymentSchedule, double amount) {
		try {
			logger.info("Creating Adjusted Edi Schedule for loan:{} and amount:{}", lendingPaymentSchedule.getId(), amount);
			if (amount <= 0) {
				logger.error("Adjusted Amount is less than 0");
				return 0;
			}
			List<LendingAdjustedEDISchedule> ediScheduleList = new ArrayList<>();
			int installmentNo = 1;
			Calendar calendar = Calendar.getInstance();
			calendar.add(Calendar.DAY_OF_MONTH, 1);
			int ediCount = (int) (amount/lendingPaymentSchedule.getEdiAmount());
			double extraAmount = amount % lendingPaymentSchedule.getEdiAmount();
			while (installmentNo <= ediCount) {
				if (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
					calendar.add(Calendar.DAY_OF_MONTH, 1);
					continue;
				}
				LendingAdjustedEDISchedule ediSchedule = new LendingAdjustedEDISchedule();
				ediSchedule.setMerchantId(lendingPaymentSchedule.getMerchant().getId());
				ediSchedule.setMerchantStoreId(lendingPaymentSchedule.getMerchantStoreId());
				ediSchedule.setLoanId(lendingPaymentSchedule.getId());
				ediSchedule.setApplicationId(lendingPaymentSchedule.getLoanApplication() != null ? lendingPaymentSchedule.getLoanApplication().getId() : null);
				ediSchedule.setDate(calendar.getTime());
				ediSchedule.setInstallmentNumber(installmentNo);
				ediSchedule.setTotalEdi(lendingPaymentSchedule.getEdiAmount());
				ediSchedule.setPrinciple(lendingPaymentSchedule.getEdiAmount());
				ediSchedule.setInterest(0D);
				ediScheduleList.add(ediSchedule);
				calendar.add(Calendar.DAY_OF_MONTH, 1);
				installmentNo++;
			}
			if (extraAmount > 0) {
				if (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
					calendar.add(Calendar.DAY_OF_MONTH, 1);
				}
				LendingAdjustedEDISchedule ediSchedule = new LendingAdjustedEDISchedule();
				ediSchedule.setMerchantId(lendingPaymentSchedule.getMerchant().getId());
				ediSchedule.setMerchantStoreId(lendingPaymentSchedule.getMerchantStoreId());
				ediSchedule.setLoanId(lendingPaymentSchedule.getId());
				ediSchedule.setApplicationId(lendingPaymentSchedule.getLoanApplication() != null ? lendingPaymentSchedule.getLoanApplication().getId() : null);
				ediSchedule.setDate(calendar.getTime());
				ediSchedule.setInstallmentNumber(installmentNo);
				ediSchedule.setTotalEdi(extraAmount);
				ediSchedule.setPrinciple(extraAmount);
				ediSchedule.setInterest(0D);
				ediScheduleList.add(ediSchedule);
			}
			if (!ediScheduleList.isEmpty()) {
				lendingAdjustedEDIScheduleDao.saveAll(ediScheduleList);
				return ediScheduleList.size();
			}
		} catch (Exception e) {
			logger.error("Exception while creating adjusted schedule", e);
		}
		return 0;
	}

	private String getBeneficiaryName(String beneficiaryName) {
		if(beneficiaryName.length() > 25) {
			beneficiaryName = beneficiaryName.substring(0,25);
		}
		return beneficiaryName;
	}


}