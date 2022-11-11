package com.bharatpe.lending.service;

import com.bharatpe.common.dao.LendingEDIScheduleDao;
import com.bharatpe.common.entities.LendingEDISchedule;
import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.common.enums.LoyaltyTransactionType;
import com.bharatpe.common.enums.Status;
import com.bharatpe.common.objects.LoyaltyServiceRequest;
import com.bharatpe.common.service.LoyaltyService;
import com.bharatpe.common.utils.NotificationUtil;
import com.bharatpe.lending.common.Handler.EnachHandler;
import com.bharatpe.lending.common.Handler.LendingPayoutsHandler;
import com.bharatpe.lending.common.dao.LendingAdjustedEDIScheduleDao;
import com.bharatpe.lending.common.dao.LendingInterestWaiverDao;
import com.bharatpe.lending.common.dao.LendingPrepaymentAuditDao;
import com.bharatpe.lending.common.dao.LendingPrepaymentDao;
import com.bharatpe.lending.common.dao.LoanDpdDao;
import com.bharatpe.lending.common.dto.LendingPayoutResponseDTO;
import com.bharatpe.lending.common.dto.NotificationPayloadDto;
import com.bharatpe.lending.common.entity.LendingAdjustedEDISchedule;
import com.bharatpe.lending.common.entity.LendingInterestWaiver;
import com.bharatpe.lending.common.entity.LendingPrepayment;
import com.bharatpe.lending.common.entity.LendingPrepaymentAudit;
import com.bharatpe.lending.common.entity.LendingVirtualAccount;
import com.bharatpe.lending.common.service.LendingNotificationService;
import com.bharatpe.lending.common.service.merchant.constants.Constants;
import com.bharatpe.lending.common.service.merchant.dto.BankDetailsDto;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.dto.MerchantDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.constant.CreditConstants;
import com.bharatpe.lending.dao.LendingLedgerDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dao.LoanPaymentOrderDao;
import com.bharatpe.lending.dto.CreditSpendRequestDTO;
import com.bharatpe.lending.dto.InitiatePaymentRequestDTO;
import com.bharatpe.lending.dto.InitiatePaymentResponseDTO;
import com.bharatpe.lending.dto.LendingPayoutRequest;
import com.bharatpe.lending.dto.LendingPayoutResponse;
import com.bharatpe.lending.dto.LoanRefundsResponseDTO;
import com.bharatpe.lending.dto.PaymentDetailDto;
import com.bharatpe.lending.dto.PaymentDetailsResponseDTO;
import com.bharatpe.lending.dto.PaymentResendOTP;
import com.bharatpe.lending.dto.PaymentStatusResponseDTO;
import com.bharatpe.lending.dto.PaymentStatusV3ResponseDTO;
import com.bharatpe.lending.dto.PgCreateTransactionRequestDTO;
import com.bharatpe.lending.dto.PgCreateTransactionResponseDTO;
import com.bharatpe.lending.dto.PgPaymentCallbackDTO;
import com.bharatpe.lending.dto.PgStatusResponse;
import com.bharatpe.lending.dto.RequestDTO;
import com.bharatpe.lending.dto.ResponseDTO;
import com.bharatpe.lending.entity.LoanPaymentOrder;
import com.bharatpe.lending.enums.*;
import com.bharatpe.lending.util.LoanUtil;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

@Service
public class PaymentService {

	Logger logger = LoggerFactory.getLogger(PaymentService.class);
	
	@Autowired
	LendingPaymentScheduleDao lendingPaymentScheduleDao;
	
	@Autowired
	APIGatewayService apiGatewayService;

	@Autowired
	LendingPayoutsHandler lendingPayoutsHandler;

	@Autowired
	LendingLedgerDao lendingLedgerDao;
	
	@Autowired
	LoyaltyService loyaltyService;

//	@Autowired
//	MerchantDao merchantDao;

	@Autowired
	LoanPaymentOrderDao loanPaymentOrderDao;
	
	@Autowired
	LendingEDIScheduleDao lendingEDIScheduleDao;

	@Autowired
	RedisNotificationService redisNotificationService;

	@Autowired
	LendingAdjustedEDIScheduleDao lendingAdjustedEDIScheduleDao;

	@Autowired
	LoanDpdDao loanDpdDao;

	@Autowired
	LendingNotificationService lendingNotificationService;

	@Autowired
	LendingPrepaymentDao lendingPrepaymentDao;

	@Autowired
	LendingPrepaymentAuditDao lendingPrepaymentAuditDao;

	@Autowired
    LoanUtil loanUtil;

	@Autowired
	NotificationUtil notificationUtil;

	@Autowired
	LendingInterestWaiverDao lendingInterestWaiverDao;

	ExecutorService notificationExecutor = Executors.newFixedThreadPool(10);

	SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	@Autowired
	MerchantService merchantService;

	public PaymentDetailsResponseDTO getPaymentDetails(BasicDetailsDto merchant) {
		logger.info("Received payment details request for merchant id {}", merchant.getId());
		try {
			
			LendingPaymentSchedule activeLoan = lendingPaymentScheduleDao.findByMerchantIdAndStatus(merchant.getId(), "ACTIVE");
			
			if(activeLoan == null) {
				logger.info("No active loan found for merchant id {}", merchant.getId());
				return new PaymentDetailsResponseDTO("No active loan found.");
			}
			LendingPrepayment lendingPrepayment = lendingPrepaymentDao.findByMerchantIdAndLoanId(activeLoan.getMerchantId(), activeLoan.getId());
			double advanceEdiAmount = lendingPrepayment != null && lendingPrepayment.getAdvanceEdiAmount() != null ? lendingPrepayment.getAdvanceEdiAmount() : 0d;
			Integer loanAmount = activeLoan.getLoanAmount().intValue();
			Integer overdueAmount = activeLoan.getDueAmount().intValue();
			Integer overdueDays = (activeLoan.getDueAmount().intValue()/activeLoan.getEdiAmount().intValue());
			Integer principalDueAmount = loanUtil.getForeclosureAmount(activeLoan);
			Integer ediHolidayInterestAmount = getEDIHolidayInterestAmount(activeLoan);
			
			boolean isPayable = true;
			if(overdueDays < 2) {
				isPayable = false;
			}
			if (activeLoan.getTentativeClosingDate().before(new Date())) {
				double totalPayable = activeLoan.getEdiAmount() * activeLoan.getEdiCount();
				int extraAmount = (int)Math.ceil(totalPayable - (activeLoan.getPaidAmount() + principalDueAmount + advanceEdiAmount));
				if (extraAmount > 0d) {
					logger.info("Need to get extra amount:{} for loanId:{}", extraAmount, activeLoan.getId());
					principalDueAmount += extraAmount;//adding extra amount in foreclosure amount
				}
			}
			
			PaymentDetailsResponseDTO.Data data= new PaymentDetailsResponseDTO.Data(loanAmount, overdueAmount, principalDueAmount + ediHolidayInterestAmount, overdueDays, isPayable, activeLoan.getEdiRemainingCount(), activeLoan.getEdiAmount());
			return new PaymentDetailsResponseDTO(data);
			
		} catch(Exception ex) {
			logger.error("Execption while fetching payment details for merchant id {}, Exception is {}", merchant.getId(), ex);
		}
		
		return new PaymentDetailsResponseDTO("Something went wrong.");
	}
	
	public InitiatePaymentResponseDTO initiatePaymentV2(BasicDetailsDto merchantBasicDetails, RequestDTO<InitiatePaymentRequestDTO> request) {
		logger.info("Received initiate payment request  for merchant {} : {}", merchantBasicDetails.getId(), request);
		try {
			LendingPaymentSchedule activeLoan = lendingPaymentScheduleDao.findByMerchantIdAndStatus(merchantBasicDetails.getId(), "ACTIVE");
			if(activeLoan == null) {
				logger.info("No active loan found for merchant id {}", merchantBasicDetails.getId());
				return new InitiatePaymentResponseDTO("No active loan found.");
			}
			Integer amount = request.getPayload().getAmount();
			if(amount < 1 ) {
				logger.info("Amount is less than 1 for merchant id {}", merchantBasicDetails.getId());
				return new InitiatePaymentResponseDTO("Amount is less than 1");
			}
			String paymentType = request.getPayload().getPaymentType();
			if (PaymentType.CUSTOM_AMOUNT.name().equalsIgnoreCase(paymentType) && amount > activeLoan.getDueAmount().intValue()) {
				logger.info("custom amount:{} more than due amount:{} for merchant:{}", amount, activeLoan.getDueAmount().intValue(), merchantBasicDetails.getId());
				return new InitiatePaymentResponseDTO("Custom amount should be less than due amount");
			}
			if (PaymentType.ADVANCE_EDI.name().equalsIgnoreCase(paymentType)) {
				Integer advanceEdiCount = request.getPayload().getAdvanceEdiCount();
				if (advanceEdiCount == null) {
					logger.info("advance edi count is not present for merchant:{}", merchantBasicDetails.getId());
					return new InitiatePaymentResponseDTO("Advance edi count not present");
				}
				if (advanceEdiCount > activeLoan.getEdiRemainingCount()) {
					logger.info("advance edi count is more than remaining edi count for merchant:{}", merchantBasicDetails.getId());
					return new InitiatePaymentResponseDTO("Advance edi count should be less than remaining edi count");
				}
				Integer advanceEdiAmount = activeLoan.getDueAmount().intValue() + (request.getPayload().getAdvanceEdiCount() * activeLoan.getEdiAmount().intValue());
				if (!amount.equals(advanceEdiAmount)) {
					logger.info("advance edi amount:{} is not matching for merchant:{}", advanceEdiAmount, merchantBasicDetails.getId());
					return new InitiatePaymentResponseDTO("Advance edi amount is not correct");
				}
			}
			LoanPaymentOrder order = new LoanPaymentOrder();

			// TODO : remove this and use api
//			Merchant merchant = merchantDao.getById(merchantBasicDetails.getId());

			order.setMerchantId(merchantBasicDetails.getId());

			order.setOwner("lending_payment_schedule");
			order.setOwnerId(activeLoan.getId());
			order.setAmount(Double.valueOf(amount));
			order.setStatus("INIT");
			if (request.getPayload().getSource() != null) {
				order.setSource(request.getPayload().getSource().name());
			}
			if (PaymentType.ADVANCE_EDI.name().equalsIgnoreCase(paymentType)) {
				order.setDescription(PaymentType.ADVANCE_EDI.name());
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
			if (activeLoan.getLoanApplication() != null && !StringUtils.isEmpty(activeLoan.getLoanApplication().getCkycId())) {//new loan flow
				pgCreateTransactionRequestDTO.setRedirectURIDeeplink("bharatpe://dynamic?key=easy-loans&wroute=payment-status&wid="+orderId);
			} else {
				pgCreateTransactionRequestDTO.setRedirectURIDeeplink("bharatpe://dynamic?key=loan&txnID=" + orderId);
			}
			pgCreateTransactionRequestDTO.setAllowedModes(Arrays.asList("CC", "DC","NB","BP","UPI","FP"));
			pgCreateTransactionRequestDTO.setLender(Lender.valueOf(activeLoan.getNbfc()));

			Long appVersion = Objects.nonNull(request.getMeta().getDeviceInfo().getAppVersion()) ? Long.parseLong(request.getMeta().getDeviceInfo().getAppVersion()) : 100L;
			logger.info("app version and client name in pg flow: {} {}",appVersion, request.getMeta().getClient());
			if (loanUtil.isInternalMerchant(merchantBasicDetails.getId())) {
				logger.info("pg flow enabling for internal merchants with app version for merchant: {}",merchantBasicDetails.getId());
				if (Objects.equals(request.getMeta().getClient(), "android")) {
					if (appVersion >= 320) {
						pgCreateTransactionRequestDTO.setCheckout("JUSPAY");
					} else {
						pgCreateTransactionRequestDTO.setCheckout("BHARATPE");
					}
				} else {
					if (appVersion >= 254) {
						pgCreateTransactionRequestDTO.setCheckout("JUSPAY");
					} else {
						pgCreateTransactionRequestDTO.setCheckout("BHARATPE");
					}
				}
			} else {
				pgCreateTransactionRequestDTO.setCheckout("BHARATPE");
			}

			PgCreateTransactionResponseDTO response = apiGatewayService.createPgTransaction(merchantBasicDetails.getId(), pgCreateTransactionRequestDTO);

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
			logger.error("Exception while initiating payment for merchant id {}", merchantBasicDetails.getId(), ex);
		}
		return new InitiatePaymentResponseDTO("Something went wrong.");
	}

	public InitiatePaymentResponseDTO initiatePayment(BasicDetailsDto merchantBasicDetails, RequestDTO<InitiatePaymentRequestDTO> request, String token) {
		logger.info("Received initiate payment request  for merchant {} : {}", merchantBasicDetails.getId(), request);
		try {
			LendingPaymentSchedule activeLoan = lendingPaymentScheduleDao.findByMerchantIdAndStatus(merchantBasicDetails.getId(), "ACTIVE");
			if(activeLoan == null) {
				logger.info("No active loan found for merchant id {}", merchantBasicDetails.getId());
				return new InitiatePaymentResponseDTO("No active loan found.");
			}
			if (request.getPayload().getType() != null && request.getPayload().getType().equals(CreditConstants.PaymentMode.BT)) {
				LendingVirtualAccount lendingVirtualAccount = apiGatewayService.createLendingVAN(merchantBasicDetails.getId(), activeLoan.getId());
				if (lendingVirtualAccount != null) {
					final Optional<BankDetailsDto> bankDetailsDtoOptional = merchantService.fetchMerchantBankDetails(merchantBasicDetails.getId());
					BankDetailsDto merchantBankDetail = null;
					if (bankDetailsDtoOptional.isPresent())
						merchantBankDetail = bankDetailsDtoOptional.get();
					InitiatePaymentResponseDTO.Data data = new InitiatePaymentResponseDTO.Data(null, null, null, null, null, null, lendingVirtualAccount.getAccountNumber(), lendingVirtualAccount.getIfsc(), merchantBankDetail.getBeneficiaryName());
					return new InitiatePaymentResponseDTO(data);
				}
				return new InitiatePaymentResponseDTO("Something went wrong.");
			}
			Integer overdueAmount = activeLoan.getDueAmount().intValue();
			Integer principalDueAmount = loanUtil.getForeclosureAmount(activeLoan);
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
				logger.info("Amount not between 1-100000 for merchant id {}", merchantBasicDetails.getId());
				return new InitiatePaymentResponseDTO("Amount should be between 1-100000.");
			}
			if (amount > 2000 && request.getPayload().getVpa() == null && request.getPayload().getType() == null) {
				logger.info("VPA missing for merchant id {}", merchantBasicDetails.getId());
				return new InitiatePaymentResponseDTO("VPA missing");
			}

			LoanPaymentOrder order = new LoanPaymentOrder();

			// TODO : remove this and use api
//			Merchant merchant = merchantDao.getById(merchantBasicDetails.getId());
			order.setMerchantId(merchantBasicDetails.getId());

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
				Map vpaResponse = apiGatewayService.createVPA(merchantBasicDetails, Double.valueOf(amount), orderId, request.getPayload().getVpa());
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
			logger.error("Exception while initiating payment for merchant id {}", merchantBasicDetails.getId(), ex);
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
				logger.info("Payment for merchant id {} and order id {} is already processed", order.getMerchantId(), request.getOrderId());
				return "OK";
			}
			if(request.getAmount() == null || request.getAmount() <= 0D) {
				logger.error("Invalid amount received for merchant {} and amount {}", order.getMerchantId(), request.getAmount());
				return "OK";
			}
			Optional<LendingPaymentSchedule> activeLoan = lendingPaymentScheduleDao.findById(order.getOwnerId());
			if(!activeLoan.isPresent()) {
				logger.error("No active loan found for id {}", order.getOwnerId());
				return "OK";
			}
			if(order.getAmount()  - request.getAmount() < -1 || order.getAmount() - request.getAmount() > 1) { 
				logger.error("Amount mismatch for the merchant {} and order id {}", order.getMerchantId(), request.getOrderId());
				order.setStatus("FAILED");
				order.setDescription("Amount mismatch");
				loanPaymentOrderDao.save(order);
				return "OK";
			}
			adjustLoanBalance(activeLoan.get(), request.getAmount(), request.getBankReferenceNumber(), order.getSource(), PaymentType.ADVANCE_EDI.name().equalsIgnoreCase(order.getDescription()), null);
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
				logger.info("Payment for merchant id {} and order id {} is already processed", order.getMerchantId(), request.getOrderId());
				return "OK";
			}

			int lockTxn = loanPaymentOrderDao.updateStatusForPendingTxn(CreditConstants.PaymentStatus.CALLBACK_RECEIVED.name(), order.getId());
			if (lockTxn != 1) {
				logger.info("Unable to take lock on loan payment order:{} ", order.getId());
				return "OK";
			}

			if(request.getPaymentAmount() == null || request.getPaymentAmount() <= 0D) {
				logger.error("Invalid amount received for merchant {} and amount {}", order.getMerchantId(), request.getPaymentAmount());
				return "OK";
			}
			Optional<LendingPaymentSchedule> activeLoan = lendingPaymentScheduleDao.findById(order.getOwnerId());
			if(!activeLoan.isPresent()) {
				logger.error("No active loan found for id {}", order.getOwnerId());
				return "OK";
			}
			if(order.getAmount()  - request.getPaymentAmount() < -1 || order.getAmount() - request.getPaymentAmount() > 1) {
				logger.error("Amount mismatch for the merchant {} and order id {}", order.getMerchantId(), request.getOrderId());
				order.setStatus("FAILED");
				order.setDescription("Amount mismatch");
				loanPaymentOrderDao.save(order);
				return "OK";
			}
			if(Objects.nonNull(request.getPayments()) && !request.getPayments().isEmpty() && Objects.nonNull(request.getPayments().get(0)) && Objects.nonNull(request.getPayments().get(0).getMode())){
				order.setSource(request.getPayments().get(0).getMode());
			}
			if (request.getPaymentStatus() != null && Objects.nonNull(request.getPayments())) {
			    if ("FAILURE".equalsIgnoreCase(request.getPaymentStatus())) {
                    order.setStatus(Status.TransactionStatus.FAILED.name());
//                    order.setDescription(response.getData().getErrorDescription());
                } else {
                    order.setStatus(request.getPaymentStatus());
                }
				if ("SUCCESS".equalsIgnoreCase(request.getPaymentStatus())) {
					String accountType = null;
					if(Objects.nonNull(request.getPayments()) && !request.getPayments().isEmpty() && Objects.nonNull(request.getPayments().get(0)) && Objects.nonNull(request.getPayments().get(0).getAccountType())){
						accountType = request.getPayments().get(0).getAccountType();
					}

					if(Objects.nonNull(request.getPayments()) && !request.getPayments().isEmpty() && Objects.nonNull(request.getPayments().get(0)) && Objects.nonNull(request.getPayments().get(0).getFinalGateway())){

						order.setFinalGateway(request.getPayments().get(0).getFinalGateway());
					}
					order.setCheckoutType(request.getCheckoutType());
					order.setBankRefNo(request.getPaymentRefId());
					adjustLoanBalance(activeLoan.get(), request.getPaymentAmount(), request.getPaymentRefId(), order.getSource(),
							PaymentType.ADVANCE_EDI.name().equalsIgnoreCase(order.getDescription()), accountType);

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

	private void sendSMS(LendingPaymentSchedule loan, Double amount, boolean isLoanClosed) {
		try {
			Optional<BasicDetailsDto> basicDetailsDto = merchantService.fetchMerchantBasicDetails(loan.getMerchantId());
			if (ObjectUtils.isEmpty(basicDetailsDto)) {
				return;
			}

//			Merchant merchant = loan.getMerchant();
			String identifier = "LENDING_PAYMENT_PUSH";
			Map<String,Object> templateParams = new HashMap<>();
			templateParams.put("amount",amount.intValue());
			String deeplink = notificationUtil.getDeeplink(basicDetailsDto.get().getSettlementType(), "LOAN_DASHBOARD");
			NotificationPayloadDto notificationPayloadDto = new NotificationPayloadDto();
			notificationPayloadDto.setPushTitle("Payment received!");
			notificationPayloadDto.setTemplateIdentifier(identifier);
			notificationPayloadDto.setMobile(basicDetailsDto.get().getMobile());
			notificationPayloadDto.setPushDeepLink(deeplink);
			notificationPayloadDto.setClientName("LENDING");
			notificationPayloadDto.setTemplateParams(templateParams);
			lendingNotificationService.notify(notificationPayloadDto);
			if(isLoanClosed) {
				if(apiGatewayService.sendCommunicationForNewOffer(loan)) {
					return;
				}
				identifier = "LENDING_PAYMENT_2_PUSH";
				notificationPayloadDto.setTemplateIdentifier(identifier);
				notificationPayloadDto.setPushTitle("The loan is closed successfully");
				lendingNotificationService.notify(notificationPayloadDto);
			}
		} catch(Exception ex) {
			logger.error("Exception while sending payment SMS to merchant {}, Exception is {}");
		}
	}
	
	private String getDescription(String bankRRN, boolean preclosure) {
		return preclosure ? "PRECLOSER_UPI : " + bankRRN : "PREPAYMENT : " + bankRRN;
	}
	
	private void createLendingLedger(LendingPaymentSchedule lendingPaymentSchedule, Double amount, Double principle, Double interest, String description, String source, String transferType) {
        if(amount == 0) {
            return;
        }
        
        LendingLedger lendingLedger = new LendingLedger();
        lendingLedger.setMerchantId(lendingPaymentSchedule.getMerchantId());
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

		lendingLedger.setTransferType(Objects.nonNull(transferType) && transferType.equals("EXTERNAL") ? "Directly transfered to lender" : "Transfer by BP");
        
        lendingLedgerDao.save(lendingLedger);

        if(amount > 0 && principle > 0) {
        	logger.info("Credit principle:{} in lending global limit for merchant:{}", principle, lendingLedger.getMerchantId());
			notificationExecutor.execute(() -> apiGatewayService.globalLimitTxn(lendingLedger.getMerchantId(), "CREDIT", principle));
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

	public PaymentStatusResponseDTO getStatus(String orderId, BasicDetailsDto merchant) {
		logger.info("Received status check request for orderId:{}", orderId);
		try {
			LoanPaymentOrder order = loanPaymentOrderDao.findByOrderId(orderId);
			if (order == null || !order.getMerchantId().equals(merchant.getId())) {
				logger.info("No order found for orderId:{}", orderId);
				return new PaymentStatusResponseDTO(false, "Order not found");
			}
			return new PaymentStatusResponseDTO(order.getStatus(), orderId, order.getAmount(), order.getBankRefNo(), order.getUpdatedAt());
		} catch (Exception e) {
			logger.error("Exception in payment status check", e);
			return new PaymentStatusResponseDTO(false, "Something went wrong");
		}
	}

	public PaymentStatusResponseDTO getStatusV2(String orderId, BasicDetailsDto merchant) {
		logger.info("Received status check request for orderId:{}", orderId);
		try {
			LoanPaymentOrder order = loanPaymentOrderDao.findByOrderId(orderId);
			if (order == null || !order.getMerchantId().equals(merchant.getId())) {
				logger.info("No order found for orderId:{}", orderId);
				return new PaymentStatusResponseDTO(false, "Order not found");
			}
			Optional<LendingPaymentSchedule> activeLoan = lendingPaymentScheduleDao.findById(order.getOwnerId());
			Lender lender = Lender.valueOf(activeLoan.get().getNbfc());
			if("PENDING".equalsIgnoreCase(order.getStatus())) {
				logger.info("pg status check for merchant id {} and order id {}", order.getMerchantId(), order.getOrderId());
				PgStatusResponse response = apiGatewayService.checkPgStatus(order.getOrderId(), lender, order.getMerchantId());
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

	public ResponseDTO resendOTP(RequestDTO<PaymentResendOTP> requestDTO, BasicDetailsDto merchant, String token) {
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

	private void adjustLoanBalance(LendingPaymentSchedule activeLoan, Double amount, String bankRefNo, String source, boolean advanceEdi, String transferType) {
		logger.info("Adjusting Balance for loanId:{} and amount:{} and advanceEdi:{}", activeLoan.getId(), amount, advanceEdi);
		LendingPrepayment lendingPrepayment = lendingPrepaymentDao.findByMerchantIdAndLoanId(activeLoan.getMerchantId(), activeLoan.getId());
		double advanceEdiAmount = lendingPrepayment != null && lendingPrepayment.getAdvanceEdiAmount() != null ? lendingPrepayment.getAdvanceEdiAmount() : 0d;
		Integer principalDueAmount = loanUtil.getForeclosureAmount(activeLoan);
		Integer ediHolidayInterestAmount = getEDIHolidayInterestAmount(activeLoan);

		Double paidInterestAmount = 0D;
		Double paidPrincipalAmount = 0D;
		boolean preclosure = false;
		boolean advanceAdjusted = false;
		logger.info("Preclosure amount for loanId:{} is:{}", activeLoan.getId(), (principalDueAmount + ediHolidayInterestAmount));
		logger.info("Advance EDI amount for loanId:{} is:{}", activeLoan.getId(), advanceEdiAmount);
		logger.info("Due amount for loanId:{} is due amount:{} due principle:{} due interest:{}", activeLoan.getId(), activeLoan.getDueAmount(), activeLoan.getDuePrinciple(), activeLoan.getDueInterest());
		if(principalDueAmount + ediHolidayInterestAmount - amount <= 1D) {
			logger.info("Received pre closure amount:{} for loan:{}", amount, activeLoan.getId());
			paidInterestAmount = (activeLoan.getDueInterest() != null ? activeLoan.getDueInterest() : 0) + ediHolidayInterestAmount;
			paidPrincipalAmount = amount - paidInterestAmount + advanceEdiAmount;
			double extraPrinciple = (activeLoan.getPaidPrinciple() + paidPrincipalAmount) - activeLoan.getLoanAmount();
			if (extraPrinciple > 0) {
				logger.info("Extra principle received for loanId:{} and extra amount:{}", activeLoan.getId(), extraPrinciple);
				paidPrincipalAmount -= extraPrinciple;
				paidInterestAmount += extraPrinciple;
			}
			logger.info("Adjusted breakup amount for loan:{} is principle:{} and interest:{}", activeLoan.getId(), paidPrincipalAmount, paidInterestAmount);
			if(activeLoan.getDueAmount() >= 0) {
				createLendingLedger(activeLoan, -1 * Math.abs(amount - activeLoan.getDueAmount() + advanceEdiAmount) , -1 * Math.abs(amount - activeLoan.getDueAmount() - ediHolidayInterestAmount + advanceEdiAmount), Double.valueOf(ediHolidayInterestAmount), "PREPAYMENT", source, transferType);
			} else {
				createLendingLedger(activeLoan, -1 * amount + advanceEdiAmount, -1 * amount - ediHolidayInterestAmount + advanceEdiAmount, Double.valueOf(ediHolidayInterestAmount), "PREPAYMENT", source, transferType);
			}

			activeLoan.setPaidAmount(activeLoan.getPaidAmount() + amount + advanceEdiAmount);
			activeLoan.setPaidInterest((activeLoan.getPaidInterest() != null ? activeLoan.getPaidInterest() : 0) + paidInterestAmount);
			activeLoan.setPaidPrinciple((activeLoan.getPaidPrinciple() != null ? activeLoan.getPaidPrinciple() : 0) + paidPrincipalAmount);

			activeLoan.setDueAmount(0D);
			activeLoan.setDueInterest(0D);
			activeLoan.setDuePrinciple(0D);

			activeLoan.setStatus("CLOSED");
			activeLoan.setClosingDate(new Date());
			preclosure = true;
			if (lendingPrepayment != null && advanceEdiAmount > 0d) {
				advanceAdjusted = true;
				lendingPrepayment.setAdvanceEdiCount(0);
				lendingPrepayment.setAdvanceEdiAmount(0D);
				lendingPrepaymentDao.save(lendingPrepayment);
			}
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
			advanceAdjusted = adjustAdvanceEdi(activeLoan, balance, advanceEdi);
			if(balance > 0D && !advanceAdjusted) {
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
							logger.info("Adjusting full installment:{} for loanId:{}", ediSchedule.getInstallmentNumber(), activeLoan.getId());
							balance -= ediSchedule.getTotalEdi();
							principle += ediSchedule.getPrinciple();
							interest += ediSchedule.getInterest();
							if (ediSchedule.getEdiType() != null && ediSchedule.getEdiType().equalsIgnoreCase("Principal Morat")) {
								adjustedIOEdiCount++;
							} else {
								adjustedEdiCount++;
							}
						} else if (balance <= ediSchedule.getInterest()) {
							logger.info("Adjusting interest:{} for installment:{} for loanId:{}", balance, ediSchedule.getInstallmentNumber(), activeLoan.getId());
							interest += balance;
							extraAmount += balance;
							balance = 0d;
						} else {
							logger.info("Adjusting interest:{} and principle:{} for installment:{} for loanId:{}", ediSchedule.getInterest(), (balance - ediSchedule.getInterest()), ediSchedule.getInstallmentNumber(), activeLoan.getId());
							interest += ediSchedule.getInterest();
							principle += balance - ediSchedule.getInterest();
							extraAmount += balance;
							balance = 0d;
						}
					}
				}
				logger.info("Adjusted principle:{} interest:{} extra amount:{} adjustedEdiCount:{} adjustedIOEdiCount:{} for loan:{}", principle, interest, extraAmount, adjustedEdiCount, adjustedIOEdiCount, activeLoan.getId());
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
				createLendingLedger(activeLoan, -1*(principle + interest), -1*principle, -1*interest, "PREPAYMENT", source, transferType);
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
		if (advanceAdjusted) {
			logger.info("Reducing adjusted amount due to advance EDI for loanId:{}, old amount:{}, new amount:{}", activeLoan.getId(), amount, (paidPrincipalAmount + paidInterestAmount));
			amount = (paidPrincipalAmount + paidInterestAmount);
		}
		logger.info("Adjusted breakup amount for loan:{} is principle:{} and interest:{}", activeLoan.getId(), paidPrincipalAmount, paidInterestAmount);
		createLendingLedger(activeLoan, amount, paidPrincipalAmount, paidInterestAmount,  getDescription(bankRefNo, preclosure), source, transferType);
		lendingPaymentScheduleDao.save(activeLoan);
		if (activeLoan.getLoanApplication() != null && activeLoan.getLoanApplication().getProcessingFee() != null && activeLoan.getLoanApplication().getProcessingFee() > 0) {
			redisNotificationService.sendRepaymentNudge(activeLoan.getMerchantId(), activeLoan.getLoanApplication().getProcessingFee());
		}
		boolean isLoanClosed = "CLOSED".equalsIgnoreCase(activeLoan.getStatus());

		Double finalAmount = amount;
		notificationExecutor.execute(() -> sendSMS(activeLoan, finalAmount, isLoanClosed));

//		if(isLoanClosed) {
//			List<String> topupLoans = Arrays.asList(LoanType.TOPUP.name(), LoanType.HALF_TOPUP.name(), LoanType.IO_TOPUP.name());
//			notificationExecutor.execute(() -> {
//				LoyaltyServiceRequest requestBean = new LoyaltyServiceRequest.LoyaltyServiceRequestBuilder(activeLoan.getMerchantId(), LoyaltyTransactionType.PRE_LOAN_CLOSURE)
//						.amount(finalAmount)
//						.merchantStoreId(null)
//						.transactionId(activeLoan.getId())
//						.build();
//				loyaltyService.pushToKafka(requestBean);
//				if(topupLoans.contains(activeLoan.getLoanApplication().getLoanType())){
//					LendingPaymentSchedule topupLoan = lendingPaymentScheduleDao.findTopupLoan(activeLoan.getMerchantId());
//					if(topupLoan != null) {
//						refundProcessingFee(topupLoan);
//					}
//				}else{
//					refundProcessingFee(activeLoan);
//				}
//				if (activeLoan.getDueAmount() < 0) {
//					logger.info("Extra amount:{} received for loanId:{}, initiating refund", activeLoan.getDueAmount(), activeLoan.getId());
//					refundExtraAmount(activeLoan);
//				}
//			});
//		}
	}

	private boolean adjustAdvanceEdi(LendingPaymentSchedule activeLoan, double balance, boolean advanceEdi) {
		if (advanceEdi && balance > 0D) {
			logger.info("Adjusting balance:{} as advance edi for loanId:{}", balance, activeLoan.getId());
			int advanceEdiCount = 0;
			if (balance % activeLoan.getEdiAmount() == 0) {
				advanceEdiCount = (int)(balance/activeLoan.getEdiAmount());
			} else if (Math.ceil(balance) % activeLoan.getEdiAmount() == 0) {
				advanceEdiCount = (int)(Math.ceil(balance)/activeLoan.getEdiAmount());
				balance = Math.ceil(balance);
			} else if (Math.floor(balance) % activeLoan.getEdiAmount() == 0) {
				advanceEdiCount = (int)(Math.floor(balance)/activeLoan.getEdiAmount());
				balance = Math.floor(balance);
			}
			if (advanceEdiCount > 0) {
				LendingPrepayment lendingPrepayment = lendingPrepaymentDao.findByMerchantIdAndLoanId(activeLoan.getMerchantId(), activeLoan.getId());
				if (lendingPrepayment != null) {
					lendingPrepayment.setAdvanceEdiAmount(lendingPrepayment.getAdvanceEdiAmount() + balance);
					lendingPrepayment.setAdvanceEdiCount(lendingPrepayment.getAdvanceEdiCount() + advanceEdiCount);
				} else {
					lendingPrepayment = new LendingPrepayment(activeLoan.getMerchantId(), activeLoan.getId(), balance, advanceEdiCount);
				}
				lendingPrepaymentDao.save(lendingPrepayment);
				lendingPrepaymentAuditDao.save(new LendingPrepaymentAudit(activeLoan.getMerchantId(), activeLoan.getId(), balance, advanceEdiCount));
				logger.info("Advance EDI adjustment successful for loanId:{} and amount:{}", activeLoan.getId(), balance);
				return true;
			} else {
				logger.error("Advance edi balance:{} is not correct for loanId:{}, adjusting as prepayment",balance, activeLoan.getId());
			}
		}
		return false;
	}

	private void refundExtraAmount(LendingPaymentSchedule lendingPaymentSchedule) {
		try {
			Optional<BasicDetailsDto> basicDetailsDto = merchantService.fetchMerchantBasicDetails(lendingPaymentSchedule.getMerchantId());
			if (ObjectUtils.isEmpty(basicDetailsDto)) {
				return;
			}
			logger.info("Refund due amount:{} for loan:{}", lendingPaymentSchedule.getDueAmount(), lendingPaymentSchedule.getId());
			String orderId = "ECOLLECT_REFUND" + System.currentTimeMillis();
			Double refundAmount = -1 * lendingPaymentSchedule.getDueAmount();
			Double principle = -1 * lendingPaymentSchedule.getDuePrinciple();
			Double interest = -1 * lendingPaymentSchedule.getDueInterest();
			LendingPayoutRequest lendingPayoutRequest = new LendingPayoutRequest(lendingPaymentSchedule.getId(), orderId, refundAmount, LendingPayoutType.LENDING_ECOLLECT_REFUND, lendingPaymentSchedule.getMerchantId(), "ECOLLECT_REFUND");
			LendingPayoutResponse lendingPayoutResponse = apiGatewayService.lendingPayout(lendingPayoutRequest);
			if (lendingPayoutResponse != null) {
				String bankRefNo = lendingPayoutResponse.getData() != null ? lendingPayoutResponse.getData().getBankReferenceNo() : null;
				createRefundLedger(lendingPaymentSchedule, DateTimeUtil.getCurrentDayStartTime(), "LOAN_REFUND", -refundAmount, -principle, -interest, 0D, 0D, bankRefNo, "REFUND");
				lendingPaymentSchedule.setDueAmount(lendingPaymentSchedule.getDueAmount() + refundAmount);
				lendingPaymentSchedule.setDueInterest(lendingPaymentSchedule.getDueInterest() + interest);
				lendingPaymentSchedule.setDuePrinciple(lendingPaymentSchedule.getDuePrinciple() + principle);
				lendingPaymentScheduleDao.save(lendingPaymentSchedule);
				final Optional<BankDetailsDto> bankDetailsDtoOptional = merchantService.fetchMerchantBankDetails(lendingPaymentSchedule.getMerchantId());
				BankDetailsDto merchantBankDetail = null;
				if (bankDetailsDtoOptional.isPresent())
					merchantBankDetail = bankDetailsDtoOptional.get();
				String identifier = "LENDING_REFUND_2_SMS";
				Map<String,Object> templateParams = new HashMap<>();
				templateParams.put("beneficiary_name",getBeneficiaryName(merchantBankDetail.getBeneficiaryName()));
				templateParams.put("refund_amount",refundAmount);
				templateParams.put("bank_name",merchantBankDetail.getBankName());
				NotificationPayloadDto notificationPayloadDto = new NotificationPayloadDto();
				notificationPayloadDto.setTemplateIdentifier(identifier);
				notificationPayloadDto.setMobile(basicDetailsDto.get().getMobile());
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
		lendingLedger.setMerchantId(lendingPaymentSchedule.getMerchantId());
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
		lendingLedger.setTransferType("Transfer by BP");
		lendingLedgerDao.save(lendingLedger);
	}

	public void refundProcessingFee(LendingPaymentSchedule lendingPaymentSchedule) {
		try {
			logger.info("enter refund processing fee for merchant: {}", lendingPaymentSchedule.getMerchantId());
			LendingPayoutResponseDTO checkRefunded = lendingPayoutsHandler.findTopByMerchantIdAndOwnerIdAndStatusAndOrderIdLike(lendingPaymentSchedule.getMerchantId(),
			lendingPaymentSchedule.getId(), "PF_CASHBACK");
			if(checkRefunded != null){
				return;
			}
			MerchantDetailsDto merchantDetailsDTO =  merchantService.fetchMerchantDetails(lendingPaymentSchedule.getMerchantId(), Collections.singletonList(Constants.MerchantUtil.Scope.BANK_DETAIL));
			BasicDetailsDto basicDetailsDto = merchantDetailsDTO.getMerchantDetail();
			BankDetailsDto merchantBankDetail = merchantDetailsDTO.getBankDetail();
			if (ObjectUtils.isEmpty(basicDetailsDto)) {
				return;
			}
			if (lendingPaymentSchedule.getStatus().equals("CLOSED") && lendingPaymentSchedule.getLoanApplication() != null
					&& lendingPaymentSchedule.getLoanApplication().getProcessingFee() != null
					&& lendingPaymentSchedule.getLoanApplication().getProcessingFee() > 0D
					&& (apiGatewayService.checkClubV2(lendingPaymentSchedule.getMerchantId()))
					&& lendingPaymentSchedule.getLoanApplication().getDisburseTimestamp()
					.before(new SimpleDateFormat("dd/MM/yyyy").parse("01/07/2022"))) {
				logger.info("refund processing fee before 1st june or club member for merchant: {}", lendingPaymentSchedule.getMerchantId());
				BigInteger maxDpd = loanDpdDao.findMaxDpd(lendingPaymentSchedule.getId());
				long dpd = LoanUtil.getDateDiffInDays(lendingPaymentSchedule.getTentativeClosingDate(), lendingPaymentSchedule.getClosingDate());
				LendingLedger lendingLedger = lendingLedgerDao.getForClosedLedger(lendingPaymentSchedule.getId());
				if (maxDpd.intValue() <= 5 &&  dpd <= 5 && (dpd >= -5 || Objects.isNull(lendingLedger))) {
					logger.info("Closing dpd is between 5 days for loanId:{}, processing fee refund for amount:{}", lendingPaymentSchedule.getId(), lendingPaymentSchedule.getLoanApplication().getProcessingFee());
					Double cashbackAmt = lendingPaymentSchedule.getLoanApplication().getProcessingFee();
					Double cashbackAmount = Math.min(cashbackAmt, 1500);
					String orderId = "PF_CASHBACK" + System.currentTimeMillis();
					LendingPayoutRequest lendingPayoutRequest = new LendingPayoutRequest(lendingPaymentSchedule.getId(), orderId, cashbackAmount, LendingPayoutType.LENDING_INCENTIVE, lendingPaymentSchedule.getMerchantId(), "PF_CASHBACK");
					LendingPayoutResponse lendingPayoutResponse = apiGatewayService.lendingPayout(lendingPayoutRequest);
					if (lendingPayoutResponse != null) {
						String identifier = "LENDING_ARRANGER_REFUND_2_SMS";
						Map<String,Object> templateParams = new HashMap<>();
						templateParams.put("beneficiary_name",getBeneficiaryName(merchantBankDetail.getBeneficiaryName()));
						templateParams.put("cashback_amount",merchantBankDetail.getBeneficiaryName());
						templateParams.put("bank_name",merchantBankDetail.getBeneficiaryName());

						NotificationPayloadDto notificationPayloadDto = new NotificationPayloadDto();
						notificationPayloadDto.setTemplateIdentifier(identifier);
						notificationPayloadDto.setMobile(basicDetailsDto.getMobile());
						notificationPayloadDto.setClientName("LENDING");
						notificationPayloadDto.setTemplateParams(templateParams);
						lendingNotificationService.notify(notificationPayloadDto);
						identifier = "LENDING_ARRANGER_FEE_REFUND_PUSH";
						String deeplink = notificationUtil.getDeeplink(basicDetailsDto.getSettlementType(),"LOAN_DASHBOARD");
						notificationPayloadDto.setPushDeepLink(deeplink);
						notificationPayloadDto.setPushTitle("Arranger Fee refund!");
						notificationPayloadDto.setTemplateIdentifier(identifier);
						lendingNotificationService.notify(notificationPayloadDto);
					}
				}
			}
		} catch (Exception e) {
			logger.error("Exception in PF Refund for loanId:{}", lendingPaymentSchedule.getId(), e);
		}
	}

	public ResponseDTO verifyPayment(RequestDTO<PaymentResendOTP> requestDTO, BasicDetailsDto merchant, String token) {
		LoanPaymentOrder loanPaymentOrder = loanPaymentOrderDao.findByOrderId(requestDTO.getPayload().getOrderId());
		if (loanPaymentOrder == null) {
			return new ResponseDTO(false, "Order not found");
		}
		if(!"PENDING".equalsIgnoreCase(loanPaymentOrder.getStatus())) {
			logger.info("Payment for merchant id {} and order id {} is already processed", loanPaymentOrder.getMerchantId(), loanPaymentOrder.getOrderId());
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
					adjustLoanBalance(activeLoan.get(), loanPaymentOrder.getAmount(), null, loanPaymentOrder.getSource(), PaymentType.ADVANCE_EDI.name().equalsIgnoreCase(loanPaymentOrder.getDescription()),"INTERNAL");
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
				ediSchedule.setMerchantId(lendingPaymentSchedule.getMerchantId());
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
				ediSchedule.setMerchantId(lendingPaymentSchedule.getMerchantId());
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

	public PaymentStatusV3ResponseDTO getStatusV3(String orderId, BasicDetailsDto merchant) {
		logger.info("Received status check request for orderId:{}", orderId);
		try {
			dateFormat.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
			LoanPaymentOrder order = loanPaymentOrderDao.findByOrderId(orderId);
			if (order == null || !order.getMerchantId().equals(merchant.getId())) {
				logger.info("No order found for orderId:{}", orderId);
				return new PaymentStatusV3ResponseDTO(false, "Order not found");
			}
			Optional<LendingPaymentSchedule> activeLoan = lendingPaymentScheduleDao.findById(order.getOwnerId());
			Lender lender = Lender.valueOf(activeLoan.get().getNbfc());
			if("PENDING".equalsIgnoreCase(order.getStatus())) {
				logger.info("pg status check for merchant id {} and order id {}", order.getMerchantId(), order.getOrderId());
				PgStatusResponse response = apiGatewayService.checkPgStatus(order.getOrderId(), lender, order.getMerchantId());
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
			PaymentStatusV3ResponseDTO.Data data = new PaymentStatusV3ResponseDTO.Data();
			data.setPaymentMode(order.getSource());
			data.setPaymentStatus(order.getStatus());
			data.setReferenceNumber(order.getBankRefNo());
			data.setTransferTime(dateFormat.format(order.getUpdatedAt()));
			data.setAmount(order.getAmount());
			data.setOrderId(orderId);
			return new PaymentStatusV3ResponseDTO(true, null, data);
		} catch (Exception e) {
			logger.error("Exception in payment status check", e);
			return new PaymentStatusV3ResponseDTO(false, "Something went wrong");
		}
	}

	public ResponseDTO applyWaiver(Long loanId, Long merchantId, WaiverType waiverType, Long userId) {
		LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.findByIdAndMerchantId(loanId, merchantId);
		if(Objects.isNull(lendingPaymentSchedule) || !"ACTIVE".equalsIgnoreCase(lendingPaymentSchedule.getStatus())) {
			logger.error("No active loan found for id {}", loanId);
			return new ResponseDTO(false, "No active loan found");
		}
		Integer foreClosureAmount = loanUtil.getForeclosureAmount(lendingPaymentSchedule) + getEDIHolidayInterestAmount(lendingPaymentSchedule);
		LoanPaymentOrder order = createOrder(lendingPaymentSchedule,waiverType.name(), foreClosureAmount);
		PaymentCallbackRequestDTO paymentCallbackRequestDTO = new PaymentCallbackRequestDTO();
		paymentCallbackRequestDTO.setAmount(order.getAmount());
		paymentCallbackRequestDTO.setStatus("SUCCESS");
		paymentCallbackRequestDTO.setOrderId(order.getOrderId());
		handleCallback(paymentCallbackRequestDTO);

		//waiver audit
		LendingInterestWaiver lendingInterestWaiver = new LendingInterestWaiver();
		lendingInterestWaiver.setAmount(foreClosureAmount);
		lendingInterestWaiver.setApplicationId(lendingPaymentSchedule.getApplicationId());
		lendingInterestWaiver.setMerchantId(lendingPaymentSchedule.getMerchantId());
		lendingInterestWaiver.setPaymentId(lendingPaymentSchedule.getId());
		lendingInterestWaiver.setSchemeName(waiverType.name());
		lendingInterestWaiver.setUserId(userId);
		lendingInterestWaiverDao.save(lendingInterestWaiver);

		lendingPaymentSchedule = lendingPaymentScheduleDao.findByIdAndMerchantId(loanId, merchantId);
		if("CLOSED".equalsIgnoreCase(lendingPaymentSchedule.getStatus())) {
			lendingPaymentSchedule.setSettlementStatus(waiverType.name());
			lendingPaymentScheduleDao.save(lendingPaymentSchedule);
			return new ResponseDTO(true, "Waiver applied successfully");
		} else {
		    logger.error("Unable to settle loan:{}", lendingPaymentSchedule.getId());
        }
		return new ResponseDTO(false, "Something went wrong");
	}

	private LoanPaymentOrder createOrder(LendingPaymentSchedule lendingPaymentSchedule, String source, Integer foreclosureAmount) {
		logger.info("Creating Order for loan Id : {}", lendingPaymentSchedule.getId());
		LoanPaymentOrder order = new LoanPaymentOrder();
		order.setMerchantId(lendingPaymentSchedule.getMerchantId());
		order.setOwner("lending_payment_schedule");
		order.setOwnerId(lendingPaymentSchedule.getId());
		order.setAmount(foreclosureAmount.doubleValue());
		order.setStatus("PENDING");
		order.setSource(source);
		order = loanPaymentOrderDao.save(order);
		String orderId = "LOAN" + (10000000L + order.getId());
		order.setOrderId(orderId);
		return loanPaymentOrderDao.save(order);
	}

	public LoanRefundsResponseDTO getRefunds(Long loanId) {
		Optional<LendingPaymentSchedule> optionalLps = lendingPaymentScheduleDao.findById(loanId);
		LoanRefundsResponseDTO loanRefundsResponseDTO = new LoanRefundsResponseDTO();
		if(optionalLps .isPresent()) {
			List<LendingPayoutResponseDTO> lendingPayoutsList = lendingPayoutsHandler.findByOwnerIdAndTypeAndCreatedAtGTE(loanId, "REFUND", "2021-08-09");

			if (Objects.nonNull(lendingPayoutsList)) {
				logger.info("number of refunds: {} for loanId: {}", loanId, lendingPayoutsList.size());

				List<LoanRefundsResponseDTO.Refund> loanRefundList = new ArrayList<>();

				for (LendingPayoutResponseDTO lendingPayouts : lendingPayoutsList) {
					LoanRefundsResponseDTO.Refund loanRefund = new LoanRefundsResponseDTO.Refund(loanId, lendingPayouts.getAmount(),
					lendingPayouts.getCreatedAt(), lendingPayouts.getPaymentType());
					loanRefundList.add(loanRefund);
				}

				loanRefundsResponseDTO.setRefundList(loanRefundList);
				loanRefundsResponseDTO.setSuccess(true);
			}
			return loanRefundsResponseDTO;
		}
		loanRefundsResponseDTO.setMessage("No loan found with id:" + loanId);
		loanRefundsResponseDTO.setSuccess(false);
		return loanRefundsResponseDTO;
	}

}