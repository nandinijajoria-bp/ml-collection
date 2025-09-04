package com.bharatpe.lending.service;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.dao.PenalChargesDao;
import com.bharatpe.lending.common.dao.PenaltyFeeLedgerDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.entity.PenalCharges;
import com.bharatpe.lending.common.entity.PenaltyFeeLedger;
import com.bharatpe.lending.common.enums.LendingEnum;
import com.bharatpe.lending.common.query.dao.LendingPullPaymentDaoSlave;
import com.bharatpe.lending.common.query.entity.LendingPullPaymentSlave;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingLedgerDao;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.util.LoanUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.bharatpe.lending.loanV3.enums.piramal.PaymentTypePiramal.NACH_BOUNCE_CHARGES;

@Service
@Slf4j
public class NachBounceChargesService {
    @Autowired
    PenalChargesDao penalChargesDao;

    @Autowired
    LendingPullPaymentDaoSlave lendingPullPaymentDaoSlave;

    @Autowired
    PenaltyFeeLedgerDao penaltyFeeLedgerDao;

    @Autowired
    LendingLedgerDao lendingLedgerDao;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LoanUtil loanUtil;

    @Value("${payu.nach.bounce.charge:500}")
    Integer payUNachBounceCharge;

    @Value("${piramal.nach.bounce.charge:500}")
    Integer piramalNachBounceCharge;

    @Value("${credit.saison.nach.bounce.charge:500}")
    Integer creditSaisonNachBounceCharge;

    @Value("${lender.nach.bounce.check:PAYU,PIRAMAL,CREDITSAISON}")
    List<String> LENDER_NACH_BOUNCE_CHECK;

    @Value("${flat.overdue.penalty.piramal.rollout.date:-}")
    String faltOverDuePenaltyRolloutDate;

    @Value("${dpd.penalty.payu.rollout.date:-}")
    String dpdPenaltyPayURolloutDate;

    @Value("${creditsaison.charges.rollout.date:-}")
    private String creditSaisonChargesRolloutDate;

    @Value("${dpd.penalty.ugro.rollout.date}")
    String dpdPenaltyUgroLoansRolloutDate;

    @Value("${ugro.nach.bounce.charge:500}")
    Integer ugroNachBounceCharge;

    public Double getNachCharges(LendingPaymentSchedule lendingPaymentSchedule) {
        Double nachCharges = 0d;
        boolean isNachBounceApplicable = checkNachBounceCharges(lendingPaymentSchedule);
        if (LENDER_NACH_BOUNCE_CHECK.contains(lendingPaymentSchedule.getNbfc()) && checkForNachBounce(lendingPaymentSchedule) && isNachBounceApplicable) {
            log.info("Found Nach Bounce Charges for loan: {}, nbfc: {} ", lendingPaymentSchedule.getId(), lendingPaymentSchedule.getNbfc());
            nachCharges += getLenderNachCharges(lendingPaymentSchedule.getNbfc());
        }
        return nachCharges;
    }

    private boolean checkNachBounceCharges(LendingPaymentSchedule lendingPaymentSchedule) {
        try {
            log.info("Checking Nach Bounce Charges for loan: {}, nbfc: {} ", lendingPaymentSchedule.getId(), lendingPaymentSchedule.getNbfc());
            if (lendingPaymentSchedule != null && lendingPaymentSchedule.getApplicationId() != null) {
                Optional<LendingApplication> lendingApplicationOptional = lendingApplicationDao.findById(lendingPaymentSchedule.getApplicationId());
                log.info("Checking Nach Bounce Charges for loan: {}, lendingApplication: {} ", lendingPaymentSchedule.getId(), lendingApplicationOptional);
                if (lendingApplicationOptional == null) return false;
                LendingApplication lendingApplication = lendingApplicationOptional.get();
                Date thresholdDate = getPenaltyActivationDateFromProperty(lendingPaymentSchedule.getNbfc());
                if (thresholdDate != null && lendingApplication.getAgreementAt().after(thresholdDate)) return true;
            }
            return false;
        }catch (Exception e){
            log.error("Getting error while real time nach bounce charges for loan: {}, nbfc: {} error {} , {}", lendingPaymentSchedule, lendingPaymentSchedule, e.getMessage(),Arrays.asList(e.getStackTrace()));
            return false;
        }
    }

    private Date getPenaltyActivationDateFromProperty(String lender) {
        String rolloutDate = getLenderNachBounceRollOutDate(lender);
        log.info("Rollout date for lender {} is {}", lender, rolloutDate);
        if(rolloutDate == null) return null;
        return DateTimeUtil.parseDate(getLenderNachBounceRollOutDate(lender), "yyyy-MM-dd hh:mm:ss");
    }

    public String getLenderNachBounceRollOutDate(String lender) {
        switch (lender) {
            case "PIRAMAL":
                return faltOverDuePenaltyRolloutDate;

            case "PAYU":
                return dpdPenaltyPayURolloutDate;

            case "CREDITSAISON":
                return creditSaisonChargesRolloutDate;

            case"UGRO" :
                return dpdPenaltyUgroLoansRolloutDate;

            default:
              return null;
        }
    }

    private Integer getLenderNachCharges(String lender) {
        switch (lender) {
            case "PIRAMAL":
                return piramalNachBounceCharge;
            case "PAYU":
                return payUNachBounceCharge;
            case "CREDITSAISON":
                return creditSaisonNachBounceCharge;
            case "UGRO":
                return ugroNachBounceCharge;
            default:
                return null;
        }
    }

    public void createCharges(LendingPaymentSchedule loan, String requestId) {
        double nachBounceCharge = getLenderNachCharges(loan.getNbfc());
        checkIfPendingNachPenalty(loan, true);
        savePenalCharges(loan, 0.0, nachBounceCharge);
        creatingPenaltyInPenaltyLedger(loan, nachBounceCharge, "Nach Bounce", false, requestId);
        createLendingLedgerForPenalty(loan, nachBounceCharge, "NACH BOUNCE PENALTY FEE");
    }

    public Integer checkIfPendingNachPenalty(LendingPaymentSchedule loan, Boolean postCharge) {
        if(LendingEnum.LENDER.PIRAMAL.toString().equals(loan.getNbfc())){
            log.info("Checking unpaid penalty for loanId {} ", loan.getId());
            LendingApplicationLenderDetails lendingApplicationLenderDetails =
                    lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusOrderByIdDesc(loan.getApplicationId(), com.bharatpe.lending.common.enums.Status.ACTIVE.name());
            PenaltyFeeLedger nachBounceLedgerApplied = penaltyFeeLedgerDao.findTop1NachBounceOrderByIdDesc(loan.getId());
            if(lendingApplicationLenderDetails != null && nachBounceLedgerApplied != null && (nachBounceLedgerApplied.getIsPosted() == null || !nachBounceLedgerApplied.getIsPosted())) {
                log.info("Unpaid penalty exists for loanId {} ", loan.getId());
                if(postCharge){
                    loanUtil.piramalPenaltyPosting(lendingApplicationLenderDetails, nachBounceLedgerApplied,nachBounceLedgerApplied.getAmount()*-1,NACH_BOUNCE_CHARGES.name());
                }
                return getLenderNachCharges(loan.getNbfc());
            }
        }
        return 0;
    }

    private boolean checkForNachBounceV2(LendingPaymentSchedule activeLoan) {
        PenaltyFeeLedger penaltyFeeLedger = penaltyFeeLedgerDao.findTop1NachBounceOrderByIdDesc(activeLoan.getId());

        Date currentDateDate = new Date();
        Date startDate = (penaltyFeeLedger != null && penaltyFeeLedger.getCreatedAt() != null) ? penaltyFeeLedger.getCreatedAt() : activeLoan.getStartDate();
        LendingPullPaymentSlave lendingPullPaymentSlave = lendingPullPaymentDaoSlave.findByMerchantIdAndLoanIdAndModeAndDateBetweenAndStatus(activeLoan.getMerchantId(),
                activeLoan.getId(), "NACH",
                startDate, currentDateDate, "FAILED");
        log.info("Failed Nach Pull in remaining days for loan: {}: {}", activeLoan.getId(), lendingPullPaymentSlave);
        return !ObjectUtils.isEmpty(lendingPullPaymentSlave);
    }

    public boolean checkForNachBounce(LendingPaymentSchedule activeLoan) {
        List<String> eligibleLendersList = Arrays.asList(LendingEnum.LENDER.PIRAMAL.toString(), LendingEnum.LENDER.CREDITSAISON.toString());
        if(eligibleLendersList.contains(activeLoan.getNbfc())){
            return checkForNachBounceV2(activeLoan);
        }
        Date loanStartDate = activeLoan.getStartDate();

        Date currentDate = DateTimeUtil.getCurrentDayStartTime();
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.MINUTE, 0);

        long diffInDays = TimeUnit.DAYS.convert(Math.abs(currentDate.getTime() - loanStartDate.getTime()), TimeUnit.MILLISECONDS);

        int remainingDays = (int) (diffInDays % 30);
        log.info("No. of remaining days : {}, startDate {} , endDate {} ", remainingDays+1, DateTimeUtil.addDays(DateTimeUtil.getCurrentDayStartTime(), -(remainingDays)), Calendar.getInstance().getTime());

        Integer lastMonthNachPullCount = lendingPullPaymentDaoSlave.findFailedNachPullInLastMonth(activeLoan.getMerchantId(),
                activeLoan.getId(), "NACH", DateTimeUtil.addDays(DateTimeUtil.getCurrentDayStartTime(), -(remainingDays)),
                Calendar.getInstance().getTime(), "FAILED");
        log.info("No. of failed Nach Pull in remaining days for loan: {}: {}", activeLoan.getId(), lastMonthNachPullCount);
        return lastMonthNachPullCount > 0;
    }

    private void savePenalCharges(LendingPaymentSchedule lendingPaymentSchedule, double penaltyFee, double nachBounceCharge) {
        try {
            log.info("Saving Penal Charges for loan: {} with penaltyFee: {}, and nach bounce: {}", lendingPaymentSchedule.getId(), penaltyFee, nachBounceCharge);
            PenalCharges penalCharge = penalChargesDao.findByLoanId(lendingPaymentSchedule.getId());
            if (Objects.isNull(penalCharge)) {
                penalCharge = new PenalCharges(lendingPaymentSchedule.getId(), lendingPaymentSchedule.getMerchantId(),
                        lendingPaymentSchedule.getNbfc(), penaltyFee, 0, nachBounceCharge, 0);
            } else {
                penalCharge.setDuePenalty(penalCharge.getDuePenalty() + penaltyFee);
                penalCharge.setDueNachBounce(penalCharge.getDueNachBounce() + nachBounceCharge);
            }

            penalChargesDao.save(penalCharge);
        } catch (Exception e) {
            log.info("Exception occurred while saving penal charges for loan: {}, {}", lendingPaymentSchedule.getId(), e.getMessage(), e);
        }
    }

    private void creatingPenaltyInPenaltyLedger(LendingPaymentSchedule loan, double penaltyFee, String description, boolean isWaiveOff, String requestID) {
        PenaltyFeeLedger penaltyFeeLedger = PenaltyFeeLedger.builder()
                .chargeRequestId(requestID)
                .loanId(loan.getId())
                .merchantId(loan.getMerchantId())
                .amount((-1)*penaltyFee)
                .description(description)
                .isWaveOff(isWaiveOff)
                .lender(loan.getNbfc())
                .isPosted(false)
                .build();
        penaltyFeeLedgerDao.save(penaltyFeeLedger);
    }

    private LendingLedger createLendingLedgerForPenalty(LendingPaymentSchedule loan, double penaltyFee, String penaltyDescription) {
        if(ObjectUtils.isEmpty(penaltyFee)) {
            return null;
        }

        LendingLedger lendingLedger = new LendingLedger();
        lendingLedger.setMerchantId(loan.getMerchantId());
        if(loan.getMerchantStoreId() != null && loan.getMerchantStoreId() > 0) lendingLedger.setMerchantStoreId(loan.getMerchantStoreId());
        lendingLedger.setLendingPaymentSchedule(loan);
        lendingLedger.setDate(DateTimeUtil.getCurrentDayStartTime());
        lendingLedger.setTxnType("EDI");
        lendingLedger.setAmount(-penaltyFee);
        lendingLedger.setInterest(0d);
        lendingLedger.setPrinciple(0d);
        lendingLedger.setOtherCharges(0d);
        lendingLedger.setPenalty(-penaltyFee);
        lendingLedger.setDescription(penaltyDescription);
        lendingLedgerDao.save(lendingLedger);
        return lendingLedger;
    }
}
