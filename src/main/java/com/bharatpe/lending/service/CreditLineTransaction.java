package com.bharatpe.lending.service;

import com.bharatpe.common.dao.MerchantDao;
import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.common.entities.Merchant;
import com.bharatpe.common.enums.NotificationProvider;
import com.bharatpe.common.handlers.EmailHandler;
import com.bharatpe.common.handlers.SmsServiceHandler;
import com.bharatpe.common.service.WhatsappNotificationService;
import com.bharatpe.lending.common.dao.*;
import com.bharatpe.lending.common.entity.*;
import com.bharatpe.lending.constant.CreditConstants;
import com.bharatpe.lending.dao.LendingLedgerDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dto.BankTransferResponseDTO;
import com.bharatpe.lending.dto.CreditSpendResponseDTO;
import com.bharatpe.lending.util.CreditUtil;
import com.bharatpe.lending.util.LoanUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class CreditLineTransaction {

    Logger logger = LoggerFactory.getLogger(CreditLineTransaction.class);

    @Autowired
    LendingClTransactionDao lendingClTransactionDao;

    @Autowired
    CreditAccountDao creditAccountDao;

    @Autowired
    LendingCaBalanceDetailDao lendingCaBalanceDetailDao;

    @Autowired
    LendingClLedgerDao lendingClLedgerDao;

    @Autowired
    LendingTlDetailsDao lendingTlDetailsDao;

    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;

    @Autowired
    LiquiloansService liquiloansService;

    @Autowired
    LendingClTransactionRequestDao lendingClTransactionRequestDao;

    @Autowired
    LendingClPaymentDao lendingClPaymentDao;

    @Autowired
    CreditAccountBillDao creditAccountBillDao;

    @Autowired
    LendingClPaymentBreakupDao lendingClPaymentBreakupDao;

    @Autowired
    LendingLedgerDao lendingLedgerDao;

    @Autowired
    EmailHandler emailHandler;
    
    @Autowired
    RedisNotificationService redisNotificationService;
    
    @Autowired
    MerchantDao merchantDao;

    @Autowired
    SmsServiceHandler smsServiceHandler;

    @Autowired
    WhatsappNotificationService whatsappNotificationService;

    @Autowired
    LendingPullPaymentDao lendingPullPaymentDao;

    public LendingClTransaction createDebitTxn(CreditAccount creditAccount, LendingClTransactionRequest paymentRequest) {
        logger.info("Initializing new transaction for account:{}, amount:{}, mode:{}", creditAccount.getId(), paymentRequest.getAmount(), paymentRequest.getMode());
        LendingClTransaction lendingClTransaction = new LendingClTransaction();
        lendingClTransaction.setCreditAccountId(creditAccount.getId());
        lendingClTransaction.setMerchantId(creditAccount.getMerchantId());
        lendingClTransaction.setMerchantStoreId(creditAccount.getMerchantStoreId());
        lendingClTransaction.setStatus(CreditConstants.PaymentStatus.INIT.name());
        lendingClTransaction.setAmount(paymentRequest.getAmount());
        lendingClTransaction.setMode("DEBIT");
        lendingClTransaction.setType(paymentRequest.getLoanType());
        lendingClTransaction.setSubType(paymentRequest.getMode());
        lendingClTransaction.setRequestId(paymentRequest.getId());
        lendingClTransaction.setOrderId(paymentRequest.getOrderId());
        lendingClTransaction.setNarration1(paymentRequest.getNarration1());
        lendingClTransaction.setNarration2(paymentRequest.getNarration2());
        lendingClTransaction.setNarration3(paymentRequest.getNarration3());
        lendingClTransaction.setIcon(paymentRequest.getIcon());
        return saveTxn(lendingClTransaction);
    }

    public LendingClTransaction createCreditTxn(CreditAccount creditAccount, Double amount, String spendMode) {
        LendingClTransaction lendingClTransaction = new LendingClTransaction();
        lendingClTransaction.setCreditAccountId(creditAccount.getId());
        lendingClTransaction.setMerchantId(creditAccount.getMerchantId());
        lendingClTransaction.setMerchantStoreId(creditAccount.getMerchantStoreId());
        lendingClTransaction.setStatus(CreditConstants.PaymentStatus.INIT.name());
        lendingClTransaction.setAmount(amount);
        lendingClTransaction.setMode("CREDIT");
        lendingClTransaction.setType(CreditConstants.PaymentType.PAYMENT.name());
        lendingClTransaction.setSubType(spendMode);
        return saveTxn(lendingClTransaction);
    }

    public void debitTxn(CreditAccount creditAccount, LendingClTransactionRequest paymentRequest, LendingClTransaction lendingClTransaction) {
        logger.info("Credit line debit for account:{}, amount:{}, mode:{}", creditAccount.getId(), paymentRequest.getAmount(), paymentRequest.getMode());
        lendingClTransaction.setStatus(CreditConstants.PaymentStatus.PENDING.name());
        LendingClLedger lendingClLedger = createClLedger(lendingClTransaction, paymentRequest.getLoanType(), -lendingClTransaction.getAmount(), -lendingClTransaction.getAmount(), 0D, 0D, 0D);
        LendingTlDetails lendingTlDetails = null;
        if ("TL".equals(paymentRequest.getLoanType())) {
            lendingTlDetails = createTlDetails(lendingClTransaction, paymentRequest.getTenure());
        }
        debitCLBalance(creditAccount, paymentRequest.getAmount(), paymentRequest.getMode(), paymentRequest.getLoanType(), lendingClTransaction, lendingClLedger, lendingTlDetails);
    }

    @Transactional
    public LendingClTransactionRequest saveTxnRequest(LendingClTransactionRequest lendingClTransactionRequest) {
        return lendingClTransactionRequestDao.save(lendingClTransactionRequest);
    }

    @Transactional
    public LendingClTransaction saveTxn(LendingClTransaction lendingClTransaction) {
        return lendingClTransactionDao.save(lendingClTransaction);
    }

    @Transactional
    public void updateTxnStatus(LendingClTransaction lendingClTransaction, CreditConstants.PaymentStatus paymentStatus) {
        lendingClTransaction.setStatus(paymentStatus.name());
        lendingClTransactionDao.save(lendingClTransaction);
    }

    public void rollbackTxn(LendingClTransaction lendingClTransaction) {
        logger.info("Rollback transaction:{}", lendingClTransaction.getId());
        LendingClLedger lendingClLedger = createClLedger(lendingClTransaction, CreditConstants.PaymentType.ROLLBACK.name(), lendingClTransaction.getAmount(), lendingClTransaction.getAmount(), 0D, 0D, 0D);
        creditCLBalance(lendingClTransaction, lendingClLedger);
    }

    @Transactional
    public void debitCLBalance(CreditAccount creditAccount, Double amount, String spendMode, String loanType, LendingClTransaction lendingClTransaction, LendingClLedger lendingClLedger, LendingTlDetails lendingTlDetails) {
        logger.info("Credit line debit for account:{}, amount:{}, mode:{}", creditAccount.getId(), amount, spendMode);
        lendingClTransactionDao.save(lendingClTransaction);
        creditAccountDao.debitBalance(creditAccount.getId(), amount);
        lendingClLedgerDao.save(lendingClLedger);
        if (lendingTlDetails != null) {
            lendingTlDetailsDao.save(lendingTlDetails);
        }
        Double usedBalanceCl = 0D;
        Double usedBalanceG1 = 0D;
        Double usedBalanceG2 = 0D;
        Double usedBalanceG3 = 0D;
        if ("CL".equals(loanType)) {
            usedBalanceCl = amount;
            String group = CreditConstants.SpendGroup.get(spendMode);
            switch (group) {
                case "G1": {
                    usedBalanceG1 = amount;
                    break;
                }
                case "G2": {
                    usedBalanceG2 = amount;
                    break;
                }
                case "G3": {
                    usedBalanceG3 = amount;
                    break;
                }
            }
        }
        lendingCaBalanceDetailDao.debitBalance(creditAccount.getId(), amount, usedBalanceCl, usedBalanceG1, usedBalanceG2, usedBalanceG3);
    }

    @Transactional
    public void creditCLBalance(LendingClTransaction lendingClTransaction, LendingClLedger lendingClLedger) {
        logger.info("Credit line credit for account:{}, amount:{}, mode:{}", lendingClTransaction.getCreditAccountId(), lendingClTransaction.getAmount(), lendingClTransaction.getSubType());
        creditAccountDao.creditBalance(lendingClTransaction.getCreditAccountId(), lendingClTransaction.getAmount());
        lendingClTransactionDao.updateStatus(CreditConstants.PaymentStatus.FAILED.name(), lendingClTransaction.getId());
        lendingClLedgerDao.save(lendingClLedger);
        Double usedBalanceCl = 0D;
        Double usedBalanceG1 = 0D;
        Double usedBalanceG2 = 0D;
        Double usedBalanceG3 = 0D;
        if ("CL".equals(lendingClTransaction.getType())) {
            usedBalanceCl = lendingClTransaction.getAmount();
            String group = CreditConstants.SpendGroup.get(lendingClTransaction.getSubType());
            switch (group) {
                case "G1": {
                    usedBalanceG1 = lendingClTransaction.getAmount();
                    break;
                }
                case "G2": {
                    usedBalanceG2 = lendingClTransaction.getAmount();
                    break;
                }
                case "G3": {
                    usedBalanceG3 = lendingClTransaction.getAmount();
                    break;
                }
            }
        }
        lendingCaBalanceDetailDao.creditBalance(lendingClTransaction.getCreditAccountId(), lendingClTransaction.getAmount(), usedBalanceCl, usedBalanceG1, usedBalanceG2, usedBalanceG3);
    }

    public LendingClLedger createClLedger(LendingClTransaction lendingClTransaction, String type, Double amount, Double principle, Double interest, Double penalty, Double otherCharges) {
        logger.info("Creating lending_cl_ledger for txn:{}", lendingClTransaction.getId());
        LendingClLedger lendingClLedger = new LendingClLedger();
        lendingClLedger.setMerchantId(lendingClTransaction.getMerchantId());
        lendingClLedger.setMerchantStoreId(lendingClTransaction.getMerchantStoreId());
        lendingClLedger.setClTransactionId(lendingClTransaction.getId());
        lendingClLedger.setTransactionType(type);
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
        return lendingClLedger;
    }

    public LendingTlDetails createTlDetails(LendingClTransaction lendingClTransaction, Integer tenure) {
        logger.info("Creating lending_tl_details for txn:{}", lendingClTransaction.getId());
        CreditSpendResponseDTO.TL tl = calculateTL(lendingClTransaction.getAmount().intValue(), tenure);
        LendingTlDetails lendingTlDetails = new LendingTlDetails();
        lendingTlDetails.setMerchantId(lendingClTransaction.getMerchantId());
        lendingTlDetails.setMerchantStoreId(lendingClTransaction.getMerchantStoreId());
        lendingTlDetails.setLendingClTransaction(lendingClTransaction);
        lendingTlDetails.setAmount(lendingClTransaction.getAmount());
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd 00:00:00");
        try {
            lendingTlDetails.setDate(format.parse(format.format(new Date())));
        } catch (ParseException e) {
            lendingTlDetails.setDate(new Date());
            logger.error("Exception---", e);
        }
        lendingTlDetails.setProcessingFee(tl.getProcessingFee().doubleValue());
        lendingTlDetails.setEdi(tl.getEdiAmount().doubleValue());
        lendingTlDetails.setInterestRate(tl.getInterestRate());
        lendingTlDetails.setTotalPayableAmount(tl.getRepaymentAmount().doubleValue());
        lendingTlDetails.setTenure(tenure+"");
        lendingTlDetails.setPayableDays(tl.getEdiCount());
        DateFormat df = new SimpleDateFormat("ddMMyy");
        String loanId = "CL" + df.format(new Date()) + lendingClTransaction.getRequestId();
        lendingTlDetails.setExternalLoanId(loanId);
        return lendingTlDetails;
    }

    private CreditSpendResponseDTO.TL calculateTL(Integer amount, int tenure) {
        int ediCount = LoanUtil.getEdiDays(tenure);
        int edi = (int) Math.ceil(((amount + (amount * 0.02 * tenure))) / ediCount);
        Integer repayment = Math.round(ediCount * edi);
        Integer interestAmount = repayment - amount;
        return new CreditSpendResponseDTO.TL(edi, tenure, 2D, 0, amount, interestAmount, repayment, ediCount);
    }

    public void createLPS(Merchant merchant, LendingClTransaction lendingClTransaction) {
        try {
            LendingTlDetails lendingTlDetails = lendingTlDetailsDao.findByLendingClTransaction(lendingClTransaction);
            LendingPaymentSchedule lendingPaymentSchedule = new LendingPaymentSchedule();
            logger.info("Populating data into lending_payment_schedule table for merchant: {}", merchant.getId());
            lendingPaymentSchedule.setCreditLoan(true);
            lendingPaymentSchedule.setLoanType("CREDIT_LINE");
            lendingPaymentSchedule.setMerchant(merchant);
            lendingPaymentSchedule.setLoanAmount(lendingTlDetails.getAmount());
            lendingPaymentSchedule.setMobile(merchant.getMobile());
            lendingPaymentSchedule.setEdiAmount(lendingTlDetails.getEdi());
            lendingPaymentSchedule.setStatus("ACTIVE");
            lendingPaymentSchedule.setNbfc("LIQUILOANS");
            lendingPaymentSchedule.setEdiCount(lendingTlDetails.getPayableDays());
            lendingPaymentSchedule.setOverdueEdiCount(0);
            lendingPaymentSchedule.setDueAmount(0D);
            lendingPaymentSchedule.setDueOtherCharges(0D);
            lendingPaymentSchedule.setDuePenalty(0D);
            lendingPaymentSchedule.setDueInterest(0D);
            lendingPaymentSchedule.setDuePrinciple(0D);
            lendingPaymentSchedule.setIncentiveAmount(0D);
            lendingPaymentSchedule.setEdiRemainingCount(lendingTlDetails.getPayableDays());
            lendingPaymentSchedule.setOverdueAmount(0D);
            lendingPaymentSchedule.setPaidAmount(0D);
            lendingPaymentSchedule.setPaidPrinciple(0D);
            lendingPaymentSchedule.setPaidInterest(0D);
            lendingPaymentSchedule.setPaidPenalty(0D);
            lendingPaymentSchedule.setPaidOtherCharges(0D);
            lendingPaymentSchedule.setTotalCashbackAmount(0D);
            lendingPaymentSchedule.setTotalPayableAmount(lendingTlDetails.getTotalPayableAmount());
            lendingPaymentSchedule.setTlDetailsId(lendingTlDetails.getId());
            lendingPaymentSchedule.setCreatedAt(new Date());
            lendingPaymentSchedule.setUpdatedAt(new Date());
            lendingPaymentSchedule.setLoanConstruct("CONSTRUCT_1");
            Date date = new Date();
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd 00:00:00");
            //getting tommorow's date
            Date tomorrow = new Date(date.getTime() + (1000 * 60 * 60 * 24));
            //checking if next day is Sunday or not because we don't cut edi on Sunday
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(tomorrow);
            if (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
                tomorrow = new Date(tomorrow.getTime() + (1000 * 60 * 60 * 24));
            }
            tomorrow = format.parse(format.format(tomorrow));
            lendingPaymentSchedule.setStartDate(tomorrow);
            lendingPaymentSchedule.setNextEdiDate(lendingPaymentSchedule.getStartDate());
            Date tenativeLoanEndDate=getDateAfterNMonths(date,Integer.parseInt(lendingTlDetails.getTenure()));
            lendingPaymentSchedule.setTentativeClosingDate(tenativeLoanEndDate);
            lendingPaymentSchedule = insertLPS(lendingPaymentSchedule);
            liquiloansService.createEdiSchedule(lendingPaymentSchedule);
            liquiloansService.changeDeductionFromInstantToDaily(merchant);
//            LendingPaymentSchedule finalLendingPaymentSchedule = lendingPaymentSchedule;
//            createLeadExecutor.submit(() -> liquiloansService.createLead(finalLendingPaymentSchedule, lendingTlDetails));
        } catch (Exception e) {
            logger.error("Error creating LPS for merchant:{} and transaction:{}", merchant.getId(), lendingClTransaction.getId());
        }
    }

    @Transactional
    public LendingPaymentSchedule insertLPS(LendingPaymentSchedule lendingPaymentSchedule) {
        return lendingPaymentScheduleDao.save(lendingPaymentSchedule);
    }

    private Date getDateAfterNMonths(Date startDate, int month){
        try {
            logger.info("Getting date after {} month",month);
            Calendar myCal = Calendar.getInstance();
            myCal.setTime(startDate);
            myCal.add(Calendar.MONTH, +month);
            Date tentativeEndDate = myCal.getTime();
            SimpleDateFormat format=new SimpleDateFormat("yyyy-MM-dd 00:00:00");
            return format.parse(format.format(tentativeEndDate));
        }
        catch(Exception e){
            logger.error("Error occured while catculating date post N month",e);
            return null;
        }
    }

    public LendingClPayment insertClPayment(LendingClTransaction lendingClTransaction, String request, String response, String mid, String vpa, String source, String mode) {
        LendingClPayment lendingClPayment = new LendingClPayment();
        lendingClPayment.setMerchantId(lendingClTransaction.getMerchantId());
        lendingClPayment.setCreditAccountId(lendingClTransaction.getCreditAccountId());
        lendingClPayment.setAmount(lendingClTransaction.getAmount());
        lendingClPayment.setRequest(request);
        lendingClPayment.setResponse(response);
        lendingClPayment.setStatus(lendingClTransaction.getStatus());
        lendingClPayment.setTxnRefNo(lendingClTransaction.getOrderId());
        lendingClPayment.setClTransactionId(lendingClTransaction.getId());
        lendingClPayment.setMid(mid);
        lendingClPayment.setVpa(vpa);
        lendingClPayment.setSource(source);
        lendingClPayment.setMode(mode);
        return updateCLPayment(lendingClPayment);
    }

    @Transactional
    public LendingClPayment updateCLPayment(LendingClPayment lendingClPayment) {
        return lendingClPaymentDao.save(lendingClPayment);
    }

    @Transactional
    public void creditRepayment(LendingClTransaction lendingClTransaction, CreditAccount creditAccount, LendingCaBalanceDetail lendingCaBalanceDetail, CreditAccountBill creditAccountBill, List<LendingClLedger> lendingClLedgers, List<LendingClPaymentBreakup> lendingClPaymentBreakups, Collection<LendingPaymentSchedule> lendingPaymentSchedules, List<LendingLedger> lendingLedgers, Double principle, Double interest, Double clPrinciple, double newMAD) {
        logger.info("Repayment for account:{}, amount:{}", creditAccount.getId(), principle);
        if (creditAccount.getAvailableBalance() + principle > creditAccount.getLimit()) {
            logger.error("Payable amount more than credit limit for merchant:{}, txn:{}", creditAccount.getMerchantId(), lendingClTransaction.getId());
            emailHandler.sendEmail(new ArrayList<String>(){{add("khushal.virmani@bharatpe.com");add("rajat.jain@bharatpe.com");}}, "Repayment more than credit limit", "Merchant id:" + creditAccount.getMerchantId() + "\nTransaction id:" + lendingClTransaction.getId());
        }
        if (creditAccountBill != null) {
            creditAccountBillDao.save(creditAccountBill);
            creditAccountDao.updateMAD(creditAccount.getId(), newMAD);
            if (newMAD == 0D) {
                logger.info("closing all bills for account:{}", creditAccount.getId());
                creditAccountBillDao.closeAllBills(creditAccount.getId(), creditAccount.getMerchantId(), creditAccountBill.getPaidAmount(), new Date());
                //restarting promotional notifications
                startPromotionalNotification(creditAccount);
                creditAccountDao.updateStatus(creditAccount.getId(), CreditConstants.AccountStatus.ACTIVE.name());
            }
            LendingPullPayment lendingPullPayment = lendingPullPaymentDao.findByMerchantId(creditAccount.getMerchantId());
            if (lendingPullPayment != null) {
                lendingPullPayment.setDueAmount(newMAD);
                lendingPullPaymentDao.save(lendingPullPayment);
            }
        }
        if (creditAccount.getStatus().equalsIgnoreCase(CreditConstants.AccountStatus.BLOCKED.name())) {
            boolean dpd = false;
            for (LendingPaymentSchedule lendingPaymentSchedule : lendingPaymentSchedules) {
                if (getDPD(lendingPaymentSchedule) >= 7) {
                    dpd = true;
                }
            }
            if (!dpd) {
                logger.info("Activating credit account:{}", creditAccount.getId());
                creditAccountDao.updateStatus(creditAccount.getId(), CreditConstants.AccountStatus.ACTIVE.name());
            }
        }
        creditAccountDao.creditBalance(creditAccount.getId(), principle);
        lendingClTransactionDao.updateStatus(CreditConstants.PaymentStatus.SUCCESS.name(), lendingClTransaction.getId());
        if (!lendingClLedgers.isEmpty()) {
            lendingClLedgerDao.saveAll(lendingClLedgers);
        }
        if (!lendingClPaymentBreakups.isEmpty()) {
            lendingClPaymentBreakupDao.saveAll(lendingClPaymentBreakups);
        }
        if (!lendingPaymentSchedules.isEmpty()) {
            for (LendingPaymentSchedule lendingPaymentSchedule : lendingPaymentSchedules) {
                if (lendingPaymentSchedule.getLoanAmount() - lendingPaymentSchedule.getPaidPrinciple() == 0) {
                    lendingPaymentSchedule.setStatus("CLOSED");
                    sendClosureNotification(lendingPaymentSchedule);
                }
            }
            lendingPaymentScheduleDao.saveAll(lendingPaymentSchedules);
        }
        if (!lendingLedgers.isEmpty()) {
            lendingLedgerDao.saveAll(lendingLedgers);
        }
        Double usedBalanceCl = 0D;
        double usedBalanceG1 = 0d;
        double usedBalanceG2 = 0d;
        double usedBalanceG3 = 0d;
        if (clPrinciple > 0) {
            usedBalanceCl = clPrinciple;
            usedBalanceG1 = (lendingCaBalanceDetail.getUsedBalanceG1()/lendingCaBalanceDetail.getUsedBalanceCl()) * clPrinciple;
            usedBalanceG2 = (lendingCaBalanceDetail.getUsedBalanceG2()/lendingCaBalanceDetail.getUsedBalanceCl()) * clPrinciple;
            usedBalanceG3 = (lendingCaBalanceDetail.getUsedBalanceG3()/lendingCaBalanceDetail.getUsedBalanceCl()) * clPrinciple;
        }
        lendingCaBalanceDetailDao.creditBalance(lendingClTransaction.getCreditAccountId(), principle, usedBalanceCl, usedBalanceG1, usedBalanceG2, usedBalanceG3);
        if (interest > 0) {
            creditAccountDao.creditInterest(creditAccount.getId(), interest);
            lendingCaBalanceDetailDao.creditInterest(creditAccount.getId(), interest);
        }
    }

    public LendingClPaymentBreakup createPaymentBreakup(LendingClTransaction lendingClTransaction, Double amount, Long loanId, String type) {
        LendingClPaymentBreakup lendingClPaymentBreakup = new LendingClPaymentBreakup();
        lendingClPaymentBreakup.setMerchantId(lendingClTransaction.getMerchantId());
        lendingClPaymentBreakup.setMerchantStoreId(lendingClTransaction.getMerchantStoreId());
        lendingClPaymentBreakup.setLendingClTransaction(lendingClTransaction);
        lendingClPaymentBreakup.setPaymentType(type);
        lendingClPaymentBreakup.setLoanId(loanId);
        lendingClPaymentBreakup.setAmount(amount);
        return lendingClPaymentBreakup;
    }
    
    private void startPromotionalNotification(CreditAccount creditAccount) {
    	Optional<Merchant> merchOptional=merchantDao.findById(creditAccount.getMerchantId());
    	if(merchOptional.isPresent()) {
    		redisNotificationService.sendPromotionalNotificationForCreditLine(merchOptional.get(), creditAccount);
    	}
    }
    
    @Transactional
    public void refundTL(LendingPaymentSchedule lendingPaymentSchedule, CreditAccount creditAccount, Double amount, LendingLedger lendingLedger, LendingClTransaction lendingClTransaction, LendingClLedger lendingClLedger){
        if (creditAccount.getAvailableBalance() + amount > creditAccount.getLimit()) {
            logger.error("Refund amount more than credit limit for merchant:{}", creditAccount.getMerchantId());
            emailHandler.sendEmail(new ArrayList<String>(){{add("khushal.virmani@bharatpe.com");add("rajat.jain@bharatpe.com");}}, "Refund more than credit limit", "Merchant id:" + creditAccount.getMerchantId());
        }
        lendingClLedgerDao.save(lendingClLedger);
        lendingPaymentScheduleDao.save(lendingPaymentSchedule);
        lendingLedgerDao.save(lendingLedger);
        creditAccountDao.creditBalance(creditAccount.getId(), amount);
        lendingCaBalanceDetailDao.creditBalance(lendingClTransaction.getCreditAccountId(), amount, 0D, 0D, 0D, 0D);
        lendingClTransactionDao.save(lendingClTransaction);
    }

    @Transactional
    public void refundCL(CreditAccount creditAccount, LendingCaBalanceDetail lendingCaBalanceDetail, Double amount, Double interest, LendingClTransaction lendingClTransaction, LendingClLedger lendingClLedger){
        if (creditAccount.getAvailableBalance() + amount > creditAccount.getLimit()) {
            logger.error("Refund amount more than credit limit for merchant:{}", creditAccount.getMerchantId());
            emailHandler.sendEmail(new ArrayList<String>(){{add("khushal.virmani@bharatpe.com");add("rajat.jain@bharatpe.com");}}, "Refund more than credit limit", "Merchant id:" + creditAccount.getMerchantId());
        }
        double usedBalanceG1 = (lendingCaBalanceDetail.getUsedBalanceG1()/lendingCaBalanceDetail.getUsedBalanceCl()) * amount;
        double usedBalanceG2 = (lendingCaBalanceDetail.getUsedBalanceG2()/lendingCaBalanceDetail.getUsedBalanceCl()) * amount;
        double usedBalanceG3 = (lendingCaBalanceDetail.getUsedBalanceG3()/lendingCaBalanceDetail.getUsedBalanceCl()) * amount;
        lendingCaBalanceDetailDao.creditBalance(lendingClTransaction.getCreditAccountId(), amount, amount, usedBalanceG1, usedBalanceG2, usedBalanceG3);
        lendingClLedgerDao.save(lendingClLedger);
        creditAccountDao.creditBalance(creditAccount.getId(), amount);
        lendingCaBalanceDetailDao.creditBalance(lendingClTransaction.getCreditAccountId(), amount, 0D, 0D, 0D, 0D);
        lendingClTransactionDao.save(lendingClTransaction);
        if (interest > 0) {
            creditAccountDao.creditInterest(creditAccount.getId(), interest);
            lendingCaBalanceDetailDao.creditInterest(creditAccount.getId(), interest);
        }
    }

    private int getDPD(LendingPaymentSchedule lendingPaymentSchedule) {
        if (lendingPaymentSchedule == null || lendingPaymentSchedule.getDueAmount() == null) {
            return 0;
        }
        return (int) (lendingPaymentSchedule.getDueAmount() / lendingPaymentSchedule.getEdiAmount());
    }

    public void sendClosureNotification(LendingPaymentSchedule lendingPaymentSchedule) {
        CreditAccount creditAccount = creditAccountDao.findTop1ByMerchantIdOrderByIdDesc(lendingPaymentSchedule.getMerchant().getId());
        Optional<Merchant> merchantOptional=merchantDao.findById(creditAccount.getMerchantId());
        if(merchantOptional.isPresent()) {
            Merchant merchant=merchantOptional.get();
            List<String> mobiles=new LinkedList<>();
            mobiles.add(merchant.getMobile());
            String message="Hi "+merchant.getBeneficiaryName()+",\n" +
                    "We have closed Rs."+lendingPaymentSchedule.getLoanAmount()+" Loan post repayment made by you.\n" +
                    "Your available loans balance is Rs."+creditAccount.getAvailableBalance()+". Spend on Bank transfers, Sending money, Bill Payments and more.\n" +
                    "Click Here: bharatpe.in/creditline for more details.";
            smsServiceHandler.sendSMS(mobiles, message, NotificationProvider.SMS.GUPSHUP);
            whatsappNotificationService.send(merchant, null, message, mobiles, null);
        }
    }
}
