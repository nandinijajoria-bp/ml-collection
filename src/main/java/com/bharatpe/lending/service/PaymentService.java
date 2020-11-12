package com.bharatpe.lending.service;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.bharatpe.common.service.WhatsappNotificationService;
import com.bharatpe.lending.common.entity.LendingClPayment;
import com.bharatpe.lending.common.entity.LendingVirtualAccount;
import com.bharatpe.lending.dto.*;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.stereotype.Service;

import com.bharatpe.common.dao.LendingEDIScheduleDao;
import com.bharatpe.common.dao.MerchantBankDetailDao;
import com.bharatpe.common.dao.MerchantDao;
import com.bharatpe.common.entities.LendingEDISchedule;
import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.common.entities.Merchant;
import com.bharatpe.common.entities.MerchantBankDetail;
import com.bharatpe.common.enums.LoyaltyTransactionType;
import com.bharatpe.common.enums.NotificationProvider;
import com.bharatpe.common.enums.Status;
import com.bharatpe.common.handlers.SmsServiceHandler;
import com.bharatpe.common.objects.LoyaltyServiceRequest;
import com.bharatpe.common.service.LoyaltyService;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.constant.CreditConstants;
import com.bharatpe.lending.dao.LendingLedgerDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dao.LoanPaymentOrderDao;
import com.bharatpe.lending.entity.LoanPaymentOrder;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class PaymentService {

	Logger logger = LoggerFactory.getLogger(PaymentService.class);
	
	@Autowired
	LendingPaymentScheduleDao lendingPaymentScheduleDao;
	
	@Autowired
	APIGatewayService apiGatewayService;
	
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
	
	ExecutorService notificationExecutor = Executors.newFixedThreadPool(5);
	
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
			
			PaymentDetailsResponseDTO.Data data= new PaymentDetailsResponseDTO.Data(loanAmount, overdueAmount, principalDueAmount + ediHolidayInterestAmount, overdueDays, isPayable);
			return new PaymentDetailsResponseDTO(data);
			
		} catch(Exception ex) {
			logger.error("Execption while fetching payment details for merchant id {}, Exception is {}", merchant.getId(), ex);
		}
		
		return new PaymentDetailsResponseDTO("Something went wrong.");
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
				String sms = "Dear Khushal Virmani,\nThis is to inform you that your daily transactions have fallen to Rs.0 in last 3 days. Continue transacting on your BharatPe QR to pay your EDI of Rs.100 on time. Payment defaults can impact your credit score.";
				whatsappNotificationService.sendWithImage(merchant, null, sms, new ArrayList<String>(){{add("919971011197");}}, null, "https://merchant-qr.s3.ap-south-1.amazonaws.com/v2/8d0cd6a2-3493-4096-a416-98c9331e39f2.png");
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
			} else if("PRINCIPAL".equalsIgnoreCase(request.getPayload().getPaymentType())) {
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
//			if (request.getPayload().getSource() != null) {
//				order.setSource(request.getPayload().getSource().name());
//			}
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
				paymentSuccess = (Boolean) result.get("success");
				otpFlow = (Boolean) result.get("otp_flow");
				authMode = (String) result.get("auth_mode");
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
				logger.error("Payment for merchant id {} and order id {} is already processed", order.getMerchant().getId(), request.getOrderId());
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
			adjustLoanBalance(activeLoan.get(), request.getAmount(), request.getBankReferenceNumber());
			order.setBankRefNo(request.getBankReferenceNumber());
			order.setStatus("SUCCESS");
			loanPaymentOrderDao.save(order);
		} catch(Exception ex) {
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
			
			String content = null;
			if(isLoanClosed) {
				content = "Hi " + merchantBankDetail.getBeneficiaryName() + ",\nThank you for making prepayment of Rs." + amount.intValue() + " towards your Bharatpe Loan. Your Loan is successfully closed.\nYou have earned 10 runs which you can use to get rewards on BharatPe app.";
			} else {
				content = "Hi " + merchantBankDetail.getBeneficiaryName() + ",\nThank you for making payment of Rs." + amount.intValue() + " towards your BharatPe Loan.";
			}
			
			smsServiceHandler.sendSMS(Arrays.asList(merchant.getMobile()), content, NotificationProvider.SMS.GUPSHUP);
			
		} catch(Exception ex) {
			logger.error("Exception while sending payment SMS to merchant {}, Exception is {}");
		}
	}
	
	private String getDescription(String bankRRN) {
		return "PREPAYMENT : " + bankRRN;
	}
	
	private void createLendingLedger(LendingPaymentSchedule lendingPaymentSchedule, Double amount, Double principle, Double interest, String description) {
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
        lendingLedger.setAdjustmentMode("UPI");
        
        lendingLedger.setDescription(description);
        
        lendingLedgerDao.save(lendingLedger);
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

	public ResponseDTO getPaymentModes(RequestDTO<CreditSpendRequestDTO> requestDTO, String token) {
		List<PaymentDetailDto> paymentDetails = apiGatewayService.getPaymentModes(requestDTO, token);
		paymentDetails.add(getBankTransferMode());
		paymentDetails.add(getGPAYMode());
		ResponseDTO responseDTO = new ResponseDTO();
		paymentDetails.removeIf(paymentDetailDto -> paymentDetailDto.getBalance() != null && paymentDetailDto.getBalance() < requestDTO.getPayload().getAmount());
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

	private void adjustLoanBalance(LendingPaymentSchedule activeLoan, Double amount, String bankRefNo) {
		logger.info("Adjusting Balance for loanId:{} and amount:{}", activeLoan.getId(), amount);
		Integer principalDueAmount = (int) Math.ceil(activeLoan.getLoanAmount() - (activeLoan.getPaidPrinciple() != null ? activeLoan.getPaidPrinciple() : 0) + (activeLoan.getDueInterest() != null ? activeLoan.getDueInterest() : 0));
		Integer ediHolidayInterestAmount = getEDIHolidayInterestAmount(activeLoan);

		Double paidInterestAmount = 0D;
		Double paidPrincipalAmount = 0D;

		if(principalDueAmount + ediHolidayInterestAmount - amount <= 1D) {

			paidInterestAmount = (activeLoan.getDueInterest() != null ? activeLoan.getDueInterest() : 0) + ediHolidayInterestAmount;
			paidPrincipalAmount = amount - paidInterestAmount;

			if(activeLoan.getDueAmount() >= 0) {
				createLendingLedger(activeLoan, -1 * (amount - activeLoan.getDueAmount()) , -1 * (amount - activeLoan.getDueAmount() - ediHolidayInterestAmount), Double.valueOf(ediHolidayInterestAmount), "PREPAYMENT");
			} else {
				createLendingLedger(activeLoan, -1 * amount , -1 * amount - ediHolidayInterestAmount, Double.valueOf(ediHolidayInterestAmount), "PREPAYMENT");
			}

			activeLoan.setPaidAmount(activeLoan.getPaidAmount() + amount);
			activeLoan.setPaidInterest((activeLoan.getPaidInterest() != null ? activeLoan.getPaidInterest() : 0) + paidInterestAmount);
			activeLoan.setPaidPrinciple((activeLoan.getPaidPrinciple() != null ? activeLoan.getPaidPrinciple() : 0) + paidPrincipalAmount);

			activeLoan.setDueAmount(0D);
			activeLoan.setDueInterest(0D);
			activeLoan.setDuePrinciple(0D);

			activeLoan.setStatus("CLOSED");
		} else {
			double balance=amount;
			if(balance>0D && activeLoan.getDueOtherCharges()!=null && activeLoan.getDueOtherCharges()>0D) {
				Double paidAmount=balance>=activeLoan.getDueOtherCharges()?activeLoan.getDueOtherCharges():balance;
				activeLoan.setDueOtherCharges(activeLoan.getDueOtherCharges()-paidAmount);
				activeLoan.setDueAmount(activeLoan.getDueAmount()-paidAmount);
				activeLoan.setPaidAmount(activeLoan.getPaidAmount()+paidAmount);
				activeLoan.setPaidOtherCharges(activeLoan.getPaidOtherCharges()+paidAmount);
				balance-=paidAmount;
			}
			if(balance>0D && activeLoan.getDuePenalty()!=null && activeLoan.getDuePenalty()>0D) {
				Double paidAmount=balance>=activeLoan.getDuePenalty()?activeLoan.getDuePenalty():balance;
				activeLoan.setDuePenalty(activeLoan.getDuePenalty()-paidAmount);
				activeLoan.setDueAmount(activeLoan.getDueAmount()-paidAmount);
				activeLoan.setPaidAmount(activeLoan.getPaidAmount()+paidAmount);
				activeLoan.setPaidPenalty(activeLoan.getPaidPenalty()+paidAmount);
				balance-=paidAmount;
			}
			if(balance>0D && activeLoan.getDueInterest()!=null && activeLoan.getDueInterest()>0D) {
				Double paidAmount=balance>=activeLoan.getDueInterest()?activeLoan.getDueInterest():balance;
				activeLoan.setDueInterest(activeLoan.getDueInterest()-paidAmount);
				activeLoan.setDueAmount(activeLoan.getDueAmount()-paidAmount);
				activeLoan.setPaidInterest((activeLoan.getPaidInterest() != null ? activeLoan.getPaidInterest() : 0)+paidAmount);
				activeLoan.setPaidAmount(activeLoan.getPaidAmount()+paidAmount);
				paidInterestAmount+=paidAmount;
				balance-=paidAmount;

			}
			if(balance>0D && activeLoan.getDuePrinciple()!=null && activeLoan.getDuePrinciple()>0D) {
				Double paidAmount=balance>=activeLoan.getDuePrinciple()?activeLoan.getDuePrinciple():balance;
				activeLoan.setDuePrinciple(activeLoan.getDuePrinciple()-paidAmount);
				activeLoan.setDueAmount(activeLoan.getDueAmount()-paidAmount);
				activeLoan.setPaidPrinciple((activeLoan.getPaidPrinciple() != null ? activeLoan.getPaidPrinciple() : 0)+paidAmount);
				activeLoan.setPaidAmount(activeLoan.getPaidAmount()+paidAmount);
				paidPrincipalAmount+=paidAmount;
				balance-=paidAmount;

			}
			if(balance>0D) {
				logger.info("Adjusting principle tl for account:{}", activeLoan.getId());
				double totalPaid = 0d;
				if ((activeLoan.getLoanAmount() - (activeLoan.getPaidPrinciple() != null ? activeLoan.getPaidPrinciple() : 0) + (activeLoan.getDueInterest() != null ? activeLoan.getDueInterest() : 0)) <= balance) {
					logger.info("Closing loan:{}", activeLoan.getId());
					totalPaid = (activeLoan.getLoanAmount() - activeLoan.getPaidPrinciple() + activeLoan.getDueInterest());
					activeLoan.setPaidAmount(activeLoan.getPaidAmount() + totalPaid);
					activeLoan.setPaidPrinciple(activeLoan.getPaidPrinciple() + totalPaid);
					activeLoan.setDueAmount(0D);
					activeLoan.setDueInterest(0D);
					activeLoan.setDuePrinciple(0D);
					activeLoan.setStatus("CLOSED");
				} else {
					List<LendingEDISchedule> ediSchedules = lendingEDIScheduleDao.findByLendingPaymentSchedule(activeLoan);
					if (ediSchedules == null || ediSchedules.isEmpty()) {
						logger.error("Edi Schedule not found for loan id:{}", activeLoan.getId());
						throw new RuntimeException("EDI Schedule Not Found");
					}
					ediSchedules.sort(Comparator.comparing(LendingEDISchedule::getInstallmentNumber));
					int ediPaidCount = activeLoan.getEdiCount() - activeLoan.getEdiRemainingCount();
					double principleAdjusted = 0d;
					double interestAdjusted = 0d;
					int ediCount = 0;
					for (LendingEDISchedule ediSchedule : ediSchedules) {
						if (ediSchedule.getInstallmentNumber() <= ediPaidCount) {
							continue;
						}
						principleAdjusted += ediSchedule.getPrinciple();
						interestAdjusted += ediSchedule.getInterest();
						ediCount++;
						if (principleAdjusted >= balance) {
							double extraAmount = principleAdjusted - balance;
							if (extraAmount > 0) {
								activeLoan.setAdjustedDueAmount(activeLoan.getAdjustedDueAmount() != null ? activeLoan.getAdjustedDueAmount() + extraAmount : extraAmount);
								principleAdjusted -= extraAmount;
							}
							break;
						}
					}
					if (principleAdjusted > 0) {
						totalPaid = principleAdjusted;
						activeLoan.setEdiRemainingCount(activeLoan.getEdiRemainingCount() - ediCount);
						activeLoan.setPaidAmount(activeLoan.getPaidAmount() + totalPaid);
						activeLoan.setPaidPrinciple((activeLoan.getPaidPrinciple() != null ? activeLoan.getPaidPrinciple() : 0) + totalPaid);
						activeLoan.setTotalPayableAmount(activeLoan.getTotalPayableAmount() - interestAdjusted);
					}
					if (activeLoan.getEdiRemainingCount() == 0 && activeLoan.getAdjustedDueAmount() != null && activeLoan.getAdjustedDueAmount() > 0D) {
						activeLoan.setDueAmount(activeLoan.getDueAmount() + activeLoan.getAdjustedDueAmount());
						activeLoan.setDuePrinciple(activeLoan.getDuePrinciple() + activeLoan.getAdjustedDueAmount());
						createLendingLedger(activeLoan, -1*activeLoan.getAdjustedDueAmount(), -1*activeLoan.getAdjustedDueAmount(), 0D, "ADJUSTED_DUE_AMOUNT");
						activeLoan.setAdjustedDueAmount(0D);
					}
				}
				paidPrincipalAmount+=totalPaid;
				createLendingLedger(activeLoan, -1*totalPaid, -1*totalPaid, 0D, "PREPAYMENT");
			}
		}

		createLendingLedger(activeLoan, amount, paidPrincipalAmount, paidInterestAmount,  getDescription(bankRefNo));
		lendingPaymentScheduleDao.save(activeLoan);
		if (activeLoan.getLoanApplication() != null && activeLoan.getLoanApplication().getProcessingFee() != null && activeLoan.getLoanApplication().getProcessingFee() > 0) {
			redisNotificationService.sendRepaymentNudge(activeLoan.getMerchant(), activeLoan.getLoanApplication().getProcessingFee());
		}
		boolean isLoanClosed = "CLOSED".equalsIgnoreCase(activeLoan.getStatus());

		notificationExecutor.submit(() -> sendSMS(activeLoan.getMerchant(), amount, isLoanClosed));

		if(isLoanClosed) {
			LoyaltyServiceRequest requestBean = new LoyaltyServiceRequest.LoyaltyServiceRequestBuilder(activeLoan.getMerchant().getId(), LoyaltyTransactionType.PRE_LOAN_CLOSURE)
					.amount(amount)
					.merchantStoreId(null)
					.transactionId(activeLoan.getId())
					.build();

			loyaltyService.pushToKafka(requestBean);
		}

		LoyaltyServiceRequest requestBean = new LoyaltyServiceRequest.LoyaltyServiceRequestBuilder(activeLoan.getMerchant().getId(), LoyaltyTransactionType.LENDING_EDI)
				.amount(amount)
				.merchantStoreId(null)
				.transactionId(activeLoan.getId())
				.build();

		loyaltyService.pushToKafka(requestBean);
	}

	public ResponseDTO verifyPayment(RequestDTO<PaymentResendOTP> requestDTO, Merchant merchant, String token) {
		LoanPaymentOrder loanPaymentOrder = loanPaymentOrderDao.findByOrderId(requestDTO.getPayload().getOrderId());
		if (loanPaymentOrder == null) {
			return new ResponseDTO(false, "Order not found");
		}
		if(!"PENDING".equalsIgnoreCase(loanPaymentOrder.getStatus())) {
			logger.error("Payment for merchant id {} and order id {} is already processed", loanPaymentOrder.getMerchant().getId(), loanPaymentOrder.getOrderId());
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
					adjustLoanBalance(activeLoan.get(), loanPaymentOrder.getAmount(), null);
					loanPaymentOrder.setStatus("SUCCESS");
					loanPaymentOrderDao.save(loanPaymentOrder);
				}
				return new ResponseDTO(true, "success");
			} else {
				logger.error("BPB verification failed for loan payment order:{}", loanPaymentOrder.getOrderId());
			}
		} catch (Exception e) {
			logger.error("Exception in payment verify", e);
		}
		return new ResponseDTO(false, "Payment verification Failed");
	}
}