package com.bharatpe.lending.collection.core.utils;

import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.enums.LoanSettlementMechanism;
import com.bharatpe.lending.util.LoanUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.EnumUtils;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import static com.bharatpe.lending.common.enums.LoanSettlementMechanism.*;

@Service
@Slf4j
public class LoanPaymentUtil {

    public static final String DEFAULT_LOAN_SETTLEMENT_MECHANISM = IPC.name();

    public static String getLoanSettlementMechanism(LendingPaymentSchedule loan) {
        log.info("getLoanSettlementMechanism for loanId: {} is {}", loan.getId(), loan.getSettlementMechanism());

        int dpdCount = LoanUtil.calculateDPD(loan.getEdiAmount(), loan.getDueAmount());
        log.info("getLoanSettlementMechanism for loanId: {} dpd is  {}", loan.getId(), dpdCount);

        if (dpdCount > 90) {
            log.info("getLoanSettlementMechanism for loanId: {} is a NPA with dpd : {} and mechanism is {}", loan.getId(), dpdCount, NPA.name());
            return NPA.name();
        }

        String mechanism = getOrDefaultSettlementMechanismFromLoan(loan.getSettlementMechanism(), DEFAULT_LOAN_SETTLEMENT_MECHANISM);
        log.info("getLoanSettlementMechanism for loanId: {} calculated mechanism is {}", loan.getId(), mechanism);

        if (EDI_BY_EDI.name().equalsIgnoreCase(mechanism)) {
            log.info("getLoanSettlementMechanism for loanId: {} is {}", loan.getId(), EDI_BY_EDI.name());
            return EDI_BY_EDI.name();
        }

        if (IPC.name().equalsIgnoreCase(mechanism)) {
            log.info("getLoanSettlementMechanism for loanId: {} is {}", loan.getId(), IPC.name());
            return IPC.name();
        }

        log.info("getLoanSettlementMechanism for loanId: {} can't determine mechanism is {} so using default mechanism {} ", loan.getId(), mechanism, DEFAULT_LOAN_SETTLEMENT_MECHANISM);
        return DEFAULT_LOAN_SETTLEMENT_MECHANISM;
    }

    public static String getOrDefaultSettlementMechanismFromLoan(String name, String defaultMechanism) {
        LoanSettlementMechanism loanSettlementMechanism = EnumUtils.getEnumIgnoreCase(LoanSettlementMechanism.class, name);
        return loanSettlementMechanism != null ? loanSettlementMechanism.name() : defaultMechanism;
    }

    public static long getDateDiffInDays(Date startTime, Date endTime) {
        long diff = endTime.getTime() - startTime.getTime();
        return TimeUnit.DAYS.convert(diff, TimeUnit.MILLISECONDS);
    }

}
