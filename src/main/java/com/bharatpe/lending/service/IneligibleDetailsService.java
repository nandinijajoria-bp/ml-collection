package com.bharatpe.lending.service;

import com.bharatpe.common.dao.*;
import com.bharatpe.common.entities.*;
import com.bharatpe.lending.common.Handler.MerchantSummaryHandler;
import com.bharatpe.lending.common.dto.MerchantResponseDTO;
import com.bharatpe.lending.common.enums.RejectionStage;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.slave.entity.PaymentTransactionNewSlave;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.constant.ExperianConstants;
import com.bharatpe.lending.constant.LendingConstants;
import com.bharatpe.lending.dto.IneligibleAPIResponseDto;
import com.bharatpe.lending.dto.IneligibleResponseDTO;
import com.bharatpe.lending.dto.IneligibleAPIResponseDto.Banner;

import com.bharatpe.lending.handlers.MerchantSummaryExceptionHandler;
import com.bharatpe.lending.util.LoanUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

@Service
public class IneligibleDetailsService {

    private Logger logger = LoggerFactory.getLogger(IneligibleDetailsService.class);

//    @Autowired
//    private MerchantSummaryDao merchantSummaryDao;

    @Autowired
    private MerchantLoanRequestDoa merchantLoanRequestDoa;

//    @Autowired
//    private MerchantDao merchantDao;

    @Autowired
    MerchantLoanRequestAuditTrailDoa merchantLoanRequestAuditTrailDoa;

//    @Autowired
//    ScoreCategoryMasterDao scoreCategoryMasterDao;
    
    @Autowired
    ExperianDao experianDao;
//
//    @Autowired
//    BharatSwipeTerminalDaoSlave bharatSwipeTerminalDaoSlave;
//
//    @Autowired
//    FPAccountDaoSlave fpAccountDaoSlave;

    @Autowired
    EasyLoanUtil easyLoanUtil;

    @Autowired
    MerchantSummaryHandler merchantSummaryHandler;

//    public IneligibleResponseDTO fetchIneligibleLoanDetails(Merchant merchant, IneligibleRequestDTO ineligibleRequestDTO) {
//        logger.debug("Fetching Ineligible Loan Details for merchantId : {}", merchant.getId());
////        MerchantSummary merchantSummary = merchantSummaryDao.getByMerchantId(merchant.getId());
//        MerchantResponseDTO merchantResponseDTO = merchantSummaryHandler.getMerchantSummary(merchant.getId());
//        if (ObjectUtils.isEmpty(merchantResponseDTO)) {
//            throw new MerchantSummaryExceptionHandler(merchant.getId().toString());
//        }
//        int previousLoanCount = (merchantResponseDTO.getTotalLoansCount() != null) ? merchantResponseDTO.getTotalLoansCount() : 0;
//        IneligibleResponseDTO ineligibleResponseDTO = new IneligibleResponseDTO(previousLoanCount);
//        MerchantLoanRequest merchantLoanRequest = merchantLoanRequestDoa.getMerchantLoanRequest(merchant.getId());
//        ScoreCategoryMaster scoreCategoryMaster = null;
//        if (merchant.getBusinessCategory() != null && !merchant.getBusinessCategory().trim().equalsIgnoreCase("")) {
//            scoreCategoryMaster = scoreCategoryMasterDao.getByCategory(merchant.getBusinessCategory());
//        }
//        if (scoreCategoryMaster == null) {
//            Object[] objects = scoreCategoryMasterDao.getCategoryAverage();
//            scoreCategoryMaster = new ScoreCategoryMaster();
//            scoreCategoryMaster.setTxnCount((double)objects[0]);
//            scoreCategoryMaster.setAvgDailyTpv((double)objects[1]);
//        }
//        if (ineligibleRequestDTO != null && ineligibleRequestDTO.getPanCard() != null && !ineligibleRequestDTO.getPanCard().trim().equalsIgnoreCase("")) {
//            logger.info("New Ineligible Loan request with panCard : {} and merchantId : {}", ineligibleRequestDTO.getPanCard(), merchant.getId());
//            merchantLoanRequestDoa.deleteByMerchantId(merchant.getId());
//            merchantLoanRequest = calculateTarget(merchantResponseDTO, merchant.getId(), ineligibleRequestDTO.getPanCard(), scoreCategoryMaster);
//            MerchantLoanRequestAuditTrail merchantLoanRequestAuditTrail = MerchantLoanRequestAuditTrail.createObject(merchantLoanRequest);
//            merchantLoanRequestAuditTrailDoa.save(merchantLoanRequestAuditTrail);
//        }
//        if (merchantLoanRequest != null) {
//            calculateIneligibleLoanDetails(merchantResponseDTO, merchantLoanRequest, ineligibleResponseDTO);
//        }
//        return ineligibleResponseDTO;
//    }

    private void calculateIneligibleLoanDetails(MerchantResponseDTO merchantResponseDTO, MerchantLoanRequest merchantLoanRequest, IneligibleResponseDTO ineligibleResponseDTO) {
        Map<String, Object> transactionCountDetails = new HashMap<>();
        Map<String, Object> transactionAmountDetails = new HashMap<>();
        Map<String, Object> loanDetails = new HashMap<>();
        int currentTxnCount = (merchantResponseDTO != null && merchantResponseDTO.getDailyTxnCount() != null) ? merchantResponseDTO.getDailyTxnCount() : 0;
        double currentTxnValue = (merchantResponseDTO != null && merchantResponseDTO.getDailyTxnAmount() != null) ? merchantResponseDTO.getDailyTxnAmount() : 0;
        int onGoingTransactions = currentTxnCount - merchantLoanRequest.getInitialTransactionCount();
        double onGoingAmount = currentTxnValue - merchantLoanRequest.getInitialTransactionAmount();
        int transactionCountLeft = Math.max(merchantLoanRequest.getTargetTransactionCount() - onGoingTransactions, 0);
        double transactionAmountLeft = Math.max(merchantLoanRequest.getTargetTransactionAmount() - onGoingAmount, 0);
        Calendar c = Calendar.getInstance();
        c.setTime(merchantLoanRequest.getCreatedAt());
        c.add(Calendar.DATE, 30);
        Date unlockDate = c.getTime();
        if (transactionCountLeft == 0 && transactionAmountLeft == 0) {
            ineligibleResponseDTO.setEligible(true);
        } else {
            ineligibleResponseDTO.setEligible(false);
        }
//        long gracePeriod = TimeUnit.DAYS.convert(new Date().getTime() - unlockDate.getTime(), TimeUnit.MILLISECONDS);
//        if (merchantLoanRequest.getRequestedLoanAmount() >= 10000 && merchantLoanRequest.getRequestedLoanAmount() < 200000 && gracePeriod > 6) {//if grace period is more than 7 days then start a new loan cycle
//            return;
//        }
//        if (merchantLoanRequest.getRequestedLoanAmount() >= 200000 && merchantLoanRequest.getRequestedLoanAmount() < 400000 && gracePeriod > 20) {//if grace period is more than 7 days then start a new loan cycle
//            return;
//        }
//        if (merchantLoanRequest.getRequestedLoanAmount() >= 400000 && gracePeriod > 29) {//if grace period is more than 7 days then start a new loan cycle
//            return;
//        }
        transactionCountDetails.put("txn_left", merchantLoanRequest.getTargetTransactionCount().equals(0) ? 0 : transactionCountLeft);
        transactionCountDetails.put("txn_ongoing", merchantLoanRequest.getTargetTransactionCount().equals(0) ? currentTxnCount : onGoingTransactions);
        transactionCountDetails.put("txn_total", merchantLoanRequest.getTargetTransactionCount().equals(0) ? currentTxnCount : merchantLoanRequest.getTargetTransactionCount());
        transactionAmountDetails.put("txn_left", merchantLoanRequest.getTargetTransactionCount().equals(0) ? 0 : transactionAmountLeft);
        transactionAmountDetails.put("txn_ongoing", merchantLoanRequest.getTargetTransactionCount().equals(0) ? currentTxnValue : onGoingAmount);
        transactionAmountDetails.put("txn_total", merchantLoanRequest.getTargetTransactionCount().equals(0) ? currentTxnValue : merchantLoanRequest.getTargetTransactionAmount());
        loanDetails.put("average_txn", 100);
        loanDetails.put("unlock_date", unlockDate);
        ineligibleResponseDTO.setTransactionCountDetails(transactionCountDetails);
        ineligibleResponseDTO.setTransactionAmtDetails(transactionAmountDetails);
        ineligibleResponseDTO.setLoanDetails(loanDetails);
        ineligibleResponseDTO.setPanCard(merchantLoanRequest.getPancardNumber());
    }

//    private MerchantLoanRequest calculateTarget(MerchantResponseDTO merchantResponseDTO, Long merchantId, String panCard, ScoreCategoryMaster scoreCategoryMaster) {
//        int currentTxnCount = (merchantResponseDTO != null && merchantResponseDTO.getDailyTxnCount() != null) ? merchantResponseDTO.getDailyTxnCount() : 0;
//        double currentTxnValue = (merchantResponseDTO != null && merchantResponseDTO.getDailyTxnAmount() != null) ? merchantResponseDTO.getDailyTxnAmount() : 0;
//        logger.info("Calculating target for ineligible loan---");
//        double totalTxnRequired = 2 * scoreCategoryMaster.getTxnCount();
//        double totalAmountRequired = 2 * scoreCategoryMaster.getAvgDailyTpv();
//        logger.info("Current transaction count : {}, Current transaction amount: {}, Transaction amount required: {}, Transaction Count required: {}", currentTxnCount, currentTxnValue, totalAmountRequired, totalTxnRequired);
//        return merchantLoanRequestDoa.save(new MerchantLoanRequest(merchantId, 0,  currentTxnCount, currentTxnValue, (int)totalTxnRequired, totalAmountRequired, panCard));
//    }
    
    public IneligibleAPIResponseDto getIneligibleDetails(BasicDetailsDto merchant) {
    	try {
//            MerchantSummary merchantSummary=merchantSummaryDao.getByMerchantId(merchant.getId());
            MerchantResponseDTO merchantResponseDTO = merchantSummaryHandler.getMerchantSummary(merchant.getId());
            if (ObjectUtils.isEmpty(merchantResponseDTO)) {
                throw new MerchantSummaryExceptionHandler(merchant.getId().toString());

            }
            IneligibleAPIResponseDto response=new IneligibleAPIResponseDto();
    		Date onboardDate=getMerchantOnboardDate(merchant, merchantResponseDTO);
            Map<String, Integer> transactionDetail=getTransactionDetails(merchant, merchantResponseDTO);
    		response.setRegistrationDate(onboardDate);
    		if (transactionDetail != null) {
                response.setPaymentAmount(transactionDetail.getOrDefault("amount", 0));
                response.setPaymentCount(transactionDetail.getOrDefault("count", 0));
            }
    		if(response.getPaymentCount() == 0) {
    			response.setNewMerchant(true);
    		}
            response.setCountSuccess(merchantResponseDTO != null && merchantResponseDTO.getUniqueCustomer1mon() != null &&  merchantResponseDTO.getUniqueCustomer1mon() >= 15);
            Experian experian=experianDao.getByMerchantId(merchant.getId());

            if (Objects.nonNull(experian) && experian.getRejected() && Objects.nonNull(experian.getRejectedDate())) {
                Integer reapplyDayDiff = easyLoanUtil.getReapplyTime(experian.getReason(), RejectionStage.EXPERIAN, merchant.getId());
                if(Objects.nonNull(reapplyDayDiff)) {
                    Long reapplyTime = reapplyDayDiff - LoanUtil.getDateDiffInDays(experian.getRejectedDate(), new Date());
                    reapplyTime = reapplyTime > 0 ? reapplyTime : 0;
                    response.setReapplyTime(reapplyTime);
                    response.setReapplyTimeEpoch(LoanUtil.addDays(new Date(), reapplyTime).getTime());
                }
            }
            if(experian!=null && experian.getReason() != null && experian.getReason().equalsIgnoreCase(ExperianConstants.ENACH)) {
                response.setEnach(LendingConstants.ENACH_BANK_MESSAGE, LendingConstants.BANK_CHANGE_DEEPLINK);
            }
//            else {
//                if (Boolean.FALSE.equals(isMerchantFPAccountEnabled(merchant.getId()))) {
//                    Banner banner = response.new Banner();
//                    banner.setDeepLink(LendingConstants.FPACCOUNT_NEWUSER_DEEPLINK);
//                    banner.setImg(LendingConstants.FPACCOUNT_NEWUSER_IMG);
//                    response.addBanner(banner);
//                }
//
//                if (Boolean.FALSE.equals(isMerchantBharatSwipeEnabled(merchant.getId()))) {
//                    Banner banner = response.new Banner();
//                    banner.setDeepLink(LendingConstants.BHARATSWIPE_NEWUSER_DEEPLINK);
//                    banner.setImg(LendingConstants.BHARATSWIPE_NEWUSER_IMG);
//                    response.addBanner(banner);
//                }
//            }
            return response;
    	}
    	catch(Exception e) {
    		logger.error("Error occured while fetching ineligiblity details",e);
    		return new IneligibleAPIResponseDto(false, "Something went wrong");
    	}
    }

//    private Boolean isMerchantBharatSwipeEnabled(Long merchantId){
//        BharatSwipeTerminalSlave bharatSwipeTerminal = bharatSwipeTerminalDaoSlave.findFirstByMerchantIdAndDeviceSerialNotNull(merchantId);
//        return bharatSwipeTerminal != null;
//    }
    
//    private Boolean isMerchantFPAccountEnabled(Long merchantId){
//        FPAccountSlave fpAccount = fpAccountDaoSlave.findByMerchantIdAndKYCStatusNotNull(merchantId);
//        return fpAccount != null;
//    }
    
    private Map<String,Integer> getTransactionDetails(BasicDetailsDto merchant, MerchantResponseDTO merchantResponseDTO){
    	try {
    		if(merchantResponseDTO!=null && merchantResponseDTO.getDailyTxnAmount()!=null && merchantResponseDTO.getDailyTxnCount()!=null) {
    			Map<String,Integer> transactionDetails=new HashMap<>();
    			transactionDetails.put("count", merchantResponseDTO.getDailyTxnCount());
    			transactionDetails.put("amount", merchantResponseDTO.getDailyTxnAmount().intValue());
    			return transactionDetails;
    		}
//    		else {
//    			return getTransactionDetailsFromPaymentTable(merchant);
//    		}
    	}
    	catch(Exception e) {
    		logger.error("Error occured while fetching transaction details",e);
    	}
    	return null;
    }
    
//    private Map<String,Integer> getTransactionDetailsFromPaymentTable(BasicDetailsDto merchant){
//    	Map<String,Integer> transactionMap=new HashMap<>();
//    	Object[] transactionDetail = (Object[])paymentTransactionNewDaoSlave.getAmountAndCountByMerchant(merchant.getId());
//        BigDecimal transactionAmount = (BigDecimal) transactionDetail[0];
//    	BigInteger count = (BigInteger) transactionDetail[1];
//    	transactionMap.put("count", count == null ? 0 : count.intValue());
//    	transactionMap.put("amount", transactionAmount == null ? 0 : transactionAmount.intValue());
//    	return transactionMap;
//    }
    
    private Date getMerchantOnboardDate(BasicDetailsDto merchant, MerchantResponseDTO merchantResponseDTO) {
    	try {
    		if(merchantResponseDTO!=null && !ObjectUtils.isEmpty(merchantResponseDTO.getFirstTransactionDate())) {
                    return merchantResponseDTO.getFirstTransactionDate();
    		}
    		else {
    			return merchant.getCreatedAt();
    		}
    	}
    	catch(Exception e) {
    		logger.error("Error occured while fetching merchant onboard date",e);
    	}
    	return null;
    }
}
