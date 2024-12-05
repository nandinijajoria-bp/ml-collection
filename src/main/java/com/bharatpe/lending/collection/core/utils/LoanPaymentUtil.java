package com.bharatpe.lending.collection.core.utils;

import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.common.utils.NotificationUtil;
import com.bharatpe.lending.common.dto.NotificationPayloadDto;
import com.bharatpe.lending.common.enums.LoanSettlementMechanism;
import com.bharatpe.lending.common.service.LendingNotificationService;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.service.APIGatewayService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.EnumUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.bharatpe.lending.collection.core.constant.ExcessConstants.ExcessCollectionAdjustmentModeDescription;
import static com.bharatpe.lending.common.enums.LoanSettlementMechanism.*;
import static com.bharatpe.lending.enums.Lender.*;

@Service
@Slf4j
public class LoanPaymentUtil {

    public static final String DEFAULT_LOAN_SETTLEMENT_MECHANISM = IPC.name();
    public static final String DEFAULT_EXCESS_ADJUSTED_DESCRPTION = "EXCESS_NACH_ADJUSTED";
    private static final Set<String> NON_NPA_SUPPORTED_LENDER = new HashSet<>(Arrays.asList(LDC.name(), MAMTA.name(), HINDON.name(), LIQUILOANS.name(), LIQUILOANS_NBFC.name(), LIQUILOANS_P2P.name(), LIQUILOANS_P2P_OF.name(), MAMTA0.name(), MAMTA1.name(), MAMTA2.name()));

    @Value("${is.new.payment.settlement.enabled:false}")
    public  boolean newPaymentSettlementModeAllowed;

    @Value("${settlement.new.rollout.date:}")
    String newSettlementRolloutDate;
    @Autowired
    MerchantService merchantService;
    @Autowired
    NotificationUtil notificationUtil;
    @Autowired
    LendingNotificationService lendingNotificationService;

    @Autowired
    APIGatewayService apiGatewayService;

    public static String getLoanSettlementMechanism(LendingPaymentSchedule loan) {
        log.info("getLoanSettlementMechanism for loanId: {} is {}", loan.getId(), loan.getSettlementMechanism());

        if (!NON_NPA_SUPPORTED_LENDER.contains(loan.getNbfc())) {
            int dpdCount = calculateDPD(loan.getEdiAmount(), loan.getDueAmount());
            log.info("getLoanSettlementMechanism for loanId: {} dpd is  {}", loan.getId(), dpdCount);

            if (dpdCount > 90 || (Objects.nonNull(loan.getSettleAllPrinciple()) && loan.getSettleAllPrinciple())) {
                log.info("getLoanSettlementMechanism for loanId: {} is a NPA with dpd : {} and mechanism is {}  settlePrinciple {}", loan.getId(), dpdCount, NPA.name(), loan.getSettleAllPrinciple());
                return NPA.name();
            }
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

    public static int calculateDPD(Double ediAmount, Double dueAmount) {
        if (dueAmount < ediAmount) return 0;
        return (int) Math.round(dueAmount / ediAmount);
    }

    public boolean checkIfNewPaymentSettlementModeActive()  {
        return newPaymentSettlementModeAllowed;
    }

    public boolean checkIfNewSettlementAllowed(Date createdAt)  {
        //LC-595 allow for all loan and lender
        return true;
        //return newPaymentSettlementModeAllowed && checkNewPaymentEligibility(createdAt);
    }

    public boolean checkNewPaymentEligibility(Date createdAt)  {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            String rolloutDate = newSettlementRolloutDate;
            if (!StringUtils.isEmpty(rolloutDate)) {
                Date date = sdf.parse(rolloutDate);
                if (createdAt.after(date)) {
                    return true;
                }
            }
        } catch (Exception e) {
            log.info("An exception occurred while checking new loan settlement eligibility");
        }
        return false;
    }

    public  void sendSMS(LendingPaymentSchedule loan, Double amount, boolean isLoanClosed) {
        try {
            Optional<BasicDetailsDto> basicDetailsDto = merchantService.fetchMerchantBasicDetails(loan.getMerchantId());
            if (ObjectUtils.isEmpty(basicDetailsDto)) {
                return;
            }

//			Merchant merchant = loan.getMerchant();
            String identifier = "LENDING_PAYMENT_PUSH";
            Map<String,Object> templateParams = new HashMap<>();
            templateParams.put("amount",amount.intValue());
            String deeplink = notificationUtil.getDeeplink(basicDetailsDto.get().getSettlementType(), "LOAN_DASHBOARD");
            NotificationPayloadDto notificationPayloadDto = new NotificationPayloadDto();
            notificationPayloadDto.setPushTitle("Payment received!");
            notificationPayloadDto.setTemplateIdentifier(identifier);
            notificationPayloadDto.setMobile(basicDetailsDto.get().getMobile());
            notificationPayloadDto.setPushDeepLink(deeplink);
            notificationPayloadDto.setClientName("LENDING");
            notificationPayloadDto.setTemplateParams(templateParams);
            lendingNotificationService.notify(notificationPayloadDto);
            if(isLoanClosed) {
                if(apiGatewayService.sendCommunicationForNewOffer(loan)) {
                    return;
                }
                identifier = "LENDING_PAYMENT_2_PUSH";
                notificationPayloadDto.setTemplateIdentifier(identifier);
                notificationPayloadDto.setPushTitle("The loan is closed successfully");
                lendingNotificationService.notify(notificationPayloadDto);
            }
        } catch(Exception ex) {
            log.error("Exception while sending payment SMS to merchant {}, Exception is {}");
        }
    }

    public static String getExcessAdjustedModeDesc(String excessMode) {
        if (StringUtils.hasLength(excessMode)) {
            try {
                return ExcessCollectionAdjustmentModeDescription.getOrDefault(excessMode, buildDefaultExcessAdjustedModeDesc(excessMode));
            } catch (Exception e) {
                log.error("Exception while getting the adjustment mode desc for {} {} {}", excessMode, e.getMessage(), Arrays.asList(e.getStackTrace()));
            }
        }
        return DEFAULT_EXCESS_ADJUSTED_DESCRPTION;
    }

    private static String buildDefaultExcessAdjustedModeDesc(String excessMode) {
        if (StringUtils.hasLength(excessMode) && !"NACH".equalsIgnoreCase(excessMode)) {
            try {
                return "EXCESS_ADJUSTED_"+excessMode;
            } catch (Exception e) {
                log.error("Exception while buildDefaultExcessAdjustedModeDesc for {}  {} {}", excessMode, e.getMessage(), Arrays.asList(e.getStackTrace()));
            }
        }
        return DEFAULT_EXCESS_ADJUSTED_DESCRPTION;
    }

    public boolean excessCollectionCommunicationSmsRequired(String source) {
        // currently there is only nach template is defined... define sms identifier before adding here
        return "BHARATPE_NACH".equalsIgnoreCase(source) || "EXCESS_NACH_ADJUSTED".equalsIgnoreCase(source);
    }
}
