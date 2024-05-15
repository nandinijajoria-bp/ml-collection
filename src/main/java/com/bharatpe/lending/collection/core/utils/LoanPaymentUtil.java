package com.bharatpe.lending.collection.core.utils;

import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.dao.LendingCollectionExcessDao;
import com.bharatpe.lending.common.entity.LendingCollectionExcess;
import com.bharatpe.lending.common.enums.CollectionTransferTypeEnum;
import com.bharatpe.lending.common.enums.LoanSettlementMechanism;
import com.bharatpe.lending.dao.LendingLedgerDao;
import com.bharatpe.lending.service.APIGatewayService;
import com.bharatpe.lending.service.LendingCollectionAuditService;
import com.bharatpe.lending.service.PaymentService;
import com.bharatpe.lending.util.LoanUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.EnumUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.bharatpe.lending.common.enums.LoanSettlementMechanism.*;
import static com.bharatpe.lending.common.enums.LoanSettlementMechanism.IPC;
import static com.bharatpe.lending.constant.PaymentConstants.EXCESS_NACH_TERMINAL_ORDER_ID_SUFFIX;

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

}
