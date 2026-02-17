package com.bharatpe.lending.ai.services;

import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.ai.dto.LendingCollectionExcessDto;
import com.bharatpe.lending.ai.dto.LendingLedgerDto;
import com.bharatpe.lending.common.dao.LendingCollectionExcessDao;
import com.bharatpe.lending.common.dao.LendingPullPaymentDao;
import com.bharatpe.lending.common.dao.PenaltyFeeLedgerDao;
import com.bharatpe.lending.common.entity.LendingCollectionExcess;
import com.bharatpe.lending.common.entity.LendingPullPayment;
import com.bharatpe.lending.common.entity.PenaltyFeeLedger;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.dao.LendingLedgerDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dto.LendingPullPaymentResponseDTO;
import org.springframework.util.StringUtils;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


import java.time.LocalDate;
import java.time.ZoneId;
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
    private LendingCollectionExcessDao lendingCollectionExcessDao;

    @Autowired
    private PenaltyFeeLedgerDao penaltyFeeLedgerDao;

    @Autowired
    private LendingPullPaymentDao lendingPullPaymentDao;

    @PersistenceContext
    private EntityManager entityManager;

    public List<List<LendingLedgerDto>> getLendingLedgerByMerchant(Long merchantId, String date) {
        List<LendingPaymentSchedule> lendingPaymentScheduleList =
                lendingPaymentScheduleDao.getLoansByMerchantIdAndStatus(merchantId, "ACTIVE");

        if (lendingPaymentScheduleList.isEmpty()) {
            log.info("No active loan found for merchantId: {}", merchantId);
            return new ArrayList<>();
        }

        Date cutoffDate;
        boolean filterBySpecificDay = false;
        LocalDate targetDay = null;

        if (date != null && !date.trim().isEmpty()) {
            Date parsedDate = DateTimeUtil.parseDate(date, "yyyy-MM-dd");
            if (parsedDate != null) {
                cutoffDate = DateTimeUtil.getStartTimeFromDateTime(parsedDate);
                targetDay = convertToLocalDate(parsedDate);
                filterBySpecificDay = true;
                log.info("Filtering ledger records for specific date: {}", date);
            } else {
                log.error("Invalid date format provided: {}. Using default 7 days behavior.", date);
                // Fall back to default behavior if date parsing fails
                cutoffDate = DateTimeUtil.addDays(DateTimeUtil.getCurrentDayStartTime(), -7);
            }
        } else {
            // When date is not provided, keep the original behavior (last 7 days)
            cutoffDate = DateTimeUtil.addDays(DateTimeUtil.getCurrentDayStartTime(), -7);
            log.info("No date provided, using default 7 days behavior");
        }

        List<List<LendingLedgerDto>> allLendingLedgerList = new ArrayList<>();

        for (LendingPaymentSchedule lendingPaymentSchedule : lendingPaymentScheduleList) {
            List<LendingLedgerDto> dtoList = new ArrayList<>();


            List<LendingLedger> lendingLedgerList =
                    lendingLedgerDao.findByLpsIdAndCreatedAtAfter(lendingPaymentSchedule.getId(), cutoffDate);

            if (!lendingLedgerList.isEmpty()) {
                if (filterBySpecificDay) {
                    // Filter records from the specific date and for this specific loan
                    LocalDate finalTargetDay1 = targetDay;
                    dtoList.addAll(
                            lendingLedgerList.stream()
                                    .filter(e -> e.getCreatedAt() != null &&
                                            convertToLocalDate(e.getCreatedAt()).equals(finalTargetDay1))
                                    .map(this::mapToDto)
                                    .collect(Collectors.toList())
                    );
                } else {
                    // Original behavior: records from cutoff date to today, for this specific loan
                    Date finalCutoffDate1 = cutoffDate;
                dtoList.addAll(
                        lendingLedgerList.stream()
                                    .filter(e -> e.getCreatedAt() != null && !e.getCreatedAt().before(finalCutoffDate1))
                                .map(this::mapToDto)
                                .collect(Collectors.toList())
                );
            }
            }

            List<PenaltyFeeLedger> penaltyList =
                    penaltyFeeLedgerDao.findAllNegativePenaltyEntries(lendingPaymentSchedule.getId());

            if (!penaltyList.isEmpty()) {
                if (filterBySpecificDay) {
                    // Filter penalty records from the specific date
                    LocalDate finalTargetDay = targetDay;
                    dtoList.addAll(
                            penaltyList.stream()
                                    .filter(p -> p.getCreatedAt() != null &&
                                            convertToLocalDate(p.getCreatedAt()).equals(finalTargetDay))
                                    .map(this::mapPenaltyToLedgerDto)
                                    .collect(Collectors.toList())
                    );
                } else {
                    // Original behavior: penalty records from cutoff date to today
                    Date finalCutoffDate = cutoffDate;
                dtoList.addAll(
                        penaltyList.stream()
                                    .filter(p -> p.getCreatedAt() != null && !p.getCreatedAt().before(finalCutoffDate))
                                .map(this::mapPenaltyToLedgerDto)
                                .collect(Collectors.toList())
                );
            }
            }
            allLendingLedgerList.add(dtoList);
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


    public List<List<LendingCollectionExcessDto>> getExcessDetailsByMerchant(Long merchantId, String date) {
        List<LendingPaymentSchedule> lendingPaymentScheduleList =
                lendingPaymentScheduleDao.getLoansByMerchantIdAndStatus(merchantId, "ACTIVE");

        if (lendingPaymentScheduleList.isEmpty()) {
            log.info("No active loan found for merchantId: {}", merchantId);
            return new ArrayList<>();
        }

        Date cutoffDate;
        boolean filterBySpecificDay = false;
        LocalDate targetDay = null;

        if (date != null && !date.trim().isEmpty()) {
            Date parsedDate = DateTimeUtil.parseDate(date, "yyyy-MM-dd");
            if (parsedDate != null) {
                cutoffDate = DateTimeUtil.getStartTimeFromDateTime(parsedDate);
                targetDay = convertToLocalDate(parsedDate);
                filterBySpecificDay = true;
                log.info("Filtering excess records for specific date: {}", date);
            } else {
                log.error("Invalid date format provided: {}. Using default 7 days behavior.", date);
                // Fall back to default behavior if date parsing fails
                cutoffDate = DateTimeUtil.addDays(DateTimeUtil.getCurrentDayStartTime(), -7);
            }
        } else {
            // When date is not provided, keep the original behavior (last 7 days)
            cutoffDate = DateTimeUtil.addDays(DateTimeUtil.getCurrentDayStartTime(), -7);
            log.info("No date provided, using default 7 days behavior");
        }

        List<List<LendingCollectionExcessDto>> allExcessList = new ArrayList<>();

        for (LendingPaymentSchedule lendingPaymentSchedule : lendingPaymentScheduleList) {
            List<LendingCollectionExcess> excessList =
                    lendingCollectionExcessDao.lendingExcessNachLedgerAfterDate(lendingPaymentSchedule.getId(), cutoffDate);

            if (!excessList.isEmpty()) {
                if (filterBySpecificDay) {
                    // Filter records from the specific date
                    LocalDate finalTargetDay = targetDay;
                List<LendingCollectionExcessDto> dtoList = excessList.stream()
                            .filter(e -> e.getCreatedAt() != null &&
                                    convertToLocalDate(e.getCreatedAt()).equals(finalTargetDay))
                            .map(this::mapToDto)
                            .collect(Collectors.toList());
                    allExcessList.add(dtoList);
                } else {
                    // Original behavior: records from cutoff date to today
                    Date finalCutoffDate = cutoffDate;
                    List<LendingCollectionExcessDto> dtoList = excessList.stream()
                            .filter(e -> e.getCreatedAt() != null && !e.getCreatedAt().before(finalCutoffDate))
                        .map(this::mapToDto)
                        .collect(Collectors.toList());
                allExcessList.add(dtoList);
            }
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

    private LocalDate convertToLocalDate(Date date) {
        return LocalDate.from(date.toInstant().atZone(ZoneId.systemDefault()));
    }

    public List<List<LendingPullPaymentResponseDTO>> getAutopayDescriptionByMerchant(Long merchantId, String date, String mode) {
        List<LendingPaymentSchedule> lendingPaymentScheduleList =
                lendingPaymentScheduleDao.getLoansByMerchantIdAndStatus(merchantId, "ACTIVE");

        if (lendingPaymentScheduleList.isEmpty()) {
            log.info("No active loan found for merchantId: {}", merchantId);
            return new ArrayList<>();
        }

        boolean filterBySpecificDate = false;
        Date startOfDay = null;
        Date endOfDay = null;

        if (date != null && !date.trim().isEmpty()) {
            Date parsedDate = DateTimeUtil.parseDate(date, "yyyy-MM-dd");
            if (parsedDate != null) {
                filterBySpecificDate = true;
                startOfDay = DateTimeUtil.getStartTimeFromDateTime(parsedDate);
                endOfDay = DateTimeUtil.getEndTimeFromDateTime(parsedDate);
                log.info("Filtering pull payment records for specific date: {}", date);
            } else {
                log.error("Invalid date format provided: {}. Using default last 7 entries.", date);
            }
        } else {
            log.info("No date provided, using default last 7 entries");
        }

        List<List<LendingPullPaymentResponseDTO>> allPullPaymentList = new ArrayList<>();

        // Fetch pull payment records for each active loan
        for (LendingPaymentSchedule lendingPaymentSchedule : lendingPaymentScheduleList) {
            List<LendingPullPaymentResponseDTO> dtoList = new ArrayList<>();

            // Build query to fetch pull payment records for this specific loan
            StringBuilder queryBuilder = new StringBuilder(
                    "SELECT lpp.* FROM lending_pull_payment lpp " +
                    "WHERE lpp.merchant_id = :merchantId " + 
                    "AND lpp.loan_id = :loanId");
            
            if (filterBySpecificDate) {
                // Filter for specific date (entire day)
                queryBuilder.append(" AND lpp.created_at >= :startOfDay " +
                        "AND lpp.created_at <= :endOfDay");
            }
            
            if (!StringUtils.isEmpty(mode)) {
                queryBuilder.append(" AND lpp.mode = :mode");
            }
            
            queryBuilder.append(" ORDER BY lpp.created_at DESC");
            
            // If no date filter, limit to last 7 entries
            if (!filterBySpecificDate) {
                queryBuilder.append(" LIMIT 7");
            }

            Query query = entityManager.createNativeQuery(queryBuilder.toString(), LendingPullPayment.class);
            query.setParameter("merchantId", merchantId);
            query.setParameter("loanId", lendingPaymentSchedule.getId());
            
            if (filterBySpecificDate) {
                query.setParameter("startOfDay", startOfDay);
                query.setParameter("endOfDay", endOfDay);
            }
            
            if (!StringUtils.isEmpty(mode)) {
                query.setParameter("mode", mode);
            }

            @SuppressWarnings("unchecked")
            List<LendingPullPayment> pullPaymentList = query.getResultList();

            if (!pullPaymentList.isEmpty()) {
                dtoList = pullPaymentList.stream()
                        .map(LendingPullPaymentResponseDTO::from)
                        .collect(Collectors.toList());
            }

            allPullPaymentList.add(dtoList);
        }

        log.info("Fetched pull payment records for merchantId: {}, grouped into {} loans", merchantId, allPullPaymentList.size());
        return allPullPaymentList;
    }
}
