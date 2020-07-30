package com.bharatpe.lending.service;

import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.common.entities.Merchant;
import com.bharatpe.lending.common.dao.*;
import com.bharatpe.lending.common.entity.CreditAccount;
import com.bharatpe.lending.common.entity.LendingClLedger;
import com.bharatpe.lending.common.entity.LendingClTransaction;
import com.bharatpe.lending.common.entity.LendingTlDetails;
import com.bharatpe.lending.constant.CreditConstants;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dto.CreditSpendResponseDTO;
import com.bharatpe.lending.util.LoanUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

@Service
@Transactional
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

    public LendingClTransaction createTxnAndDebit(CreditAccount creditAccount, Double amount, String loanType, String spendMode, Long requestId, Integer tenure) {
        logger.info("Initializing new transaction for account:{}, amount:{}, mode:{}", creditAccount.getId(), amount, spendMode);
        LendingClTransaction lendingClTransaction = new LendingClTransaction();
        lendingClTransaction.setCreditAccountId(creditAccount.getId());
        lendingClTransaction.setMerchantId(creditAccount.getMerchantId());
        lendingClTransaction.setMerchantStoreId(creditAccount.getMerchantStoreId());
        lendingClTransaction.setStatus(CreditConstants.PaymentStatus.INIT.name());
        lendingClTransaction.setAmount(amount);
        lendingClTransaction.setMode("DEBIT");
        lendingClTransaction.setType(loanType);
        lendingClTransaction.setSubType(spendMode);
        lendingClTransaction.setRequestId(requestId);
        lendingClTransaction = lendingClTransactionDao.save(lendingClTransaction);
        debitCLBalance(creditAccount, amount, spendMode, loanType);
        if ("CL".equals(loanType)) {
            insertClLedger(lendingClTransaction, CreditConstants.PaymentType.CL.name(), -lendingClTransaction.getAmount(), -lendingClTransaction.getAmount(), 0D, 0D, 0D);
        } else {
            insertTlDetails(lendingClTransaction, tenure);
        }
        return lendingClTransaction;
    }

    public void debitBPB(CreditAccount creditAccount, LendingClTransaction lendingClTransaction, String type, Integer tenure, Merchant merchant){
        lendingClTransactionDao.updateStatusAndType(CreditConstants.PaymentStatus.SUCCESS.name(), type, lendingClTransaction.getId());
        debitCLBalance(creditAccount, lendingClTransaction.getAmount(), lendingClTransaction.getSubType(), type);
        if ("CL".equals(type)) {
            insertClLedger(lendingClTransaction, CreditConstants.PaymentType.CL.name(), -lendingClTransaction.getAmount(), -lendingClTransaction.getAmount(), 0D, 0D, 0D);
        } else {
            insertTlDetails(lendingClTransaction, tenure);
            createLPS(merchant, lendingClTransaction);
        }
    }

    public void rollbackTxn(LendingClTransaction lendingClTransaction) {
        logger.info("Rollback transaction:{}", lendingClTransaction.getId());
        creditCLBalance(lendingClTransaction.getCreditAccountId(), lendingClTransaction.getAmount(), lendingClTransaction.getSubType(), lendingClTransaction.getType());
        if ("CL".equals(lendingClTransaction.getType())) {
            insertClLedger(lendingClTransaction, CreditConstants.PaymentType.ROLLBACK.name(), lendingClTransaction.getAmount(), lendingClTransaction.getAmount(), 0D, 0D, 0D);
        } else {
            lendingTlDetailsDao.deleteByTransactionId(lendingClTransaction.getId());
        }
        lendingClTransactionDao.updateStatus(CreditConstants.PaymentStatus.FAILED.name(), lendingClTransaction.getId());
    }

    public void debitCLBalance(CreditAccount creditAccount, Double amount, String spendMode, String loanType) {
        logger.info("Credit line debit for account:{}, amount:{}, mode:{}", creditAccount.getId(), amount, spendMode);
        creditAccountDao.debitBalance(creditAccount.getId(), amount);
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

    public void creditCLBalance(Long creditAccountId, Double amount, String spendMode, String loanType) {
        logger.info("Credit line credit for account:{}, amount:{}, mode:{}", creditAccountId, amount, spendMode);
        creditAccountDao.creditBalance(creditAccountId, amount);
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
        lendingCaBalanceDetailDao.creditBalance(creditAccountId, amount, usedBalanceCl, usedBalanceG1, usedBalanceG2, usedBalanceG3);
    }

    public void insertClLedger(LendingClTransaction lendingClTransaction, String type, Double amount, Double principle, Double interest, Double penalty, Double otherCharges) {
        logger.info("Inserting lending_cl_ledger for txn:{}", lendingClTransaction.getId());
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
        lendingClLedgerDao.save(lendingClLedger);
    }

    public LendingTlDetails insertTlDetails(LendingClTransaction lendingClTransaction, Integer tenure) {
        logger.info("Inserting lending_tl_details for txn:{}", lendingClTransaction.getId());
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
        String loanId = "CL" + df.format(new Date()) + lendingClTransaction.getId();
        lendingTlDetails.setExternalLoanId(loanId);
        return lendingTlDetailsDao.save(lendingTlDetails);
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
            lendingPaymentSchedule = lendingPaymentScheduleDao.save(lendingPaymentSchedule);
            liquiloansService.createEdiSchedule(lendingPaymentSchedule);
//            LendingPaymentSchedule finalLendingPaymentSchedule = lendingPaymentSchedule;
//            createLeadExecutor.submit(() -> liquiloansService.createLead(finalLendingPaymentSchedule, lendingTlDetails));
        } catch (Exception e) {
            logger.error("Error creating LPS for merchant:{} and transaction:{}", merchant.getId(), lendingClTransaction.getId());
        }
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

}
