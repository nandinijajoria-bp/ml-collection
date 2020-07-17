package com.bharatpe.lending.service;

import com.bharatpe.common.entities.Merchant;
import com.bharatpe.lending.common.dao.*;
import com.bharatpe.lending.common.entity.*;
import com.bharatpe.lending.constant.CreditConstants;
import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.util.CreditUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Date;

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

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

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
        //String group = CreditConstants.SpendGroup.get(client);
        double balance = creditAccount.getAvailableBalance();
//        switch (group) {
//            case "G1": {
//                Double limit = lendingCaBalanceDetail.getAccountLimit() * creditLineCategories.getMaxCreditLimit() * creditLineCategories.getG1Limit();
//                balance = limit - lendingCaBalanceDetail.getUsedBalanceG1();
//                break;
//            }
//            case "G2": {
//                Double limit = lendingCaBalanceDetail.getAccountLimit() * creditLineCategories.getMaxCreditLimit() * creditLineCategories.getG2Limit();
//                balance = limit - lendingCaBalanceDetail.getUsedBalanceG2();
//                break;
//            }
//            case "G3": {
//                Double limit = lendingCaBalanceDetail.getAccountLimit() * creditLineCategories.getMaxCreditLimit() * creditLineCategories.getG3Limit();
//                balance = limit - lendingCaBalanceDetail.getUsedBalanceG3();
//                break;
//            }
//        }
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
        String deeplink;
        if ("prod".equalsIgnoreCase(activeProfile)) {
            deeplink = "bharatpe://dynamic?key=credit-line&wroute=order&wid=" + paymentRequest.getId();
        } else {
            deeplink = "bharatpe://dynamic?key=credit-line-dev&wroute=order&wid=" + paymentRequest.getId();
        }
        return new CreditSpendResponseDTO(paymentRequest.getId(), deeplink);
    }

    @Transactional
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
        boolean sufficientBalance = "CL".equals(paymentRequest.getLoanType()) ? CreditUtil.isSufficientCLBalance(lendingCaBalanceDetail, paymentRequest.getAmount().intValue(), paymentRequest.getMode(), creditLineCategories)
                : CreditUtil.isSufficientTLBalance(creditAccount, lendingCaBalanceDetail, paymentRequest.getAmount().intValue());
        if (!sufficientBalance) {
            return new CreditSpendVerifyResponseDTO(false, "Insufficient Balance");
        }
        transaction.setType(paymentRequest.getLoanType());
        transaction.setStatus(CreditConstants.PaymentStatus.SUCCESS.name());
        lendingClTransactionDao.save(transaction);
        creditLineService.debitCLBalance(creditAccount, lendingCaBalanceDetail, paymentRequest.getAmount().intValue(), paymentRequest.getMode(), paymentRequest.getLoanType());
        if ("CL".equals(paymentRequest.getLoanType())) {
            creditLineService.insertClLedger(transaction);
        } else {
            LendingTlDetails lendingTlDetails = creditLineService.insertTlDetails(transaction, paymentRequest.getTenure());
            creditLineService.createLPS(lendingTlDetails, merchant);
        }
        return createSpendVerifyResponse(transaction, creditAccount);
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

    private CreditSpendVerifyResponseDTO createSpendVerifyResponse(LendingClTransaction lendingClTransaction, CreditAccount creditAccount) {
        CreditSpendVerifyResponseDTO responseDTO = new CreditSpendVerifyResponseDTO();
        responseDTO.setTransactionId(lendingClTransaction.getId());
        responseDTO.setAmount(Double.valueOf(df.format(lendingClTransaction.getAmount())));
        responseDTO.setTransferTime(new Date());
        responseDTO.setAvailableLimit(Double.valueOf(df.format(creditAccount.getAvailableBalance())));
        responseDTO.setStatus(lendingClTransaction.getStatus());
        return responseDTO;
    }

    public CreditSpendVerifyResponseDTO checkStatus(String orderId, String client, Merchant merchant) {
        LendingClTransaction lendingClTransaction = lendingClTransactionDao.findByMerchantIdAndSubTypeAndOrderId(merchant.getId(), client, orderId);
        if (lendingClTransaction == null) {
            return new CreditSpendVerifyResponseDTO(false, "orderId not found for this client");
        }
        return new CreditSpendVerifyResponseDTO(lendingClTransaction.getId(), Double.valueOf(df.format(lendingClTransaction.getAmount())), lendingClTransaction.getCreatedAt(), lendingClTransaction.getStatus());
    }
}
