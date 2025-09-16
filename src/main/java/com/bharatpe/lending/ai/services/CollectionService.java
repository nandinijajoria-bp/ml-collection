package com.bharatpe.lending.ai.services;

import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.ai.dto.LendingCollectionExcessDto;
import com.bharatpe.lending.ai.dto.LendingLedgerDto;
import com.bharatpe.lending.common.dao.LendingCollectionExcessDao;
import com.bharatpe.lending.common.dao.PenaltyFeeLedgerDao;
import com.bharatpe.lending.common.entity.LendingCollectionExcess;
import com.bharatpe.lending.common.entity.PenaltyFeeLedger;
import com.bharatpe.lending.dao.LendingLedgerDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CollectionService {

    @Autowired
   private LendingPaymentScheduleDao lendingPaymentScheduleDao;

    @Autowired
    private  LendingLedgerDao lendingLedgerDao;

    @Autowired
    LendingCollectionExcessDao lendingCollectionExcessDao;

    @Autowired
    PenaltyFeeLedgerDao penaltyFeeLedgerDao;

    public List<List<LendingLedgerDto>> getLendingLedgerByMerchant(Long merchantId, Long days) {
        List<LendingPaymentSchedule> lendingPaymentScheduleList =
                lendingPaymentScheduleDao.getLoansByMerchantIdAndStatus(merchantId, "ACTIVE");

        if (lendingPaymentScheduleList.isEmpty()) {
            log.info("No active loan found for merchantId: {}", merchantId);
            return new ArrayList<>();
        }
        if (days == null || days <= 0) {
            days = 7L;
        }

        Instant cutoffInstant = Instant.now().minus(days, ChronoUnit.DAYS);
        Date cutoffDate = Date.from(cutoffInstant);

        List<List<LendingLedgerDto>> allLendingLedgerList = new ArrayList<>();

        for (LendingPaymentSchedule lendingPaymentSchedule : lendingPaymentScheduleList) {
            List<LendingLedgerDto> dtoList = new ArrayList<>();


            List<LendingLedger> lendingLedgerList =
                    lendingLedgerDao.findByLendingPaymentScheduleOrderByDateAsc(lendingPaymentSchedule);

            if (!lendingLedgerList.isEmpty()) {
                dtoList.addAll(
                        lendingLedgerList.stream()
                                .filter(e -> e.getCreatedAt() != null && !e.getCreatedAt().before(cutoffDate))
                                .map(this::mapToDto)
                                .collect(Collectors.toList())
                );
            }


            List<PenaltyFeeLedger> penaltyList =
                    penaltyFeeLedgerDao.findAllNegativePenaltyEntries(lendingPaymentSchedule.getId());

            if (!penaltyList.isEmpty()) {
                dtoList.addAll(
                        penaltyList.stream()
                                .filter(p -> p.getCreatedAt() != null && !p.getCreatedAt().before(cutoffDate))
                                .map(this::mapPenaltyToLedgerDto)
                                .collect(Collectors.toList())
                );
            }
        }



        return allLendingLedgerList;
    }

    private LendingLedgerDto mapToDto(LendingLedger entity) {
        return LendingLedgerDto.builder()
                .merchantId(entity.getMerchantId())
                .merchantStoreId(entity.getMerchantStoreId())
                .lendingPaymentScheduleId(
                        entity.getLendingPaymentSchedule() != null ? entity.getLendingPaymentSchedule().getId() : null
                )
                .settlementId(entity.getSettlementId())
                .txnType(entity.getTxnType())
                .date(entity.getDate())
                .amount(entity.getAmount())
                .description(entity.getDescription())
                .principle(entity.getPrinciple())
                .interest(entity.getInterest())
                .otherCharges(entity.getOtherCharges())
                .penalty(entity.getPenalty())
                .adjustmentMode(entity.getAdjustmentMode())
                .transferType(entity.getTransferType())
                .terminalOrderId(entity.getTerminalOrderId())
                .build();
    }


    public List<List<LendingCollectionExcessDto>> getExcessDetailsByMerchant(Long merchantId, Long days) {
        List<LendingPaymentSchedule> lendingPaymentScheduleList =
                lendingPaymentScheduleDao.getLoansByMerchantIdAndStatus(merchantId, "ACTIVE");

        if (lendingPaymentScheduleList.isEmpty()) {
            log.info("No active loan found for merchantId: {}", merchantId);
            return new ArrayList<>();
        }

        if (days == null || days <= 0) {
            days = 7L;
        }
        Instant cutoffInstant = Instant.now().minus(days, ChronoUnit.DAYS);
        Date cutoffDate = Date.from(cutoffInstant);

        List<List<LendingCollectionExcessDto>> allExcessList = new ArrayList<>();

        for (LendingPaymentSchedule lendingPaymentSchedule : lendingPaymentScheduleList) {
            List<LendingCollectionExcess> excessList =
                    lendingCollectionExcessDao.findByMerchantIdAndLoanIdOrderByIdAsc(merchantId, lendingPaymentSchedule.getId());

            if (!excessList.isEmpty()) {
                List<LendingCollectionExcessDto> dtoList = excessList.stream()
                        .filter(e -> e.getCreatedAt() != null && !e.getCreatedAt().before(cutoffDate))
                        .map(this::mapToDto)
                        .collect(Collectors.toList());
                allExcessList.add(dtoList);
            }
        }

        return allExcessList;
    }

    private LendingCollectionExcessDto mapToDto(LendingCollectionExcess entity) {
        return LendingCollectionExcessDto.builder()
                .merchantId(entity.getMerchantId())
                .merchantStoreId(entity.getMerchantStoreId())
                .loanId(entity.getLoanId())
                .excessNachCreditAmount(entity.getExcessNachCreditAmount())
                .amount(entity.getAmount())
                .terminalOrderId(entity.getTerminalOrderId())
                .status(entity.getStatus())
                .transferType(entity.getTransferType())
                .creditDate(entity.getCreditDate())
                .deductedAmount(entity.getDeductedAmount())
                .deductionCount(entity.getDeductionCount())
                .postingRequired(entity.getPostingRequired())
                .mode(entity.getMode())
                .source(entity.getSource())
                .build();
    }

    private LendingLedgerDto mapPenaltyToLedgerDto(PenaltyFeeLedger penalty) {
        return LendingLedgerDto.builder()
                .merchantId(penalty.getMerchantId())
                .lendingPaymentScheduleId(penalty.getLoanId())
                .txnType("PENALTY")
                .date(penalty.getCreatedAt())
                .amount(penalty.getAmount())
                .description(penalty.getDescription())
                .penalty(penalty.getAmount())
                .adjustmentMode("AUTO")
                .build();
    }
}
