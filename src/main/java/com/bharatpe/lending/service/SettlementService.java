package com.bharatpe.lending.service;

import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.dao.LendingPaymentScheduleSnapDao;
import com.bharatpe.lending.common.dao.SettlementDetailsDao;
import com.bharatpe.lending.common.entity.LendingPaymentScheduleSnapshot;
import com.bharatpe.lending.common.entity.SettlementDetails;
import com.bharatpe.lending.common.util.LenderConfigUtil;
import com.bharatpe.lending.dao.LendingLedgerDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dto.ResponseDTO;
import com.bharatpe.lending.enums.LoanStatus;
import com.bharatpe.lending.enums.WaiverType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.Objects;

import static com.bharatpe.lending.constant.LendingConfigKeys.*;
import static com.bharatpe.lending.enums.LoanStatus.SETTLED;
import static com.bharatpe.lending.enums.SettlementDetailsStatus.CLOSED;
import static com.bharatpe.lending.enums.SettlementDetailsStatus.INIT;
import static com.bharatpe.lending.enums.WaiverType.*;
import static com.bharatpe.lending.enums.WaiverType.DECEASED_SCHEME;

@Service
@Slf4j
public class SettlementService {

    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;

    @Autowired
    private LenderConfigUtil lenderConfigUtil;

    @Autowired
    private LendingPaymentScheduleSnapDao lendingPaymentScheduleSnapDao;

    @Autowired
    LendingLedgerDao lendingLedgerDao;

    @Autowired
    private SettlementDetailsDao settlemetDetailsDao;

    @Transactional
    public ResponseDTO applySettlementWaiver(Long loanId, Long merchantId, WaiverType waiverType, Long userId) {
        LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.findByIdAndMerchantId(loanId, merchantId);
        if(Objects.isNull(lendingPaymentSchedule) || !"ACTIVE".equalsIgnoreCase(lendingPaymentSchedule.getStatus())) {
            log.error("No active loan found for id {}", loanId);
            return new ResponseDTO(false, "No active loan found");
        }

        switch (waiverType) {
            case SCHEME1:
                return waiverSchemeForSettlementCases(lendingPaymentSchedule);
            case EXCEPTION:
                return waiverExceptionForSettlementCases(lendingPaymentSchedule);
            case DECEASED_SCHEME:
                return waiverDeceasedForSettlementCases(lendingPaymentSchedule);
            default:
                return new ResponseDTO(false, "Invalid Waiver type");
        }
    }

    public ResponseDTO waiverSchemeForSettlementCases(LendingPaymentSchedule lendingPaymentSchedule) {
        Date requestDate = new Date();
        Long loanId = lendingPaymentSchedule.getId();
        log.info("Request received to settle loan as SCHEME1 for loan id: {} on {}", loanId, requestDate);
        try {
            adjustEdiWaiverEntry(lendingPaymentSchedule, SCHEME1, requestDate);
            adjustWaiverEntry(lendingPaymentSchedule, SCHEME1, requestDate);

            createSnapshotForLoan(lendingPaymentSchedule);

            lendingPaymentSchedule.setStatus(SETTLED.name());
            lendingPaymentSchedule.setSettlementStatus(SCHEME1.name());
            lendingPaymentSchedule.setDueAmount(0.0);
            lendingPaymentSchedule.setDuePrinciple(0.0);
            lendingPaymentSchedule.setDueInterest(0.0);
            lendingPaymentSchedule.setEdiRemainingCount(0);
            lendingPaymentSchedule.setSettlementDate(requestDate);

            lendingPaymentScheduleDao.save(lendingPaymentSchedule);

            return new ResponseDTO(true, "Waiver applied successfully");
        }  catch (Exception ex) {
            log.error("Unable to settle loan:{}", loanId);
            log.error("Error occurred! Something went wrong! {}, Stack: {}", ex.getMessage(), Arrays.asList(ex.getStackTrace()));
        }
        return new ResponseDTO(false, "Something went wrong");
    }

//    Loan Closing in Cooling Off Period
    private ResponseDTO waiverExceptionForSettlementCases(LendingPaymentSchedule lendingPaymentSchedule) {
        Date requestDate = new Date();
        log.info("Request received to close loan in cooling off period for loan id: {} on {}", lendingPaymentSchedule.getId(), requestDate);
        try {
            int coolOffPeriod = Integer.parseInt(lenderConfigUtil.getLenderConfig(lendingPaymentSchedule.getNbfc(), LENDER_COOL_OFF_PERIOD));
            Date loanDisbursalDate = lendingPaymentSchedule.getStartDate();
            LocalDate localDate = loanDisbursalDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();

            Date coolOffExpiryDate = Date.from(localDate.plusDays(coolOffPeriod).atStartOfDay(ZoneId.systemDefault()).toInstant());
            if (requestDate.after(coolOffExpiryDate)) {
                log.error("Invalid request, cooling off period is over. Cooling off period: {} coolOffExpiryDate: {}", coolOffPeriod, coolOffExpiryDate);
                return new ResponseDTO(false, "Invalid request! Cooling off period expired!!");
            }

//            Expecting only for principle to be received till date.
            if (lendingPaymentSchedule.getPaidAmount() < lendingPaymentSchedule.getLoanAmount()) {
                log.error("Unable to close loan due to unpaid principle for loan id: {}", lendingPaymentSchedule.getId());
                return new ResponseDTO(false, "Unable to close loan due to unpaid principle!");
            }
            adjustEdiWaiverEntry(lendingPaymentSchedule, CLOSED_IN_COOLING_OFF, requestDate);
            adjustWaiverEntry(lendingPaymentSchedule, CLOSED_IN_COOLING_OFF, requestDate);

            lendingPaymentSchedule.setStatus(LoanStatus.CLOSED.name());
            lendingPaymentSchedule.setSettlementStatus(EXCEPTION.name());
            lendingPaymentSchedule.setDueAmount(0.0);
            lendingPaymentSchedule.setDuePrinciple(0.0);
            lendingPaymentSchedule.setDueInterest(0.0);
            lendingPaymentSchedule.setEdiRemainingCount(0);
            lendingPaymentSchedule.setClosingDate(requestDate);

            lendingPaymentScheduleDao.save(lendingPaymentSchedule);
            return new ResponseDTO(true, "Waiver applied successfully");
        } catch (NumberFormatException ex) {
            log.error("Unable to settle loan:{}", lendingPaymentSchedule.getId());
            log.error("Error occurred! Invalid config value for cooling off period!{}, {}", ex.getMessage(), Arrays.asList(ex.getStackTrace()));
        } catch (Exception ex) {
            log.error("Unable to settle loan:{}", lendingPaymentSchedule.getId());
            log.error("Error occurred! Something went wrong! {}, Stack: {}", ex.getMessage(), Arrays.asList(ex.getStackTrace()));
        }

        return new ResponseDTO(false, "Something went wrong");
    }
    private ResponseDTO waiverDeceasedForSettlementCases(LendingPaymentSchedule lendingPaymentSchedule) {
        Date requestDate = new Date();
        log.info("Request received to settle loan for deceased for loan id: {} on {}", lendingPaymentSchedule.getId(), requestDate);
        try {
            lendingPaymentSchedule.setStatus(LoanStatus.DECEASED.name());
            lendingPaymentSchedule.setSettlementStatus(DECEASED_SCHEME.name());

            lendingPaymentScheduleDao.save(lendingPaymentSchedule);
            return new ResponseDTO(true, "Deceased scheme applied successfully");
        }  catch (Exception ex) {
            log.error("Unable to settle loan:{}", lendingPaymentSchedule.getId());
            log.error("Error occurred! Something went wrong! {}, Stack: {}", ex.getMessage(), Arrays.asList(ex.getStackTrace()));
        }
        return new ResponseDTO(false, "Something went wrong");
    }


    private void adjustEdiWaiverEntry(LendingPaymentSchedule lendingPaymentSchedule, WaiverType waiverType, Date requestDate) {

        LendingLedgerDao.LendingLedgerSummaryDto dueRaisedTillDate = lendingLedgerDao.fetchLedgerEdiSummary(lendingPaymentSchedule.getId());
        if (Objects.isNull(dueRaisedTillDate)) {
            log.info("No entry created in ledger for loanId: {}", lendingPaymentSchedule.getId());
        }
        double dueAmount = -1.0 * dueRaisedTillDate.getTotalDueAmount() - lendingPaymentSchedule.getTotalPayableAmount();
        double duePrinciple = -1.0 * dueRaisedTillDate.getTotalDuePrinciple() - lendingPaymentSchedule.getLoanAmount();
        double dueInterest = -1.0 * dueRaisedTillDate.getTotalDueInterest() - lendingPaymentSchedule.getInterest();

        log.info("EDI_WAIVER for loanId: {} of dueAmount: {}, duePrinciple: {}, dueInterest: {}", lendingPaymentSchedule.getId(),
                dueAmount, duePrinciple, dueInterest);
        if (dueAmount < 0) {
            LendingLedger lendingLedger = new LendingLedger();
            lendingLedger.setMerchantId(lendingPaymentSchedule.getMerchantId());
            lendingLedger.setLendingPaymentSchedule(lendingPaymentSchedule);
            lendingLedger.setAmount(dueAmount);
            lendingLedger.setPrinciple(duePrinciple);
            lendingLedger.setInterest(dueInterest);
            lendingLedger.setPenalty(0.0);
            lendingLedger.setOtherCharges(0.0);
            lendingLedger.setDescription(EDI_WAIVER);
            lendingLedger.setTxnType("EDI");
            lendingLedger.setDate(requestDate);
            lendingLedger.setAdjustmentMode(waiverType.name());

            lendingLedgerDao.save(lendingLedger);
        } else {
            log.info("No entry created in ledger for loanId: {}", lendingPaymentSchedule.getId());
        }
    }

    private void adjustWaiverEntry(LendingPaymentSchedule lendingPaymentSchedule, WaiverType waiverType, Date requestDate) {

        LendingLedgerDao.LendingLedgerSummaryDto paidTillDate = lendingLedgerDao.fetchLedgerSummaryPaid(lendingPaymentSchedule.getId());

        if (Objects.isNull(paidTillDate)) {
            log.info("No entry created in ledger for loanId: {}", lendingPaymentSchedule.getId());
        }

        double duePenalty = Objects.nonNull(lendingPaymentSchedule.getDuePenalty()) ? lendingPaymentSchedule.getDuePenalty() : 0.0;
        double dueOtherCharges = Objects.nonNull(lendingPaymentSchedule.getDueOtherCharges()) ? lendingPaymentSchedule.getDueOtherCharges() : 0.0;

        double duePrinciple = lendingPaymentSchedule.getLoanAmount() - paidTillDate.getTotalPaidPrinciple();
        double dueInterest = lendingPaymentSchedule.getInterest() - paidTillDate.getTotalPaidInterest();
        double dueAmount = duePrinciple + dueInterest + duePenalty + dueOtherCharges;

        log.info("SETTLEMENT_WAIVER for loanId: {} of dueAmount: {}, duePrinciple: {}, dueInterest: {}, duePenalty: {}, dueOtherCharges: {}",
                lendingPaymentSchedule.getId(), dueAmount, duePrinciple, dueInterest, duePenalty, dueOtherCharges);
        if (dueAmount > 0) {
            LendingLedger lendingLedger = new LendingLedger();
            lendingLedger.setMerchantId(lendingPaymentSchedule.getMerchantId());
            lendingLedger.setLendingPaymentSchedule(lendingPaymentSchedule);
            lendingLedger.setAmount(dueAmount);
            lendingLedger.setPrinciple(duePrinciple);
            lendingLedger.setInterest(dueInterest);
            lendingLedger.setPenalty(duePenalty);
            lendingLedger.setOtherCharges(dueOtherCharges);
            lendingLedger.setDescription(SETTLEMENT_WAIVER);
            lendingLedger.setTxnType("EDI");
            lendingLedger.setDate(requestDate);
            lendingLedger.setAdjustmentMode(waiverType.name());

            lendingLedgerDao.save(lendingLedger);
        } else {
            log.info("No entry created in ledger for loanId: {}", lendingPaymentSchedule.getId());
        }
    }

    private void createSnapshotForLoan(LendingPaymentSchedule lendingPaymentSchedule) {

        LendingPaymentScheduleSnapshot lpsSnap = LendingPaymentScheduleSnapshot.builder()
                .loanId(lendingPaymentSchedule.getId())
                .merchantId(lendingPaymentSchedule.getMerchantId())
                .merchantStoreId(lendingPaymentSchedule.getMerchantStoreId())
                .loanType(lendingPaymentSchedule.getLoanType())
                .loanAmount(lendingPaymentSchedule.getLoanAmount())
                .ediAmount(lendingPaymentSchedule.getEdiAmount())
                .startDate(lendingPaymentSchedule.getStartDate())
                .ediCount(lendingPaymentSchedule.getEdiCount())
                .interestOnlyEdiAmount(lendingPaymentSchedule.getInterestOnlyEdiAmount())
                .interestOnlyStartDate(lendingPaymentSchedule.getInterestOnlyStartDate())
                .interestOnlyEdiCount(lendingPaymentSchedule.getInterestOnlyEdiCount())
                .remainingInterestOnlyEdiCount(lendingPaymentSchedule.getRemainingInterestOnlyEdiCount())
                .overdueIntrestRate(lendingPaymentSchedule.getOverdueIntrestRate())
                .overdueEdiCount(lendingPaymentSchedule.getOverdueEdiCount())
                .overdueAmount(lendingPaymentSchedule.getOverdueAmount())
                .incentiveAmount(lendingPaymentSchedule.getIncentiveAmount())
                .ediRemainingCount(lendingPaymentSchedule.getEdiRemainingCount())
                .dueAmount(lendingPaymentSchedule.getDueAmount())
                .paidAmount(lendingPaymentSchedule.getPaidAmount())
                .totalCashbackAmount(lendingPaymentSchedule.getTotalCashbackAmount())
                .totalPenaltyAmount(lendingPaymentSchedule.getTotalPenaltyAmount())
                .nextEdiDate(lendingPaymentSchedule.getNextEdiDate())
                .status(lendingPaymentSchedule.getStatus())
                .offDay(lendingPaymentSchedule.getOffDay())
                .applicationId(lendingPaymentSchedule.getApplicationId())
                .totalPayableAmount(lendingPaymentSchedule.getTotalPayableAmount())
                .mobile(lendingPaymentSchedule.getMobile())
                .nbfc(lendingPaymentSchedule.getNbfc())
                .closingDate(lendingPaymentSchedule.getClosingDate())
                .tentativeClosingDate(lendingPaymentSchedule.getTentativeClosingDate())
                .loanConstruct(lendingPaymentSchedule.getLoanConstruct())
                .interest(lendingPaymentSchedule.getInterest())
                .otherCharges(lendingPaymentSchedule.getOtherCharges())
                .duePrinciple(lendingPaymentSchedule.getDuePrinciple())
                .dueInterest(lendingPaymentSchedule.getDueInterest())
                .dueOtherCharges(lendingPaymentSchedule.getDueOtherCharges())
                .duePenalty(lendingPaymentSchedule.getDuePenalty())
                .paidPrinciple(lendingPaymentSchedule.getPaidPrinciple())
                .paidInterest(lendingPaymentSchedule.getPaidInterest())
                .paidOtherCharges(lendingPaymentSchedule.getPaidOtherCharges())
                .paidPenalty(lendingPaymentSchedule.getPaidPenalty())
                .disbursalSettlementId(lendingPaymentSchedule.getDisbursalSettlementId())
                .creditLoan(lendingPaymentSchedule.getCreditLoan())
                .tlDetailsId(lendingPaymentSchedule.getTlDetailsId())
                .lenderDisbursalNotify(lendingPaymentSchedule.getLenderDisbursalNotify())
                .adjustedDueAmount(lendingPaymentSchedule.getAdjustedDueAmount())
                .adjustedPaidAmount(lendingPaymentSchedule.getAdjustedPaidAmount())
                .settlementStatus(lendingPaymentSchedule.getSettlementStatus())
                .settlementMechanism(lendingPaymentSchedule.getSettlementMechanism())
                .settleAllPrinciple(lendingPaymentSchedule.getSettleAllPrinciple())
                .writeoffFor(lendingPaymentSchedule.getWriteoffFor())
                .lastOverDueAmount(lendingPaymentSchedule.getLastOverDueAmount())
                .isSettlementInitiated(lendingPaymentSchedule.getSettlementInitiated())
                .settlementDate(lendingPaymentSchedule.getSettlementDate())
                .build();

        lendingPaymentScheduleSnapDao.save(lpsSnap);
    }

    @Transactional
    public ResponseDTO applySettlementReversal(Long loanId, Long merchantId, WaiverType waiverType, Long userId) {
        try {
            LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.findByIdAndMerchantId(loanId, merchantId);
            if(Objects.isNull(lendingPaymentSchedule)) {
                log.error("No loan found for id {}", loanId);
                return new ResponseDTO(false, "No active loan found");
            }
//            Reversal is currently available only for Scheme1
            if (!SCHEME1.equals(waiverType)) {
                return new ResponseDTO(false, "Invalid Waiver reversal type");
            }
            LoanStatus status = SETTLED;

            if (!status.name().equals(lendingPaymentSchedule.getStatus())) {
                log.error("No loan found for id {} and status: {}", loanId, status);
                return new ResponseDTO(false, "No loan found for status!!");
            }

            LendingPaymentScheduleSnapshot lpsSnap = lendingPaymentScheduleSnapDao.findTop1ByLoanIdOrderByIdDesc(lendingPaymentSchedule.getId());
            if (Objects.isNull(lpsSnap)) {
                log.error("Lps snap not found for id {} and status: {}", loanId, status);
                return new ResponseDTO(false, "Some error occurred while re-activating loan!!");
            }

            ledgerReversalForWaiverEntry(lendingPaymentSchedule, waiverType);
            reOpenLoanFromLpsSnap(lpsSnap, lendingPaymentSchedule, waiverType);
            log.info("Loan marked as active successfully!! LPS: {}", lendingPaymentSchedule);

        } catch (Exception ex) {
            log.error("Unable to settle loan:{}", loanId);
            log.error("Error occurred! Something went wrong! {}, Stack: {}", ex.getMessage(), Arrays.asList(ex.getStackTrace()));
        }

        return new ResponseDTO(true, "Settlement reversed successfully!!");
    }

    private void ledgerReversalForWaiverEntry(LendingPaymentSchedule lendingPaymentSchedule, WaiverType waiverType) {
        LendingLedgerDao.LendingLedgerSummaryDto waiverEntry = lendingLedgerDao.fetchLedgerSummaryWaiverEntry(lendingPaymentSchedule.getId(), SETTLEMENT_WAIVER);
        if (Objects.isNull(waiverEntry)) {
            log.info("No entry created in ledger for loanId: {}", lendingPaymentSchedule.getId());
        }
        double amount = -1.0 * waiverEntry.getTotalPaidAmount();
        double principle = -1.0 * waiverEntry.getTotalPaidPrinciple();
        double interest = -1.0 * waiverEntry.getTotalPaidInterest();
        double penalty = -1.0 * waiverEntry.getTotalPaidPenalty();
        double otherCharges = -1.0 * waiverEntry.getTotalPaidOtherCharges();

        log.info("SETTLEMENT_WAIVER_REVERSAL for loanId: {} of dueAmount: {}, duePrinciple: {}, dueInterest: {}, duePenalty: {}, dueOtherCharges: {}",
                lendingPaymentSchedule.getId(), amount, principle, interest, penalty, otherCharges);
        if (amount < 0) {
            LendingLedger lendingLedger = new LendingLedger();
            lendingLedger.setMerchantId(lendingPaymentSchedule.getMerchantId());
            lendingLedger.setLendingPaymentSchedule(lendingPaymentSchedule);
            lendingLedger.setAmount(amount);
            lendingLedger.setPrinciple(principle);
            lendingLedger.setInterest(interest);
            lendingLedger.setPenalty(penalty);
            lendingLedger.setOtherCharges(otherCharges);
            lendingLedger.setDescription(SETTLEMENT_WAIVER_REVERSAL);
            lendingLedger.setTxnType("EDI");
            lendingLedger.setDate(new Date());
            lendingLedger.setAdjustmentMode(waiverType.name());

//            Set Due amounts to LPS
            lendingPaymentSchedule.setDueAmount(-1.0 * amount);
            lendingPaymentSchedule.setDuePrinciple(-1.0 * principle);
            lendingPaymentSchedule.setDueInterest(-1.0 * interest);
            lendingPaymentSchedule.setDuePenalty(-1.0 * penalty);
            lendingPaymentSchedule.setDueOtherCharges(-1.0 * otherCharges);

            lendingLedgerDao.save(lendingLedger);
        } else {
            log.info("No entry created in ledger for loanId: {}", lendingPaymentSchedule.getId());
        }
    }

    private void reOpenLoanFromLpsSnap(LendingPaymentScheduleSnapshot lpsSnap, LendingPaymentSchedule lendingPaymentSchedule, WaiverType waiverType) {

        lendingPaymentSchedule.setMerchantId(lpsSnap.getMerchantId());
        lendingPaymentSchedule.setMerchantStoreId(lpsSnap.getMerchantStoreId());
        lendingPaymentSchedule.setLoanType(lpsSnap.getLoanType());
        lendingPaymentSchedule.setLoanAmount(lpsSnap.getLoanAmount());
        lendingPaymentSchedule.setEdiAmount(lpsSnap.getEdiAmount());
        lendingPaymentSchedule.setStartDate(lpsSnap.getStartDate());
        lendingPaymentSchedule.setEdiCount(lpsSnap.getEdiCount());
        lendingPaymentSchedule.setInterestOnlyEdiAmount(lpsSnap.getInterestOnlyEdiAmount());
        lendingPaymentSchedule.setInterestOnlyStartDate(lpsSnap.getInterestOnlyStartDate());
        lendingPaymentSchedule.setInterestOnlyEdiCount(lpsSnap.getInterestOnlyEdiCount());
        lendingPaymentSchedule.setRemainingInterestOnlyEdiCount(lpsSnap.getRemainingInterestOnlyEdiCount());
        lendingPaymentSchedule.setOverdueIntrestRate(lpsSnap.getOverdueIntrestRate());
        lendingPaymentSchedule.setOverdueEdiCount(lpsSnap.getOverdueEdiCount());
        lendingPaymentSchedule.setOverdueAmount(lpsSnap.getOverdueAmount());
        lendingPaymentSchedule.setIncentiveAmount(lpsSnap.getIncentiveAmount());
//        Re-open cases EDI remaining count set to 0. All dues already raised
        lendingPaymentSchedule.setEdiRemainingCount(0);
        lendingPaymentSchedule.setPaidAmount(lpsSnap.getPaidAmount());
        lendingPaymentSchedule.setTotalCashbackAmount(lpsSnap.getTotalCashbackAmount());
        lendingPaymentSchedule.setTotalPenaltyAmount(lpsSnap.getTotalPenaltyAmount());
        lendingPaymentSchedule.setNextEdiDate(lpsSnap.getNextEdiDate());
        lendingPaymentSchedule.setStatus(lpsSnap.getStatus());
        lendingPaymentSchedule.setOffDay(lpsSnap.getOffDay());
        lendingPaymentSchedule.setApplicationId(lpsSnap.getApplicationId());
        lendingPaymentSchedule.setTotalPayableAmount(lpsSnap.getTotalPayableAmount());
        lendingPaymentSchedule.setMobile(lpsSnap.getMobile());
        lendingPaymentSchedule.setNbfc(lpsSnap.getNbfc());
        lendingPaymentSchedule.setClosingDate(lpsSnap.getClosingDate());
        lendingPaymentSchedule.setTentativeClosingDate(lpsSnap.getTentativeClosingDate());
        lendingPaymentSchedule.setLoanConstruct(lpsSnap.getLoanConstruct());
        lendingPaymentSchedule.setInterest(lpsSnap.getInterest());
        lendingPaymentSchedule.setOtherCharges(lpsSnap.getOtherCharges());
        lendingPaymentSchedule.setPaidPrinciple(lpsSnap.getPaidPrinciple());
        lendingPaymentSchedule.setPaidInterest(lpsSnap.getPaidInterest());
        lendingPaymentSchedule.setPaidOtherCharges(lpsSnap.getPaidOtherCharges());
        lendingPaymentSchedule.setPaidPenalty(lpsSnap.getPaidPenalty());
        lendingPaymentSchedule.setDisbursalSettlementId(lpsSnap.getDisbursalSettlementId());
        lendingPaymentSchedule.setCreditLoan(lpsSnap.getCreditLoan());
        lendingPaymentSchedule.setTlDetailsId(lpsSnap.getTlDetailsId());
        lendingPaymentSchedule.setLenderDisbursalNotify(lpsSnap.getLenderDisbursalNotify());
        lendingPaymentSchedule.setAdjustedDueAmount(lpsSnap.getAdjustedDueAmount());
        lendingPaymentSchedule.setAdjustedPaidAmount(lpsSnap.getAdjustedPaidAmount());
        lendingPaymentSchedule.setSettlementMechanism(lpsSnap.getSettlementMechanism());
        lendingPaymentSchedule.setSettleAllPrinciple(lpsSnap.getSettleAllPrinciple());
        lendingPaymentSchedule.setWriteoffFor(lpsSnap.getWriteoffFor());
        lendingPaymentSchedule.setLastOverDueAmount(lpsSnap.getLastOverDueAmount());
//        For reversal cases these: reset these fields
        lendingPaymentSchedule.setSettlementInitiated(false);
        lendingPaymentSchedule.setSettlementStatus(SCHEME1.name());

        lendingPaymentScheduleDao.save(lendingPaymentSchedule);
    }

    public void checkForLoanSettlement(LendingPaymentSchedule loan) {
        long loanId = loan.getId();
        SettlementDetails settlementDetails = settlemetDetailsDao.findByLoanIdAndStatus(loanId, INIT.name());

        if (Objects.isNull(settlementDetails)) {
            log.error("Settlement Details not found initiated state for loanId : {}", loanId);
            return;
        }

        if (Objects.isNull(settlementDetails)) {
            log.error("Settlement Details not found initiated state for loanId : {}", loanId);
            return;
        }
        LendingLedgerDao.LendingLedgerSummaryDto ledgerSummary =
                lendingLedgerDao.fetchLedgerSummaryPaidAfterSettlementInit(loan.getId(), settlementDetails.getId(), settlementDetails.getCreatedAt());

        if (Objects.isNull(ledgerSummary) || ledgerSummary.getTotalPaidAmount() < settlementDetails.getSettlementAmountOffer()) {
            log.error("Unable to settle loan!! Settlement amount not received for loanId : {} till date: {}", loanId, new Date());
            return;
        }
        waiverSchemeForSettlementCases(loan);

        settlementDetails.setStatus(CLOSED.name());
        settlemetDetailsDao.save(settlementDetails);
    }
}
