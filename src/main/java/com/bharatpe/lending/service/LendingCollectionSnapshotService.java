package com.bharatpe.lending.service;

import com.bharatpe.lending.common.dao.LendingCollectionSnapshotDao;
import com.bharatpe.lending.common.entity.LendingCollectionSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class LendingCollectionSnapshotService {

    @Autowired
    private LendingCollectionSnapshotDao lendingCollectionSnapshotDao;

    public void updateTodayWithExcess(Long loanId, Double excessAmount) {
        try {
            ZoneId istZone = ZoneId.of("Asia/Kolkata");
            LocalDate todayLocal = LocalDate.now(istZone);
            Date todayDate = java.sql.Date.valueOf(todayLocal);

            int excessVal = (int) Math.round(excessAmount);
            int totalExcess = excessVal;
            updateTodaysPaidAmountBySettledAmount(loanId, excessVal, todayLocal);

            List<LendingCollectionSnapshot> unsettledRows = lendingCollectionSnapshotDao.findUnsettledInstallmentsTillDate(loanId, todayDate);
            for (LendingCollectionSnapshot unsettledRow : unsettledRows) {
                if (totalExcess <= 0) {
                    break;
                }
                int remainingForRow = Optional.ofNullable(unsettledRow.getRemainingForThisEdi()).orElse(0);
                int amountToSettle = Math.min(totalExcess, remainingForRow);
                updateSnapshotRowObject(loanId, unsettledRow.getInstallmentDate(), (double) amountToSettle, todayDate);
                totalExcess -= amountToSettle;
            }

            LendingCollectionSnapshot todaysRow = lendingCollectionSnapshotDao.findByLoanIdAndInstallmentDate(loanId, todayDate).orElse(null);
            if (todaysRow == null) return;

            totalExcess = Optional.ofNullable(todaysRow.getExcessAmt()).orElse(0) + totalExcess;
            todaysRow.setExcessAmt(totalExcess);
            todaysRow.setShowExcessMessage(Optional.ofNullable(todaysRow.getExcessAmt()).orElse(0) > 0);

            Integer scheduledEdi = lendingCollectionSnapshotDao.findScheduledEdiAmountByLoanId(loanId);
            if (scheduledEdi != null && scheduledEdi > 0) {

                // Calculate Full Days and the Partial Remainder
                int fullDaysCovered = totalExcess / scheduledEdi;
                int partialRemainder = totalExcess % scheduledEdi;
                int remainingExcess = totalExcess;

                LocalDate startMarkingFrom = todayLocal.plusDays(1);
                LocalDate resetTillDate = getFutureResetTillDate(todaysRow, startMarkingFrom, fullDaysCovered, partialRemainder);

                List<LendingCollectionSnapshot> rowsToUpdate = new ArrayList<>();
                if (!resetTillDate.isBefore(startMarkingFrom)) {
                    List<LendingCollectionSnapshot> futureRows = lendingCollectionSnapshotDao.findByLoanIdAndInstallmentDateBetweenOrderByInstallmentDateAsc(
                            loanId, java.sql.Date.valueOf(startMarkingFrom), java.sql.Date.valueOf(resetTillDate));
                    for (LendingCollectionSnapshot row : futureRows) {
                        resetFutureSnapshotRow(row);
                    }
                    rowsToUpdate.addAll(futureRows);
                }

                // Handle Full Days (ADVANCE)
                if (fullDaysCovered > 0) {
                    LocalDate endFullMarkingAt = startMarkingFrom.plusDays(fullDaysCovered - 1);
                    Date settledUntilDate = java.sql.Date.valueOf(endFullMarkingAt);
                    List<LendingCollectionSnapshot> fullRows = lendingCollectionSnapshotDao.findByLoanIdAndInstallmentDateBetweenOrderByInstallmentDateAsc(loanId, java.sql.Date.valueOf(startMarkingFrom), settledUntilDate);

                    for (LendingCollectionSnapshot row : fullRows) {
                        remainingExcess = Math.max(0, remainingExcess - scheduledEdi);
                        row.setRemainingForThisEdi(0);
                        row.setAppliedToThisEdi(scheduledEdi);
                        row.setDisplayDueAmount(scheduledEdi);
                        row.setExcessAmt(remainingExcess);
                        row.setStatus("ADVANCE");
                        row.setPaidOnDate(todayDate);
                        row.setShowExcessMessage(true);
                        row.setSettledUntilDt(settledUntilDate);
                        addOrReplace(rowsToUpdate, row);
                    }
                    todaysRow.setSettledUntilDt(settledUntilDate);
                } else {
                    todaysRow.setSettledUntilDt(null);
                }

                // Handle the partial payment case
                if (partialRemainder > 0) {
                    LocalDate partialDayDate = startMarkingFrom.plusDays(fullDaysCovered);

                    lendingCollectionSnapshotDao.findByLoanIdAndInstallmentDate(loanId, java.sql.Date.valueOf(partialDayDate))
                            .ifPresent(pRow -> {
                                int totalApplied = partialRemainder;
                                int scheduled = Optional.ofNullable(pRow.getScheduledEdiAmount()).orElse(0);

                                pRow.setAppliedToThisEdi(totalApplied);
                                pRow.setRemainingForThisEdi(Math.max(0, scheduled - totalApplied));
                                pRow.setPaidOnDate(todayDate);
                                pRow.setSettledUntilDt(null);
                                // [Future Date Logic]:
                                // Requirement: if excess payment settles it, the applied amount as display
                                pRow.setDisplayDueAmount(totalApplied);
                                pRow.setExcessAmt(0);
                                pRow.setShowExcessMessage(true);

                                pRow.setStatus(determineStatus(pRow));
                                addOrReplace(rowsToUpdate, pRow);
                            });
                }

                if (!rowsToUpdate.isEmpty()) {
                    lendingCollectionSnapshotDao.saveAll(rowsToUpdate);
                }
            }
            lendingCollectionSnapshotDao.save(todaysRow);
        } catch (Exception e) {
            log.error("[SNAPSHOT-EXCESS] FATAL ERROR for LoanId: {}. Message: {}", loanId, e.getMessage(), e);
        }
    }

    private LocalDate getFutureResetTillDate(LendingCollectionSnapshot todaysRow, LocalDate startMarkingFrom, int fullDaysCovered, int partialRemainder) {
        LocalDate resetTillDate = startMarkingFrom.minusDays(1);

        if (fullDaysCovered > 0) {
            resetTillDate = startMarkingFrom.plusDays(fullDaysCovered - 1);
        }
        if (partialRemainder > 0) {
            resetTillDate = startMarkingFrom.plusDays(fullDaysCovered);
        }
        if (todaysRow.getSettledUntilDt() != null) {
            LocalDate previousSettledUntil = new java.sql.Date(todaysRow.getSettledUntilDt().getTime()).toLocalDate();
            resetTillDate = maxDate(resetTillDate, previousSettledUntil.plusDays(1));
        }

        return resetTillDate;
    }

    private LocalDate maxDate(LocalDate first, LocalDate second) {
        return first.isAfter(second) ? first : second;
    }

    private void resetFutureSnapshotRow(LendingCollectionSnapshot row) {
        int scheduled = Optional.ofNullable(row.getScheduledEdiAmount()).orElse(0);
        row.setAppliedToThisEdi(0);
        row.setRemainingForThisEdi(scheduled);
        row.setDisplayDueAmount(scheduled);
        row.setPaidOnDate(null);
        row.setExcessAmt(0);
        row.setShowExcessMessage(false);
        row.setSettledUntilDt(null);
        row.setStatus("SCHEDULED");
    }

    private void addOrReplace(List<LendingCollectionSnapshot> rows, LendingCollectionSnapshot rowToAdd) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).getInstallmentDate().equals(rowToAdd.getInstallmentDate())) {
                rows.set(i, rowToAdd);
                return;
            }
        }
        rows.add(rowToAdd);
    }

    public LendingCollectionSnapshot updateSnapshotRowObject(Long loanId, Date installmentDate, Double settledAmount, Date paymentDate) {
        if (settledAmount <= 0) return null;

        try {
            LocalDate localDate = new java.sql.Date(installmentDate.getTime()).toLocalDate();
            Date normalizedInstallmentDate = java.sql.Date.valueOf(localDate);

            LendingCollectionSnapshot row = lendingCollectionSnapshotDao.findByLoanIdAndInstallmentDate(loanId, normalizedInstallmentDate).orElse(null);

            if (row != null) {

                ZoneId istZone = ZoneId.of("Asia/Kolkata");
                LocalDate today = LocalDate.now(istZone);

                int amountToApply = (int) Math.round(settledAmount);
                int scheduledEdi = Optional.ofNullable(row.getScheduledEdiAmount()).orElse(0);
                int existingRemaining = Optional.ofNullable(row.getRemainingForThisEdi()).orElse(0);

                // Check if the row is already fully settled (by excess payment received) and matches the incoming amount
                if (row.getRemainingForThisEdi() == 0 && row.getAppliedToThisEdi().equals(amountToApply)) {
                    return row;
                }

                // Update Core Financials
                int existingApplied = Optional.ofNullable(row.getAppliedToThisEdi()).orElse(0);
                int totalApplied = Math.min(scheduledEdi, existingApplied + amountToApply);
                row.setAppliedToThisEdi(totalApplied);
                int newRem = Math.max(0, existingRemaining - amountToApply);
                row.setRemainingForThisEdi(newRem);
                row.setPaidOnDate(paymentDate);

                // Logic for displayDueAmount
                if (localDate.isEqual(today)) {
                    if (newRem > 0) {
                        // Get the latest backlog of unpaid money from previous days
                        int totalPastDues = lendingCollectionSnapshotDao.sumRemainingByLoanIdAndDateBefore(loanId, normalizedInstallmentDate);

                        // Add today's current debt to that backlog
                        // This gives the merchant the "Total needed to be current"
                        row.setDisplayDueAmount(totalPastDues + newRem);
                    } else {
                        // If today's EDI is cleared, switch to receipt mode
                        row.setDisplayDueAmount(scheduledEdi);
                    }
                } else if (localDate.isBefore(today)) {
                    // Past Day Row: Show "Debt"
                    if (newRem == 0) {
                        row.setDisplayDueAmount(scheduledEdi);
                    } else {
                        row.setDisplayDueAmount(newRem);
                    }
                } else {
                    // Future Day Row: show how much has been paid in advance until the day becomes current.
                    row.setDisplayDueAmount(totalApplied);
                }

                row.setStatus(determineStatus(row));
                updateTodaysPaidAmountBySettledAmount(loanId, amountToApply, today);
            }
            return row;
        } catch (Exception e) {
            log.error("[SNAPSHOT-UPDATE-OBJECT] Error for loanId {}: {}", loanId, e.getMessage());
            return null;
        }
    }

    private void updateTodaysPaidAmountBySettledAmount(Long loanId, int amountToApply, LocalDate today) {
        Date todayDate = java.sql.Date.valueOf(today);
        LendingCollectionSnapshot todaysRow = lendingCollectionSnapshotDao.findByLoanIdAndInstallmentDate(loanId, todayDate).orElse(null);
        if (todaysRow == null) {
            return;
        }

        int updatedPaidAmt = Optional.ofNullable(todaysRow.getPaidAmt()).orElse(0) + amountToApply;
        todaysRow.setPaidAmt(updatedPaidAmt);
        if (Optional.ofNullable(todaysRow.getRemainingForThisEdi()).orElse(0) == 0) {
            todaysRow.setDisplayDueAmount(updatedPaidAmt);
        }
        lendingCollectionSnapshotDao.save(todaysRow);
    }

    private String determineStatus(LendingCollectionSnapshot snapshot) {
        int rem = Optional.ofNullable(snapshot.getRemainingForThisEdi()).orElse(0);
        int applied = Optional.ofNullable(snapshot.getAppliedToThisEdi()).orElse(0);

        ZoneId istZone = ZoneId.of("Asia/Kolkata");
        LocalDate today = LocalDate.now(istZone);
        LocalDate instDate = new java.sql.Date(snapshot.getInstallmentDate().getTime()).toLocalDate();

        // CASE 1: FULLY SETTLED (rem == 0)
        if (rem == 0) {
            if (instDate.isBefore(today)) {
                return "LATE";     // Fully paid after the day passed
            } else if (instDate.isAfter(today)) {
                return "ADVANCE";  // Fully paid before the day arrived
            } else {
                return "PAID";     // Fully paid today
            }
        }

        // CASE 2: PARTIAL OR UNPAID (rem > 0)
        if (instDate.isBefore(today)) {
            // The day has passed and debt remains
            return "NOT_PAID";
        }

        if (instDate.isEqual(today)) {
            // The active day. Even if partially paid, it's still DUE
            return "DUE";
        }

        // CASE 3: FUTURE DATES (instDate.isAfter(today))
        // If some money is applied but not 100%, we still call it ADVANCE.
        // If no money is applied, it's just SCHEDULED.
        return (applied > 0) ? "ADVANCE" : "SCHEDULED";
    }
}