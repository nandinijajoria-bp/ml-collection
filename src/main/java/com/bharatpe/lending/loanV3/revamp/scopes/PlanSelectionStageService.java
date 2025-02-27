package com.bharatpe.lending.loanV3.revamp.scopes;

import com.bharatpe.lending.common.dao.LendingRiskVariablesDao;
import com.bharatpe.lending.common.entity.LendingRiskVariables;
import com.bharatpe.lending.enums.EmiLoanStatus;
import com.bharatpe.lending.loanV2.dto.EmiEligibility;
import com.bharatpe.lending.loanV3.revamp.dto.EligibilityStateDTO;
import com.bharatpe.lending.loanV3.revamp.dto.EmiDashboardResponse;
import com.bharatpe.lending.loanV3.revamp.dto.LendingStateDTO;
import com.bharatpe.lending.loanV3.revamp.dto.ScopeDataArgs;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import com.bharatpe.lending.loanV3.revamp.services.EligibilityV3Service;
import com.bharatpe.lending.loanV3.revamp.services.businessLoan.EmiDashboardService;
import com.bharatpe.lending.loanV3.utils.EmiUtils;
import com.bharatpe.lending.loanV3.utils.LrvUtility;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlanSelectionStageService implements IStageDataService<EligibilityStateDTO>{

    @Value("${emi.default.loan.amount:1500000}")
    private double emiDefaultLoanAmount;

    private final EmiDashboardService emiDashboardService;
    private final EligibilityV3Service eligibilityV3Service;
    private final LendingRiskVariablesDao lendingRiskVariablesDao;
    private final LrvUtility lrvUtility;
    private final EmiUtils emiUtils;

    @Override
    public LendingStateDTO<EligibilityStateDTO> fetchScopedData(ScopeDataArgs scopeDataArgs) {
        return null;
    }

    @Override
    public LendingStateDTO<EligibilityStateDTO> processCurrentStage(ScopeDataArgs scopeDataArgs) {
        CompletableFuture<EmiDashboardResponse> emiDataFuture = emiDashboardService.getEmiDashboardResponse(
                scopeDataArgs.getMerchant().getId(), scopeDataArgs.getToken());
        EligibilityStateDTO eligibilityStateDTO = new EligibilityStateDTO();
        eligibilityStateDTO.setMerchant(scopeDataArgs.getMerchant());
        eligibilityV3Service.fetchEligibilityWithoutScopeRequest(scopeDataArgs.getLoanDetailsV3Request(), eligibilityStateDTO);
        LendingRiskVariables lendingRiskVariables = lendingRiskVariablesDao.findByMerchantId(scopeDataArgs.getMerchant().getId());
        EmiDashboardResponse emiDashboardResponse = emiDashboardService.getData(emiDataFuture);
        log.info("lending_risk_variable entry and emi_dashboard response for merchant_id: {} is: {}, {}",
                scopeDataArgs.getMerchant().getId(), emiDashboardResponse, lendingRiskVariables);
        eligibilityStateDTO.setEmiEligibility(getEmiEligibility(emiDashboardResponse, lendingRiskVariables));
        return LendingStateDTO.<EligibilityStateDTO>builder().data(eligibilityStateDTO).scopeState(LendingViewStates.PLAN_SELECTION_PAGE).lendingViewStates(LendingViewStates.OFFER_PAGE).build();
    }

    /**
     *
     * @param emiDashboardResponse bl response
     * @param lendingRiskVariables lrv entry of the merchant
     * @return Emi Eligibility object with emiLoanAmount if user is eligible for loan
     */
    private EmiEligibility getEmiEligibility(
            EmiDashboardResponse emiDashboardResponse, LendingRiskVariables lendingRiskVariables){
        EmiEligibility emiEligibility = new EmiEligibility();
        if(emiDashboardResponse==null){
            return emiEligibility;
        }
        EmiDashboardResponse.Data emiDashboardData = emiDashboardResponse.getResult();
        if(emiDashboardData==null || EmiLoanStatus.EXPIRED.getStatus().equalsIgnoreCase(emiDashboardData.getStatus())){
            if(lrvUtility.getBlEligibility(lendingRiskVariables)){
                emiEligibility.setEmiLoanAmount(emiDefaultLoanAmount);
            }
        } else if(EmiLoanStatus.REJECTED.getStatus().equalsIgnoreCase(emiDashboardData.getStatus())){
            emiEligibility.setEmiLoanAmount(emiDefaultLoanAmount);
            if(!emiUtils.isEligible(emiDashboardData.getLastRejectedDate(), lendingRiskVariables)){
                emiEligibility.setEmiRejected(true);
                emiEligibility.setRejectReason(emiDashboardData.getRejectReason());
            }
        }
        return emiEligibility;
    }
}
