package com.bharatpe.lending.service.impl;

import com.bharatpe.common.dao.LendingEDIScheduleDao;
import com.bharatpe.common.entities.LendingEDISchedule;
import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.dao.*;
import com.bharatpe.lending.common.dto.LoanRestructureRequestDto;
import com.bharatpe.lending.common.dto.TLLoanRestructureResponseDto;
import com.bharatpe.lending.common.entity.LendingCollectionExcess;
import com.bharatpe.lending.common.entity.LoanRestructureData;
import com.bharatpe.lending.common.entity.PenalCharges;
import com.bharatpe.lending.common.entity.PenaltyFeeLedger;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.dao.LendingEdiScheduleSnapshotDao;
import com.bharatpe.lending.dao.LendingLedgerDao;
import com.bharatpe.lending.dao.LendingLedgerSnapshotDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.entity.LendingEdiScheduleSnapshot;
import com.bharatpe.lending.entity.LendingLedgerSnapshot;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.enums.LoanStatus;
import com.bharatpe.lending.loanV3.dto.LenderEdIScheduleResponseDTO;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.services.associationsV2.trillionloans.impl.TLRepaymentScheduleService;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.bharatpe.lending.service.PaymentService;
import com.bharatpe.lending.util.SnapshotUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import static com.bharatpe.lending.common.Constants.SnapshotSource.RESTRUCTURING;
import static com.bharatpe.lending.common.enums.LenderAssociationStages.RESTRUCTURE_APPROVE;
import static com.bharatpe.lending.common.Constants.LoanRestructureStatus.*;
import static com.bharatpe.lending.common.enums.LenderAssociationStages.RESTRUCTURE_STATUS;

@Service
@Slf4j
public class LoanRestructureServiceImpl {

    private static final String FAILED_REASON_1 = "Restructured at lender failed at Bharatpe";
    private static final String UNABLE_TO_FETCH_RPS = "Unable to fetch RPS schedule from lender";
    private static final String UNABLE_TO_FETCH_DATA = "Unable to fetch data at Bharatpe";
    private static final String UNABLE_TO_FETCH_RESPONSE = "Unable to fetch response from NBFC";
    private static final String GENERIC_ERROR_REMARK = "Some Error Occurred";

    @Autowired
    private ILenderAPIGateway lenderAPIGateway;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SnapshotUtil snapshotUtil;

    @Autowired
    private LoanRestructureDataDao loanRestructureDataDao;

    @Autowired
    private LendingPaymentScheduleDao lendingPaymentScheduleDao;

    @Autowired
    private LendingPaymentScheduleSnapDao lendingPaymentScheduleSnapDao;

    @Autowired
    private LendingLedgerSnapshotDao lendingLedgerSnapshotDao;

    @Autowired
    private LendingEdiScheduleSnapshotDao lendingEdiScheduleSnapshotDao;

    @Autowired
    private LendingLedgerDao lendingLedgerDao;

    @Autowired
    private LendingEDIScheduleDao lendingEDIScheduleDao;

    @Autowired
    private PenaltyFeeLedgerDao penaltyFeeLedgerDao;

    @Autowired
    private PenalChargesDao penalChargesDao;

    @Autowired
    private LendingCollectionExcessDao lendingCollectionExcessDao;

    @Autowired
    private TLRepaymentScheduleService tlRepaymentScheduleService;

    @Autowired
    private PaymentService paymentService;

    @Value("${tl.loan.restructure.timeout.threshold:30000}")
    Integer tlLoanRestructureTimeoutThreshold;

    @Transactional(rollbackFor = Exception.class)
    public LoanRestructureData restructureLoan(LoanRestructureData loanRestructureData) {
        try {
            log.info("Restructuring loan for merchantId: {}, applicationId: {}, lan: {}, requestId: {}",
                    loanRestructureData.getMerchantId(), loanRestructureData.getApplicationId(),
                    loanRestructureData.getLan(), loanRestructureData.getRequestId());

            loanRestructureData = getLoanRestructureResponseFromLender(loanRestructureData);

            if (SUCCESS_AT_LENDER.name().equals(loanRestructureData.getStatus().name())) {
                loanRestructureData = initiateRestructureAtBP(loanRestructureData);
            }
            loanRestructureData = loanRestructureDataDao.save(loanRestructureData); // Save the updated loan restructure data to the database
            log.info("Completed loan restructuring process for merchantId: {}, applicationId: {}, lan: {}, requestId: {}. Final status: {}",
                    loanRestructureData.getMerchantId(), loanRestructureData.getApplicationId(),
                    loanRestructureData.getLan(), loanRestructureData.getRequestId(), loanRestructureData.getStatus());
            return loanRestructureData; // Return the updated loan restructure data after processing
        } catch (Exception e) {
            log.error("Error occurred during loan restructuring for merchantId: {}, applicationId: {}, lan: {}, requestId: {}. Error: {} Stack: {}",
                    loanRestructureData.getMerchantId(), loanRestructureData.getApplicationId(),
                    loanRestructureData.getLan(), loanRestructureData.getRequestId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
            loanRestructureData.setStatus(PENDING);
            loanRestructureData.setRemarks(GENERIC_ERROR_REMARK);
            return loanRestructureData;
        }
    }

    private LoanRestructureData getLoanRestructureResponseFromLender(LoanRestructureData loanRestructureData) {
        log.info("Getting loan restructure response for merchantId: {}, applicationId: {}, lan: {}, requestId: {}",
                loanRestructureData.getMerchantId(), loanRestructureData.getApplicationId(),
                loanRestructureData.getLan(), loanRestructureData.getRequestId());
        try {
            NBFCResponseDTO nbfcResponseDTO = invokeApiCallWithLender(loanRestructureData, RESTRUCTURE_APPROVE);

            if (nbfcResponseDTO == null) {
                log.error("No response received from lender for loan restructure for applicationId: {}, lan: {}, requestId: {}",
                        loanRestructureData.getApplicationId(), loanRestructureData.getLan(), loanRestructureData.getRequestId());
                loanRestructureData.setStatus(PENDING);
                loanRestructureData.setRemarks(UNABLE_TO_FETCH_RESPONSE);
                return loanRestructureData;
            }

            if (nbfcResponseDTO != null && nbfcResponseDTO.getSuccess() && nbfcResponseDTO.getData() != null) {
                // Update the loanRestructureData based on the response from the lender
                log.info("Loan restructure approved by lender for applicationId: {}, lan: {}, requestId: {}",
                        loanRestructureData.getApplicationId(), loanRestructureData.getLan(), loanRestructureData.getRequestId());
                TLLoanRestructureResponseDto response = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDTO.getData()), TLLoanRestructureResponseDto.class);
                if ("SUCCESS".equalsIgnoreCase(response.getStatus())) {
                    loanRestructureData.setStatus(SUCCESS_AT_LENDER);
                } else if ("FAIL".equalsIgnoreCase(response.getStatus())) {
                    log.info("Loan restructure failed as per lender response for applicationId: {}, lan: {}, requestId: {}. Response status: {}",
                            loanRestructureData.getApplicationId(), loanRestructureData.getLan(), loanRestructureData.getRequestId(), response.getStatus());
                    loanRestructureData.setStatus(FAILED);
                    loanRestructureData.setRemarks(response.getMessage());
                } else {
                    log.info("Loan restructure failed as per lender response for applicationId: {}, lan: {}, requestId: {}. Response status: {}",
                            loanRestructureData.getApplicationId(), loanRestructureData.getLan(), loanRestructureData.getRequestId(), response.getStatus());
                    loanRestructureData.setStatus(PENDING);
                    loanRestructureData.setRemarks(response.getMessage());
                }
            } else {
                log.info("Loan restructure failed or rejected by lender for applicationId: {}, lan: {}, requestId: {}",
                        loanRestructureData.getApplicationId(), loanRestructureData.getLan(), loanRestructureData.getRequestId());
                loanRestructureData.setStatus(FAILED);
                loanRestructureData.setRemarks(GENERIC_ERROR_REMARK);
            }
        } catch (Exception e) {
            log.error("Error while converting loan restructure response to JSON string: {} Stack: {}", e.getMessage(), Arrays.asList(e.getStackTrace()));
            loanRestructureData.setStatus(PENDING);
            loanRestructureData.setRemarks(GENERIC_ERROR_REMARK);
        }
        return loanRestructureData;
    }

    private NBFCResponseDTO invokeApiCallWithLender(LoanRestructureData loanRestructureData, LenderAssociationStages requestType) {

        log.info("Initiating loan restructure with lender for applicationId: {}, lan: {}, requestId: {}",
                loanRestructureData.getApplicationId(), loanRestructureData.getLan(), loanRestructureData.getRequestId());
        NBFCRequestDTO nbfcRequestDto = NBFCRequestDTO.builder()
                .productName("LENDING")
                .lender(Lender.TRILLIONLOANS.name())
                .applicationId(loanRestructureData.getApplicationId())
                .payload(LoanRestructureRequestDto.builder()
                        .lan(String.valueOf(loanRestructureData.getLan()))
                        .requestId(loanRestructureData.getRequestId())
                        .build())
                .build();
        log.info("Invoking lender API gateway for loan restructure with request: {}", nbfcRequestDto);
        NBFCResponseDTO nbfcResponseDto =
                lenderAPIGateway.invokeStage(nbfcRequestDto, requestType, tlLoanRestructureTimeoutThreshold);
        log.info("Received response from lender API gateway for loan restructure: {}", nbfcResponseDto);

        return nbfcResponseDto;
    }

    private LoanRestructureData initiateRestructureAtBP(LoanRestructureData loanRestructureData) {
        log.info("Initiating loan restructure at BP for applicationId: {}, lan: {}, requestId: {}",
                loanRestructureData.getApplicationId(), loanRestructureData.getLan(), loanRestructureData.getRequestId());
        LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.findByApplicationId(loanRestructureData.getApplicationId());
        if (lendingPaymentSchedule == null) {
            log.info("No lending payment schedule found for applicationId: " + loanRestructureData.getApplicationId());
            loanRestructureData.setRemarks(FAILED_REASON_1);
            loanRestructureData.setStatus(FAILED);
            return loanRestructureData;
        }
        if (!LoanStatus.ACTIVE.name().equals(lendingPaymentSchedule.getStatus())) {
            log.error("Loan is not active for applicationId: {} but re-structured at Lender", loanRestructureData.getApplicationId());
            loanRestructureData.setRemarks(FAILED_REASON_1);
            loanRestructureData.setStatus(FAILED);
            return loanRestructureData;
        }

        LenderEdIScheduleResponseDTO lenderEdIScheduleResponse = getLenderEdiSchedule(loanRestructureData.getApplicationId());
        if (lenderEdIScheduleResponse == null || lenderEdIScheduleResponse.getRepaymentSchedule() == null || lenderEdIScheduleResponse.getRepaymentSchedule().isEmpty()) {
            log.error("Failed to fetch lender EDI schedule for applicationId: {}, cannot proceed with loan restructuring at BP for applicationId: {}, lan: {}, requestId: {}",
                    loanRestructureData.getApplicationId(), loanRestructureData.getApplicationId(), loanRestructureData.getLan(), loanRestructureData.getRequestId());
            loanRestructureData.setRemarks(UNABLE_TO_FETCH_RPS);
            loanRestructureData.setStatus(PENDING);
            return loanRestructureData;
        }
        // create snapshots
        createSnapshots(lendingPaymentSchedule);

        loanRestructureData = processLoanRestructureAtBP(lendingPaymentSchedule, lenderEdIScheduleResponse, loanRestructureData);

        paymentService.removeAppBottomSheet(loanRestructureData.getLoanId(), loanRestructureData.getMerchantId());

        return loanRestructureData;
    }

    private LoanRestructureData processLoanRestructureAtBP(LendingPaymentSchedule lps,
                 LenderEdIScheduleResponseDTO lenderEdIScheduleResponse, LoanRestructureData loanRestructureData) {
        log.info("Processing loan restructure at BP for loan id: {} with new EDI schedule", lps.getId());

        List<LenderEdIScheduleResponseDTO.RepaymentSchedule> newEdiScheduleList = lenderEdIScheduleResponse.getRepaymentSchedule();

        int graceStartInstallmentNumber = lenderEdIScheduleResponse.getGraceStartInstallmentNumber();
        graceStartInstallmentNumber = graceStartInstallmentNumber <= 0 ? 1: graceStartInstallmentNumber; // just a safe check, as grace start installment number should be atleast 1

        List<LendingEDISchedule> oldEdiScheduleList = lendingEDIScheduleDao.findByLendingPaymentSchedule(lps);
        if (oldEdiScheduleList == null || oldEdiScheduleList.isEmpty()) {
            log.error("No old EDI schedule found for loan id: {} while creating snapshot for EDI schedule", lps.getId());
            loanRestructureData.setRemarks(UNABLE_TO_FETCH_DATA);
            loanRestructureData.setStatus(FAILED);
            return loanRestructureData;
        }

        oldEdiScheduleList = oldEdiScheduleList.stream()
                .filter(edi -> edi.getInstallmentNumber() > 0)
                .sorted(Comparator.comparing(LendingEDISchedule::getInstallmentNumber))
                .collect(Collectors.toList());

        newEdiScheduleList = newEdiScheduleList.stream()
                .sorted(Comparator.comparing(LenderEdIScheduleResponseDTO.RepaymentSchedule::getInstallmentNumber))
                .collect(Collectors.toList());

        List<LendingEDISchedule> upsertedEdiSchedules = new ArrayList<>();

        double principleToBeReversed = 0;
        double interestToBeReversed = 0;

        for (int i = 0; i < newEdiScheduleList.size(); i++) {

            int installmentNumber = i + 1;
            LenderEdIScheduleResponseDTO.RepaymentSchedule newSchedule = newEdiScheduleList.get(i);
            if (i >= oldEdiScheduleList.size()) {
                upsertedEdiSchedules.add(createNewEdiScheduleFromNewSchedule(newSchedule, lps));
                continue;
            }

            LendingEDISchedule oldSchedule = oldEdiScheduleList.get(i);
            if (installmentNumber < graceStartInstallmentNumber) {
                validateLendingEdiScheduleWithNewSchedule(oldSchedule, newSchedule);
                continue;
            }
            double paidPrincipal = oldSchedule.getPaidPrinciple() == null ? 0 : oldSchedule.getPaidPrinciple();
            double paidInterest = oldSchedule.getPaidInterest() == null ? 0 : oldSchedule.getPaidInterest();
            principleToBeReversed += paidPrincipal;
            interestToBeReversed += paidInterest;

            upsertedEdiSchedules.add(updateOldEdiScheduleWithNewSchedule(oldSchedule, newSchedule));
        }
        log.info("Total principal to be reversed for loan id: {} is {}, total interest to be reversed is {}", lps.getId(), principleToBeReversed, interestToBeReversed);
        log.info("Saving updated EDI schedule for loan id: {}, altered schedule count: {}", lps.getId(), upsertedEdiSchedules.size());
        lendingEDIScheduleDao.saveAll(upsertedEdiSchedules);

        // ledger reverse and due 0 to be set for old ledgers
        reverseLedgerEntries(lps, principleToBeReversed, interestToBeReversed, oldEdiScheduleList.get(graceStartInstallmentNumber-1).getDate());
        // charges to be waived
        double waivedCharges = waiveUnpaidCharges(lps);
        // lps to be updated
        updateLendingPaymentSchedule(lps, lenderEdIScheduleResponse, waivedCharges, principleToBeReversed, interestToBeReversed);

        loanRestructureData.setStatus(SUCCESS);
        loanRestructureData.setRestructuredAt(new Date());
        return loanRestructureData;
    }

    private void updateLendingPaymentSchedule(LendingPaymentSchedule lps, LenderEdIScheduleResponseDTO lenderEdIScheduleResponse,
                                              double waivedCharges, double principleToBeReversed, double interestToBeReversed) {

        log.info("Updating lending payment schedule for loan id: {} with new EDI schedule details", lps.getId());
        Date newTentativeClosingDate =  lenderEdIScheduleResponse.getLoanMaturityDate();
        Double newTotalInterestPayable = lenderEdIScheduleResponse.getTotalInterestPayable();
        Double newTotalPayableAmount = newTotalInterestPayable + lps.getLoanAmount();
        int newEdiCount = lenderEdIScheduleResponse.getRepaymentSchedule().size();
        int ediExtended =  newEdiCount - lps.getEdiCount();
        int newEdiRemainingCount = lps.getEdiRemainingCount() + ediExtended;
        double amountReversed = principleToBeReversed + interestToBeReversed;

        lps.setEdiAmount(lenderEdIScheduleResponse.getNewEdiAmount());
        lps.setEdiCount(newEdiCount);
        lps.setEdiRemainingCount(newEdiRemainingCount);
        lps.setDueAmount(0D);
        lps.setPaidAmount(lps.getPaidAmount() - amountReversed);
        lps.setTotalPenaltyAmount(Math.max(0, lps.getTotalPenaltyAmount() != null? lps.getTotalPenaltyAmount() - waivedCharges : 0));
        lps.setTotalPayableAmount(newTotalPayableAmount);
        lps.setTentativeClosingDate(newTentativeClosingDate);
        lps.setInterest(newTotalInterestPayable);
        lps.setDuePrinciple(0D);
        lps.setDueInterest(0D);
        lps.setDuePenalty(0D);

        if (principleToBeReversed > 0) {
            lps.setPaidPrinciple(lps.getPaidPrinciple() - principleToBeReversed);
        }
        if (interestToBeReversed > 0) {
            lps.setPaidInterest(lps.getPaidInterest() - interestToBeReversed);
        }
        log.info("Saving updated lending payment schedule for loan id: {} after restructuring", lps.getId());
        lendingPaymentScheduleDao.save(lps);
    }

    private double waiveUnpaidCharges(LendingPaymentSchedule lps) {
        log.info("Waiving unpaid charges for loan id: {}", lps.getId());
        List<PenaltyFeeLedger> penaltyFeeLedgerList = penaltyFeeLedgerDao.findByLoanIdOrderByCreatedAtDesc(lps.getId());
        if (penaltyFeeLedgerList == null || penaltyFeeLedgerList.isEmpty()) {
            log.info("No unpaid charges found for loan id: {}", lps.getId());
            return 0;
        }
        penaltyFeeLedgerList = penaltyFeeLedgerList.stream()
            .filter(p -> p.getIsWaveOff() == null || !p.getIsWaveOff())
            .collect(Collectors.toList());
        if (penaltyFeeLedgerList.isEmpty()) {
            log.info("No unpaid charges found for loan id: {} after filtering waived off charges", lps.getId());
            return 0;
        }
        List<PenaltyFeeLedger> positiveAmountLedgers = new ArrayList<>();
        List<PenaltyFeeLedger> negativeAmountLedgers = new ArrayList<>();
        double positiveAmountSum = 0;
        double negativeAmountSum = 0;

        for (PenaltyFeeLedger ledger : penaltyFeeLedgerList) {
            if (ledger.getAmount() > 0) {
                positiveAmountLedgers.add(ledger);
                positiveAmountSum += ledger.getAmount();
            } else if (ledger.getAmount() < 0) {
                negativeAmountLedgers.add(ledger);
                negativeAmountSum += Math.abs(ledger.getAmount());
            }
        }

        if (positiveAmountSum == negativeAmountSum) {
            log.info("No unpaid charges found for loan id: {} after filtering waived off charges and zero amount charges", lps.getId());
            return 0;
        }

        if (positiveAmountSum > negativeAmountSum) {
            log.info("PENALTY WAIVER DATA ISSUE LOAN RESTRUCTURING Positive amount sum is greater than negative amount " +
                    "sum for loan id: {}. Positive amount sum: {}, negative amount sum: {}", lps.getId(), positiveAmountSum, negativeAmountSum);
            return 0;
        }

        double amountToBeWaived = negativeAmountSum - positiveAmountSum;
        double finalWaiverAmount = amountToBeWaived;
        log.info("Waiving off unpaid charges for loan id: {}. Total amount to be waived off: {}", lps.getId(), amountToBeWaived);

        negativeAmountLedgers = negativeAmountLedgers.stream()
                .sorted(Comparator.comparing(PenaltyFeeLedger::getId).reversed())
                .collect(Collectors.toList());

        List<PenaltyFeeLedger> ledgersToBeUpdated = new ArrayList<>();

        for (PenaltyFeeLedger ledger : negativeAmountLedgers) {
            if (amountToBeWaived <= 0) {
                break;
            }
            double ledgerAmount = Math.abs(ledger.getAmount());
            double waiverAmt = Math.min(ledgerAmount, amountToBeWaived);

            ledgersToBeUpdated.add(createWaiverPenaltyFeeLedgerEntry(ledger, waiverAmt, lps));

            if (waiverAmt == ledgerAmount) {
                ledger.setIsWaveOff(true);
                ledgersToBeUpdated.add(ledger);
            }
            amountToBeWaived -= waiverAmt;
            log.info("Waiving off charge with id: {} for loan id: {}. Waiver amount: {}, remaining amount to be waived off: {}",
                    ledger.getId(), lps.getId(), waiverAmt, amountToBeWaived);
        }
        penaltyFeeLedgerDao.saveAll(ledgersToBeUpdated);
        log.info("Completed waiving off unpaid charges for loan id: {} and waived amount : {}", lps.getId(), finalWaiverAmount);

        return finalWaiverAmount;
    }

    private PenaltyFeeLedger createWaiverPenaltyFeeLedgerEntry(PenaltyFeeLedger ledger, double amountToBeWaived, LendingPaymentSchedule lps) {
        PenaltyFeeLedger waiverLedger = new PenaltyFeeLedger();
        waiverLedger.setLoanId(ledger.getLoanId());
        waiverLedger.setMerchantId(ledger.getMerchantId());
        waiverLedger.setAmount(amountToBeWaived);
        waiverLedger.setDescription(ledger.getDescription() + " Waiver : " + ledger.getId());
        waiverLedger.setIsWaveOff(true);
        waiverLedger.setLender(ledger.getLender());

        LendingLedger lendingLedger = new LendingLedger();
        lendingLedger.setLendingPaymentSchedule(lps);
        lendingLedger.setMerchantId(ledger.getMerchantId());
        lendingLedger.setAmount(amountToBeWaived);
        lendingLedger.setPenalty(amountToBeWaived);
        lendingLedger.setDescription("Charges Waived");
        lendingLedger.setDate(new Date());
        lendingLedger.setPrinciple(0D);
        lendingLedger.setInterest(0D);
        lendingLedger.setOtherCharges(0D);
        lendingLedger.setTxnType("EDI");
        lendingLedger.setAdjustmentMode("WAIVER");

        lendingLedgerDao.save(lendingLedger);

        PenalCharges penalCharges = penalChargesDao.findByLoanId(ledger.getLoanId());
        if (penalCharges == null) {
            log.error("PENAL CHARGES DATA ISSUE LOAN RESTRUCTURING No penal charges entry found for loan id: {} while creating waiver ledger entry for penalty fee ledger id: {}",
                    ledger.getLoanId(), ledger.getId());
            return waiverLedger;
        }

        if ("Penalty Fee".equals(ledger.getDescription())) {
            penalCharges.setDuePenalty(Math.min(0, penalCharges.getDuePenalty() - amountToBeWaived));
        }
        if ("Nach Bounce".equals(ledger.getDescription())) {
            penalCharges.setDueNachBounce(Math.min(0, penalCharges.getDueNachBounce() - amountToBeWaived));
        }
        penalChargesDao.save(penalCharges);

        return waiverLedger;
    }

    private void reverseLedgerEntries(LendingPaymentSchedule lps, double principleToBeReversed, double interestToBeReversed, Date graceStartDate) {
        log.info("Reversing ledger entries for loan id: {}. Principle to be reversed: {}, interest to be reversed: {}, grace start date {}",
                lps.getId(), principleToBeReversed, interestToBeReversed, graceStartDate);
        List<LendingLedger> lendingLedgers = lendingLedgerDao.findByLendingPaymentSchedule(lps);
        if (lendingLedgers == null || lendingLedgers.isEmpty()) {
            log.error("LOAN RESTRUCTURING LEDGER DATA FLAGGED No ledger entries found for loan id: {} while reversing ledger entries during loan restructuring", lps.getId());
            return;
        }
        List<LendingLedger> ledgersToBeUpdated = new ArrayList<>();

        List<LendingLedger> negativeAmountLedgers = lendingLedgers.stream()
            .filter(ledger -> ledger.getAmount() != null && ledger.getAmount() < 0)
            .sorted(Comparator.comparing(LendingLedger::getId).reversed())
            .collect(Collectors.toList());

        for (LendingLedger ledger : negativeAmountLedgers) {
            if (ledger.getDate() != null && ledger.getDate().before(graceStartDate)) {
                break; // stop processing as soon as a date before graceStartDate is found
            }
            ledger.setPrinciple(0D);
            ledger.setInterest(0D);
            ledger.setAmount(0D);
            ledger.setUpdatedAt(new Date());

            if (ledger.getPenalty() != null && ledger.getPenalty() < 0) {
                ledger.setAmount(ledger.getPenalty());
            }
            ledgersToBeUpdated.add(ledger);
        }

        List<LendingLedger> positiveAmountLedgers = lendingLedgers.stream()
            .filter(ledger -> ledger.getAmount() != null && ledger.getAmount() > 0)
            .sorted(Comparator.comparing(LendingLedger::getId).reversed())
            .collect(Collectors.toList());

        for (LendingLedger ledger : positiveAmountLedgers) {

            if (principleToBeReversed <= 0 && interestToBeReversed <= 0) {
                break;
            }
            double principleReversal = 0;
            double interestReversal = 0;
            log.info("LOAN RESTRUCTURING LEDGER Reversing ledger entry with id: {} for loan id: {}. Current principle: {}, current interest: {}, current amount: {}, principle to be reversed: {}, interest to be reversed: {}",
                    ledger.getId(), lps.getId(), ledger.getPrinciple(), ledger.getInterest(), ledger.getAmount(), principleToBeReversed, interestToBeReversed);

            principleReversal = Math.min(ledger.getPrinciple() == null ? 0 : ledger.getPrinciple(), principleToBeReversed);
            if (principleToBeReversed > 0 && ledger.getPrinciple() != null && ledger.getPrinciple() > 0) {
                ledger.setPrinciple(ledger.getPrinciple() - principleReversal);
                ledger.setAmount(ledger.getAmount() - principleReversal);
                principleToBeReversed -= principleReversal;
            }

            interestReversal = Math.min(ledger.getInterest() == null ? 0 : ledger.getInterest(), interestToBeReversed);
            if (interestToBeReversed > 0 && ledger.getInterest() != null && ledger.getInterest() > 0) {
                ledger.setInterest(ledger.getInterest() - interestReversal);
                ledger.setAmount(ledger.getAmount() - interestReversal);
                interestToBeReversed -= interestReversal;
            }
            ledger.setUpdatedAt(new Date());
            ledgersToBeUpdated.add(ledger);

            createExcessEntryForReversedAmount(ledger, principleReversal + interestReversal);
            log.info("LOAN RESTRUCTURING LEDGER After reversing ledger entry with id: {} for loan id: {}. Updated principle: {}, updated interest: {}, updated amount: {}, remaining principle to be reversed: {}, remaining interest to be reversed: {}",
                    ledger.getId(), lps.getId(), ledger.getPrinciple(), ledger.getInterest(), ledger.getAmount(), principleToBeReversed, interestToBeReversed);
        }

        if (principleToBeReversed > 0 || interestToBeReversed > 0) {
            log.error("LOAN RESTRUCTURING LEDGER REVERSAL INCOMPLETE for loan id: {}. Remaining principle to be reversed: {}, remaining interest to be reversed: {}. This indicates that not all the necessary ledger entries were reversed during loan restructuring",
                    lps.getId(), principleToBeReversed, interestToBeReversed);
        } else {
            log.info("LOAN RESTRUCTURING LEDGER REVERSAL COMPLETE for loan id: {}. All necessary ledger entries were reversed successfully during loan restructuring", lps.getId());
        }

        log.info("Saving reversed ledger entries for loan id: {}, total ledgers to be updated: {}", lps.getId(), ledgersToBeUpdated.size());
        lendingLedgerDao.saveAll(ledgersToBeUpdated);
    }

    private void createExcessEntryForReversedAmount(LendingLedger ledger, double excessAmount) {
        log.info("Creating excess entry for reversed excessAmount for loan id: {}. Original ledger id: {}, excess excessAmount: {}",
                ledger.getLendingPaymentSchedule().getId(), ledger.getId(), excessAmount);

        LendingCollectionExcess lendingCollectionExcess =
                lendingCollectionExcessDao.findByLoanIdAndTerminalOrderId(ledger.getLendingPaymentSchedule().getId(), ledger.getTerminalOrderId());

        if (lendingCollectionExcess != null) {
            double updatedExcessAmount = (lendingCollectionExcess.getAmount() != null ? lendingCollectionExcess.getAmount() : 0) + excessAmount;
            lendingCollectionExcess.setAmount(updatedExcessAmount);
            lendingCollectionExcess.setStatus("ACTIVE");
            lendingCollectionExcessDao.save(lendingCollectionExcess);
            log.info("Updated existing excess entry for loan id: {}. Excess entry id: {}, updated excess excessAmount: {}",
                    ledger.getLendingPaymentSchedule().getId(), lendingCollectionExcess.getId(), updatedExcessAmount);
        } else {
            LendingCollectionExcess newExcessEntry = new LendingCollectionExcess();
            newExcessEntry.setLoanId(ledger.getLendingPaymentSchedule().getId());
            newExcessEntry.setMerchantId(ledger.getMerchantId());
            newExcessEntry.setExcessNachCreditAmount(ledger.getAmount());
            newExcessEntry.setAmount(excessAmount);
            newExcessEntry.setTerminalOrderId(ledger.getTerminalOrderId());

            newExcessEntry.setDeductionCount(0);
            newExcessEntry.setCreditDate(ledger.getDate());
            newExcessEntry.setTransferType(ledger.getTransferType());
            newExcessEntry.setDeductedAmount(0D);
            newExcessEntry.setSource("LOAN_RESTRUCTURING");
            newExcessEntry.setStatus("ACTIVE");
            lendingCollectionExcessDao.save(newExcessEntry);
            log.info("Created new excess entry for loan id: {}. New excess entry id: {}, excess excessAmount: {}",
                    ledger.getLendingPaymentSchedule().getId(), newExcessEntry.getId(), newExcessEntry.getAmount());
        }
    }

    private LendingEDISchedule updateOldEdiScheduleWithNewSchedule(LendingEDISchedule oldSchedule, LenderEdIScheduleResponseDTO.RepaymentSchedule newSchedule) {
        oldSchedule.setDate(newSchedule.getDueDate());
        oldSchedule.setOpeningBalance(newSchedule.getOpeningBalance());
        oldSchedule.setPrinciple(newSchedule.getPrincipal());
        oldSchedule.setInterest(newSchedule.getInterest());
        oldSchedule.setTotalEdi(newSchedule.getTotalEdi());
        if (newSchedule.getTotalEdi() == 0) {
            oldSchedule.setPaidPrinciple(0D);
            oldSchedule.setPaidInterest(0D);
        } else {
            oldSchedule.setPaidPrinciple(null);
            oldSchedule.setPaidInterest(null);
        }
        return oldSchedule;
    }

    private void validateLendingEdiScheduleWithNewSchedule(LendingEDISchedule oldSchedule, LenderEdIScheduleResponseDTO.RepaymentSchedule newSchedule) {
//        This block just logs the RPS MISMATCH ERROR
        if (oldSchedule.getInstallmentNumber() != newSchedule.getInstallmentNumber()) {
            log.error("RPS MISMATCH ERROR Installment number mismatch between old EDI schedule and new schedule for loan id: {}. Old installment number: {}, new installment number: {}",
                    oldSchedule.getLendingPaymentSchedule().getId(), oldSchedule.getInstallmentNumber(), newSchedule.getInstallmentNumber());
        }
        if (!oldSchedule.getDate().equals(newSchedule.getDueDate())) {
            log.error("RPS MISMATCH ERROR Due date mismatch between old EDI schedule and new schedule for loan id: {}. Old due date: {}, new due date: {}",
                    oldSchedule.getLendingPaymentSchedule().getId(), oldSchedule.getDate(), newSchedule.getDueDate());
        }
        if (!oldSchedule.getOpeningBalance().equals(newSchedule.getOpeningBalance())) {
            log.error("RPS MISMATCH ERROR Opening balance mismatch between old EDI schedule and new schedule for loan id: {}. Old opening balance: {}, new opening balance: {}",
                    oldSchedule.getLendingPaymentSchedule().getId(), oldSchedule.getOpeningBalance(), newSchedule.getOpeningBalance());
        }
        if (!oldSchedule.getPrinciple().equals(newSchedule.getPrincipal())) {
            log.error("RPS MISMATCH ERROR Principal mismatch between old EDI schedule and new schedule for loan id: {}. Old principal: {}, new principal: {}",
                    oldSchedule.getLendingPaymentSchedule().getId(), oldSchedule.getPrinciple(), newSchedule.getPrincipal());
        }
        if (!oldSchedule.getInterest().equals(newSchedule.getInterest())) {
            log.error("RPS MISMATCH ERROR Interest mismatch between old EDI schedule and new schedule for loan id: {}. Old interest: {}, new interest: {}",
                    oldSchedule.getLendingPaymentSchedule().getId(), oldSchedule.getInterest(), newSchedule.getInterest());
        }
        if (!oldSchedule.getTotalEdi().equals(newSchedule.getTotalEdi())) {
            log.error("RPS MISMATCH ERROR Total EDI mismatch between old EDI schedule and new schedule for loan id: {}. Old total EDI: {}, new total EDI: {}",
                    oldSchedule.getLendingPaymentSchedule().getId(), oldSchedule.getTotalEdi(), newSchedule.getTotalEdi());
        }
        if (oldSchedule.getPaidPrinciple() == null || !oldSchedule.getPaidPrinciple().equals(oldSchedule.getPrinciple())) {
            log.error("RPS MISMATCH ERROR Paid principal mismatch between old EDI schedule and new schedule for loan id: {}. Old paid principal: {}, new principal: {}",
                    oldSchedule.getLendingPaymentSchedule().getId(), oldSchedule.getPaidPrinciple(), newSchedule.getPrincipal());
        }
        if (oldSchedule.getPaidInterest() == null || !oldSchedule.getPaidInterest().equals(oldSchedule.getInterest())) {
            log.error("RPS MISMATCH ERROR Paid interest mismatch between old EDI schedule and new schedule for loan id: {}. Old paid interest: {}, new interest: {}",
                    oldSchedule.getLendingPaymentSchedule().getId(), oldSchedule.getPaidInterest(), newSchedule.getInterest());
        }
    }

    private LendingEDISchedule createNewEdiScheduleFromNewSchedule(LenderEdIScheduleResponseDTO.RepaymentSchedule loanSchedule, LendingPaymentSchedule lps) {
        LendingEDISchedule currentSchedule = new LendingEDISchedule();
        currentSchedule.setDate(loanSchedule.getDueDate());
        currentSchedule.setEdiType("Regular");
        currentSchedule.setInstallmentNumber(loanSchedule.getInstallmentNumber());
        currentSchedule.setOpeningBalance(loanSchedule.getOpeningBalance());
        currentSchedule.setInterest(loanSchedule.getInterest());
        currentSchedule.setPrinciple(loanSchedule.getPrincipal());
        currentSchedule.setProcessingFee(0D);
        currentSchedule.setTotalEdi(loanSchedule.getTotalEdi());
        currentSchedule.setOtherCharges(0D);
        currentSchedule.setMerchantId(lps.getMerchantId());
        currentSchedule.setLoanApplication(lps.getLoanApplication());
        currentSchedule.setLendingPaymentSchedule(lps);
        currentSchedule.setMerchantStoreId(null);

        return currentSchedule;
    }

    private void createSnapshots(LendingPaymentSchedule lps) {
        createLpsSnapshots(lps);
        createLedgerSnapshots(lps);
        createEdiScheduleSnapshots(lps);
    }

    private void createEdiScheduleSnapshots(LendingPaymentSchedule lps) {
        log.info("Creating snapshots for EDI schedule for loan id: {}", lps.getId());
        Integer previousSnapshotVersion = lendingEdiScheduleSnapshotDao.findTop1VersionByLoanIdAndSourceOrderByIdDesc(lps.getId(), RESTRUCTURING.name());

        if (previousSnapshotVersion == null) {
            previousSnapshotVersion = 0;
        }
        int newSnapshotVersion = previousSnapshotVersion + 1;

        List<LendingEDISchedule> ediScheduleList = lendingEDIScheduleDao.findByLendingPaymentSchedule(lps);
        if (ediScheduleList == null || ediScheduleList.isEmpty()) {
            log.error("No EDI schedule found for loan id: {} while creating snapshot for EDI schedule", lps.getId());
            return;
        }

        ediScheduleList = ediScheduleList.stream()
                .sorted(Comparator.comparing(LendingEDISchedule::getInstallmentNumber))
                .collect(Collectors.toList());

        List<LendingEdiScheduleSnapshot> ediScheduleSnapshots = new ArrayList<>();

        int counter = 0;
        for (LendingEDISchedule ediSchedule : ediScheduleList) {
            ediScheduleSnapshots.add(snapshotUtil.createSnapshotForEdiSchedule(ediSchedule, RESTRUCTURING, newSnapshotVersion));
            counter++;
        }
        lendingEdiScheduleSnapshotDao.saveAll(ediScheduleSnapshots);

        log.info("Created snapshot for EDI schedule for loan id: {}, snapshot version: {}, edi schedule count: {}", lps.getId(), newSnapshotVersion, counter);
    }

    private void createLedgerSnapshots(LendingPaymentSchedule lps) {
        log.info("Creating snapshots for ledger for loan id: {}", lps.getId());
        Integer previousSnapshotVersion = lendingLedgerSnapshotDao.findTop1VersionByLoanIdAndSourceOrderByIdDesc(lps.getId(), RESTRUCTURING.name());

        if (previousSnapshotVersion == null) {
            previousSnapshotVersion = 0;
        }
        int newSnapshotVersion = previousSnapshotVersion + 1;

        List<LendingLedger> lendingLedgers = lendingLedgerDao.findByLendingPaymentSchedule(lps);

        if (lendingLedgers == null || lendingLedgers.isEmpty()) {
            log.error("No ledger entries found for loan id: {} while creating snapshot for ledger", lps.getId());
            return;
        }

        List<LendingLedgerSnapshot> lendingLedgerSnapshots = new ArrayList<>();

        lendingLedgers = lendingLedgers.stream()
                .sorted(Comparator.comparing(LendingLedger::getId))
                .collect(Collectors.toList());

        int counter = 0;
        for (LendingLedger ledger : lendingLedgers) {
            lendingLedgerSnapshots.add(snapshotUtil.createSnapshotForLedger(lps, ledger, RESTRUCTURING, newSnapshotVersion));
            counter++;
        }
        lendingLedgerSnapshotDao.saveAll(lendingLedgerSnapshots);

        log.info("Created snapshots for ledger for loan id: {} ledger count : {}", lps.getId(), counter);
    }

    private void createLpsSnapshots(LendingPaymentSchedule lps) {
        log.info("Creating snapshots for lending payment schedule for loan id: {}", lps.getId());
        Integer previousSnapshotVersion = lendingPaymentScheduleSnapDao.findTop1VersionByLoanIdAndSourceOrderByIdDesc(lps.getId(), RESTRUCTURING.name());

        if (previousSnapshotVersion == null) {
            previousSnapshotVersion = 0;
        }
        int newSnapshotVersion = previousSnapshotVersion + 1;
        snapshotUtil.createSnapshotForLPS(lps, RESTRUCTURING, newSnapshotVersion);
        log.info("Created snapshot for lending payment schedule for loan id: {}, snapshot version: {}", lps.getId(), newSnapshotVersion);
    }

    private LenderEdIScheduleResponseDTO getLenderEdiSchedule(Long applicationId) {
        LenderEdIScheduleResponseDTO ediSchedule = tlRepaymentScheduleService.invokeRpsGenerateForRestructure(applicationId);
        if (ediSchedule == null) {
            log.error("Failed to fetch lender EDI schedule for applicationId: {}", applicationId);
            return null;
        }
        log.info("Fetched lender EDI schedule for applicationId: {}", applicationId);
        return ediSchedule;
    }

    @Async
    @Transactional(rollbackFor = Exception.class)
    public void processLoanRestructureAsync(LoanRestructureData loanRestructureData) {
        try {
            log.info("Asynchronously processing loan restructure for applicationId: {}, lan: {}, requestId: {}",
                    loanRestructureData.getApplicationId(), loanRestructureData.getLan(), loanRestructureData.getRequestId());

            loanRestructureData = getLoanRestructureStatusResponseFromLender(loanRestructureData);

            if (SUCCESS_AT_LENDER.equals(loanRestructureData.getStatus())) {
                log.info("Loan restructure approved at lender for applicationId: {}, lan: {}, requestId: {}. Initiating restructure at BP.",
                        loanRestructureData.getApplicationId(), loanRestructureData.getLan(), loanRestructureData.getRequestId());
                loanRestructureData = initiateRestructureAtBP(loanRestructureData);
            }

            if (PENDING.equals(loanRestructureData.getStatus())) {
                log.info("Loan restructure is in pending state for applicationId: {}, lan: {}, requestId: {}. retrying now.",
                        loanRestructureData.getApplicationId(), loanRestructureData.getLan(), loanRestructureData.getRequestId());
                loanRestructureData = restructureLoan(loanRestructureData);
            }

            loanRestructureData = loanRestructureDataDao.save(loanRestructureData);

            log.info("Completed asynchronous processing of loan restructure at BP for applicationId: {}, lan: {}, requestId: {}",
                    loanRestructureData.getApplicationId(), loanRestructureData.getLan(), loanRestructureData.getRequestId());
        } catch (Exception e) {
            log.error("Error while processing loan restructure asynchronously for applicationId: {}, lan: {}, requestId: {}. Error: {} Stack: {}",
                    loanRestructureData.getApplicationId(), loanRestructureData.getLan(), loanRestructureData.getRequestId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
            loanRestructureData.setStatus(PENDING);
            loanRestructureData.setRemarks(GENERIC_ERROR_REMARK);
            loanRestructureDataDao.save(loanRestructureData);
        }
    }

    private LoanRestructureData getLoanRestructureStatusResponseFromLender(LoanRestructureData loanRestructureData) {
        log.info("Getting loan restructure status response for merchantId: {}, applicationId: {}, lan: {}, requestId: {}",
                loanRestructureData.getMerchantId(), loanRestructureData.getApplicationId(),
                loanRestructureData.getLan(), loanRestructureData.getRequestId());
        try {
            NBFCResponseDTO nbfcResponseDTO = invokeApiCallWithLender(loanRestructureData, RESTRUCTURE_STATUS);

            if (nbfcResponseDTO == null) {
                log.error("No response received from lender for loan restructure for applicationId: {}, lan: {}, requestId: {}",
                        loanRestructureData.getApplicationId(), loanRestructureData.getLan(), loanRestructureData.getRequestId());
                loanRestructureData.setStatus(PENDING);
                return loanRestructureData;
            }

            if (nbfcResponseDTO != null && nbfcResponseDTO.getSuccess() && nbfcResponseDTO.getData() != null) {
                // Update the loanRestructureData based on the response from the lender
                log.info("Loan restructure approved by lender for applicationId: {}, lan: {}, requestId: {}",
                        loanRestructureData.getApplicationId(), loanRestructureData.getLan(), loanRestructureData.getRequestId());
                TLLoanRestructureResponseDto response = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDTO.getData()), TLLoanRestructureResponseDto.class);
                if ("SUCCESS".equalsIgnoreCase(response.getStatus())) {
                    loanRestructureData.setStatus(SUCCESS_AT_LENDER);
                } else if ("NOT_TRIGGERED".equalsIgnoreCase(response.getStatus())) {
                    log.info("Loan restructure failed as per lender response for applicationId: {}, lan: {}, requestId: {}. Response status: {}",
                            loanRestructureData.getApplicationId(), loanRestructureData.getLan(), loanRestructureData.getRequestId(), response.getStatus());
                    loanRestructureData.setStatus(PENDING);
                } else {
                    log.info("Loan restructure rejected by lender for applicationId: {}, lan: {}, requestId: {}. Response status: {}",
                            loanRestructureData.getApplicationId(), loanRestructureData.getLan(), loanRestructureData.getRequestId(), response.getStatus());
                    loanRestructureData.setStatus(FAILED);
                }
            } else {
                log.info("Loan restructure failed or rejected by lender for applicationId: {}, lan: {}, requestId: {}",
                        loanRestructureData.getApplicationId(), loanRestructureData.getLan(), loanRestructureData.getRequestId());
                loanRestructureData.setStatus(FAILED);
            }
        } catch (Exception e) {
            log.error("Error while converting loan restructure response to JSON string: {} Stack: {}", e.getMessage(), Arrays.asList(e.getStackTrace()));
            loanRestructureData.setStatus(PENDING);
        }
        return loanRestructureData;
    }

}
