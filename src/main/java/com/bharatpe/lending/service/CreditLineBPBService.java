package com.bharatpe.lending.service;

import com.bharatpe.common.dao.MerchantDao;
import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.common.entities.Merchant;
import com.bharatpe.common.enums.Status;
import com.bharatpe.lending.common.dao.*;
import com.bharatpe.lending.common.entity.*;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.constant.CreditConstants;
import com.bharatpe.lending.dao.LendingLedgerDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.util.CreditUtil;
import com.bharatpe.lending.util.LoanUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
public class CreditLineBPBService {

    Logger logger = LoggerFactory.getLogger(CreditLineBPBService.class);

    @Autowired
    CreditAccountDao creditAccountDao;

    @Autowired
    LendingCaBalanceDetailDao lendingCaBalanceDetailDao;

    @Autowired
    CreditLineCategoriesDao creditLineCategoriesDao;

    @Autowired
    LendingClTransactionRequestDao lendingClTransactionRequestDao;

    @Autowired
    CreditLineService creditLineService;

    @Autowired
    LendingClTransactionDao lendingClTransactionDao;

    @Autowired
    LendingTlDetailsDao lendingTlDetailsDao;

    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;

    @Autowired
    LendingLedgerDao lendingLedgerDao;

    @Autowired
    LendingClLedgerDao lendingClLedgerDao;

    @Autowired
    MerchantDao merchantDao;

    @Autowired
    CreditLineTransaction creditLineTransaction;

    @Value("${cl.deeplink}")
    private String clDeeplink;

    private final DecimalFormat df = new DecimalFormat("#.##");

    public CheckBalanceResponseDTO getBalance(Merchant merchant, String client) {
        CreditAccount creditAccount = creditAccountDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchant.getId(), CreditConstants.AccountStatus.ACTIVE.name());
        if (creditAccount == null) {
            logger.info("Credit account not found for merchant:{}", merchant.getId());
            return new CheckBalanceResponseDTO(false, "Credit account does not exist");
        }
        LendingCaBalanceDetail lendingCaBalanceDetail = lendingCaBalanceDetailDao.findByMerchantIdAndCreditAccountId(merchant.getId(), creditAccount.getId());
        CreditLineCategories creditLineCategories = creditLineCategoriesDao.findTop1ByCategoryOrderByMaxCreditLimitDesc(creditAccount.getSegment());
        if (lendingCaBalanceDetail == null || creditLineCategories == null) {
            logger.info("Credit account categories not found for merchant:{}", merchant.getId());
            return new CheckBalanceResponseDTO(false, "Credit account category does not exist");
        }
        if (!CreditConstants.validSpendMode(client)) {
            logger.info("Invalid client name for merchant:{}", merchant.getId());
            return new CheckBalanceResponseDTO(false, "Invalid client");
        }
        double balance = creditAccount.getAvailableBalance();
        return new CheckBalanceResponseDTO(Double.valueOf(df.format(creditAccount.getLimit())), Double.valueOf(df.format(balance)));
    }

    @Transactional
    public CreditSpendResponseDTO createTxn(Long merchantId, CreateTxnRequestDTO requestDTO) {
        if (requestDTO.getAmount() == null || requestDTO.getMode() == null || !CreditConstants.validSpendMode(requestDTO.getMode())) {
            return new CreditSpendResponseDTO(false, "Invalid request");
        }
        CreditAccount creditAccount = creditAccountDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchantId, "ACTIVE");
        if (creditAccount == null) {
            return new CreditSpendResponseDTO(false, "Credit Account does not exist");
        }
        LendingCaBalanceDetail lendingCaBalanceDetail = lendingCaBalanceDetailDao.findByMerchantIdAndCreditAccountId(merchantId, creditAccount.getId());
        if (!CreditUtil.isSufficientBalance(creditAccount, lendingCaBalanceDetail, requestDTO.getAmount().intValue())) {
            return new CreditSpendResponseDTO(false, "Insufficient Balance");
        }
        LendingClTransactionRequest paymentRequest = lendingClTransactionRequestDao.save(new LendingClTransactionRequest(merchantId, creditAccount.getId(), requestDTO.getMode(), requestDTO.getAmount()));
        insertClTransaction(creditAccount, paymentRequest.getAmount(), paymentRequest.getLoanType(), paymentRequest.getMode(), requestDTO, CreditConstants.PaymentStatus.PENDING.name(), paymentRequest.getId());
        String deeplink = clDeeplink + "&wroute=order&wid=" + paymentRequest.getId();
        return new CreditSpendResponseDTO(paymentRequest.getId(), deeplink);
    }

    public CreditSpendVerifyResponseDTO deductCL(Merchant merchant, CreditDeductRequestDTO requestDTO) {
        if (requestDTO.getRequestId() == null || (!"TL".equals(requestDTO.getLoanType()) && !"CL".equals(requestDTO.getLoanType()))) {
            return new CreditSpendVerifyResponseDTO(false, "Invalid request");
        }
        if ("TL".equals(requestDTO.getLoanType()) && (requestDTO.getTenure() == null || !Arrays.asList(1,3,6,9,12,15).contains(requestDTO.getTenure()))) {
            return new CreditSpendVerifyResponseDTO(false, "Invalid request");
        }
        CreditAccount creditAccount = creditAccountDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchant.getId(), "ACTIVE");
        if (creditAccount == null) {
            return new CreditSpendVerifyResponseDTO(false, "Credit Account does not exist");
        }
        LendingClTransactionRequest paymentRequest = lendingClTransactionRequestDao.findByIdAndMerchantId(requestDTO.getRequestId(),merchant.getId());
        if (paymentRequest == null) {
            return new CreditSpendVerifyResponseDTO(false, "Invalid Payment request_id");
        }
        LendingClTransaction transaction = lendingClTransactionDao.findByCreditAccountIdAndRequestId(creditAccount.getId(), paymentRequest.getId());
        if (transaction == null) {
            return new CreditSpendVerifyResponseDTO(false, "transaction not found");
        }
        paymentRequest.setLoanType(requestDTO.getLoanType());
        paymentRequest.setTenure(requestDTO.getTenure());
        lendingClTransactionRequestDao.save(paymentRequest);
        LendingCaBalanceDetail lendingCaBalanceDetail = lendingCaBalanceDetailDao.findByMerchantIdAndCreditAccountId(merchant.getId(), creditAccount.getId());
        CreditLineCategories creditLineCategories = creditLineCategoriesDao.findTop1ByCategoryOrderByMaxCreditLimitDesc(creditAccount.getSegment());
        List<LendingTlDetails> todayLoans = lendingTlDetailsDao.findByMerchantIdAndDateBetween(merchant.getId(), DateTimeUtil.getCurrentDayStartTime(), DateTimeUtil.getEndTimeFromDateTime(new Date()));
        boolean sufficientBalance = "CL".equals(paymentRequest.getLoanType()) ? CreditUtil.isSufficientCLBalance(lendingCaBalanceDetail, paymentRequest.getAmount().intValue(), paymentRequest.getMode(), creditLineCategories)
                : CreditUtil.isSufficientTLBalance(creditAccount, lendingCaBalanceDetail, paymentRequest.getAmount().intValue(), todayLoans);
        if (!sufficientBalance) {
            return new CreditSpendVerifyResponseDTO(false, "Insufficient Balance");
        }
        creditLineTransaction.debitBPB(creditAccount, transaction, paymentRequest.getLoanType(), paymentRequest.getTenure(), merchant);
        //send debit notification
        try {
            String message = paymentRequest.getLoanType().equalsIgnoreCase("CL") ? creditLineService.getFlexibileNotificationMessage(transaction, merchant) : creditLineService.getFixedNotificationMessage(transaction, merchant);
            creditLineService.sendNotification(message, merchant);
        } catch (Exception e) {
            logger.error("Unable to send debit notification", e);
        }
        return createSpendVerifyResponse(transaction);
    }

    private void insertClTransaction(CreditAccount creditAccount, Double amount, String loanType, String spendMode, CreateTxnRequestDTO requestDTO, String status, Long requestId) {
        LendingClTransaction lendingClTransaction = new LendingClTransaction();
        lendingClTransaction.setCreditAccountId(creditAccount.getId());
        lendingClTransaction.setMerchantId(creditAccount.getMerchantId());
        lendingClTransaction.setMerchantStoreId(creditAccount.getMerchantStoreId());
        lendingClTransaction.setStatus(status);
        lendingClTransaction.setAmount(amount);
        lendingClTransaction.setMode("DEBIT");
        lendingClTransaction.setType(loanType);
        lendingClTransaction.setSubType(spendMode);
        if (requestDTO != null) {
            lendingClTransaction.setOrderId(requestDTO.getOrderId());
            lendingClTransaction.setNarration1(requestDTO.getNarration1());
            lendingClTransaction.setNarration2(requestDTO.getNarration2());
            lendingClTransaction.setNarration3(requestDTO.getNarration3());
            lendingClTransaction.setIcon(requestDTO.getIcon());
        }
        lendingClTransaction.setRequestId(requestId);
        lendingClTransactionDao.save(lendingClTransaction);
    }

    private CreditSpendVerifyResponseDTO createSpendVerifyResponse(LendingClTransaction lendingClTransaction) {
        Optional<LendingClTransaction> lendingClTransactionOptional = lendingClTransactionDao.findById(lendingClTransaction.getId());
        if (lendingClTransactionOptional.isPresent()) {
            lendingClTransaction = lendingClTransactionOptional.get();
        }
        CreditAccount creditAccount = creditAccountDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(lendingClTransaction.getMerchantId(), "ACTIVE");
        CreditSpendVerifyResponseDTO responseDTO = new CreditSpendVerifyResponseDTO();
        responseDTO.setTransactionId(lendingClTransaction.getId());
        responseDTO.setAmount(Double.valueOf(df.format(lendingClTransaction.getAmount())));
        responseDTO.setTransferTime(new Date());
        responseDTO.setAvailableLimit(Double.valueOf(df.format(creditAccount.getAvailableBalance())));
        responseDTO.setStatus(lendingClTransaction.getStatus());
        return responseDTO;
    }

    public CreditSpendVerifyResponseDTO checkStatus(Long orderId) {
        Optional<LendingClTransaction> lendingClTransactionOptional = lendingClTransactionDao.findById(orderId);
        if (!lendingClTransactionOptional.isPresent()) {
            return new CreditSpendVerifyResponseDTO(false, "orderId not found for this client");
        }
        LendingClTransaction lendingClTransaction = lendingClTransactionOptional.get();
        return new CreditSpendVerifyResponseDTO(lendingClTransaction.getId(), Double.valueOf(df.format(lendingClTransaction.getAmount())), lendingClTransaction.getCreatedAt(), lendingClTransaction.getStatus());
    }

    @Transactional
    public CreditSpendVerifyResponseDTO refund(CreditRefundRequestDTO requestDTO) {
        Optional<LendingClTransaction> lendingClTransactionOptional = lendingClTransactionDao.findById(requestDTO.getOrderId());
        if (!lendingClTransactionOptional.isPresent() || !CreditConstants.PaymentStatus.SUCCESS.name().equalsIgnoreCase(lendingClTransactionOptional.get().getStatus())) {
            return new CreditSpendVerifyResponseDTO(false, "transaction not found");
        }
        LendingClTransaction lendingClTransaction = lendingClTransactionOptional.get();
        if ("TL".equalsIgnoreCase(lendingClTransaction.getType()) && requestDTO.getAmount() < lendingClTransaction.getAmount()) {
            return new CreditSpendVerifyResponseDTO(false, "Partial refund not supported for TL");
        }
        List<LendingClTransaction> refunds = lendingClTransactionDao.findByCreditAccountIdAndParentId(lendingClTransaction.getCreditAccountId(), lendingClTransaction.getId());
        double remainingRefund = lendingClTransaction.getAmount();
        for (LendingClTransaction refund : refunds) {
            remainingRefund -= refund.getAmount();
        }
        if (requestDTO.getAmount() > remainingRefund) {
            return new CreditSpendVerifyResponseDTO(false, "Refund amount more than transaction amount");
        }
        Optional<Merchant> merchantOptional = merchantDao.findById(lendingClTransaction.getMerchantId());
        if ("TL".equalsIgnoreCase(lendingClTransaction.getType())) {
            return refundTL(lendingClTransaction, requestDTO.getAmount(), merchantOptional.get());
        } else {
            return refundCL(lendingClTransaction, requestDTO.getAmount(), merchantOptional.get());
        }
    }

    private CreditSpendVerifyResponseDTO refundTL(LendingClTransaction lendingClTransaction, Double amount, Merchant merchant) {
        LendingTlDetails lendingTlDetails = lendingTlDetailsDao.findByLendingClTransaction(lendingClTransaction);
        if (lendingTlDetails == null) {
            logger.error("lending tl details not found for transaction:{}", lendingClTransaction.getId());
            return new CreditSpendVerifyResponseDTO(false, "Loan details not found for this transaction");
        }
        LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.findByTlDetailsIdAndCreditLoanAndStatus(lendingTlDetails.getId(), true, "ACTIVE");
        if (lendingPaymentSchedule == null) {
            logger.error("no active loan found for transaction:{}", lendingClTransaction.getId());
            return new CreditSpendVerifyResponseDTO(false, "No Active Loan found for this transaction");
        }
        lendingPaymentSchedule.setPaidAmount(lendingPaymentSchedule.getPaidAmount() + amount);
        lendingPaymentSchedule.setPaidPrinciple(lendingPaymentSchedule.getPaidPrinciple() + amount);
        lendingPaymentSchedule.setStatus("CLOSED");
        lendingPaymentScheduleDao.save(lendingPaymentSchedule);
        //TODO need to refund paid edi
        CreditAccount creditAccount = creditAccountDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(lendingClTransaction.getMerchantId(), "ACTIVE");
        LendingCaBalanceDetail lendingCaBalanceDetail = lendingCaBalanceDetailDao.findByMerchantIdAndCreditAccountId(creditAccount.getMerchantId(), creditAccount.getId());
        double updatedBalance = (creditAccount.getAvailableBalance() + amount) >= creditAccount.getLimit() ? creditAccount.getLimit() : creditAccount.getAvailableBalance() + amount;
        creditAccount.setAvailableBalance(updatedBalance);
        creditAccount.setUsedBalance(creditAccount.getUsedBalance() - amount);
        lendingCaBalanceDetail.setAvailableBalance(updatedBalance);
        lendingCaBalanceDetail.setUsedBalance(lendingCaBalanceDetail.getUsedBalance() - amount);
        creditAccountDao.save(creditAccount);
        lendingCaBalanceDetailDao.save(lendingCaBalanceDetail);
        createLendingLedger(lendingPaymentSchedule, DateTimeUtil.getCurrentDayStartTime(), Status.LendingTransactionType.EDI.toString(), amount, amount);
        createRefundTransaction(lendingClTransaction, amount, CreditConstants.PaymentType.REFUND.name());
        String message = "We have refunded Rs." + amount + " towards your BharatPe Loans Balance on account of failed "
                + CreditConstants.SpendModeFrontEndFormat.getOrDefault(lendingClTransaction.getSubType(), lendingClTransaction.getSubType()) +
                " transaction.\n\nYour updated BharatPe Loans Balance is Rs." + creditAccount.getAvailableBalance() + ". Use it for Bank transfers, Paying Bills, Sending money, Shopping etc.\nClick Here: " + CreditConstants.MESSAGE_NOTIFICATION_LINK + " for more details.";
        creditLineService.sendNotification(message, merchant);
        return new CreditSpendVerifyResponseDTO(true, null);
    }

    private CreditSpendVerifyResponseDTO refundCL(LendingClTransaction lendingClTransaction, Double amount, Merchant merchant) {
        CreditAccount creditAccount = creditAccountDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(lendingClTransaction.getMerchantId(), "ACTIVE");
        LendingCaBalanceDetail lendingCaBalanceDetail = lendingCaBalanceDetailDao.findByMerchantIdAndCreditAccountId(creditAccount.getMerchantId(), creditAccount.getId());
        double refundAmount = amount;
        CreditLineCategories creditLineCategories = creditLineCategoriesDao.findTop1ByCategoryOrderByMaxCreditLimitDesc(creditAccount.getSegment());
        double interest = LoanUtil.getDateDiffInDays(lendingClTransaction.getCreatedAt(), new Date()) * amount * creditLineCategories.getClInterestRate();
        if (interest > 0) {
            creditAccount.setInterestDue(creditAccount.getInterestDue() - interest);
            lendingCaBalanceDetail.setInterestDue(lendingCaBalanceDetail.getInterestDue() - interest);
            refundAmount += interest;
            //createRefundTransaction(lendingClTransaction, interest, CreditConstants.PaymentType.INTEREST_ROLLBACK.name());
        }
        double updatedBalance = (creditAccount.getAvailableBalance() + refundAmount) >= creditAccount.getLimit() ? creditAccount.getLimit() : creditAccount.getAvailableBalance() + refundAmount;
        creditAccount.setAvailableBalance(updatedBalance);
        creditAccount.setUsedBalance(creditAccount.getUsedBalance() - refundAmount);
        lendingCaBalanceDetail.setAvailableBalance(updatedBalance);
        lendingCaBalanceDetail.setUsedBalance(lendingCaBalanceDetail.getUsedBalance() - refundAmount);
        if (lendingCaBalanceDetail.getUsedBalanceCl() > 0) {
            double usedG1 = lendingCaBalanceDetail.getUsedBalanceG1() - ((lendingCaBalanceDetail.getUsedBalanceG1()/lendingCaBalanceDetail.getUsedBalanceCl()) * refundAmount);
            double usedG2 = lendingCaBalanceDetail.getUsedBalanceG2() - ((lendingCaBalanceDetail.getUsedBalanceG2()/lendingCaBalanceDetail.getUsedBalanceCl()) * refundAmount);
            double usedG3 = lendingCaBalanceDetail.getUsedBalanceG3() - ((lendingCaBalanceDetail.getUsedBalanceG3()/lendingCaBalanceDetail.getUsedBalanceCl()) * refundAmount);
            lendingCaBalanceDetail.setUsedBalanceG1(usedG1);
            lendingCaBalanceDetail.setUsedBalanceG2(usedG2);
            lendingCaBalanceDetail.setUsedBalanceG3(usedG3);
            lendingCaBalanceDetail.setUsedBalanceCl(usedG1 + usedG2 + usedG3);
        }
        creditAccountDao.save(creditAccount);
        lendingCaBalanceDetailDao.save(lendingCaBalanceDetail);
        insertClLedger(lendingClTransaction, refundAmount, amount, refundAmount - amount);
        createRefundTransaction(lendingClTransaction, amount, CreditConstants.PaymentType.REFUND.name());
        String message;
        if (interest > 0) {
            message = "We have refunded Rs." + amount + " towards your BharatPe Loans Balance on account of failed "
                    + CreditConstants.SpendModeFrontEndFormat.getOrDefault(lendingClTransaction.getSubType(), lendingClTransaction.getSubType()) +
                    " transaction.\nIn addition, charges of Rs."+interest+" are also reversed.\n\nYour updated BharatPe Loans Balance is Rs." + creditAccount.getAvailableBalance() + ". Use it for Bank transfers, Paying Bills, Sending money, Shopping etc.\nClick Here: " + CreditConstants.MESSAGE_NOTIFICATION_LINK + " for more details.";
        } else {
            message = "We have refunded Rs." + amount + " towards your BharatPe Loans Balance on account of failed "
                    + CreditConstants.SpendModeFrontEndFormat.getOrDefault(lendingClTransaction.getSubType(), lendingClTransaction.getSubType()) +
                    " transaction.\n\nYour updated BharatPe Loans Balance is Rs." + creditAccount.getAvailableBalance() + ". Use it for Bank transfers, Paying Bills, Sending money, Shopping etc.\nClick Here: " + CreditConstants.MESSAGE_NOTIFICATION_LINK + " for more details.";
        }
        creditLineService.sendNotification(message, merchant);
        return new CreditSpendVerifyResponseDTO(true, null);
    }

    private void createRefundTransaction(LendingClTransaction transaction, Double amount, String type) {
        LendingClTransaction lendingClTransaction = new LendingClTransaction();
        lendingClTransaction.setCreditAccountId(transaction.getCreditAccountId());
        lendingClTransaction.setMerchantId(transaction.getMerchantId());
        lendingClTransaction.setMerchantStoreId(transaction.getMerchantStoreId());
        lendingClTransaction.setStatus(CreditConstants.PaymentStatus.SUCCESS.name());
        lendingClTransaction.setAmount(amount);
        lendingClTransaction.setMode("CREDIT");
        lendingClTransaction.setType(type);
        lendingClTransaction.setSubType(transaction.getSubType());
        lendingClTransaction.setParentId(transaction.getId());
        lendingClTransactionDao.save(lendingClTransaction);
    }

    private void createLendingLedger(LendingPaymentSchedule lendingPaymentSchedule, Date date, String txnType, Double amount, Double principle) {
        LendingLedger lendingLedger = new LendingLedger();
        lendingLedger.setMerchant(lendingPaymentSchedule.getMerchant());
        if(lendingPaymentSchedule.getMerchantStoreId() != null && lendingPaymentSchedule.getMerchantStoreId() > 0){
            lendingLedger.setMerchantStoreId(lendingPaymentSchedule.getMerchantStoreId());
        }
        lendingLedger.setLendingPaymentSchedule(lendingPaymentSchedule);
        lendingLedger.setDate(date);
        lendingLedger.setTxnType(txnType);
        lendingLedger.setAmount(amount);
        lendingLedger.setInterest(0.0);
        lendingLedger.setOtherCharges(0.0);
        lendingLedger.setPenalty(0.0);
        lendingLedger.setPrinciple(principle);
        lendingLedger.setDescription("CREDIT_LINE");
        lendingLedger.setAdjustmentMode(CreditConstants.PaymentType.REFUND.name());
        lendingLedgerDao.save(lendingLedger);
    }

    private void insertClLedger(LendingClTransaction lendingClTransaction, Double amount, Double principle, Double interest) {
        LendingClLedger lendingClLedger = new LendingClLedger();
        lendingClLedger.setMerchantId(lendingClTransaction.getMerchantId());
        lendingClLedger.setMerchantStoreId(lendingClTransaction.getMerchantStoreId());
        lendingClLedger.setClTransactionId(lendingClTransaction.getId());
        lendingClLedger.setTransactionType(CreditConstants.PaymentType.REFUND.name());
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
        lendingClLedger.setOtherCharges(0D);
        lendingClLedger.setPenalty(0D);
        lendingClLedgerDao.save(lendingClLedger);
    }
}
