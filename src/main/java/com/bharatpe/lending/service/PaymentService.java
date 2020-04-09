package com.bharatpe.lending.service;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.bharatpe.common.dao.MerchantBankDetailDao;
import com.bharatpe.common.dao.MerchantDao;
import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.common.entities.Merchant;
import com.bharatpe.common.entities.MerchantBankDetail;
import com.bharatpe.common.enums.LoyaltyTransactionType;
import com.bharatpe.common.enums.NotificationProvider;
import com.bharatpe.common.handlers.SmsServiceHandler;
import com.bharatpe.common.objects.LoyaltyServiceRequest;
import com.bharatpe.common.service.LoyaltyService;
import com.bharatpe.lending.dao.LendingLedgerDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dto.InitiatePaymentRequestDTO;
import com.bharatpe.lending.dto.InitiatePaymentResponseDTO;
import com.bharatpe.lending.dto.PaymentDetailsResponseDTO;
import com.bharatpe.lending.dto.RequestDTO;

@Service
public class PaymentService {

	Logger logger = LoggerFactory.getLogger(PaymentService.class);
	
	@Autowired
	LendingPaymentScheduleDao lendingPaymentScheduleDao;
	
	SimpleDateFormat sdf = new SimpleDateFormat("ddMMyyhhmmss");
	
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
	
	ExecutorService notificationExecutor = Executors.newFixedThreadPool(5);
	
	public PaymentDetailsResponseDTO getPaymentDetails(Merchant merchant) {
		logger.info("Received payment details request for merchant id {}", merchant.getId());
		try {
			LendingPaymentSchedule activeLoan = lendingPaymentScheduleDao.findByMerchantIdAndStatus(merchant.getId(), "ACTIVE");
			
			if(activeLoan == null) {
				logger.error("No active loan found for merchant id {}", merchant.getId());
				return new PaymentDetailsResponseDTO("No active loan found.");
			}
			
			Integer loanAmount = activeLoan.getLoanAmount().intValue();
			Integer overdueAmount = activeLoan.getDueAmount().intValue();
			Integer overdueDays = (activeLoan.getDueAmount().intValue()/activeLoan.getEdiAmount().intValue());
			Integer principalDueAmount = (int) Math.ceil(activeLoan.getLoanAmount() - activeLoan.getPaidPrinciple() + activeLoan.getDueInterest());
			
			boolean isPayable = true;
			if(overdueAmount < 1 && principalDueAmount > 100000) {
				isPayable = false;
			}
			
			PaymentDetailsResponseDTO.Data data= new PaymentDetailsResponseDTO.Data(loanAmount, overdueAmount, principalDueAmount, overdueDays, isPayable);
			return new PaymentDetailsResponseDTO(data);
			
		} catch(Exception ex) {
			logger.error("Execption while fetching payment details for merchant id {}, Exception is {}", merchant.getId(), ex);
		}
		
		return new PaymentDetailsResponseDTO("Something went wrong.");
	}
	
	public InitiatePaymentResponseDTO initiatePayment(Merchant merchant, RequestDTO<InitiatePaymentRequestDTO> request) {
		logger.info("Received initiate payment request  for merchant {} : {}", merchant.getId(), request);
		try {
			
			LendingPaymentSchedule activeLoan = lendingPaymentScheduleDao.findByMerchantIdAndStatus(merchant.getId(), "ACTIVE");
			
			if(activeLoan == null) {
				logger.error("No active loan found for merchant id {}", merchant.getId());
				return new InitiatePaymentResponseDTO("No active loan found.");
			}
			
			Integer overdueAmount = activeLoan.getDueAmount().intValue();
			Integer principalDueAmount = (int) Math.ceil(activeLoan.getLoanAmount() - activeLoan.getPaidPrinciple() + activeLoan.getDueInterest());
			
			Integer amount = 0;
			if("CUSTOM".equalsIgnoreCase(request.getPayload().getPaymentType())) {
				amount = request.getPayload().getAmount();
			} else if("PRINCIPAL".equalsIgnoreCase(request.getPayload().getPaymentType())) {
				amount = principalDueAmount;
			} else {
				amount = overdueAmount;
			}
			
			if(amount > 100000) {
				logger.error("Amount greater than 100000 for merchant id {}", merchant.getId());
				return new InitiatePaymentResponseDTO("Amount can not be greater than 100000.");
			}
			
			String orderId = activeLoan.getId() + sdf.format(new Date());
			
			Map vpaResponse = apiGatewayService.createVPA(merchant, Double.valueOf(amount), orderId);
			
			if(vpaResponse == null || !"OK".equalsIgnoreCase((String) vpaResponse.get("status"))) {
				logger.error("Create VPA not successful, retuning failure.");
				return new InitiatePaymentResponseDTO("Something went wrong.");
			}
			
			String vpa = (String) vpaResponse.get("bharatpeTxnId");
			String paymentLink = (String) vpaResponse.get("paymentLink");
			String intent = (String) vpaResponse.get("upiString");
			
			InitiatePaymentResponseDTO.Data data = new InitiatePaymentResponseDTO.Data(vpa, intent, paymentLink);
			return new InitiatePaymentResponseDTO(data);
		} catch(Exception ex) {
			logger.error("Execption while initiating payment for merchant id {}, Exception is {}", merchant.getId(), ex);
		}
		return new InitiatePaymentResponseDTO("Something went wrong.");
	}
	
	public String handleCallback(PaymentCallbackRequestDTO request) {
		logger.info("Received payment callback request for merchant {} : {}", request.getMerchantId(), request);
		try {
			
			Optional<Merchant> merchant = merchantDao.findById(request.getMerchantId());
			if(!merchant.isPresent()) {
				logger.error("Merchant not found with id {}", request.getMerchantId());
				return "OK";
			}
			
			if(request.getAmount() == null || request.getAmount() <= 0D) {
				logger.error("Invalid amount received for merchant {} and amount {}", request.getMerchantId(), request.getAmount());
				return "OK";
			}
			
			LendingPaymentSchedule activeLoan = lendingPaymentScheduleDao.findByMerchantIdAndStatus(merchant.get().getId(), "ACTIVE");
			
			if(activeLoan == null) {
				logger.error("No active loan found for merchant id {}", merchant.get().getId());
				return "OK";
			}
			
			List<LendingLedger> ledgers = lendingLedgerDao.findByLendingPaymentScheduleAndDescription(activeLoan, getDescription(request.getBankReferenceNo()));
			
			if(ledgers != null && !ledgers.isEmpty()) {
				logger.error("Payment alrady done for loan id {} and bank rrn {}", activeLoan.getId(), request.getBankReferenceNo());
				return "OK";
			}
 			
			Integer principalDueAmount = (int) Math.ceil(activeLoan.getLoanAmount() - activeLoan.getPaidPrinciple() + activeLoan.getDueInterest());
			
			Double paidInterestAmount = 0D;
			Double paidPrincipalAmount = 0D;
			
			if(principalDueAmount - request.getAmount() <= 1D) {
				
				paidInterestAmount = activeLoan.getDueInterest();
				paidPrincipalAmount = request.getAmount() - paidInterestAmount;
				
				if(activeLoan.getDueAmount() >= 0) {
					createLendingLedger(activeLoan, -1 * (request.getAmount() - activeLoan.getDueAmount()) , -1 * (request.getAmount() - activeLoan.getDueAmount()), 0D, "PREPAYMENT");
				} else {
					createLendingLedger(activeLoan, -1 * request.getAmount() , -1 * request.getAmount(), 0D, "PREPAYMENT");
				}
				
				activeLoan.setPaidAmount(activeLoan.getPaidAmount() + request.getAmount());
				activeLoan.setPaidInterest(activeLoan.getPaidInterest() + paidInterestAmount);
				activeLoan.setPaidPrinciple(activeLoan.getPaidPrinciple() + paidPrincipalAmount);

				activeLoan.setDueAmount(0D);
				activeLoan.setDueInterest(0D);
				activeLoan.setDuePrinciple(0D);
				
				activeLoan.setStatus("CLOSED");
			} else {
				activeLoan.setDueAmount(activeLoan.getDueAmount() - request.getAmount());
				activeLoan.setPaidAmount(activeLoan.getPaidAmount() + request.getAmount());
				
				Double balance = request.getAmount() - activeLoan.getDueInterest();
				
				paidInterestAmount = activeLoan.getDueInterest();
				paidPrincipalAmount = balance;
				activeLoan.setPaidInterest(activeLoan.getPaidInterest() + activeLoan.getDuePrinciple());
				activeLoan.setDueInterest(0D);
				activeLoan.setDuePrinciple(activeLoan.getDuePrinciple() - balance);
				activeLoan.setPaidPrinciple(activeLoan.getPaidPrinciple() + balance);
			}
					
			createLendingLedger(activeLoan, request.getAmount(), paidPrincipalAmount, paidInterestAmount,  getDescription(request.getBankReferenceNo()));
			lendingPaymentScheduleDao.save(activeLoan);
			
			notificationExecutor.submit(() -> sendSMS(merchant.get(), request.getAmount()));

			if("CLOSED".equalsIgnoreCase(activeLoan.getStatus())) {
				LoyaltyServiceRequest requestBean = new LoyaltyServiceRequest.LoyaltyServiceRequestBuilder(merchant.get().getId(), LoyaltyTransactionType.PRE_LOAN_CLOSURE)
	                    .amount(request.getAmount())
	                    .merchantStoreId(null)
	                    .transactionId(activeLoan.getId())
                    .build();
				
				loyaltyService.pushAsync(requestBean);
			} 
			
			LoyaltyServiceRequest requestBean = new LoyaltyServiceRequest.LoyaltyServiceRequestBuilder(merchant.get().getId(), LoyaltyTransactionType.LENDING_EDI)
	                    .amount(request.getAmount())
	                    .merchantStoreId(null)
	                    .transactionId(activeLoan.getId())
                    .build();
			
			loyaltyService.pushAsync(requestBean);

		} catch(Exception ex) {
			logger.error("Execption whilehandling payment callback for merchant id {}, Exception is {}", request.getMerchantId(), ex);
		}
		return "OK";
	}
	
	private void sendSMS(Merchant merchant, Double amount) {
		try {
			MerchantBankDetail merchantBankDetail = merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchant.getId(),"ACTIVE");
			if(merchantBankDetail == null) {
				return;
			}
			
			String content = "Hi " + merchantBankDetail.getBeneficiaryName() + ",\nYou have successfully made Pre-Payment your Rs." + amount.intValue() + " for your BharatPe Loan.";
			
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
}
