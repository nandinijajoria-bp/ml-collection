package com.bharatpe.lending.service.helper;

import com.bharatpe.lending.common.dao.PenalChargesDao;
import com.bharatpe.lending.common.dao.SettlementDetailsDao;
import com.bharatpe.lending.common.entity.PenalCharges;
import com.bharatpe.lending.common.entity.SettlementDetails;
import com.bharatpe.lending.common.query.dao.AutoPayUPISlaveDao;
import com.bharatpe.lending.common.query.dao.LendingApplicationLenderDetailsDaoSlave;
import com.bharatpe.lending.common.query.dao.LendingEDIScheduleQueryDao;
import com.bharatpe.lending.common.query.dao.LendingLedgerSlaveDao;
import com.bharatpe.lending.common.query.dao.LendingPrePaymentSlaveDao;
import com.bharatpe.lending.common.query.dao.LendingPullPaymentDaoSlave;
import com.bharatpe.lending.common.query.dao.LoanDpdDaoSlave;
import com.bharatpe.lending.common.query.entity.AutoPayUPISlave;
import com.bharatpe.lending.common.query.entity.LendingApplicationLenderDetailsSlave;
import com.bharatpe.lending.common.query.entity.LendingEDIScheduleQuery;
import com.bharatpe.lending.common.query.entity.LendingLedgerSlave;
import com.bharatpe.lending.common.query.entity.LendingPaymentScheduleSlave;
import com.bharatpe.lending.common.query.entity.LendingPrepaymentSlave;
import com.bharatpe.lending.common.query.entity.LendingPullPaymentSlave;
import com.bharatpe.lending.common.query.entity.LoanDpdSlave;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.dto.LendingMerchantLoansResponseDTO;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.enums.LoanStatus;
import com.bharatpe.lending.loanV2.service.ExcessNachService;
import com.bharatpe.lending.service.APIGatewayService;
import com.bharatpe.lending.util.LoanUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import javax.validation.constraints.NotNull;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.bharatpe.lending.enums.LoanStatus.ACTIVE;
import static com.bharatpe.lending.enums.LoanStatus.DECEASED;
import static com.bharatpe.lending.enums.SettlementDetailsStatus.INIT;

@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantLoanServiceHelper {
    private final LendingLedgerSlaveDao lendingLedgerSlaveDao;
    private final LendingEDIScheduleQueryDao lendingEDIScheduleQueryDao;
    private final LendingPrePaymentSlaveDao lendingPrePaymentSlaveDao;
    private final PenalChargesDao penalChargesDao;
    private final ExcessNachService excessNachService;
    private final LendingPullPaymentDaoSlave lendingPullPaymentDaoSlave;
    private final AutoPayUPISlaveDao autoPayUPISlaveDao;
    private final APIGatewayService apiGatewayService;
    private final EasyLoanUtil easyLoanUtil;
    private final LoanDpdDaoSlave loanDpdDaoSlave;
    private final SettlementDetailsDao settlemetDetailsDao;
    private final LoanUtil loanUtil;
    private final LendingApplicationLenderDetailsDaoSlave lendingApplicationLenderDetailsDaoSlave;

    public void prepareAllLoanDetails(Long merchantId, LendingMerchantLoansResponseDTO responseDTO, List<Long> loanIds) {
        if(Objects.isNull(merchantId) || CollectionUtils.isEmpty(loanIds)
                || Objects.isNull(responseDTO) || CollectionUtils.isEmpty(responseDTO.getLoans())) {
            log.error("Got null merchantId or empty merchantLoans or null responseDTO. inputs: merchantId={}, loansIds={}, responseDTO={}"
                    , merchantId, loanIds, responseDTO);
            return;
        }
        Map<Long, LendingLedgerSlave> ledgersByLoanId = lendingLedgerSlaveDao.findLastPaymentEntriesByMerchantAndLoans(merchantId, loanIds).stream()
                .collect(Collectors.toMap(LendingLedgerSlave::getLoanId, Function.identity(), (first, second) -> first));

        Map<Long, LendingEDIScheduleQuery> ediSchedulesByLoanId = lendingEDIScheduleQueryDao.getLatestByLoanIds(loanIds).stream()
                .collect(Collectors.toMap(LendingEDIScheduleQuery::getLoanId, Function.identity(), (first, second) -> first));

        Map<Long, LendingPrepaymentSlave> prepaymentsByLoanId = lendingPrePaymentSlaveDao.findByMerchantIdAndLoanIds(merchantId, loanIds).stream()
                .collect(Collectors.toMap(LendingPrepaymentSlave::getLoanId, Function.identity(), (first, second) -> first));

        Map<Long, PenalCharges> penalChargesByLoanId = penalChargesDao.findByLoanIdIn(loanIds).stream()
                .collect(Collectors.toMap(PenalCharges::getLoanId, Function.identity(), (first, second) -> first));

        for (LendingMerchantLoansResponseDTO.Loan loan : responseDTO.getLoans()) {
            if(ACTIVE.name().equals(loan.getStatus()) || DECEASED.name().equals(loan.getStatus())) {
                LendingLedgerSlave lastEdiCreated = lendingLedgerSlaveDao.findLastEDIDueEntryByMerchantAndLoan(merchantId, loan.getLoanId());
                if (!ObjectUtils.isEmpty(lastEdiCreated)) {
                    LocalDate lastEdiDate = LocalDate.parse(new SimpleDateFormat("yyyy-MM-dd").format(lastEdiCreated.getCreatedAt()));
                    loan.setTodayEdi(lastEdiDate.equals(LocalDate.now()) ? Math.abs(lastEdiCreated.getAmount()) : 0);
                }
                if (!ObjectUtils.isEmpty(loan.getDueAmount()) && !ObjectUtils.isEmpty(loan.getTodayEdi())) {
                    if (loan.getDueAmount() > loan.getTodayEdi()) {
                        loan.setPendingEdi(ObjectUtils.isEmpty(lastEdiCreated) ? 0 : loan.getDueAmount() - Math.abs(loan.getTodayEdi()));
                    } else {
                        loan.setPendingEdi(0D);
                    }
                }
                Double excessCollectionBalance = excessNachService.getExcessCollectionBalanceAmount(merchantId, loan.getLoanId());
                if (excessCollectionBalance == null) excessCollectionBalance = 0.0;

                loan.setTotalDue(loan.getDueAmount() + loan.getDuePenalty());
                loan.setTotalExcessBalance(Math.min(excessCollectionBalance, loan.getTotalDue()));
                loan.setNetPayable(Math.max(loan.getTotalDue() - loan.getTotalExcessBalance(), 0));
            }
            loan.setDpd(LoanUtil.calculateDPD(loan.getEdiAmount(), loan.getDueAmount()));

            Optional<LendingLedgerSlave> lendingLedger = Optional.ofNullable(ledgersByLoanId.get(loan.getLoanId()));
            loan.setLastEdiPaid(lendingLedger.map(LendingLedgerSlave::getAmount).orElse(0D));
            LendingEDIScheduleQuery lendingEDISchedule = ediSchedulesByLoanId.get(loan.getLoanId());

            loan.setShowCustomAmount(lendingEDISchedule != null);

            LendingPrepaymentSlave lendingPrepayment = prepaymentsByLoanId.get(loan.getLoanId());
            setLoanPrepaymentData(loan, lendingPrepayment);

            PenalCharges penalCharges = penalChargesByLoanId.get(loan.getLoanId());
            loan.setDuePenalty(Objects.nonNull(penalCharges) ? penalCharges.getDuePenalty() : loan.getDuePenalty());
            loan.setNachBounceAmount(Objects.nonNull(penalCharges) ? penalCharges.getDueNachBounce() : 0);

            if (LoanStatus.ACTIVE.name().equals(loan.getStatus())) {
                log.info("active loan application id is {}", loan.getApplicationId());
                LendingPullPaymentSlave pullPayment = lendingPullPaymentDaoSlave.findTop1ByMerchantIdAndModeOrderByIdDesc(merchantId, "AUTOPAYUPI");
                setLoanPresentmentData(loan, pullPayment);

                Optional<AutoPayUPISlave> autoPayUPI = autoPayUPISlaveDao.findTop1ByMerchantIdAndApplicationIdOrderByIdDesc(merchantId, loan.getApplicationId());
                if (autoPayUPI.isPresent()) {
                    loan.setAutoPayMandateStatus(autoPayUPI.get().getStatus());
                    loan.setMandateRegisterId(autoPayUPI.get().getOrderId());
                }

                if(easyLoanUtil.percentScaleUp(merchantId, apiGatewayService.getUpiPercent())
                        && Lender.LDC.name().equalsIgnoreCase(loan.getLender())){
                    Optional<LoanDpdSlave> loanDpd = loanDpdDaoSlave.findTop1ByLoanIdOrderByIdDesc(loan.getLoanId());
                    loan.setAutoPayEligibility(loanDpd.map(dpd -> dpd.getDpd() < 3 && dpd.getDpd() != 0).orElse(Boolean.FALSE));
                }
                if (loan.isSettlementInitiated()) {
                    SettlementDetails settlementDetails = settlemetDetailsDao.findByLoanIdAndStatus(loan.getLoanId(), INIT.name());
                    if(Objects.nonNull(settlementDetails)){
                        loan.setSettlementAmountOffer(settlementDetails.getSettlementAmountOffer());
                        loan.setSettlementExpiryDate(settlementDetails.getSettlementExpiryDate());
                    }
                }
            }
            LendingApplicationLenderDetailsSlave lendingApplicationLenderDetailsSlave = lendingApplicationLenderDetailsDaoSlave.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(loan.getApplicationId(),"ACTIVE",loan.getLender());
            if(!ObjectUtils.isEmpty(lendingApplicationLenderDetailsSlave)){
                loan.setAnnualRoi(lendingApplicationLenderDetailsSlave.getAnnualRoi());
            }
            Double nachBounce = loanUtil.getNachBounceAmountConfig(lendingApplicationLenderDetailsSlave);
            responseDTO.setConfigNachBounceAmount(nachBounce);
        }
    }

    public void setLoanPrepaymentData(@NotNull LendingMerchantLoansResponseDTO.Loan loan, LendingPrepaymentSlave lendingPrepayment) {
        log.info("setting loan prepayment data for loan: {}, lendingPrepayment: {}", loan, lendingPrepayment);
        if(Objects.isNull(loan)){
            return;
        }
        double advanceEdiAmount = lendingPrepayment != null && lendingPrepayment.getAdvanceEdiAmount() != null ? lendingPrepayment.getAdvanceEdiAmount() : 0d;
        loan.setPaidAmount((ObjectUtils.isEmpty(loan.getPaidAmount()) ? 0 : loan.getPaidAmount()) + advanceEdiAmount);
        loan.setPendingAmount((ObjectUtils.isEmpty(loan.getPendingAmount()) ? 0 : loan.getPendingAmount()) - advanceEdiAmount);
        loan.setPaidPrinciple((ObjectUtils.isEmpty(loan.getPaidPrinciple()) ? 0 : loan.getPaidPrinciple()) + advanceEdiAmount);
        loan.setEdiDays(loan.getEdiCount() % 30 == 0 ? 7 : 6);
    }

    public void setLoanPresentmentData(LendingMerchantLoansResponseDTO.Loan loan, LendingPullPaymentSlave pullPayment) {
        log.info("setting loan presentment data for loan: {}, pullPayment: {}", loan, pullPayment);
        if(Objects.isNull(loan) || Objects.isNull(pullPayment)){
            return;
        }
        Double amount = pullPayment.getDeductedAmount();
        String status = pullPayment.getStatus();
        loan.setPresentmentStatus(status);
        loan.setPresentmentAmount(amount);
        loan.setPresentmentDate(pullPayment.getUpdatedAt());
    }

}
