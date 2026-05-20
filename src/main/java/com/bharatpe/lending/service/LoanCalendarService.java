package com.bharatpe.lending.service;


import com.bharatpe.lending.common.dao.LendingCollectionSnapshotDao;
import com.bharatpe.lending.common.query.dao.LendingPullPaymentDaoSlave;
import com.bharatpe.lending.common.entity.LendingCollectionSnapshot;
import com.bharatpe.lending.common.query.dao.LendingPaymentScheduleDaoSlave;
import com.bharatpe.lending.common.query.dao.LoanPaymentOrderSlaveDao;
import com.bharatpe.lending.common.query.entity.LendingPaymentScheduleSlave;
import com.bharatpe.lending.common.query.entity.LoanPaymentOrderSlave;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.enums.LoanStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class LoanCalendarService {

    @Autowired
    private LendingPaymentScheduleDaoSlave lendingPaymentScheduleDaoSlave;

    @Autowired
    private LendingCollectionSnapshotDao lendingCollectionSnapshotDao;

    @Autowired
    private LoanPaymentOrderSlaveDao loanPaymentOrderSlaveDao;

    @Autowired
    private LendingPullPaymentDaoSlave lendingPullPaymentDaoSlave;

    private static final DateTimeFormatter ATTEMPT_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    public CalendarViewResponseDTO getCalendarViewData(Long merchantId, Integer month, Integer year) {

        // Fetch Active Loan
        LendingPaymentScheduleSlave loan = lendingPaymentScheduleDaoSlave.findByMerchantIdAndStatus(merchantId, Collections.singletonList(LoanStatus.ACTIVE.name()));

        if (loan == null) {
            log.error("Loan calendar: no active loan for merchantId {}", merchantId);
            return CalendarViewResponseDTO.builder()
                    .merchantId(merchantId)
                    .hasData(false)
                    .build();
        }

        ZoneId zoneId = ZoneId.of("Asia/Kolkata");
        LocalDate todayLocal = LocalDate.now(zoneId);
        LocalDate nextEdiDate = loan.getNextEdiDate().toInstant().atZone(zoneId).toLocalDate();

        if (nextEdiDate.isAfter(todayLocal)) {
            return CalendarViewResponseDTO.builder()
                    .merchantId(merchantId)
                    .hasData(false)
                    .build();
        }

        int targetMonth = (month != null) ? month : todayLocal.getMonthValue();
        int targetYear = (year != null) ? year : todayLocal.getYear();
        YearMonth targetYearMonth = YearMonth.of(targetYear, targetMonth);

        LocalDate loanStart = loan.getStartDate().toInstant().atZone(zoneId).toLocalDate();
        LocalDate loanEnd = loan.getTentativeClosingDate().toInstant().atZone(zoneId).toLocalDate();

        boolean isLoanStarted = !todayLocal.isBefore(loanStart);
        LocalDate monthStart = targetYearMonth.atDay(1);
        LocalDate monthEnd = targetYearMonth.atEndOfMonth();

        // effectiveStart handles: Max(1st of Month, Loan Start Date)
        LocalDate effectiveStart = monthStart.isBefore(loanStart) ? loanStart : monthStart;
        LocalDate effectiveEnd = monthEnd.isAfter(loanEnd) ? loanEnd : monthEnd;

        // Fetch Snapshots
        List<LendingCollectionSnapshot> snapshots = Collections.emptyList();
        if (!effectiveStart.isAfter(effectiveEnd)) {
            Date fromDate = Date.from(effectiveStart.atStartOfDay(zoneId).toInstant());
            Date toDate = Date.from(effectiveEnd.plusDays(1).atStartOfDay(zoneId).minusNanos(1).toInstant());
            snapshots = lendingCollectionSnapshotDao.findByLoanIdAndInstallmentDateBetweenOrderByInstallmentDateAsc(loan.getId(), fromDate, toDate);
        }

        // Determine Default View Date
        Date defaultViewDate;

        if (targetYear == todayLocal.getYear() && targetMonth == todayLocal.getMonthValue()) {
            // Current Month -> Focus on Today
            defaultViewDate = Date.from(todayLocal.atStartOfDay(zoneId).toInstant());
        }
        else if (targetYearMonth.isBefore(YearMonth.from(todayLocal))) {
            // Past Months -> Focus on Max(1st of Month, Loan Start)
            defaultViewDate = Date.from(effectiveStart.atStartOfDay(zoneId).toInstant());
        }
        else {
            // Future Months -> Check if 1st entry is PAID/ADVANCE
            if (!snapshots.isEmpty()) {
                LendingCollectionSnapshot firstSnapshot = snapshots.get(0);
                String status = firstSnapshot.getStatus();

                if ("PAID".equalsIgnoreCase(status) || "ADVANCE".equalsIgnoreCase(status)) {
                    // If paid ahead, focus on the start of that month's data
                    defaultViewDate = Date.from(effectiveStart.atStartOfDay(zoneId).toInstant());
                } else {
                    defaultViewDate = null;
                }
            } else {
                defaultViewDate = null;
            }
        }

        List<DayWiseInstallmentDTO> dayWise = snapshots.stream()
                .map(this::mapToDayWiseDto)
                .collect(Collectors.toList());

        return CalendarViewResponseDTO.builder()
                .merchantId(merchantId)
                .hasData(!dayWise.isEmpty())
                .todayDate(Date.from(todayLocal.atStartOfDay(zoneId).toInstant()))
                .isLoanStarted(isLoanStarted)
                .defaultViewDate(defaultViewDate)
                .loanEdiAmt(loan.getEdiAmount())
                .loanStartDate(loan.getStartDate())
                .tentativeLoanClosingDate(loan.getTentativeClosingDate())
                .totalLoanAmount(loan.getLoanAmount())
                .amountRepaid(loan.getPaidAmount())
                .remainingAmount(loan.getLoanAmount() - loan.getPaidAmount())
                .lender(loan.getNbfc())
                .dayWiseData(dayWise)
                .build();
    }

    private DayWiseInstallmentDTO mapToDayWiseDto(LendingCollectionSnapshot s) {
        return DayWiseInstallmentDTO.builder()
                .date(s.getInstallmentDate())
                .paidOnDate(s.getPaidOnDate())
                .overdueSinceDt(s.getOverdueSinceDt())
                .overdueEndDt((s.getOverdueEndDt()))
                .scheduledEdiAmount(s.getScheduledEdiAmount())
                .displayDueAmount(s.getDisplayDueAmount())
                .paidAmount(s.getPaidAmt())
                .appliedToThisEdi(s.getAppliedToThisEdi())
                .remainingForThisEdi(s.getRemainingForThisEdi())
                .excessAmt(s.getExcessAmt())
                .settledUntilDt(s.getSettledUntilDt())
                .status(s.getStatus())
                .previousAmtDue(s.getPreviousAmtDue())
                .dpd(s.getDpd())
                .isPartiallyPaid(checkPartiallyPaid(s))
                .showExcessMessage(Boolean.TRUE.equals(s.getShowExcessMessage()))
                .build();
    }

    public boolean checkPartiallyPaid(LendingCollectionSnapshot snapshot) {
        ZoneId istZone = ZoneId.of("Asia/Kolkata");
        LocalDate today = LocalDate.now(istZone);
        LocalDate instDate = new java.sql.Date(snapshot.getInstallmentDate().getTime()).toLocalDate();

        // Only true for current day if both applied and rem are > 0
        return instDate.isEqual(today) &&
                Optional.ofNullable(snapshot.getAppliedToThisEdi()).orElse(0) > 0 &&
                Optional.ofNullable(snapshot.getRemainingForThisEdi()).orElse(0) > 0;
    }

    // Fetches details of payments that happened on a specific date
    public LoanTransactionHistoryResponseDTO getTransactionsForDate(Long merchantId, Date date) {
        if (date == null) {
            log.error("Date is null");
            return new LoanTransactionHistoryResponseDTO();
        }

        LendingPaymentScheduleSlave loan = lendingPaymentScheduleDaoSlave.findByMerchantIdAndStatus(merchantId, Collections.singletonList(LoanStatus.ACTIVE.name()));

        if (loan == null) {
            log.error("Loan transactions: no active loan for merchantId {}", merchantId);
            return new LoanTransactionHistoryResponseDTO();
        }

        ZoneId z = ZoneId.systemDefault();
        LocalDate day = date.toInstant().atZone(z).toLocalDate();
        Date startInclusive = Date.from(day.atStartOfDay(z).toInstant());
        Date endExclusive = Date.from(day.plusDays(1).atStartOfDay(z).toInstant());

        List<LoanPaymentOrderSlave> orders = loanPaymentOrderSlaveDao.findByLoanMerchantCreatedBetween(loan.getId(), merchantId, startInclusive, endExclusive);
        log.info("Loan transactions fetched for loanId: {}, merchantId: {}, day: {}, startInclusive: {}, endExclusive: {}, ordersCount: {}",
                loan.getId(), merchantId, day, startInclusive, endExclusive, orders != null ? orders.size() : 0);

        List<LoanPaymentOrderSlave> orderList = orders != null ? orders : Collections.emptyList();
        List<LoanPaymentAttemptItemDTO> items = orderList.stream()
                .map(this::mapToPaymentAttempt)
                .collect(Collectors.toList());

        return LoanTransactionHistoryResponseDTO.builder()
                .date(startInclusive)
                .totalPaid(sumTransactionAmounts(orderList))
                .transactions(items)
                .build();
    }

    private static String sumTransactionAmounts(List<LoanPaymentOrderSlave> orders) {
        double sum = orders.stream()
                .filter(o -> "SUCCESS".equalsIgnoreCase(o.getStatus()))
                .mapToDouble(o -> o.getAmount() != null ? o.getAmount() : 0d)
                .sum();
        return Double.toString(sum);
    }

    private LoanPaymentAttemptItemDTO mapToPaymentAttempt(LoanPaymentOrderSlave o) {
        return LoanPaymentAttemptItemDTO.builder()
                .date(formatDateTime(o.getCreatedAt()))
                .paymentStatus(o.getStatus())
                .amount(o.getAmount() != null ? o.getAmount().toString() : "0")
                .transactionId(o.getBankRefNo() != null ? o.getBankRefNo() : o.getTerminalOrderId())
                .typeOfPayment("Instalment")
                .modeOfPayment(o.getSource())
                .build();
    }

    private String formatDateTime(Date d) {
        if (d == null) return null;
        return ATTEMPT_TS.format(ZonedDateTime.ofInstant(d.toInstant(), ZoneId.systemDefault()));
    }

    public FailedTransactionResponseDTO getFailureDetails(Long merchantId, Date date) {
        ZoneId zoneId = ZoneId.systemDefault();
        LocalDate day = date.toInstant().atZone(zoneId).toLocalDate();
        String failureDate = DateTimeUtil.getDateInFormat(date, "yyyy-MM-dd");
        String message="";
        LendingPaymentScheduleSlave loan = lendingPaymentScheduleDaoSlave.findByMerchantIdAndStatus(merchantId, Collections.singletonList(LoanStatus.ACTIVE.name()));
        if (loan == null) {
            return new FailedTransactionResponseDTO(failureDate, message);
        }

        List<String> errorDescriptions = lendingPullPaymentDaoSlave.findFailedErrorDescriptionsForDate(merchantId, failureDate);

        boolean hasInsufficientFundsReason = false;
        boolean hasTechnicalReason = false;

        if (errorDescriptions == null || errorDescriptions.isEmpty()) {
            hasTechnicalReason = true;
        } else {
            for (String description : errorDescriptions) {
                if (description == null || description.trim().isEmpty()) {
                    hasTechnicalReason = true;
                    continue;
                }
                String normalizedDescription = description.toLowerCase(Locale.ROOT);
                if (normalizedDescription.contains("insufficient") && normalizedDescription.contains("fund")) {
                    hasInsufficientFundsReason = true;
                } else {
                    hasTechnicalReason = true;
                }
            }
        }

        if (hasInsufficientFundsReason && hasTechnicalReason) {
            message = "Payment unsuccessful due to technical issue and insufficient funds in bank account.";
        } else if (hasInsufficientFundsReason) {
            message = "Payment unsuccessful due to insufficient funds in bank account.";
        } else {
            message = "Payment unsuccessful due to technical issue.";
        }

        Date installmentDate = java.sql.Date.valueOf(day);
        Optional<LendingCollectionSnapshot> snapshot = lendingCollectionSnapshotDao.findByLoanIdAndInstallmentDate(loan.getId(), installmentDate);
        if (snapshot.isPresent()) {
            LendingCollectionSnapshot row = snapshot.get();
            if ("LATE".equalsIgnoreCase(row.getStatus()) && row.getPaidOnDate() != null) {
                String paidOnDate = DateTimeUtil.getDateInFormat(row.getPaidOnDate(), "yyyy-MM-dd");
                message = message + " Instalment was settled on " + paidOnDate + ".";
            }
        }

        return new FailedTransactionResponseDTO(failureDate, message);
    }
}