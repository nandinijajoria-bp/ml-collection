package com.bharatpe.lending.service;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.dao.PenalChargesDao;
import com.bharatpe.lending.common.dao.PenaltyFeeLedgerDao;
import com.bharatpe.lending.common.entity.PenalCharges;
import com.bharatpe.lending.common.entity.PenaltyFeeLedger;
import com.bharatpe.lending.common.query.dao.LendingPullPaymentDaoSlave;
import com.bharatpe.lending.common.query.dao.PenaltyFeeConfigDaoSlave;
import com.bharatpe.lending.common.query.entity.LendingPullPaymentSlave;
import com.bharatpe.lending.common.query.entity.PenaltyFeeConfigSlave;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dto.PenaltyWaiverResponseDto;
import com.bharatpe.lending.dto.ResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.bharatpe.lending.common.enums.LendingEnum.LENDER.LIQUILOANS_P2P;
import static com.bharatpe.lending.common.enums.LendingEnum.LENDER.LIQUILOANS_P2P_OF;

@Component
public class PenaltyFeeService {
    private final Logger logger = LoggerFactory.getLogger(PenaltyFeeService.class);

    List<String> penaltyV1LenderList = Arrays.asList(LIQUILOANS_P2P.name(), LIQUILOANS_P2P_OF.name());

    @Value("${penalty.v1.eligible.lenders}")
    String penaltyV1EligibleLenders;

    @Value("${penalty.v2.rollout.date:}")
    String penaltyV2RolloutDate;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    PenaltyFeeConfigDaoSlave penaltyFeeConfigDaoSlave;

    @Autowired
    PenaltyFeeLedgerDao penaltyFeeLedgerDao;

    @Autowired
    LendingPullPaymentDaoSlave lendingPullPaymentDaoSlave;

    @Autowired
    PenalChargesDao penalChargesDao;

    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;


    public double createPenaltyFee(LendingPaymentSchedule activeLoan){
        logger.info("Creating Penalty Fee for loan: {}", activeLoan);

        LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchantId(activeLoan.getApplicationId(), activeLoan.getMerchantId());
        if(ObjectUtils.isEmpty(lendingApplication)){
            logger.info("Lending Application not found for this loan: {}", activeLoan.getId());
            return 0;
        }
        logger.info("Lending Application for loan: {}: {}", activeLoan.getId(), lendingApplication);

        int version = getPenaltyVersion(activeLoan.getNbfc(), lendingApplication.getAgreementAt());
        logger.info("Applicable Penalty Fee Version: {}", version);

        if(version == 1){
            return createPenaltyFeeV1(activeLoan);
        }
        return createPenaltyFeeV2(activeLoan, lendingApplication);
    }

    public double createPenaltyFeeV1(LendingPaymentSchedule loan){
        logger.info("Creating Penalty Fee V1 for loan: {}", loan);
        double penaltyFee = 0;
        try {
            double existingPenaltyAmount = Objects.nonNull(loan.getTotalPenaltyAmount()) ? loan.getTotalPenaltyAmount() : 0;
            int dpd = calculateDpd(loan.getDueAmount(), loan.getEdiAmount());
            logger.info("Calculated DPD for loan : {} : {}", loan.getApplicationId(), dpd);

            if (dpd > 6 && dpd < 120 && penaltyV1EligibleLenders.contains(loan.getNbfc())) {
                PenaltyFeeLedger penaltyFeeLedger = penaltyFeeLedgerDao.findTop1OrderByIdDesc(loan.getId());
                List<PenaltyFeeConfigSlave> penaltyFeeConfigList = penaltyFeeConfigDaoSlave.findDPDPenaltyChargeConfig(loan.getDueAmount(), loan.getNbfc());
                if (CollectionUtils.isEmpty(penaltyFeeConfigList)) {
                    logger.info("No Penalty Fee Config found for loan: {}, amount: {}, lender: {}", loan.getId(), loan.getDueAmount(), loan.getNbfc());
                    return penaltyFee;
                }

                // if there is specific config defined at level 1 that will be picked else default level 1 will be picked
                PenaltyFeeConfigSlave penaltyFeeConfigSlave = penaltyFeeConfigList.get(0);
                if (!Objects.isNull(penaltyFeeLedger) && getDateDiffInDays(penaltyFeeLedger.getCreatedAt(), new Date()) < 30) {
                    logger.info("Loan lies in less than 30 days count for next penalty: {}", loan.getApplicationId());
                    return loan.getDuePenalty();
                }

                penaltyFee = penaltyFeeConfigSlave.getPenalty();
                if (penaltyFee > 0) {
                    penaltyFeeLedger = new PenaltyFeeLedger(loan.getMerchantId(), loan.getId(), -penaltyFee,
                            "Penalty Fee", false, loan.getNbfc());
                    penaltyFeeLedgerDao.save(penaltyFeeLedger);
                }

                existingPenaltyAmount += penaltyFee;
                penaltyFee += Objects.nonNull(loan.getDuePenalty()) ? loan.getDuePenalty() : 0;
                loan.setDuePenalty(penaltyFee);
                loan.setTotalPenaltyAmount(existingPenaltyAmount);
                lendingPaymentScheduleDao.save(loan);
                return penaltyFee;
            }
        }
        catch (Exception e){
            logger.error("Error in creating Penalty Fee V1 for Loan: {}", loan.getId());
        }

        return penaltyFee;
    }


    public double createPenaltyFeeV2(LendingPaymentSchedule loan, LendingApplication lendingApplication){
        logger.info("Creating Penalty Fee V2 for loan: {}", loan);
        double penaltyFee = 0;
        double existingPenaltyAmount = Objects.nonNull(loan.getTotalPenaltyAmount()) ? loan.getTotalPenaltyAmount() : 0;
        double nachBouncePenaltyCharge = 650.0;
        try {
            Date thresholdDate = getPenaltyV2ThresholdDate();

            int overdueEdiCount = Objects.nonNull(loan.getOverdueEdiCount()) ? loan.getOverdueEdiCount() : 0;
            double lastOverDueAmount = Objects.nonNull(loan.getLastOverDueAmount()) ? loan.getLastOverDueAmount() : 0;
            double overdueAmount = Objects.nonNull(loan.getOverdueAmount()) ? loan.getOverdueAmount() : 0;

            overdueAmount = Math.max(overdueAmount, loan.getDueAmount() - lastOverDueAmount);
            overdueEdiCount += 1;

            if (lendingApplication.getAgreementAt().after(thresholdDate) && overdueEdiCount > 30) {
                logger.info("Applying Penalty Fee for loan: {} with overdueCount: {} with date: {}", loan.getId(), overdueEdiCount, lendingApplication.getAgreementAt());

                PenaltyFeeConfigSlave penaltyFeeConfigSlave = penaltyFeeConfigDaoSlave.findByDueAmountAndVersionAndStatusAndLender(loan.getOverdueAmount(), 2.0, loan.getNbfc());

                penaltyFee = penaltyFeeConfigSlave.getPenalty();

                if (penaltyFee > 0) {
                    creatingPenaltyInPenaltyLedger(loan, penaltyFee, "Penalty Fee", false);
                }

                logger.info("Total Penalty Fee after Penalty on Overdue Amount for loan: {}: {}", loan.getId(), penaltyFee);

                //For Nach Bounce Penalty
                boolean isNachBounce = checkForNachBounce(loan);
                savePenalCharges(loan, isNachBounce, penaltyFee, nachBouncePenaltyCharge);

                if (isNachBounce) {
                    penaltyFee += nachBouncePenaltyCharge;
                    creatingPenaltyInPenaltyLedger(loan, nachBouncePenaltyCharge, "Nach Bounce", false);
                }

                logger.info("Total Penalty Fee after Penalty on Nach Bounce for loan: {}: {}", loan.getId(), penaltyFee);

                //Resetting everything to 0, starting 30 days cycle again
                lastOverDueAmount = overdueAmount;
                overdueEdiCount = 0;
                overdueAmount = 0;

                existingPenaltyAmount += penaltyFee;

                penaltyFee += Objects.nonNull(loan.getDuePenalty()) ? loan.getDuePenalty() : 0;

                loan.setDuePenalty(penaltyFee);
                loan.setTotalPenaltyAmount(existingPenaltyAmount);
                loan.setLastOverDueAmount(lastOverDueAmount);

            }
            loan.setOverdueAmount(overdueAmount);
            loan.setOverdueEdiCount(overdueEdiCount);
            lendingPaymentScheduleDao.save(loan);
        }
        catch (Exception e){
            logger.error("Error in Create Penalty Fee V2 for loan: {}: {}", loan.getId(), e.getMessage(), e);
        }
        return penaltyFee;
    }

    private void creatingPenaltyInPenaltyLedger(LendingPaymentSchedule loan, double penaltyFee, String description, boolean isWaiveOff) {
        PenaltyFeeLedger penaltyFeeLedger = new PenaltyFeeLedger(loan.getMerchantId(), loan.getId(), -penaltyFee, description, isWaiveOff, loan.getNbfc());
        penaltyFeeLedgerDao.save(penaltyFeeLedger);
    }

    public ResponseDTO applyPenaltyWaiver(Long applicationId, Double amount){
        PenaltyWaiverResponseDto penaltyWaiverResponseDto = new PenaltyWaiverResponseDto();
        try {
            LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.findByApplicationId(applicationId);
            if (ObjectUtils.isEmpty(lendingPaymentSchedule)) {
                logger.info("No data received from loan for applicationId : {}  ", applicationId);
                penaltyWaiverResponseDto.setMessage("No data found");
                return new ResponseDTO(false, "No data found", penaltyWaiverResponseDto);
            }
            PenalCharges penalCharges = penalChargesDao.findByLoanId(lendingPaymentSchedule.getId());
            logger.info("Penal charges for applicationId: {} and amount: {}: {}", applicationId, amount, penalCharges);
            if ((!ObjectUtils.isEmpty(penalCharges) && penalCharges.getDuePenalty() < amount) || lendingPaymentSchedule.getDuePenalty() <= 0D || lendingPaymentSchedule.getDuePenalty() < amount) {
                logger.info("Due Penalty not exist/Due Penalty is lesser than the amount for applicationId : {}", applicationId);
                penaltyWaiverResponseDto.setMessage("Amount is greater than due penalty");

                if (!ObjectUtils.isEmpty(penalCharges)) {
                    penaltyWaiverResponseDto.setDuePenalty(penalCharges.getDuePenalty());
                } else {
                    penaltyWaiverResponseDto.setDuePenalty(lendingPaymentSchedule.getDuePenalty());
                }

                penaltyWaiverResponseDto.setTotalPenalty(lendingPaymentSchedule.getTotalPenaltyAmount());
                penaltyWaiverResponseDto.setDueAmount(lendingPaymentSchedule.getDueAmount());
                return new ResponseDTO(false, "Amount is greater than due penalty", penaltyWaiverResponseDto);
            }

            PenaltyFeeLedger penaltyFeeLedger = new PenaltyFeeLedger(lendingPaymentSchedule.getMerchantId(), lendingPaymentSchedule.getId(), amount, "WAIVE_OFF", true, lendingPaymentSchedule.getNbfc());

            Double duePenalty = lendingPaymentSchedule.getDuePenalty();
            Double dueAmount = lendingPaymentSchedule.getDueAmount();
            lendingPaymentSchedule.setDuePenalty(duePenalty - amount);
            lendingPaymentSchedule.setDueAmount(dueAmount - amount);
            if (!ObjectUtils.isEmpty(penalCharges)) {
                penalCharges.setDuePenalty(penalCharges.getDuePenalty() - amount);
            }
            updateDuePenaltyFieldsInDb(lendingPaymentSchedule, penaltyFeeLedger, penalCharges);

            if (!ObjectUtils.isEmpty(penalCharges)) {
                penaltyWaiverResponseDto.setDuePenalty(penalCharges.getDuePenalty());
            } else {
                penaltyWaiverResponseDto.setDuePenalty(lendingPaymentSchedule.getDuePenalty());
            }
            penaltyWaiverResponseDto.setTotalPenalty(lendingPaymentSchedule.getTotalPenaltyAmount());
            penaltyWaiverResponseDto.setDueAmount(lendingPaymentSchedule.getDueAmount());
            penaltyWaiverResponseDto.setMessage("Penalty fee waiver updated successfully");

            return new ResponseDTO(true, "Penalty fee waiver updated successfully", penaltyWaiverResponseDto);
        }
        catch (Exception e){
            logger.error("Exception occurred in Penalty Fee Waive Off for lending application: {}: {}", applicationId, e.getMessage(), e);
        }
        return new ResponseDTO(false, "500","Internal Server Error");
    }

    public void updateDuePenaltyFieldsInDb(LendingPaymentSchedule lendingPaymentSchedule, PenaltyFeeLedger penaltyFeeLedger, PenalCharges penalCharges) {
        logger.info("Going to update db for penalty waiver fee for loan: {}", lendingPaymentSchedule.getId());

        if(!ObjectUtils.isEmpty(lendingPaymentSchedule)) {
            logger.info("Saving Due Penalty in Lending Payment Schedule: {}", lendingPaymentSchedule);
            lendingPaymentScheduleDao.save(lendingPaymentSchedule);
        }
        if(!ObjectUtils.isEmpty(penaltyFeeLedger)) {
            logger.info("Saving Due Penalty in Penalty Fee Ledger: {}", penaltyFeeLedger);
            penaltyFeeLedgerDao.save(penaltyFeeLedger);
        }
        if(!ObjectUtils.isEmpty(penalCharges)) {
            logger.info("Saving Due Penalty in Penal Charges: {}", penalCharges);
            penalChargesDao.save(penalCharges);
        }
    }

    private int getPenaltyVersion(String nbfc, Date agreementAt) {
        int version = 2;
        try {
            Date thresholdDate = getPenaltyV2ThresholdDate();
            if (penaltyV1LenderList.contains(nbfc) && !ObjectUtils.isEmpty(agreementAt) && !ObjectUtils.isEmpty(thresholdDate) && !agreementAt.after(thresholdDate)) {
                version = 1;
            }
        }
        catch (Exception e){
            logger.error("Error in getPenaltyVersion for Nbfc: {}, greementAt: {}: {}", nbfc, agreementAt, e.getMessage(), e);
        }
        return version;
    }

    private Date getPenaltyV2ThresholdDate() {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
            return sdf.parse(penaltyV2RolloutDate);
        }
        catch (Exception e){
            logger.error("Error in getPenaltyV2ThresholdDate : {}", e.getMessage(), e);
        }
        return null;
    }

    private boolean checkForNachBounce(LendingPaymentSchedule activeLoan) {
        LendingPullPaymentSlave lendingPullPaymentSlave = lendingPullPaymentDaoSlave.findByMerchantIdAndLoanIdAndModeAndDateBetweenAndStatus(activeLoan.getMerchantId(),
                activeLoan.getId(), "NACH",
                DateTimeUtil.addDays(activeLoan.getUpdatedAt(), -30), activeLoan.getUpdatedAt(), "FAILED");

        return !ObjectUtils.isEmpty(lendingPullPaymentSlave);
    }

    private void savePenalCharges(LendingPaymentSchedule lendingPaymentSchedule, boolean isNachBounce, double penaltyFee, double nachBounceCharge) {
        try {
            logger.info("Saving Penal Charges for loan: {} with penaltyFee: {}, is nach bounce: {}", lendingPaymentSchedule.getId(), penaltyFee, isNachBounce);
            PenalCharges penalCharge = penalChargesDao.findByLoanId(lendingPaymentSchedule.getId());
            double nachCharge = isNachBounce ? nachBounceCharge : 0;
            if (Objects.isNull(penalCharge)) {
                penalCharge = new PenalCharges(lendingPaymentSchedule.getId(), lendingPaymentSchedule.getMerchantId(),
                        lendingPaymentSchedule.getNbfc(), penaltyFee, 0, nachCharge, 0);
            } else {
                penalCharge.setDuePenalty(penalCharge.getDuePenalty() + penaltyFee);
                penalCharge.setDueNachBounce(penalCharge.getDueNachBounce() + nachCharge);
            }

            penalChargesDao.save(penalCharge);
        } catch (Exception e) {
            logger.info("Exception occurred while saving penal charges for loan: {}, {}", lendingPaymentSchedule.getId(), e.getMessage(), e);
        }
    }

    private int calculateDpd(Double dueAmount, Double ediAmount) {
        if (dueAmount == null || ediAmount == null || ediAmount == 0) return 0;
        return (int) Math.round(dueAmount / ediAmount);
    }

    public long getDateDiffInDays(Date startTime, Date endTime) {
        long diff = endTime.getTime() - startTime.getTime();
        return TimeUnit.DAYS.convert(diff, TimeUnit.MILLISECONDS);
    }

}
