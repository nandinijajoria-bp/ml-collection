package com.bharatpe.lending.loanV3.utils;

import com.bharatpe.lending.common.dao.LendingRiskVariablesDao;
import com.bharatpe.lending.common.entity.LendingRiskVariables;
import com.bharatpe.lending.common.query.entity.LendingApplicationSlave;
import com.bharatpe.lending.enums.ApplicationStatus;
import com.bharatpe.lending.enums.EmiLoanStatus;
import com.bharatpe.lending.loanV3.revamp.dto.EmiDashboardResponse;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Setter
@Component
@RequiredArgsConstructor
public class EmiUtils {

    @Value("${emi.eligibility.rejection.buffer.time:30}")
    private int emiEligibilityRejectionBufferTime;

    @Value("${emi.flow.enabled:true}")
    private boolean emiFlowEnabled;

    private final LendingRiskVariablesDao lendingRiskVariablesDao;
    private final LrvUtility lrvUtility;

    private final List<String> nonActiveLoanStatus = Arrays.asList("rejected", "expired", "closed");


    public boolean isActive(EmiDashboardResponse emiDashboardResponse){
        return emiDashboardResponse!=null && emiDashboardResponse.getResult()!=null && emiDashboardResponse.getResult().getStatus()!=null
                && !nonActiveLoanStatus.contains(emiDashboardResponse.getResult().getStatus().toLowerCase());
    }

    public boolean isRejectedWithConditions(EmiDashboardResponse emiDashboardResponse, LendingApplicationSlave openApplication) {
        if (emiDashboardResponse == null || emiDashboardResponse.getResult() == null || emiDashboardResponse.getResult().getStatus() == null) {
            return false;
        }

        if(!ObjectUtils.isEmpty(openApplication) && ApplicationStatus.REJECTED.name().equalsIgnoreCase(openApplication.getStatus()) && EmiLoanStatus.REJECTED.name().equalsIgnoreCase(emiDashboardResponse.getResult().getStatus())) {
            LocalDateTime ediApplicationUpdatedAt = openApplication.getUpdatedAt().toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();
            LocalDateTime lastRejectedDate = emiDashboardResponse.getResult().getLastRejectedDate();
            if(ObjectUtils.isEmpty(lastRejectedDate)) {
                return false;
            }
            // Check if the last rejected date is before the EDI application updated at
            if (lastRejectedDate.isBefore(ediApplicationUpdatedAt)) {
                return false;
            }
        }
        if(EmiLoanStatus.REJECTED.name().equalsIgnoreCase(emiDashboardResponse.getResult().getStatus())) {
            LocalDateTime lastRejectedDate = emiDashboardResponse.getResult().getLastRejectedDate();
            if(ObjectUtils.isEmpty(lastRejectedDate)) {
                return false;
            }
            Integer emiELigibleIn = getEmiEligibleIn(lastRejectedDate);
            return emiELigibleIn>0;
        }
        return false;
    }

    /**
     *
     * @param emiDashboardData data fetched from emi service, null in case of api error
     * @param lendingRiskVariables lrv entry
     * @return true only in case, if data is fetched properly, there is no active emi application
     *  and bl_eligibility is true(in case of rejected date is more than 30 days)
     */
    public boolean isEligible(EmiDashboardResponse emiDashboardData, LendingRiskVariables lendingRiskVariables){
        if(emiDashboardData == null){
            return false;
        }
        if(emiDashboardData.getResult() == null || EmiLoanStatus.EXPIRED.getStatus().equalsIgnoreCase(emiDashboardData.getResult().getStatus())){
            return lrvUtility.getBlEligibility(lendingRiskVariables);
        }
        if(( ! EmiLoanStatus.REJECTED.getStatus().equalsIgnoreCase(emiDashboardData.getResult().getStatus()))
                || Objects.isNull(emiDashboardData.getResult().getLastRejectedDate())){
            return  false;
        }
        Integer emiELigibleIn = getEmiEligibleIn(emiDashboardData.getResult().getLastRejectedDate());

        if(emiELigibleIn != null && emiELigibleIn <= 0) {
            return lrvUtility.getBlEligibility(lendingRiskVariables);
        }
        return false;
    }

    public boolean isEligibleForPlanSelectionPage(EmiDashboardResponse emiDashboardData, LendingRiskVariables lendingRiskVariables) {
        if (emiDashboardData == null) {
            return false;
        }
        if (emiDashboardData.getResult() == null || EmiLoanStatus.EXPIRED.getStatus().equalsIgnoreCase(emiDashboardData.getResult().getStatus())) {
            return lrvUtility.getBlEligibility(lendingRiskVariables);
        }
        if ((EmiLoanStatus.REJECTED.getStatus().equalsIgnoreCase(emiDashboardData.getResult().getStatus()))) {
            if(ObjectUtils.isEmpty(emiDashboardData.getResult().getLastRejectedDate())){
                return false;
            }
            Integer emiELigibleIn = getEmiEligibleIn(emiDashboardData.getResult().getLastRejectedDate());
            if(emiELigibleIn != null && emiELigibleIn <= 0) {
                return lrvUtility.getBlEligibility(lendingRiskVariables);
            } else {
                return true;
            }
        }

        return false;
    }

    public boolean isEligible(LocalDateTime rejectedDate, LendingRiskVariables lendingRiskVariables){
        if(rejectedDate==null){
            return false;
        }
        Integer emiELigibleIn = getEmiEligibleIn(rejectedDate);
        if(emiELigibleIn != null && emiELigibleIn <= 0) {
            return lrvUtility.getBlEligibility(lendingRiskVariables);
        }
        return false;
    }

    public boolean isEligible(Long merchantId){
        LendingRiskVariables lendingRiskVariables = lendingRiskVariablesDao.findByMerchantId(merchantId);
        return lrvUtility.getBlEligibility(lendingRiskVariables);
    }

    public boolean isEmiFlowEnabled(){
        return emiFlowEnabled;
    }

    public boolean isEligibleForEdiCreateApplication(EmiDashboardResponse emiDashboardResponse){
        if(emiDashboardResponse==null){
            return false;
        }

        if(emiDashboardResponse.getResult()==null || emiDashboardResponse.getResult().getStatus()==null){
            return true;
        }
        String emiStatus = emiDashboardResponse.getResult().getStatus();
        return EmiLoanStatus.REJECTED.getStatus().equalsIgnoreCase(emiStatus)
                || EmiLoanStatus.EXPIRED.getStatus().equalsIgnoreCase(emiStatus)
                || EmiLoanStatus.CLOSED.getStatus().equalsIgnoreCase(emiStatus);
    }

    public Integer getEmiEligibleIn(LocalDateTime lastRejectedDate) {
        if (lastRejectedDate == null) {
            return null;
        }
        LocalDateTime eligibleDate = lastRejectedDate.plusDays(emiEligibilityRejectionBufferTime);
        long daysLeft = ChronoUnit.DAYS.between(LocalDateTime.now(), eligibleDate);
        return daysLeft <= 0 ? 0 : (int) daysLeft;
    }
}
