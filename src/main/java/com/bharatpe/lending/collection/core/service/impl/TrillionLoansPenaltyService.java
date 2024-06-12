package com.bharatpe.lending.collection.core.service.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.collection.core.service.IPenaltyService;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.dao.LendingApplicationDao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
@Slf4j
public class TrillionLoansPenaltyService implements IPenaltyService {

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Value("${overdue.penalty.trillions.rollout.date}")
    String overDuePenaltyRolloutDate;

    @Value("${dpd.penalty.eligible.lenders}")
    String dpdPenaltyEligibleLenders;

    @Override
    public int getPenaltyVersion(LendingPaymentSchedule lendingPaymentSchedule) {
        log.info("lending payment schedule: {}", lendingPaymentSchedule);

        LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchantId(lendingPaymentSchedule.getApplicationId(), lendingPaymentSchedule.getMerchantId());

        return getApplicablePenaltyVersionCode(lendingPaymentSchedule.getNbfc(), lendingApplication.getAgreementAt());
    }

    private Date getPenaltyActivationDateFromProperty(String overDuePenaltyRolloutDate) {
        return DateTimeUtil.parseDate(overDuePenaltyRolloutDate, "yyyy-MM-dd hh:mm:ss");
    }

    private int getApplicablePenaltyVersionCode(String lender, Date agreementAt) {
        Date thresholdDate = getPenaltyActivationDateFromProperty(overDuePenaltyRolloutDate);
        if (dpdPenaltyEligibleLenders.contains(lender) && agreementAt != null && thresholdDate != null && !agreementAt.after(thresholdDate)) {
            return 1;
        }
        return 2;
    }

}
