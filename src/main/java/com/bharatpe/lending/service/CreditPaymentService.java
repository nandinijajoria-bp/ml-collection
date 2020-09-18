package com.bharatpe.lending.service;

import com.bharatpe.common.dao.InternalClientDao;
import com.bharatpe.common.dao.LendingEDIScheduleDao;
import com.bharatpe.common.dao.MerchantBankDetailDao;
import com.bharatpe.common.dao.MerchantDao;
import com.bharatpe.common.entities.*;
import com.bharatpe.common.enums.Gateway;
import com.bharatpe.common.enums.Status;
import com.bharatpe.common.handlers.SmsServiceHandler;
import com.bharatpe.common.service.WhatsappNotificationService;
import com.bharatpe.common.utils.AesEncryption;
import com.bharatpe.common.utils.HmacCalculator;
import com.bharatpe.lending.common.dao.*;
import com.bharatpe.lending.common.entity.*;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.constant.CreditConstants;
import com.bharatpe.lending.dao.LendingLedgerDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.handlers.GupShupOTPHandler;
import com.bharatpe.lending.util.CreditUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import com.bharatpe.common.enums.NotificationProvider;

import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
@SuppressWarnings("unchecked")
public class CreditPaymentService {

    Logger logger= LoggerFactory.getLogger(CreditPaymentService.class);

    @Autowired
    RestTemplate restTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    CreditAccountDao creditAccountDao;

    @Autowired
    LendingClTransactionDao lendingClTransactionDao;

    @Autowired
    LendingClLedgerDao lendingClLedgerDao;

    @Autowired
    InternalClientDao internalClientDao;

    @Autowired
    HmacCalculator hmacCalculator;

    @Autowired
    AesEncryption aesEncryption;

    @Autowired
    LendingClPaymentDao lendingClPaymentDao;

    @Autowired
    LendingCaBalanceDetailDao lendingCaBalanceDetailDao;

    @Autowired
    CreditAccountBillDao creditAccountBillDao;

    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;

    @Autowired
    CreditUtil creditUtil;

    @Autowired
    SmsServiceHandler smsServiceHandler;
    
    @Autowired
    WhatsappNotificationService whatsappNotificationService;

    @Value("${create.vpa.endpoint}")
    String DYNAMIC_VPA_HOST;

    @Value("${internal.credit.merchant.id}")
    long merchantId;

    @Value("${payment.service.host}")
    public String PAYMENT_HOST;
    
    @Value("${upiPayment.cancel.endpoint}")
    public String CANCEL_PAYMENT_URL;
    
    @Value("${upiPayment.statusCheck.endpoint}")
    public String UPI_PAYMENT_STATUS_CHECK_URL;

    @Autowired
    MerchantDao merchantDao;

    @Autowired
    LendingEDIScheduleDao lendingEDIScheduleDao;

    @Autowired
    CreditLineTransaction creditLineTransaction;
    
    @Autowired
    CreditLineService creditLineService;

    private static String secret;

    private static String mid;
    
    private final DecimalFormat df = new DecimalFormat("#.##");
    
    @Autowired
    MerchantBankDetailDao merchantBankDetailDao;

    public ResponseDTO getPaymentModes(RequestDTO<CreditSpendRequestDTO> requestDTO, String token) {
        List<PaymentDetailDto> paymentDetails = new ArrayList<>();
        try {
            UriComponents requestUrl = UriComponentsBuilder.fromHttpUrl(PAYMENT_HOST + CreditConstants.PAYMENT_MODE_URL).build();
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
            headers.set("token", token);
            headers.set("clientName", "CREDIT_LINE");
            Map<String, Object> requestParams = new HashMap<>();
            requestParams.put("common_params", requestDTO.getMeta());
            requestParams.put("params", requestDTO.getSimInfo());

            HttpEntity<Object> entity = new HttpEntity<>(requestParams,headers);
            long startTime = System.currentTimeMillis();
            ResponseEntity<Object> response=null;
            int retry=0;
            while(retry<3) {
            	try {
            		response= restTemplate.exchange(requestUrl.encode().toUri(), HttpMethod.POST, entity, Object.class);
                	if(response.getBody()!=null) {
                		break;
                	}
            	}
            	catch(Exception e) {
            		logger.error("Error occured while fetching payment mode",e);
            	}
            	
            	retry++;
            }
            
            logger.info("Response : {} ", objectMapper.writeValueAsString(response.getBody()));
            if (response.getBody() != null) {
                paymentDetails = objectMapper.readValue(objectMapper.writeValueAsString(((Map<String, Object>) response.getBody()).get("data")),
                        new TypeReference<List<PaymentDetailDto>>() {
                        });
            }
            logger.info("Successfully Fetched Balance info in {} ms", System.currentTimeMillis() - startTime);
        } catch (HttpClientErrorException e) {
            logger.error("Error fetching Balance info : {} ", e.getMessage());
        } catch (Exception e) {
            logger.error("Error parsing details from bbps : {} ", e.getMessage());
        } finally {
            if (paymentDetails.isEmpty()) {
                logger.info("No Payment Mode Received, Falling Back to UPI Payment Mode");
                paymentDetails.addAll(fetchPaymentModes());
            }
        }
        ResponseDTO responseDTO = new ResponseDTO();
        paymentDetails.removeIf(paymentDetailDto -> paymentDetailDto.getBalance() != null && paymentDetailDto.getBalance() < requestDTO.getPayload().getAmount());
        if (paymentDetails.isEmpty()) {
            responseDTO.setSuccess(false);
            responseDTO.setMessage("No Payment Mode Found");
        } else {
        	paymentDetails=checkForUpiTransaction(requestDTO.getPayload().getAmount(), paymentDetails);
            responseDTO.setSuccess(true);
            responseDTO.setData(paymentDetails);
        }
        return responseDTO;
    }
    
    private List<PaymentDetailDto> checkForUpiTransaction(Integer amount,List<PaymentDetailDto> paymentDetails){
    	paymentDetails.forEach(pd->{
    		if(pd.getType().equalsIgnoreCase("UPI")) {
    			pd.setUpiType(amount>2000?"collect_request":"intent");
    		}
    	});
    	return paymentDetails;
    } 

    private List<PaymentDetailDto> fetchPaymentModes() {
        List<PaymentDetailDto> paymentDetails = new ArrayList<>();
        try {
            String content = "[\n" +
                    "  {\n" +
                    "            \"name\": \"Pay Using UPI\",\n" +
                    "            \"type\": \"UPI\",\n" +
                    "            \"fund_source\": \"UPI\",\n" +
                    "            \"balance\": null,\n" +
                    "            \"amount_limit\": 100000.0,\n" +
                    "            \"description\": null,\n" +
                    "            \"offers\": null,\n" +
                    "            \"auth_type\": null,\n" +
                    "            \"psps\": [\n" +
                    "                \"com.google.android.apps.nbu.paisa.user\",\n" +
                    "                \"net.one97.paytm\",\n" +
                    "                \"in.org.npci.upiapp\",\n" +
                    "                \"com.csam.icici.bank.imobile\",\n" +
                    "                \"com.mobikwik_new\",\n" +
                    "                \"com.myairtelapp\",\n" +
                    "                \"com.phonepe.app\",\n" +
                    "                \"com.olacabs.customer\"\n" +
                    "            ],\n" +
                    "            \"auth_required\": false,\n" +
                    "            \"default\": false,\n" +
                    "            \"enable\": true,\n" +
                    "            \"initiate_sb\": false,\n" +
                    "            \"sb_link\": null\n" +
                    "        }\n" +
                    "]";
            paymentDetails = objectMapper.readValue(content, new TypeReference<List<PaymentDetailDto>>() {
            });
        } catch (Exception e) {
            logger.error("Error Parsing payment Modes : {}", e.getMessage());
        }
        return paymentDetails;
    }

    public PaymentInitiateResponseDTO initiatePayment(RequestDTO<CreditPaymentRequestDTO> requestDTO, Merchant merchant, String token) throws JsonProcessingException {
        CreditAccount creditAccount = creditAccountDao.findByMerchantIdForDashBoard(merchant.getId());
        if (creditAccount == null) {
            return new PaymentInitiateResponseDTO(false, "Credit Account does not exist");
        }
        if (requestDTO.getPayload().getAmount() > creditUtil.getPayableAmount(creditAccount)) {
            return new PaymentInitiateResponseDTO(false, "Amount more than total payable amount");
        }
        LendingClTransaction lendingClTransaction = creditLineTransaction.createCreditTxn(creditAccount, requestDTO.getPayload().getAmount(), requestDTO.getPayload().getSource().name());
        String upiString = null;
        String request = null;
        String response = null;
        String vpa = null;
        Boolean otpFlow = null;
        String authMode = null;
        boolean paymentSuccess = false;
        String orderId = null;
        String mid = null;
        if (requestDTO.getPayload().getType().equals(CreditConstants.PaymentMode.BPB)) {
            Map<String, Object> result = initiateTxn(requestDTO, lendingClTransaction.getId(), token, "BharatPe Loans", requestDTO.getPayload().getSource().name());
            Boolean success = (Boolean) result.get("success");
            if (success) {
                otpFlow = (Boolean) result.get("otp_flow");
                authMode = (String) result.get("auth_mode");
                request = (String) result.get("request");
                response = (String) result.get("response");
                orderId = result.get("bp_txn_id").toString();
                paymentSuccess = true;
            }
        } else { //UPI
        	Map<String, Object> paymentResponse;
        	if(requestDTO.getPayload().getAmount()>2000) {
        		if(requestDTO.getPayload().getVpa()==null) {
        			return new PaymentInitiateResponseDTO(false, "VPA missing");
        		}
        		paymentResponse= createVPA(requestDTO.getPayload().getAmount(), lendingClTransaction.getId().toString(),requestDTO.getPayload().getVpa());
        	}
        	else {
        		paymentResponse = createVPA(requestDTO.getPayload().getAmount(), lendingClTransaction.getId().toString(),null);
        	}
            if (paymentResponse != null) {
                request = (String) paymentResponse.get("request");
                response = objectMapper.writeValueAsString(paymentResponse.get("response"));
                VPARequestDto vpaRequestDto = (VPARequestDto) paymentResponse.get("response");
                if (vpaRequestDto != null) {
                    vpa = vpaRequestDto.getBharatpeTxnId();
                    upiString = vpaRequestDto.getUpiString();
                    paymentSuccess = true;
                    mid = getMid();
                }
            }
        }
        if (paymentSuccess) {
            lendingClTransaction.setStatus(CreditConstants.PaymentStatus.PENDING.name());
            lendingClTransaction.setOrderId(orderId);
            LendingClPayment lendingClPayment = creditLineTransaction.insertClPayment(lendingClTransaction, request, response, mid, vpa, requestDTO.getPayload().getSource().name(), requestDTO.getPayload().getType().name());
            lendingClTransaction.setRequestId(lendingClPayment.getId());
            creditLineTransaction.saveTxn(lendingClTransaction);
            return new PaymentInitiateResponseDTO(lendingClTransaction.getId(), upiString, otpFlow, authMode);
        } else {
            logger.error("Payment Failed for Txn Id : {}", lendingClTransaction.getId());
            lendingClTransaction.setStatus(CreditConstants.PaymentStatus.FAILED.name());
            creditLineTransaction.saveTxn(lendingClTransaction);
            return new PaymentInitiateResponseDTO(false, "Payment Failed");
        }
    }

    public CreditSpendResponseDTO verifyPayment(RequestDTO<CreditSpendVerifyRequestDTO> requestDTO, Merchant merchant, String token) {
        CreditAccount creditAccount = creditAccountDao.findByMerchantIdForDashBoard(merchant.getId());
        if (creditAccount == null) {
            return new CreditSpendResponseDTO(false, "Credit Account does not exist");
        }
        LendingClTransaction lendingClTransaction = lendingClTransactionDao.findByIdAndCreditAccountId(requestDTO.getPayload().getRequestId(), creditAccount.getId());
        if (lendingClTransaction == null || !CreditConstants.PaymentStatus.PENDING.name().equalsIgnoreCase(lendingClTransaction.getStatus())) {
            return new CreditSpendResponseDTO(false, "Invalid request id");
        }
        int lockTxn = lendingClTransactionDao.updateStatusForPendingTxn(CreditConstants.PaymentStatus.CALLBACK_RECEIVED.name(), lendingClTransaction.getId());
        if (lockTxn != 1) {
            return new CreditSpendResponseDTO(false, "Invalid request id");
        }
        Map<String, Object> result = verifyTxn(requestDTO, token);
        Boolean success = (Boolean) result.get("success");
        if (success) {
            String response = (String) result.get("response");
            Double paymentAmount = (Double) result.get("amount");
            String paymentStatus = (String) result.get("status");
            Long transactionId = Long.parseLong((String) result.get("order_id"));
            LendingClPayment lendingClPayment = lendingClPaymentDao.findByClTransactionId(lendingClTransaction.getId());
            if (lendingClPayment != null) {
                lendingClPayment.setResponse(response);
                lendingClPayment.setStatus(paymentStatus);
                creditLineTransaction.updateCLPayment(lendingClPayment);
            }
            if (CreditConstants.PaymentStatus.FAILED.name().equals(paymentStatus) || !transactionId.equals(lendingClTransaction.getId()) || !lendingClTransaction.getAmount().equals(paymentAmount)) {
                lendingClTransactionDao.updateStatus(CreditConstants.PaymentStatus.FAILED.name(), lendingClTransaction.getId());
                creditLineService.sendFiledTransNotification(lendingClTransaction, merchant);
            } else if (CreditConstants.PaymentStatus.SUCCESS.name().equals(paymentStatus)) {
                updateBalances(creditAccount, lendingClTransaction);
            }
            return new CreditSpendResponseDTO(true, "success");
        } else {
            logger.error("BPB verification failed for txn:{}", lendingClTransaction.getId());
            lendingClTransactionDao.updateStatus(CreditConstants.PaymentStatus.PENDING.name(), lendingClTransaction.getId());
        }
        return new CreditSpendResponseDTO(false, "Payment verification Failed");
    }
    
    public void sendNotification(LendingClTransaction lendingClTransaction){
        Optional<Merchant> merchant = merchantDao.findById(lendingClTransaction.getMerchantId());
        CreditAccount creditAccount = creditAccountDao.findByMerchantIdForDashBoard(lendingClTransaction.getMerchantId());
    	String message="Hi "+getBenefecieryName(merchant.get())+",\nRs."+Double.valueOf(df.format(lendingClTransaction.getAmount()))+" repayment towards outstanding Bharatpe Loan is successful.\n"+
    					"Available Loan Balance now is Rs. " +Double.valueOf(df.format(creditAccount.getAvailableBalance())) +"\n.Click Here:: "+CreditConstants.MESSAGE_NOTIFICATION_LINK;
    	List<String> mobiles=new LinkedList<>();
    	mobiles.add(merchant.get().getMobile());
    	smsServiceHandler.sendSMS(mobiles, message, NotificationProvider.SMS.GUPSHUP);
		whatsappNotificationService.send(merchant.get(), null, message+" for more details.", mobiles, null);
    }
    
    public String getBenefecieryName(Merchant merchant) {
    	
    	MerchantBankDetail merchantBankDetail=merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchant.getId(), "ACTIVE");
    	if(merchantBankDetail!=null) {
    		return merchantBankDetail.getBeneficiaryName();
    	}
    	return "";
    }
    
    public PaymentInitiateResponseDTO resendOTP(RequestDTO<CreditSpendVerifyRequestDTO> requestDTO, Merchant merchant, String token) {
        CreditAccount creditAccount = creditAccountDao.findByMerchantIdForDashBoard(merchant.getId());
        if (creditAccount == null) {
            return new PaymentInitiateResponseDTO(false, "Credit Account does not exist");
        }
        LendingClTransaction lendingClTransaction = lendingClTransactionDao.findByIdAndCreditAccountId(requestDTO.getPayload().getRequestId(), creditAccount.getId());
        if (lendingClTransaction == null || !CreditConstants.PaymentStatus.PENDING.name().equalsIgnoreCase(lendingClTransaction.getStatus())) {
            return new PaymentInitiateResponseDTO(false, "Invalid request id");
        }
        Map<String, Object> result = sendOTP(requestDTO, token);
        Boolean success = (Boolean) result.get("success");
        if (success) {
            return new PaymentInitiateResponseDTO(true, "success");
        }
        return new PaymentInitiateResponseDTO(false, "Unable to resend otp");
    }

    private Map<String, Object> sendOTP(RequestDTO<CreditSpendVerifyRequestDTO> requestDTO, String token) {
        Map<String, Object> result = new HashMap<>();
        InternalClient internalClient = internalClientDao.findByClientName("CREDIT_LINE");
        try {
            Map requestParams = generateSendMoneyVerify(requestDTO);
            String hash = hmacCalculator.calculateHmac(hmacCalculator.getPayload(requestParams), aesEncryption.decrypt(internalClient.getSecret()));
            UriComponents requestUrl = UriComponentsBuilder.fromHttpUrl(PAYMENT_HOST + CreditConstants.BP_BALANCE_RESEND_OTP_URL).build();
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
            headers.setCacheControl(CacheControl.noCache());
            headers.set("token", token);
            headers.set("hash", hash);
            headers.set("clientName", "CREDIT_LINE");

            HttpEntity<Object> entity = new HttpEntity<>(requestParams, headers);
            result.put("request", objectMapper.writeValueAsString(entity));
            long startTime = System.currentTimeMillis();
            int retryCount=0;
            while(retryCount<3) {
            	try {
            		ResponseEntity<Object> response = restTemplate.exchange(requestUrl.encode().toUri(), HttpMethod.POST, entity, Object.class);
                    result.put("response", objectMapper.writeValueAsString(response.getBody()));
                    logger.info("Response : {} ", objectMapper.writeValueAsString(response.getBody()));
                    if(response.getBody()!=null) {
                    	result.put("success", ((Map<String, Object>) response.getBody()).get("success"));
                        logger.info("Successfully resend otp for BP Balance in {} ms", System.currentTimeMillis() - startTime);
                        return result;
                    } 
            	}
            	catch(Exception e) {
            		logger.error("Error occured while sending otp",e);
            	}
            	retryCount++;
            }
            
        } catch (HttpClientErrorException e) {
            result.put("success", Boolean.FALSE);
            logger.error("Error resend otp for BP Balance info---", e);
        } catch (Exception e) {
            result.put("success", Boolean.FALSE);
            logger.error("Error parsing details from BP Balance---", e);
        }
        return result;
    }

    private void updateBalances(CreditAccount creditAccount, LendingClTransaction lendingClTransaction) {
        logger.info("Repayment for amount:{} for account:{}", lendingClTransaction.getAmount(), creditAccount.getId());
        LendingCaBalanceDetail lendingCaBalanceDetail = lendingCaBalanceDetailDao.findByMerchantIdAndCreditAccountId(creditAccount.getMerchantId(), creditAccount.getId());
        double remainingAmount = lendingClTransaction.getAmount();
        CreditAccountBill creditAccountBill = creditAccountBillDao.getLastUnpaidBill(creditAccount.getId(), creditAccount.getMerchantId());
        double paymentCL = 0d;
        double clPenalty = 0d;
        double clInterest = 0d;
        double clPrinciple = 0d;
        double clOtherCharges = 0d;
        double paymentTL = 0d;
        double tlPenalty = 0d;
        double tlInterest = 0d;
        double tlPrinciple = 0d;
        double tlOtherCharges = 0d;
        double newMAD = creditAccount.getMinimumAmountDue();
        List<LendingClLedger> lendingClLedgerList = new ArrayList<>();
        List<LendingClPaymentBreakup> lendingClPaymentBreakups = new ArrayList<>();
        Map<Long, LendingPaymentSchedule> lendingPaymentScheduleMap = new LinkedHashMap<>();
        List<LendingLedger> lendingLedgers = new ArrayList<>();
        //Clear unpaid bill balances
        if (creditAccountBill != null) {
            logger.info("Adjusting Bill:{} for account:{}", creditAccountBill.getId(), creditAccount.getId());
            double remainingPenalty = creditAccountBill.getPenalty() - creditAccountBill.getPaidPenalty();
            double remainingInterest = creditAccountBill.getInterestAmount() - creditAccountBill.getPaidInterest();
            double remainingPrinciple = creditAccountBill.getPrincipleAmount() - creditAccountBill.getPaidPrinciple();
            if (remainingAmount > 0 && remainingPenalty > 0) {
                logger.info("Adjusting penalty for bill:{} and account:{}", creditAccountBill.getId(), creditAccount.getId());
                double remaining = remainingPenalty - remainingAmount < 0 ? 0 : remainingPenalty - remainingAmount;
                remainingAmount = remainingAmount - remainingPenalty < 0 ? 0 : remainingAmount - remainingPenalty;
                paymentCL += (creditAccountBill.getPenalty() - remaining);
                clPenalty += (creditAccountBill.getPenalty() - remaining);
                creditAccountBill.setPaidPenalty((creditAccountBill.getPenalty() - remaining));
            }
            if (remainingAmount > 0 && remainingInterest > 0) {
                logger.info("Adjusting interest for bill:{} and account:{}", creditAccountBill.getId(), creditAccount.getId());
                double remaining = remainingInterest - remainingAmount < 0 ? 0 : remainingInterest - remainingAmount;
                remainingAmount = remainingAmount - remainingInterest < 0 ? 0 : remainingAmount - remainingInterest;
                paymentCL += (creditAccountBill.getInterestAmount() - remaining);
                clInterest += (creditAccountBill.getInterestAmount() - remaining);
                creditAccountBill.setPaidInterest((creditAccountBill.getInterestAmount() - remaining));
            }
            if (remainingAmount > 0 && creditAccountBill.getPrincipleAmount() > 0) {
                logger.info("Adjusting principle mad for bill:{} and account:{}", creditAccountBill.getId(), creditAccount.getId());
                double remaining = remainingPrinciple - remainingAmount < 0 ? 0 : remainingPrinciple - remainingAmount;
                remainingAmount = remainingAmount - remainingPrinciple < 0 ? 0 : remainingAmount - remainingPrinciple;
                double principlePaid = (remainingPrinciple - remaining);
                paymentCL += principlePaid;
                clPrinciple += principlePaid;
                creditAccountBill.setPaidPrinciple(creditAccountBill.getPaidPrinciple() + principlePaid);
            }
            newMAD = (creditAccountBill.getPenalty() - creditAccountBill.getPaidPenalty()) + (creditAccountBill.getInterestAmount() - creditAccountBill.getPaidInterest()) + (creditAccountBill.getPrincipleAmount() - creditAccountBill.getPaidPrinciple());
            logger.info("New MAD:{} for account:{}", newMAD, creditAccount.getId());
            creditAccountBill.setPaidAmount(creditAccountBill.getPaidPrinciple() + creditAccountBill.getPaidInterest() + creditAccountBill.getPaidPenalty());
        }
        //"ClearTerm loan EDIs O/s"
        if (remainingAmount > 0) {
            logger.info("Adjusting overdue tl for account:{}", creditAccount.getId());
            List<LendingPaymentSchedule> termLoanList=lendingPaymentScheduleDao.findByMerchantIdAndStatusAndCreditLoan(creditAccount.getMerchantId(), "ACTIVE", true);
            termLoanList.sort(Comparator.comparing(LendingPaymentSchedule::getId));
            for (LendingPaymentSchedule lendingPaymentSchedule : termLoanList) {
                lendingPaymentScheduleMap.put(lendingPaymentSchedule.getId(), lendingPaymentSchedule);
                if (lendingPaymentSchedule.getDueAmount() != null && lendingPaymentSchedule.getDueAmount() > 0) {
                    double totalPaid = 0d;
                    double paidOtherCharges = 0d;
                    double paidPenalty = 0d;
                    double paidInterest = 0d;
                    double paidPrinciple = 0d;
                    if (remainingAmount > 0 && lendingPaymentSchedule.getDueOtherCharges() != null && lendingPaymentSchedule.getDueOtherCharges() > 0) {
                        logger.info("Adjusting due other charges for loan:{} and account:{}", lendingPaymentSchedule.getId(), creditAccount.getId());
                        double dueOtherCharges = lendingPaymentSchedule.getDueOtherCharges();
                        double remaining = lendingPaymentSchedule.getDueOtherCharges() - remainingAmount < 0 ? 0 : lendingPaymentSchedule.getDueOtherCharges() - remainingAmount;
                        remainingAmount = remainingAmount - lendingPaymentSchedule.getDueOtherCharges() < 0 ? 0 : remainingAmount - lendingPaymentSchedule.getDueOtherCharges();
                        lendingPaymentSchedule.setDueOtherCharges(remaining);
                        paidOtherCharges = (dueOtherCharges - remaining);
                        totalPaid += paidOtherCharges;
                        tlOtherCharges += paidOtherCharges;
                        paymentTL += paidOtherCharges;
                        lendingPaymentSchedule.setPaidOtherCharges(lendingPaymentSchedule.getPaidOtherCharges() + paidOtherCharges);
                        lendingPaymentSchedule.setPaidAmount(lendingPaymentSchedule.getPaidAmount() + paidOtherCharges);
                    }
                    if (remainingAmount > 0 && lendingPaymentSchedule.getDuePenalty() != null && lendingPaymentSchedule.getDuePenalty() > 0) {
                        logger.info("Adjusting due penalty for loan:{} and account:{}", lendingPaymentSchedule.getId(), creditAccount.getId());
                        double duePenalty = lendingPaymentSchedule.getDuePenalty();
                        double remaining = lendingPaymentSchedule.getDuePenalty() - remainingAmount < 0 ? 0 : lendingPaymentSchedule.getDuePenalty() - remainingAmount;
                        remainingAmount = remainingAmount - lendingPaymentSchedule.getDuePenalty() < 0 ? 0 : remainingAmount - lendingPaymentSchedule.getDuePenalty();
                        lendingPaymentSchedule.setDuePenalty(remaining);
                        paidPenalty = (duePenalty - remaining);
                        totalPaid += paidPenalty;
                        tlPenalty += paidPenalty;
                        paymentTL += paidPenalty;
                        lendingPaymentSchedule.setPaidPenalty(lendingPaymentSchedule.getPaidPenalty() + paidPenalty);
                        lendingPaymentSchedule.setPaidAmount(lendingPaymentSchedule.getPaidAmount() + paidPenalty);
                    }
                    if (remainingAmount > 0 && lendingPaymentSchedule.getDueInterest() != null && lendingPaymentSchedule.getDueInterest() > 0) {
                        logger.info("Adjusting due interest for loan:{} and account:{}", lendingPaymentSchedule.getId(), creditAccount.getId());
                        double dueInterest = lendingPaymentSchedule.getDueInterest();
                        double remaining = lendingPaymentSchedule.getDueInterest() - remainingAmount < 0 ? 0 : lendingPaymentSchedule.getDueInterest() - remainingAmount;
                        remainingAmount = remainingAmount - lendingPaymentSchedule.getDueInterest() < 0 ? 0 : remainingAmount - lendingPaymentSchedule.getDueInterest();
                        lendingPaymentSchedule.setDueInterest(remaining);
                        paidInterest = (dueInterest - remaining);
                        totalPaid += paidInterest;
                        tlInterest += paidInterest;
                        paymentTL += paidInterest;
                        lendingPaymentSchedule.setPaidInterest(lendingPaymentSchedule.getPaidInterest() + paidInterest);
                        lendingPaymentSchedule.setPaidAmount(lendingPaymentSchedule.getPaidAmount() + paidInterest);
                    }
                    if (remainingAmount > 0 && lendingPaymentSchedule.getDuePrinciple() != null && lendingPaymentSchedule.getDuePrinciple() > 0) {
                        logger.info("Adjusting due principle for loan:{} and account:{}", lendingPaymentSchedule.getId(), creditAccount.getId());
                        double duePrinciple = lendingPaymentSchedule.getDuePrinciple();
                        double remaining = lendingPaymentSchedule.getDuePrinciple() - remainingAmount < 0 ? 0 : lendingPaymentSchedule.getDuePrinciple() - remainingAmount;
                        remainingAmount = remainingAmount - lendingPaymentSchedule.getDuePrinciple() < 0 ? 0 : remainingAmount - lendingPaymentSchedule.getDuePrinciple();
                        lendingPaymentSchedule.setDuePrinciple(remaining);
                        paidPrinciple = (duePrinciple - remaining);
                        totalPaid += paidPrinciple;
                        tlPrinciple += paidPrinciple;
                        paymentTL += paidPrinciple;
                        lendingPaymentSchedule.setPaidPrinciple(lendingPaymentSchedule.getPaidPrinciple() + paidPrinciple);
                        lendingPaymentSchedule.setPaidAmount(lendingPaymentSchedule.getPaidAmount() + paidPrinciple);
                    }
                    double dueOtherCharges = lendingPaymentSchedule.getDueOtherCharges() != null ? lendingPaymentSchedule.getDueOtherCharges() : 0d;
                    double duePenalty = lendingPaymentSchedule.getDuePenalty() != null ? lendingPaymentSchedule.getDuePenalty() : 0d;
                    double dueInterest = lendingPaymentSchedule.getDueInterest() != null ? lendingPaymentSchedule.getDueInterest() : 0d;
                    double duePrinciple = lendingPaymentSchedule.getDuePrinciple() != null ? lendingPaymentSchedule.getDuePrinciple() : 0d;
                    double newDueAmount = dueOtherCharges + duePenalty + dueInterest + duePrinciple;
                    logger.info("New due amount:{} for loan:{} and account:{}", newDueAmount, lendingPaymentSchedule.getId(), creditAccount.getId());
                    lendingPaymentSchedule.setDueAmount(newDueAmount);
                    if (totalPaid > 0) {
                        lendingClPaymentBreakups.add(creditLineTransaction.createPaymentBreakup(lendingClTransaction, totalPaid, lendingPaymentSchedule.getId(), CreditConstants.PaymentType.TL.name()));
                        lendingLedgers.add(createLendingLedger(lendingPaymentSchedule, DateTimeUtil.getCurrentDayStartTime(), Status.LendingTransactionType.EDI.toString(), totalPaid, paidPrinciple, paidInterest, paidOtherCharges, paidPenalty, "CREDIT_LINE", lendingClTransaction.getSubType()));
                    }
                }
            }
        }
        //"Clear Remaining CL Interest due"
        double adjustedInterest = 0d;
        if (remainingAmount > 0 && creditAccount.getInterestDue() > 0) {
            logger.info("Adjusting interest due for account:{}", creditAccount.getId());
            double remaining = creditAccount.getInterestDue() - remainingAmount < 0 ? 0 : creditAccount.getInterestDue() - remainingAmount;
            remainingAmount = remainingAmount - creditAccount.getInterestDue() < 0 ? 0 : remainingAmount - creditAccount.getInterestDue();
            paymentCL += (creditAccount.getInterestDue() - remaining);
            clInterest += (creditAccount.getInterestDue() - remaining);
            adjustedInterest += (creditAccount.getInterestDue() - remaining);
        }
        //"Clear Remaining CL Principal o/s"
        if (remainingAmount > 0) {
            logger.info("Adjusting due cl principle for account:{}", creditAccount.getId());
            double remaining = lendingCaBalanceDetail.getUsedBalanceCl() - remainingAmount < 0 ? 0 : lendingCaBalanceDetail.getUsedBalanceCl() - remainingAmount;
            remainingAmount = remainingAmount - lendingCaBalanceDetail.getUsedBalanceCl() < 0 ? 0 : remainingAmount - lendingCaBalanceDetail.getUsedBalanceCl();
            double clPaid = (lendingCaBalanceDetail.getUsedBalanceCl() - remaining);
            paymentCL += clPaid;
            clPrinciple += clPaid;
        }
        //"Clear Term loan Principle"
        if (remainingAmount > 0) {
            logger.info("Adjusting principle tl for account:{}", creditAccount.getId());
            for (LendingPaymentSchedule lendingPaymentSchedule : lendingPaymentScheduleMap.values()) {
                if (remainingAmount > 0) {
                    double totalPaid = 0d;
                    if ((lendingPaymentSchedule.getLoanAmount() - lendingPaymentSchedule.getPaidPrinciple() + lendingPaymentSchedule.getDueInterest()) <= remainingAmount) {
                        logger.info("Closing loan:{} for account:{}", lendingPaymentSchedule.getId(), creditAccount.getId());
                        totalPaid = (lendingPaymentSchedule.getLoanAmount() - lendingPaymentSchedule.getPaidPrinciple() + lendingPaymentSchedule.getDueInterest());
                        tlPrinciple += totalPaid;
                        paymentTL += totalPaid;
                        remainingAmount = remainingAmount - totalPaid < 0 ? 0 : remainingAmount - totalPaid;
                        lendingPaymentSchedule.setPaidAmount(lendingPaymentSchedule.getPaidAmount() + totalPaid);
                        lendingPaymentSchedule.setPaidPrinciple(lendingPaymentSchedule.getPaidPrinciple() + totalPaid);
                    } else {
                        List<LendingEDISchedule> ediSchedules = lendingEDIScheduleDao.findByLendingPaymentSchedule(lendingPaymentSchedule);
                        if (ediSchedules == null || ediSchedules.isEmpty()) {
                            logger.error("Edi Schedule not found for loan id:{}", lendingPaymentSchedule.getId());
                            continue;
                        }
                        ediSchedules.sort(Comparator.comparing(LendingEDISchedule::getInstallmentNumber));
                        int ediPaidCount = lendingPaymentSchedule.getEdiCount() - lendingPaymentSchedule.getEdiRemainingCount();
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
                            if (principleAdjusted >= remainingAmount) {
                                double extraAmount = principleAdjusted - remainingAmount;
                                if (extraAmount > 0) {
                                    LendingEDISchedule lastSchedule = ediSchedules.get(ediSchedules.size()-1);
                                    lastSchedule.setPrinciple(lastSchedule.getPrinciple() + extraAmount);
                                    lastSchedule.setInterest(lastSchedule.getInterest() + ediSchedule.getInterest());
                                    lendingEDIScheduleDao.save(lastSchedule);
                                    principleAdjusted -= extraAmount;
                                }
                                break;
                            }
                        }
                        if (principleAdjusted > 0) {
                            totalPaid = principleAdjusted;
                            tlPrinciple += totalPaid;
                            paymentTL += totalPaid;
                            remainingAmount = remainingAmount - totalPaid < 0 ? 0 : remainingAmount - totalPaid;
                            lendingPaymentSchedule.setEdiRemainingCount(lendingPaymentSchedule.getEdiRemainingCount() - ediCount);
                            lendingPaymentSchedule.setPaidAmount(lendingPaymentSchedule.getPaidAmount() + totalPaid);
                            lendingPaymentSchedule.setPaidPrinciple(lendingPaymentSchedule.getPaidPrinciple() + totalPaid);
                            lendingPaymentSchedule.setTotalPayableAmount(lendingPaymentSchedule.getTotalPayableAmount() - interestAdjusted);
                        }
                    }
                    if (totalPaid > 0) {
                        lendingLedgers.add(createLendingLedger(lendingPaymentSchedule, DateTimeUtil.getCurrentDayStartTime(), Status.LendingTransactionType.EDI.toString(), totalPaid, totalPaid, 0d, 0d, 0d, "CREDIT_LINE", lendingClTransaction.getSubType()));
                        lendingClPaymentBreakups.add(creditLineTransaction.createPaymentBreakup(lendingClTransaction, totalPaid, lendingPaymentSchedule.getId(), CreditConstants.PaymentType.TL.name()));
                    }
                }
            }
        }
        if (paymentCL > 0) {
            lendingClPaymentBreakups.add(creditLineTransaction.createPaymentBreakup(lendingClTransaction, paymentCL, null, CreditConstants.PaymentType.CL.name()));
            lendingClLedgerList.add(creditLineTransaction.createClLedger(lendingClTransaction, CreditConstants.PaymentType.CL.name(), paymentCL, clPrinciple, clInterest, clPenalty, clOtherCharges));
        }
        if (paymentTL > 0) {
            lendingClLedgerList.add(creditLineTransaction.createClLedger(lendingClTransaction, CreditConstants.PaymentType.TL.name(), paymentTL, tlPrinciple, tlInterest, tlPenalty, tlOtherCharges));
        }
        creditLineTransaction.creditRepayment(lendingClTransaction, creditAccount, lendingCaBalanceDetail, creditAccountBill, lendingClLedgerList, lendingClPaymentBreakups, lendingPaymentScheduleMap.values(), lendingLedgers, clPrinciple + tlPrinciple, adjustedInterest, clPrinciple, newMAD);
        sendNotification(lendingClTransaction);
    }
    
    public LendingLedger createLendingLedger(LendingPaymentSchedule lendingPaymentSchedule, Date date, String txnType, Double amount, Double principle, Double interest, Double otherCharges, Double penalty, String description, String adjustmentMode) {
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
        return lendingLedger;
    }

    private Map<String, Object> initiateTxn(RequestDTO<CreditPaymentRequestDTO> requestDTO, Long txnId, String token, String beneficiaryName, String paymentSource) {
        Map<String, Object> result = new HashMap<>();
        InternalClient internalClient = internalClientDao.findByClientName("CREDIT_LINE");
        try {
            Map requestParams = generateBPBRequest(requestDTO, txnId, beneficiaryName, paymentSource);
            String hash = hmacCalculator.calculateHmac(hmacCalculator.getPayload(requestParams), aesEncryption.decrypt(internalClient.getSecret()));
            UriComponents requestUrl = UriComponentsBuilder.fromHttpUrl(PAYMENT_HOST + CreditConstants.BP_BALANCE_CREATE_TXN_URL).build();
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
            headers.setCacheControl(CacheControl.noCache());
            headers.set("token", token);
            headers.set("hash", hash);
            headers.set("clientName", "CREDIT_LINE");

            HttpEntity<Object> entity = new HttpEntity<>(requestParams, headers);
            result.put("request", objectMapper.writeValueAsString(entity));
            long startTime = System.currentTimeMillis();
            int retryCount=0;
            while(retryCount<3) {
            	try {
                    ResponseEntity<Object> response = restTemplate.exchange(requestUrl.encode().toUri(), HttpMethod.POST, entity, Object.class);
                    if (response.getBody() != null) {
                        result.put("response", objectMapper.writeValueAsString(response.getBody()));
                        logger.info("Response : {} ", objectMapper.writeValueAsString(response.getBody()));
                        result.put("success", ((Map<String, Object>) response.getBody()).get("success"));
                        Map<String, Object> responseData = (Map<String, Object>) ((Map<String, Object>) response.getBody()).get("data");
                        result.put("otp_flow", responseData.get("otp_flow"));
                        result.put("auth_mode", responseData.get("auth_mode"));
                        result.put("bp_txn_id", responseData.get("bp_txn_id"));
                        logger.info("Successfully created txn for BP Balance in {} ms", System.currentTimeMillis() - startTime);
                        return result;
                    }
                } catch (Exception e) {
                    logger.error("Error Starting txn for BP Balance info---", e);
                }
            	retryCount++;
            }
            
        } catch (HttpClientErrorException e) {
            result.put("success", Boolean.FALSE);
            logger.error("Error Starting txn for BP Balance info---", e);
        } catch (Exception e) {
            result.put("success", Boolean.FALSE);
            logger.error("Error parsing details from BP Balance---", e);
        }
        return result;
    }

    private Map<String, Object> generateBPBRequest(RequestDTO<CreditPaymentRequestDTO> requestDTO, Long txnId, String beneficiaryName, String paymentSource) {
        Map<String, Object> requestParams = new HashMap<>();
        Map<String, Object> params = new HashMap<>();
        Map<String, Object> commonParams = new HashMap<>();
        Map<String, Object> deviceInfo = new HashMap<>();
        List<Map<String, Object>> sims = new ArrayList<>();
        for (SimInfo.Sim sim : requestDTO.getSimInfo().getSims()) {
            Map<String, Object> map = new HashMap<>();
            map.put("slot", sim.getSlot());
            map.put("sim_id", sim.getSimId());
            map.put("carrier_name", sim.getCarrierName());
            map.put("phone", sim.getPhone());
            sims.add(map);
        }
        commonParams.put("app_version", requestDTO.getMeta().getAppVersion());
        commonParams.put("client", requestDTO.getMeta().getClient());
        commonParams.put("lat", requestDTO.getMeta().getLatitude());
        commonParams.put("lon", requestDTO.getMeta().getLongitude());
        commonParams.put("ip", requestDTO.getMeta().getIp());
        commonParams.put("device_id", requestDTO.getMeta().getDeviceId());
        deviceInfo.put("os", requestDTO.getMeta().getDeviceInfo().getOs());
        deviceInfo.put("manufacturer", requestDTO.getMeta().getDeviceInfo().getManufacturer());
        deviceInfo.put("device", requestDTO.getMeta().getDeviceInfo().getDevice());
        deviceInfo.put("is_virtual", requestDTO.getMeta().getDeviceInfo().getIsVirtual());
        commonParams.put("device_info", deviceInfo);
        params.put("amount", requestDTO.getPayload().getAmount());
        params.put("order_id", txnId);
        params.put("beneficiary_name", beneficiaryName);
        params.put("source", paymentSource);
        if (requestDTO.getPayload().getAppHash() != null) {
            params.put("app_hash", requestDTO.getPayload().getAppHash());
        }
        params.put("install_id", requestDTO.getSimInfo().getInstallId());
        params.put("device_id", requestDTO.getSimInfo().getDeviceId());
        params.put("sims", sims);
        if (requestDTO.getPayload().getAppHash() != null) {
            params.put("app_hash", requestDTO.getPayload().getAppHash());
        }
        requestParams.put("common_params", commonParams);
        requestParams.put("params", params);
        return requestParams;
    }

    private Map<String, Object> generateSendMoneyVerify(RequestDTO<CreditSpendVerifyRequestDTO> requestDTO) {
        Map<String, Object> requestParams = new HashMap<>();
        Map<String, Object> params = new HashMap<>();
        Map<String, Object> commonParams = new HashMap<>();
        Map<String, Object> deviceInfo = new HashMap<>();
        commonParams.put("app_version", requestDTO.getMeta().getAppVersion());
        commonParams.put("client", requestDTO.getMeta().getClient());
        commonParams.put("lat", requestDTO.getMeta().getLatitude());
        commonParams.put("lon", requestDTO.getMeta().getLongitude());
        commonParams.put("ip", requestDTO.getMeta().getIp());
        commonParams.put("device_id", requestDTO.getMeta().getDeviceId());
        deviceInfo.put("os", requestDTO.getMeta().getDeviceInfo().getOs());
        deviceInfo.put("manufacturer", requestDTO.getMeta().getDeviceInfo().getManufacturer());
        deviceInfo.put("device", requestDTO.getMeta().getDeviceInfo().getDevice());
        deviceInfo.put("is_virtual", requestDTO.getMeta().getDeviceInfo().getIsVirtual());
        commonParams.put("device_info", deviceInfo);
        if (requestDTO.getPayload().getOtp() != null) {
            params.put("otp", requestDTO.getPayload().getOtp());
        }
        if (requestDTO.getPayload().getAppHash() != null) {
            params.put("app_hash", requestDTO.getPayload().getAppHash());
        }
        params.put("order_id", requestDTO.getPayload().getRequestId());
        if (requestDTO.getPayload().getAppHash() != null) {
            params.put("app_hash", requestDTO.getPayload().getAppHash());
        }
        requestParams.put("common_params", commonParams);
        requestParams.put("params", params);
        return requestParams;
    }

    private Map<String, Object> verifyTxn(RequestDTO<CreditSpendVerifyRequestDTO> requestDTO, String token) {
        Map<String, Object> result = new HashMap<>();
        InternalClient internalClient = internalClientDao.findByClientName("CREDIT_LINE");
        try {
            Map requestParams = generateSendMoneyVerify(requestDTO);
            String hash = hmacCalculator.calculateHmac(hmacCalculator.getPayload(requestParams), aesEncryption.decrypt(internalClient.getSecret()));
            UriComponents requestUrl = UriComponentsBuilder.fromHttpUrl(PAYMENT_HOST + CreditConstants.BP_BALANCE_CONFIRM_TXN_URL).build();
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
            headers.setCacheControl(CacheControl.noCache());
            headers.set("token", token);
            headers.set("hash", hash);
            headers.set("clientName", "CREDIT_LINE");

            HttpEntity<Object> entity = new HttpEntity<>(requestParams, headers);
            result.put("request", objectMapper.writeValueAsString(entity));
            long startTime = System.currentTimeMillis();
            int retryCount=0;
            while(retryCount<3) {
            	try {
            		ResponseEntity<Object> response = restTemplate.exchange(requestUrl.encode().toUri(), HttpMethod.POST, entity, Object.class);
                	if(response.getBody()!=null) {
                		result.put("response", objectMapper.writeValueAsString(response.getBody()));
                        logger.info("Response : {} ", objectMapper.writeValueAsString(response.getBody()));
                        result.put("success", ((Map<String, Object>) response.getBody()).get("success"));
                        Map<String, Object> responseData = (Map<String, Object>) ((Map<String, Object>) response.getBody()).get("data");
                        result.put("order_id", responseData.get("order_id"));
                        result.put("amount", responseData.get("amount"));
                        result.put("status", responseData.get("status"));
                        logger.info("Successfully verified txn for BP Balance in {} ms", System.currentTimeMillis() - startTime);
                        return result;
                	}
            	}
            	catch(Exception e) {
            		logger.error("Error occured while verifying txn",e);
            	}
            	
            	retryCount++;
            }
            
        } catch (HttpClientErrorException e) {
            result.put("success", Boolean.FALSE);
            logger.error("Error Verifying txn for BP Balance info---", e);
        } catch (Exception e) {
            result.put("success", Boolean.FALSE);
            logger.error("Error parsing details from BP Balance---", e);
        }
        return result;
    }

    private Map<String, Object> createVPA(Double amount, String txn_id, String vpa) {
        logger.info("Start processing for txn: {}", txn_id);
        try {
            Map<String, Object> response = new HashMap<>();
            Map requestParams = new HashMap<>();
            requestParams.put("amount", amount);
            requestParams.put("orderId", txn_id);
            requestParams.put("mid", getMid());
            requestParams.put("gateway", Gateway.FEDERAL.name());
            requestParams.put("beneficiaryName", "BharatPe Loans");
            if(vpa!=null) {
                requestParams.put("payerVpa", vpa);
            }
            String hash = hmacCalculator.calculateHmac(hmacCalculator.getPayload(requestParams), getSecret());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setCacheControl(CacheControl.noCache());
            headers.set("mid", getMid());
            headers.set("hash", hash);
            HttpEntity<Map> request = new HttpEntity<>(requestParams, headers);

            long startTime = System.currentTimeMillis();
            logger.info("create vpa internal request: {}", request);

            response.put("request", objectMapper.writeValueAsString(request));
            int retryCount=0;
            VPARequestDto responseObj=null;
            while(retryCount<3) {
            	try {
            		responseObj = restTemplate.postForObject(DYNAMIC_VPA_HOST, request, VPARequestDto.class);
                	if(responseObj!=null && responseObj.getResponseCode().equalsIgnoreCase("100")) {
                		break;
                	}
            	}
            	catch(Exception ex) {
            		logger.error("error processing txn for dynamic vpa, txn: {}, {}", txn_id, ex);
            	}
            	
            	retryCount++;
            }
            response.put("response", responseObj);
            logger.info("create vpa response: {}, response time: {}", objectMapper.writeValueAsString(response), (System.currentTimeMillis() - startTime));
            return response;
        } catch (Exception ex) {
            logger.error("error processing txn for dynamic vpa, txn: {}, {}", txn_id, ex);
        }
        return null;
    }

    private void insertClLedger(LendingClTransaction lendingClTransaction, Double amount, Double principle, Double penalty, Double interest, Double otherCharges) {
        LendingClLedger lendingClLedger = new LendingClLedger();
        lendingClLedger.setMerchantId(lendingClTransaction.getMerchantId());
        lendingClLedger.setMerchantStoreId(lendingClTransaction.getMerchantStoreId());
        lendingClLedger.setClTransactionId(lendingClTransaction.getId());
        lendingClLedger.setTransactionType("PAYMENT");
        lendingClLedger.setAmount(amount);
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd 00:00:00");
        try {
            lendingClLedger.setDate(format.parse(format.format(new Date())));
        } catch (ParseException e) {
            lendingClLedger.setDate(new Date());
            logger.error("Exception---", e);
        }
        lendingClLedger.setPrinciple(principle);
        lendingClLedger.setInterest(interest);
        lendingClLedger.setOtherCharges(otherCharges);
        lendingClLedger.setPenalty(penalty);
        lendingClLedgerDao.save(lendingClLedger);
    }

    public void updatePaymentStatus(VPAResponseDto responseDto) {
        logger.info("Received request to update Payment Status : {}", responseDto);
//        if (!mid.equals(getMid())) {
//            logger.error("Invalid mid in vpa callback");
//            return;
//        }
        Optional<LendingClTransaction> lendingClTransactionOptional;
        CreditConstants.PaymentStatus status;
        try {
            Long txnId = Long.valueOf(responseDto.getOrderId());
            status = CreditConstants.PaymentStatus.valueOf(responseDto.getStatus());
            lendingClTransactionOptional = lendingClTransactionDao.findById(txnId);
            if (!lendingClTransactionOptional.isPresent() || !CreditConstants.PaymentStatus.PENDING.name().equalsIgnoreCase(lendingClTransactionOptional.get().getStatus())) {
                logger.info("No Transaction found with Id : {} ", txnId);
                return;
            }
        } catch (Exception e) {
            logger.info("Invalid Transaction id : {} or Status : {} ", responseDto.getOrderId(), responseDto.getStatus());
            return;
        }
        LendingClTransaction lendingClTransaction = lendingClTransactionOptional.get();
        int lockTxn = lendingClTransactionDao.updateStatusForPendingTxn(CreditConstants.PaymentStatus.CALLBACK_RECEIVED.name(), lendingClTransaction.getId());
        if (lockTxn != 1) {
            logger.info("Unable to take lock on txn:{} ", lendingClTransaction.getId());
            return;
        }
        LendingClPayment lendingClPayment = lendingClPaymentDao.findByClTransactionId(lendingClTransaction.getId());
        if (lendingClPayment != null) {
            try {
                lendingClPayment.setResponse(objectMapper.writeValueAsString(responseDto));
            } catch (JsonProcessingException e) {
                lendingClPayment.setResponse(responseDto.toString());
            }
            lendingClPayment.setStatus(status.name());
            lendingClPayment.setTxnRefNo(responseDto.getBankReferenceNumber());
            creditLineTransaction.updateCLPayment(lendingClPayment);
        }
        lendingClTransaction.setOrderId(responseDto.getTransactionId());
        lendingClTransaction.setBankReferenceId(responseDto.getBankReferenceNumber());
        lendingClTransaction.setNarration1(responseDto.getCustomerName());
        lendingClTransaction.setNarration2(responseDto.getTransactionMessage());
        lendingClTransactionDao.save(lendingClTransaction);
        if (CreditConstants.PaymentStatus.FAILED.equals(status) || !(responseDto.getAmount().equals(lendingClTransaction.getAmount()))) {
            lendingClTransaction.setStatus(CreditConstants.PaymentStatus.FAILED.name());
            creditLineTransaction.saveTxn(lendingClTransaction);
            sendFiledNotification(lendingClTransaction);
        } else if (CreditConstants.PaymentStatus.SUCCESS.equals(status)) {
            CreditAccount creditAccount = creditAccountDao.findByMerchantIdForDashBoard(lendingClTransaction.getMerchantId());
            updateBalances(creditAccount, lendingClTransaction);
        }
    }
    
    private void sendFiledNotification(LendingClTransaction lendingClTransaction) {
    	Optional<Merchant> merchantOptional=merchantDao.findById(lendingClTransaction.getMerchantId());
    	if(merchantOptional.isPresent()) {
    		creditLineService.sendFiledTransNotification(lendingClTransaction, merchantOptional.get());
    	}
    }

    private String getSecret() {
        Optional<Merchant> merchantOptional = merchantDao.findById(merchantId);
        if (merchantOptional.isPresent()) {
            Merchant merchant = merchantOptional.get();
            if (secret == null) {
                secret = aesEncryption.decrypt(merchant.getSecret());
            }
        }
        return secret;
    }

    private String getMid() {
        Optional<Merchant> merchantOptional = merchantDao.findById(merchantId);
        if (merchantOptional.isPresent()) {
            Merchant merchant = merchantOptional.get();
            if (mid == null) {
                mid = merchant.getMid();
            }
        }
        return mid;
    }
    
    public PaymentCancellationResponseDto cancelUpiPayment(Long transactionId) {
    	LendingClPayment lendingClPayment=lendingClPaymentDao.findByClTransactionId(transactionId);
    	if(lendingClPayment==null) {
    		logger.error("lendingClPayment detail not found for transaction id {}",transactionId);
    		return new PaymentCancellationResponseDto(false,"Payment detail not found", null,null);
    	}
    	PaymentCancellationResponseDto responseDto= cancelPayment(transactionId.toString(), lendingClPayment);
    	if(responseDto==null) {
    		return new PaymentCancellationResponseDto(false,"Cancellation failed",null,null);
    	}
    	return responseDto;
    }
    
    public PaymentCancellationResponseDto cancelPayment(String orderId,LendingClPayment lendingClPayment) {
	    logger.info("Start Fetching payment status for orderId {}", orderId);
	    try {
            Map requestParams = new HashMap<>();
            requestParams.put("orderId", orderId);
            requestParams.put("mid", getMid());
            //requestParams.put("txnId", lendingClPayment.getVpa());
        
            String hash = hmacCalculator.calculateHmac(hmacCalculator.getPayload(requestParams), getSecret());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setCacheControl(CacheControl.noCache());
            headers.set("mid", getMid());
            headers.set("hash", hash);
            HttpEntity<Map> request = new HttpEntity<>(headers);
            int retryCount=0;
            String URL=UPI_PAYMENT_STATUS_CHECK_URL+"?orderId="+orderId+"&mid="+getMid();
            logger.info("upi check status internal URL {} and request: {}", URL,request);
            while(retryCount<3) {
            	try {
            		ResponseEntity<Object> responseObj=restTemplate.exchange(URL, HttpMethod.GET, request, Object.class);
                	logger.info("UPI status response {}",objectMapper.writeValueAsString(responseObj.getBody()));
                	if(responseObj!=null && responseObj.hasBody()) {
                		
                		if("100".equalsIgnoreCase(((Map<String, Object>) responseObj.getBody()).get("responseCode").toString()) && ("PENDING".equalsIgnoreCase(((Map<String, Object>) responseObj.getBody()).get("paymentStatus").toString()))) {
                	    	
                			if(cancelPayment(lendingClPayment)) {
                				changePaymentStatus(lendingClPayment);
                				return new PaymentCancellationResponseDto(true,"",true,"CANCELLED");
                	    	}
                	    	else {
                	    		return new PaymentCancellationResponseDto(false,"Cancellation failed",null,null);
                	    	}
                		}
                		else if("100".equalsIgnoreCase(((Map<String, Object>) responseObj.getBody()).get("responseCode").toString()) && ("SUCCESS".equalsIgnoreCase(((Map<String, Object>) responseObj.getBody()).get("paymentStatus").toString()))){
                			return new PaymentCancellationResponseDto(true,"",false,"SUCCESS");
                		}
                		else if("100".equalsIgnoreCase(((Map<String, Object>) responseObj.getBody()).get("responseCode").toString()) && ("CANCELLED".equalsIgnoreCase(((Map<String, Object>) responseObj.getBody()).get("paymentStatus").toString()))){
                			return new PaymentCancellationResponseDto(true,"",true,"CANCELLED");
                		}
                		else {
                			return new PaymentCancellationResponseDto(true,"",false,"FAILED");
                		}
                	}
            	}
            	catch(Exception e) {
            		logger.error("error processing txn for dynamic vpa, txn: {}, {}", orderId, e);
            	}
            	
            	retryCount++;
            }
            
    	} catch (Exception ex) {
	        logger.error("error processing txn for dynamic vpa, txn: {}, {}", orderId, ex);
	    }
        return null;
    }
    
    public void changePaymentStatus(LendingClPayment lendingClPayment) {
    	Optional<LendingClTransaction> lendingOptional=lendingClTransactionDao.findById(lendingClPayment.getClTransactionId());
    	if(lendingOptional.isPresent()) {
    		LendingClTransaction lendingClTransaction=lendingOptional.get();
    		lendingClTransaction.setStatus("CANCELLED");
    		lendingClTransactionDao.save(lendingClTransaction);
    		lendingClPayment.setStatus("CANCELLED");
    		lendingClPaymentDao.save(lendingClPayment);
    	}
    }
    
    public boolean cancelPayment(LendingClPayment lendingClPayment) {
    	try {
            Map requestParams = new HashMap<>();
            requestParams.put("orderId", lendingClPayment.getClTransactionId());
            requestParams.put("txnId", lendingClPayment.getVpa());
        
            String hash = hmacCalculator.calculateHmac(hmacCalculator.getPayload(requestParams), getSecret());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setCacheControl(CacheControl.noCache());
            headers.set("mid", getMid());
            headers.set("hash", hash);
            HttpEntity<Map> request = new HttpEntity<>(requestParams, headers);
            logger.info("cancel upi internal request: {}", request);
            int retryCount=0;
            UpiPaymentCancellationResponseDto responseObj=null;
            while(retryCount<3) {
            	try {
            		responseObj = restTemplate.postForObject(CANCEL_PAYMENT_URL, request, UpiPaymentCancellationResponseDto.class);
                	logger.info("UPI payment cancellation response {}",responseObj.toString());
                	if(responseObj!=null && responseObj.getResponseCode().equalsIgnoreCase("100")) {
                		if(responseObj.getPaymentStatus().equalsIgnoreCase("CANCELLED")) {
                			return true;
                		}
                		else {
                			return false;
                		}
                	}
                	break;
                }
                catch(Exception e){
                	logger.error("Error occured while cancelling order with transaction id {}",lendingClPayment.getClTransactionId());
                }
            	retryCount++;
            }
            
    	}
    	catch(Exception e) {
    		logger.error("Error occured while cancelling order with transaction id {}",lendingClPayment.getClTransactionId());
    	}
    	return false;
    }
}
