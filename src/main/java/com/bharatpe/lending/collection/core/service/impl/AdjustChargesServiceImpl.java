package com.bharatpe.lending.collection.core.service.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.collection.core.dto.internal.PaymentCalculation;
import com.bharatpe.lending.common.dao.PenaltyFeeLedgerDao;
import com.bharatpe.lending.common.entity.PenaltyFeeLedger;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.common.util.LenderConfigUtil;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.loanV3.services.associationsV2.creditsaison.impl.CreditSaisonPostChargeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import static com.bharatpe.lending.constant.LendingConfigKeys.CHARGES_APPORTIONMENT_ENABLED_LENDERS;
import static com.bharatpe.lending.enums.Lender.CREDITSAISON;

@Service
@Slf4j
public class AdjustChargesServiceImpl {

    Map<String , Integer> chargesDescPriorityMap = new HashMap<String , Integer>() {{
        put("Nach Bounce", 1);
        put("Penalty Fee", 2);
    }};
    @Autowired
    private PenaltyFeeLedgerDao penaltyFeeLedgerDao;

    @Autowired
    private LendingApplicationDao lendingApplicationDao;

    @Autowired
    private LenderConfigUtil lenderConfigUtil;

    @Autowired
    private CreditSaisonPostChargeService creditSaisonPostChargeService;

    @Value("${creditsaison.charges.rollout.date:-}")
    private String creditSaisonChargesRolloutDate;

    public boolean isChargesApportionmentEnabled(LendingPaymentSchedule activeLoan) {

        String  dateStr = lenderConfigUtil.getLenderConfig(activeLoan.getNbfc(), CHARGES_APPORTIONMENT_ENABLED_LENDERS);

        log.info("In isChargesApportionmentEnabled with loanId: {}, nbfc: {}, dateStr: {}", activeLoan.getId(), activeLoan.getNbfc(), dateStr);
        if ("null".equals(dateStr)) {
            return Boolean.FALSE;
        }
        try {
            Date thresholdDate = getDateFromStr(dateStr);
            LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchantId(activeLoan.getApplicationId(), activeLoan.getMerchantId());

            Date agreementAt = lendingApplication.getAgreementAt();
            if (agreementAt != null && thresholdDate != null && agreementAt.after(thresholdDate)) {
                return Boolean.TRUE;
            }
        } catch (Exception e) {
            log.info("Exception occurred in isChargesApportionmentEnabled dateStr: {}, nbfc: {}, msg: {}, Stack: {}",
                    dateStr, activeLoan.getNbfc(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }

        return Boolean.FALSE;
    }

    public PaymentCalculation checkAndAdjustChargesApportionment(LendingPaymentSchedule activeLoan, double penaltyPaid) {
        log.info("In checkAndAdjustChargesApportionment!!");
        try {
            if (isChargesApportionmentEnabled(activeLoan)) {
                return adjustChargesApportionment(activeLoan, penaltyPaid);
            }
        } catch (Exception e) {
            log.error("Exception occurred while charges Apportionment for loanID: {} with error msg: {} , Stack: {}",
                    activeLoan.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return PaymentCalculation.builder()
                .isChargeApportioned(Boolean.FALSE)
                .nachBounceSettled(0)
                .penalChargesSettled(0)
                .build();
    }

    public PaymentCalculation adjustChargesApportionment(LendingPaymentSchedule activeLoan, double paidPenaltyAmt) {
        log.info("In adjustChargesApportionment for loanID: {}, paidPenaltyAmt: {}", activeLoan.getId(), paidPenaltyAmt);

        double nachBounceAmt = 0;
        double penalChargesAmt = 0;
        PaymentCalculation paymentCalculation = PaymentCalculation.builder()
                .isChargeApportioned(Boolean.TRUE)
                .nachBounceSettled(nachBounceAmt)
                .penalChargesSettled(penalChargesAmt)
                .build();

        if (paidPenaltyAmt <= 0) return paymentCalculation;

        List<PenaltyFeeLedger> penaltyFeeLedgerList = getSortedPenaltyFeeLedger(activeLoan);

        if (CollectionUtils.isEmpty(penaltyFeeLedgerList)) return paymentCalculation;

        Map<PenaltyFeeLedger, Double> paidNachBounceMap = new HashMap<>();
        Map<PenaltyFeeLedger, Double> paidPenalChargeMap = new HashMap<>();

        for (PenaltyFeeLedger penaltyFeeLedger: penaltyFeeLedgerList) {
            if (paidPenaltyAmt <= 0) break;

            double paidTillDate = Objects.isNull(penaltyFeeLedger.getPaidAmount()) ? 0 : penaltyFeeLedger.getPaidAmount();
            double dueAmount = Math.abs(penaltyFeeLedger.getAmount());
            double dueTillDate = dueAmount - paidTillDate;
            double adjustedAmount = Math.min(dueTillDate, paidPenaltyAmt);
            double finalPaidAmt = paidTillDate + adjustedAmount;

            penaltyFeeLedger.setPaidAmount(finalPaidAmt);
            paidPenaltyAmt -= adjustedAmount;

            if ("Nach Bounce".equals(penaltyFeeLedger.getDescription())) {
                paidNachBounceMap.put(penaltyFeeLedger, adjustedAmount);
                nachBounceAmt += adjustedAmount;
            }
            if ("Penalty Fee".equals(penaltyFeeLedger.getDescription())) {
                paidPenalChargeMap.put(penaltyFeeLedger, adjustedAmount);
                penalChargesAmt += adjustedAmount;
            }
        }
        checkAndPostCharges(activeLoan, paidNachBounceMap, paidPenalChargeMap);
        penaltyFeeLedgerDao.saveAll(penaltyFeeLedgerList);

        return PaymentCalculation.builder()
                .nachBounceSettled(nachBounceAmt)
                .penalChargesSettled(penalChargesAmt)
                .isChargeApportioned(Boolean.TRUE)
                .build();
    }

    private void checkAndPostCharges(LendingPaymentSchedule activeLoan, Map<PenaltyFeeLedger, Double> paidNachBounceMap,
                                     Map<PenaltyFeeLedger, Double> paidPenalChargeMap) {
        log.info("In checkAndPostCharges for loanID: {}, paidNachBounceMap: {}, paidPenalChargeMap: {}",
                activeLoan.getId(), paidNachBounceMap, paidPenalChargeMap);
        if (isChargesPostingRequiredCreditSaison(activeLoan)) {
            postChargesForCreditSaison(activeLoan, paidNachBounceMap, paidPenalChargeMap);
        }
    }

    private void postChargesForCreditSaison(LendingPaymentSchedule activeLoan, Map<PenaltyFeeLedger, Double> paidNachBounceMap,
                                            Map<PenaltyFeeLedger, Double> paidPenalChargeMap) {
        creditSaisonPostChargeService.postPendingChargesToLender(activeLoan, paidNachBounceMap, paidPenalChargeMap);
    }

    private List<PenaltyFeeLedger> getSortedPenaltyFeeLedger(LendingPaymentSchedule activeLoan) {

        List<PenaltyFeeLedger> penaltyFeeLedgerList = penaltyFeeLedgerDao.findUnPaidPenaltyFeeLedgers(activeLoan.getId());

        if (CollectionUtils.isEmpty(penaltyFeeLedgerList)) {
            log.info("No unpaid penalty list found for loanID: {}", activeLoan.getId());
            return Collections.emptyList();
        }

        penaltyFeeLedgerList.sort((p1, p2) -> {
            LocalDate date1 = p1.getCreatedAt().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            LocalDate date2 = p2.getCreatedAt().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();

            int dateCompare = date1.compareTo(date2);
            if (dateCompare != 0) {
                return dateCompare;
            }

            // Same day → compare by description priority
            int priority1 = chargesDescPriorityMap.getOrDefault(p1.getDescription(), Integer.MAX_VALUE);
            int priority2 = chargesDescPriorityMap.getOrDefault(p2.getDescription(), Integer.MAX_VALUE);
            return Integer.compare(priority1, priority2);
        });
        return penaltyFeeLedgerList;
    }


    // For Credit Saison
    public boolean isChargesPostingRequiredCreditSaison(LendingPaymentSchedule activeLoan) {
        LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchantId(activeLoan.getApplicationId(), activeLoan.getMerchantId());

        if(ObjectUtils.isEmpty(lendingApplication)){
            log.error("Lending application not found for loan: {}", activeLoan.getId());
            return Boolean.FALSE;
        }
        if (CREDITSAISON.name().equals(activeLoan.getNbfc()) && checkForChargesRolloutDate(lendingApplication.getAgreementAt())) {
            LocalDate tentativeClosingDate = activeLoan.getTentativeClosingDate().toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate();
            LocalDate currentDate = LocalDate.now();

            if (currentDate.isBefore(tentativeClosingDate)) {
                log.info("Posting is required for LoanId: {}, tentativeClosingDate: {}", activeLoan.getId(), tentativeClosingDate);
                return Boolean.TRUE;
            }
        }
        return Boolean.FALSE;
    }

    private Date getDateFromStr(String dateStr) {
        return DateTimeUtil.parseDate(dateStr, "yyyy-MM-dd HH:mm:ss");
    }
    private boolean checkForChargesRolloutDate(Date agreementAt) {
        Date thresholdDate = getDateFromStr(creditSaisonChargesRolloutDate);
        if (agreementAt != null && thresholdDate != null && agreementAt.after(thresholdDate)) {
            return Boolean.TRUE;
        }
        return Boolean.FALSE;
    }
}
