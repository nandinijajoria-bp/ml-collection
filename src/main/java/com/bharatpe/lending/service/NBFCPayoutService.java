package com.bharatpe.lending.service;

import com.bharatpe.lending.common.dao.NBFCPayoutDao;
import com.bharatpe.lending.common.entity.NBFCPayout;
import com.bharatpe.lending.common.enums.TransactionStatus;
import com.bharatpe.lending.common.service.NBFCService;
import com.bharatpe.lending.dto.payout.BeneficiaryInfoDTO;
import com.bharatpe.lending.dto.payout.PayoutResponseDTO;
import com.bharatpe.lending.handlers.PayoutHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

@Service
@Slf4j
public class NBFCPayoutService {

    private final NBFCPayoutDao nbfcPayoutDao;
    private final PayoutHandler payoutHandler;
    private final NBFCService nbfcService;

    private static final Map<String, String> bankCode = new HashMap<String, String>() {{
        put("ICIC0000544", "ICIC29");
        put("ICIC0000229", "ICIC29");
        put("INDB0000006", "INDB33");
        put("INDB0000018", "INDB33");
        put("KKBK0000958", "KKBK37");
    }};

    public NBFCPayoutService(NBFCPayoutDao nbfcPayoutDao, PayoutHandler payoutHandler, NBFCService nbfcService) {
        this.nbfcPayoutDao = nbfcPayoutDao;
        this.payoutHandler = payoutHandler;
        this.nbfcService = nbfcService;
    }

    public NBFCPayout processPayout(Long id) throws IOException {
        Optional<NBFCPayout> payoutOptional = nbfcPayoutDao.findById(id);
        if (!payoutOptional.isPresent()) {
            log.error("No NBFC payout found for id: {}", id);
            return null;
        }
        NBFCPayout payout = payoutOptional.get();
        if (TransactionStatus.PENDING.name().equals(payout.getStatus())) {
            PayoutResponseDTO payoutStatus = payoutHandler.checkStatus(payout.getOrderId(), payout.getType());
            payout = updatePayoutByStatusData(payout, payoutStatus);
        }
//        NBFC nbfc = nbfcService.getNBFCById(payout.getNbfcId());
//        if (TransactionStatus.FAILED.name().equals(payout.getStatus())) {
//            if (ObjectUtils.isEmpty(nbfc.getMaxAutoTransferAttempt()) || payout.getRetryAttempt() >= nbfc.getMaxAutoTransferAttempt()) {
//                log.info("Max config attempt reached not attempting more");
//                return;
//            }
//            payout.setRetryAttempt(payout.getRetryAttempt() + 1);
//            return;
//        }

        if (Objects.equals(TransactionStatus.INIT.name(), payout.getStatus())) {
            payout.setOrderId(payout.getId() + "A" + (payout.getRetryAttempt() + 1));
            payout.setStatus(TransactionStatus.PENDING.name());
            payout = nbfcPayoutDao.save(payout);
            payoutHandler.initiatePayout(payout.getOrderId(), payout.getAmount(), payout.getType()
                    , BeneficiaryInfoDTO.from(payout.getAccountNumber(), payout.getIfsc(), payout.getBeneficiaryName(), bankCode.get(payout.getIfsc())));
            return payout;
        }
        log.info("No action taken on the payout {} as status is {}", payout.getId(), payout.getStatus());
        return payout;
    }

    private NBFCPayout updatePayoutByStatusData(NBFCPayout payout, PayoutResponseDTO payoutStatus) {
        if ("SUCCESS".equals(payoutStatus.getStatus())) {
            payout.setStatus(TransactionStatus.SUCCESS.name());
            payout.setCompletedAt(payoutStatus.getSettlementDate());
        } else if ("FAILED_NO_RETRY".equals(payoutStatus.getStatus())) {
            payout.setStatus(TransactionStatus.FAILED.name());
            payout.setCompletedAt(payoutStatus.getSettlementDate());
        }
        payout.setBankReferenceNumber(payoutStatus.getBankReferenceNo());
        payout.setResponseMsg(payoutStatus.getRemarks());
        payout.setResponseCode(payout.getResponseCode());
        payout.setPayoutId(payout.getPayoutId());
        return nbfcPayoutDao.save(payout);
    }

    public void updatePayoutByStatusData(PayoutResponseDTO payoutStatus) {
        NBFCPayout payout = nbfcPayoutDao.findByOrderId(payoutStatus.getOrderId());
        if (payout == null) {
            log.error("No NBFC payout found for payout status callback: {}", payoutStatus);
            return;
        }
        updatePayoutByStatusData(payout, payoutStatus);
    }
}
