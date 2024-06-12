package com.bharatpe.lending.collection.core.service.impl;

import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.collection.core.dto.PenaltyWaiverResponseDto;
import com.bharatpe.lending.collection.core.service.PenaltyFactory;
import com.bharatpe.lending.collection.core.service.PenaltyFeeService;
import com.bharatpe.lending.common.dao.PenalChargesDao;
import com.bharatpe.lending.common.dao.PenaltyFeeLedgerDao;
import com.bharatpe.lending.common.entity.PenalCharges;
import com.bharatpe.lending.common.entity.PenaltyFeeLedger;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dto.ResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

@Component
public class PenaltyFeeServiceImpl implements PenaltyFeeService {
    private final Logger logger = LoggerFactory.getLogger(PenaltyFeeServiceImpl.class);

    @Autowired
    PenaltyFeeLedgerDao penaltyFeeLedgerDao;

    @Autowired
    PenalChargesDao penalChargesDao;

    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;

    @Autowired
    GenericDpdBasedPenaltyService genericDpdBasedPenaltyService;

    @Autowired
    GenericOverDueBasedPenaltyService genericOverDueBasedPenaltyService;

    @Autowired
    PenaltyFactory penaltyFactory;

    @Override
    public double checkAndApplyPenaltyFee(LendingPaymentSchedule activeLoan){
        logger.info("Checking Penalty Fee for loan: {}", activeLoan);

        int version = penaltyFactory.getPenaltyService(activeLoan.getNbfc()).getPenaltyVersion(activeLoan);

        logger.info("Applicable Penalty Fee Version: {}", version);

        if (version == 1) {
            return genericDpdBasedPenaltyService.applyPenalty(activeLoan);
        }
        if(version == 2){
            return genericOverDueBasedPenaltyService.applyPenalty(activeLoan);
        }
        return 0;
    }

    @Override
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

    private void updateDuePenaltyFieldsInDb(LendingPaymentSchedule lendingPaymentSchedule, PenaltyFeeLedger penaltyFeeLedger, PenalCharges penalCharges) {
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
}
