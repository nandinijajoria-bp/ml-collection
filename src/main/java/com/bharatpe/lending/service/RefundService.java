package com.bharatpe.lending.service;

import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.Handler.EnachHandler;
import com.bharatpe.lending.common.Handler.LendingPayoutsHandler;
import com.bharatpe.lending.common.dto.BharatPeEnachResponseDTO;
import com.bharatpe.lending.common.dto.LendingPayoutResponseDTO;
import com.bharatpe.lending.common.dto.NotificationPayloadDto;
import com.bharatpe.lending.common.enums.CollectionTransferTypeEnum;
import com.bharatpe.lending.common.service.LendingNotificationService;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.dao.LendingLedgerDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.enums.LendingPayoutType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class RefundService {

    private final Logger logger = LoggerFactory.getLogger(RefundService.class);

    @Autowired
    APIGatewayService apiGatewayService;

    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;

    @Autowired
    LendingPayoutsHandler lendingPayoutsHandler;

    @Autowired
    LendingLedgerDao lendingLedgerDao;

    @Autowired
    PaymentService paymentService;

    @Autowired
    EnachHandler enachHandler;

//    @Autowired
//    SmsServiceHandler smsServiceHandler;

    @Autowired
    LiquiloansService liquiloansService;

    @Autowired
    LendingNotificationService lendingNotificationService;

    ExecutorService executorService = Executors.newFixedThreadPool(10);
    @Autowired
    MerchantService merchantService;

    public CommonResponse nachRefund(NachRefundRequest refundRequest) {
        try {
            LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.findByIdAndMerchantId(refundRequest.getLoanId(), refundRequest.getMerchantId());
            if (lendingPaymentSchedule == null) {
                logger.info("Loan not found for id:{}", refundRequest.getLoanId());
                return new CommonResponse(false, "Loan not found");
            }
            Optional<BasicDetailsDto> basicDetailsDto = merchantService.fetchMerchantBasicDetails(lendingPaymentSchedule.getMerchantId());
            if (ObjectUtils.isEmpty(basicDetailsDto)) {
                return new CommonResponse(false, "failed to retrieve Merchant details");
            }

            boolean success = false;
            Double refundAmount = 0D;
            if (refundRequest.getAmount() != null) {
                logger.info("Manual refund amount:{} for loanId:{}", refundRequest.getAmount(), refundRequest.getLoanId());
                String orderId = "REFUND" + System.currentTimeMillis();
                refundAmount = refundRequest.getAmount();
                LendingPayoutRequest lendingPayoutRequest = new LendingPayoutRequest(lendingPaymentSchedule.getId(), orderId, refundAmount, LendingPayoutType.LENDING_NACH_REFUND, lendingPaymentSchedule.getMerchantId(), "MANUAL_REFUND");
                LendingPayoutResponse lendingPayoutResponse = apiGatewayService.lendingPayout(lendingPayoutRequest);
                if (lendingPayoutResponse != null) {
                    success = true;
//                    String bankRefNo = lendingPayoutResponse.getData() != null ? lendingPayoutResponse.getData().getBankReferenceNo() : null;
//                    createLendingLedger(lendingPaymentSchedule, DateTimeUtil.getCurrentDayStartTime(), "LOAN_REFUND", -refundAmount, -refundAmount, 0D, 0D, 0D, bankRefNo, "REFUND");
//                    lendingPaymentSchedule.setPaidAmount(lendingPaymentSchedule.getPaidAmount()-refundAmount);
//                    lendingPaymentSchedule.setPaidPrinciple(lendingPaymentSchedule.getPaidPrinciple()-refundAmount);
//                    lendingPaymentSchedule.setDueAmount(lendingPaymentSchedule.getDueAmount()+refundAmount);
//                    lendingPaymentSchedule.setDuePrinciple(lendingPaymentSchedule.getDuePrinciple()+refundAmount);
//                    lendingPaymentScheduleDao.save(lendingPaymentSchedule);
                }
            } else if (lendingPaymentSchedule.getStatus().equals("INACTIVE") && lendingPaymentSchedule.getPaidAmount() > 0D) {
                logger.info("Refund paid amount:{} for inactive loan:{}",lendingPaymentSchedule.getPaidAmount(), lendingPaymentSchedule.getId());
                String orderId = "INACTIVE_REFUND" + System.currentTimeMillis();
                refundAmount = lendingPaymentSchedule.getPaidAmount();
                LendingPayoutRequest lendingPayoutRequest = new LendingPayoutRequest(lendingPaymentSchedule.getId(), orderId, refundAmount, LendingPayoutType.LENDING_NACH_REFUND, lendingPaymentSchedule.getMerchantId(), "NACH_REFUND");
                LendingPayoutResponse lendingPayoutResponse = apiGatewayService.lendingPayout(lendingPayoutRequest);
                if (lendingPayoutResponse != null) {
                    success = true;
                    String bankRefNo = lendingPayoutResponse.getData() != null ? lendingPayoutResponse.getData().getBankReferenceNo() : null;
                    createLendingLedger(lendingPaymentSchedule, DateTimeUtil.getCurrentDayStartTime(), "LOAN_REFUND", -refundAmount, -lendingPaymentSchedule.getPaidPrinciple(), -lendingPaymentSchedule.getPaidInterest(), 0D, 0D, bankRefNo, "REFUND");
                    lendingPaymentSchedule.setDueAmount(0D);
                    lendingPaymentSchedule.setDueInterest(0D);
                    lendingPaymentSchedule.setDuePrinciple(0D);
                    lendingPaymentSchedule.setPaidAmount(0D);
                    lendingPaymentSchedule.setPaidPrinciple(0D);
                    lendingPaymentSchedule.setPaidInterest(0D);
                    lendingPaymentScheduleDao.save(lendingPaymentSchedule);
                }
            } else if (lendingPaymentSchedule.getDueAmount() < 0D && lendingPaymentSchedule.getStatus().equals("CLOSED")) {
                logger.info("Refund due amount:{} for loan:{}",lendingPaymentSchedule.getDueAmount(), lendingPaymentSchedule.getId());
                String orderId = "NACH_REFUND" + System.currentTimeMillis();
                refundAmount = -1 * lendingPaymentSchedule.getDueAmount();
                Double principle = -1 * lendingPaymentSchedule.getDuePrinciple();
                Double interest = -1 * lendingPaymentSchedule.getDueInterest();
                LendingPayoutRequest lendingPayoutRequest = new LendingPayoutRequest(lendingPaymentSchedule.getId(), orderId, refundAmount, LendingPayoutType.LENDING_NACH_REFUND, lendingPaymentSchedule.getMerchantId(), "NACH_REFUND");
                LendingPayoutResponse lendingPayoutResponse = apiGatewayService.lendingPayout(lendingPayoutRequest);
                if (lendingPayoutResponse != null) {
                    success = true;
//                    String bankRefNo = lendingPayoutResponse.getData() != null ? lendingPayoutResponse.getData().getBankReferenceNo() : null;
//                    createLendingLedger(lendingPaymentSchedule, DateTimeUtil.getCurrentDayStartTime(), "LOAN_REFUND", -refundAmount, -principle, -interest, 0D, 0D, bankRefNo, "REFUND");
//                    lendingPaymentSchedule.setDueAmount(lendingPaymentSchedule.getDueAmount() + refundAmount);
//                    lendingPaymentSchedule.setDueInterest(lendingPaymentSchedule.getDueInterest() + interest);
//                    lendingPaymentSchedule.setDuePrinciple(lendingPaymentSchedule.getDuePrinciple() + principle);
//                    lendingPaymentSchedule.setPaidPrinciple(lendingPaymentSchedule.getPaidPrinciple()-principle);
//                    lendingPaymentSchedule.setPaidInterest(lendingPaymentSchedule.getPaidInterest()-interest);
//                    lendingPaymentSchedule.setPaidAmount(lendingPaymentSchedule.getPaidAmount()-refundAmount);
//                    lendingPaymentScheduleDao.save(lendingPaymentSchedule);
                }
            }
            if (success) {
                String identifier = "LENDING_REFUND_SMS";
                Map<String,Object> templateParams = new HashMap<>();
                templateParams.put("refund_amount",refundAmount);
                templateParams.put("loan_id",lendingPaymentSchedule.getId());
                NotificationPayloadDto notificationPayloadDto = new NotificationPayloadDto();
                notificationPayloadDto.setTemplateIdentifier(identifier);
                notificationPayloadDto.setMobile(basicDetailsDto.get().getMobile());
                notificationPayloadDto.setClientName("LENDING");
                notificationPayloadDto.setTemplateParams(templateParams);
                lendingNotificationService.notify(notificationPayloadDto);

                return new CommonResponse(true, "Loan refund success for loanId:" + lendingPaymentSchedule.getId());
            } else {
                return new CommonResponse(false, "Loan refund failed for loanId:" + lendingPaymentSchedule.getId());
            }
        } catch (Exception e) {
            logger.error("Exception in nach refund for loanId:{}", refundRequest.getLoanId(), e);
        }
        return new CommonResponse(false, "Something went wrong");
    }

    public CommonResponse processingFeeRefund(ProcessingFeeRequest processingFeeRequest, Boolean callFromLMS){
        try{
            LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.findByIdAndMerchantId(processingFeeRequest.getLoanId(), processingFeeRequest.getMerchantId());
            if (lendingPaymentSchedule == null) {
                logger.info("Loan not found for id:{}", processingFeeRequest.getLoanId());
                return new CommonResponse(false, "Loan not found");
            }

            String refundType = processingFeeRequest.getType();
            if(refundType.equalsIgnoreCase("PROCESSING_FEE")){
                LendingPayoutResponseDTO lendingPayouts =
                  lendingPayoutsHandler.findTopByMerchantIdAndOwnerIdAndStatusAndOrderIdLike(lendingPaymentSchedule.getMerchantId(),
                    lendingPaymentSchedule.getId(), "PF_CASHBACK");
                if(lendingPayouts != null){
                    logger.info("Already Processing Fee Refund For id :{}", processingFeeRequest.getLoanId());
                    return new CommonResponse(false, "Refund Already Done");
                }
                executorService.execute(() -> paymentService.refundProcessingFee(lendingPaymentSchedule));

            }else if(refundType.equalsIgnoreCase("CASHBACK")){
                LendingPayoutResponseDTO lendingPayouts = lendingPayoutsHandler.findByMerchantIdAndOwnerIdForNachCashBack(lendingPaymentSchedule.getMerchantId(),
                  lendingPaymentSchedule.getId());
                if(lendingPayouts != null){
                    logger.info("Already Nach Cashback For id :{}", processingFeeRequest.getLoanId());
                    return new CommonResponse(false, "Refund Already Done");
                }

                BharatPeEnachResponseDTO bharatPeEnach = enachHandler.isSuccess(lendingPaymentSchedule.getMerchantId(), lendingPaymentSchedule.getLoanApplication().getId());
//                if (bharatPeEnach != null) {
//                    executorService.execute(() -> liquiloansService.initiateEnachCashback(lendingPaymentSchedule));
//                }
            }
            return new CommonResponse(true, "Loan refund success for loanId:" + lendingPaymentSchedule.getId());

        }catch (Exception e){
            logger.error("Exception in Processing Fee refund for loanId:{}", processingFeeRequest.getLoanId(), e);
        }
        return  new CommonResponse(false,"Something Went Wrong");
    }

    public void createLendingLedger(LendingPaymentSchedule lendingPaymentSchedule, Date date, String txnType, Double amount, Double principle, Double interest, Double otherCharges, Double penalty, String description, String adjustmentMode) {
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
        lendingLedger.setTransferType(CollectionTransferTypeEnum.TRANSFER_BY_BP.name());
        lendingLedgerDao.save(lendingLedger);
    }
}
