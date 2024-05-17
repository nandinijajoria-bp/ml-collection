package com.bharatpe.lending.collection.core.service.impl;

import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.dao.PenaltyFeeLedgerDao;
import com.bharatpe.lending.common.entity.PenaltyFeeLedger;
import com.bharatpe.lending.common.query.dao.PenaltyFeeConfigDaoSlave;
import com.bharatpe.lending.common.query.entity.PenaltyFeeConfigSlave;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.util.LoanUtil;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Date;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
public class GenericDpdBasedPenaltyService {
    private final Logger logger = LoggerFactory.getLogger(GenericDpdBasedPenaltyService.class);

    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;

    @Autowired
    PenaltyFeeLedgerDao penaltyFeeLedgerDao;

    @Autowired
    PenaltyFeeConfigDaoSlave penaltyFeeConfigDaoSlave;

    @Value("${dpd.penalty.eligible.lenders}")
    String dpdPenaltyEligibleLenders;

    public double applyPenalty(LendingPaymentSchedule loan) {
        logger.info("Creating DPD Based Penalty Fee for loan: {}", loan);
        double penaltyFee = 0d;

        try {
            double existingPenaltyAmount = Objects.nonNull(loan.getTotalPenaltyAmount()) ? loan.getTotalPenaltyAmount() : 0;
            int dpd = LoanUtil.calculateDPD(loan.getDueAmount(), loan.getEdiAmount());
            logger.info("Calculated DPD for loan : {} : {}", loan.getApplicationId(), dpd);

            if (dpd > 6 && dpd < 120 && dpdPenaltyEligibleLenders.contains(loan.getNbfc())) {
                PenaltyFeeLedger penaltyFeeLedger = penaltyFeeLedgerDao.findTop1OrderByIdDesc(loan.getId());
                List<PenaltyFeeConfigSlave> penaltyFeeConfigList = penaltyFeeConfigDaoSlave.findDPDPenaltyChargeConfig(loan.getDueAmount(), loan.getNbfc());
                if (CollectionUtils.isEmpty(penaltyFeeConfigList)) {
                    logger.info("No Penalty Fee Config found for loan: {}, amount: {}, lender: {}", loan.getId(), loan.getDueAmount(), loan.getNbfc());
                    return penaltyFee;
                }

                // if there is specific config defined at level 1 that will be picked else default level 1 will be picked
                PenaltyFeeConfigSlave penaltyFeeConfigSlave = penaltyFeeConfigList.get(0);
                if (!Objects.isNull(penaltyFeeLedger) && LoanUtil.getDateDiffInDays(penaltyFeeLedger.getCreatedAt(), new Date()) < 30) {
                    logger.info("Loan lies in less than 30 days count for next penalty: {}", loan.getApplicationId());
                    return loan.getDuePenalty();
                }

                penaltyFee = getPenaltyFromConfig(penaltyFeeConfigSlave, loan.getDueAmount());
                logger.info("Found Penalty Fee from Config for loan: {} : {}", loan.getId(), penaltyFee);

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
        } catch(Exception e) {
            logger.error("Error in creating DPD Based Penalty Fee for Loan: {}: {}", loan.getId(), e.getMessage(), e);
        }
        return penaltyFee;
    }

    private double getPenaltyFromConfig(PenaltyFeeConfigSlave penaltyFeeConfigSlave, Double dueAmount) {
        if("FLAT".equals(penaltyFeeConfigSlave.getType())){
            return penaltyFeeConfigSlave.getPenalty();
        }
        double rate = penaltyFeeConfigSlave.getPenalty();
        return dueAmount * rate;
    }

}
