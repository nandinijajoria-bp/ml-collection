package com.bharatpe.lending.loanV3.revamp.scopes;

import com.bharatpe.common.dao.ExperianDao;
import com.bharatpe.common.entities.Experian;
import com.bharatpe.lending.common.enums.FunnelEnums;
import com.bharatpe.lending.common.service.FunnelService;
import com.bharatpe.lending.handlers.KycHandler;
import com.bharatpe.lending.loanV3.revamp.constants.LoanDetailsConstant;
import com.bharatpe.lending.loanV3.revamp.dto.EligibilityStateDTO;
import com.bharatpe.lending.loanV3.revamp.dto.LendingStateDTO;
import com.bharatpe.lending.loanV3.revamp.dto.ScopeDataArgs;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import com.bharatpe.lending.loanV3.revamp.response.LoanDashboardApiVersion;
import com.bharatpe.lending.loanV3.revamp.services.EligibilityV3Service;
import com.bharatpe.lending.loanV3.revamp.services.LoanDashboardService;
import com.bharatpe.lending.service.APIGatewayService;
import com.bharatpe.lending.util.LoanUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;

@Component
@Slf4j
public class OfferStageDataService implements IStageDataService<EligibilityStateDTO>{

    @Autowired
    EligibilityV3Service eligibilityV3Service;

    @Autowired
    ExperianDao experianDao;

    @Autowired
    APIGatewayService apiGatewayService;

    @Autowired
    KycHandler kycHandler;

    @Autowired
    LoanUtil loanUtil;

    @Autowired
    LoanDashboardService loanDashboardService;

    @Autowired
    FunnelService funnelService;

    @Override
    public LendingStateDTO<EligibilityStateDTO> processCurrentStage(ScopeDataArgs scopeDataArgs) {
        LendingStateDTO<EligibilityStateDTO> lendingStateDTO = fetchScopedData(scopeDataArgs);
        if (loanUtil.isApplicableForAggregationFlow(scopeDataArgs.getMerchant().getId(), null)){
            lendingStateDTO.setLendingViewStates(LendingViewStates.LENDER_AGGREGATION);
        } else if(lendingStateDTO.getData().getIsPreapprovedRepeatLoan()){
            lendingStateDTO.setLendingViewStates(LendingViewStates.KYC_PAGE);
        } else{
            lendingStateDTO.setLendingViewStates(LendingViewStates.SHOP_DETAILS_PAGE);
        }
        return lendingStateDTO;
    }

    @Override
    public LendingStateDTO<EligibilityStateDTO> fetchScopedData(ScopeDataArgs scopeDataArgs) {
        EligibilityStateDTO eligibilityStateDTO = new EligibilityStateDTO();
        try {

            LoanDashboardApiVersion loanDashboardApiVersion = loanDashboardService.getLoanDashboardApiVersion(scopeDataArgs.getMerchant().getId());
            if(LoanDetailsConstant.VERSION_V2.equalsIgnoreCase(loanDashboardApiVersion.getApiVersion())){
                funnelService.submitEventV3(scopeDataArgs.getMerchant().getId(), null, null,
                        FunnelEnums.StageId.OFFER_PAGE, FunnelEnums.StageEvent.INITIATED, LocalDateTime.now().toString(), LoanDetailsConstant.FUNNEL_VERSION_TAG);
            }
            else{
                funnelService.submitEvent(scopeDataArgs.getMerchant().getId(), null, null,
                        FunnelEnums.StageId.OFFER_PAGE, FunnelEnums.StageEvent.INITIATED, LocalDateTime.now().toString());
            }

            eligibilityStateDTO.setMerchant(scopeDataArgs.getMerchant());
            //set merchant Id
            eligibilityStateDTO.setMerchantId(scopeDataArgs.getMerchant().getId());
            eligibilityStateDTO.setAccountDetails(loanUtil.getAccountDetails(scopeDataArgs.getMerchant().getId()));
            Experian experian = experianDao.getByMerchantId(scopeDataArgs.getMerchant().getId());
            if (experian != null) {
                eligibilityStateDTO.setPancard(experian.getPancardNumber());
                eligibilityStateDTO.setPincode(experian.getPincode() != null ? String.valueOf(experian.getPincode()) : null);
                eligibilityStateDTO.setHasExperian(true);
                eligibilityStateDTO.setExperian(experian);
            }
            eligibilityStateDTO.setKycPanStatus(kycHandler.getPanStatus(scopeDataArgs.getMerchant().getId()));

            eligibilityStateDTO.setBpClubMember(apiGatewayService.eligibleForProcessingFee(scopeDataArgs.getMerchant().getId()));
            eligibilityV3Service.fetchEligibility(scopeDataArgs.getLoanDetailsV3Request(), eligibilityStateDTO);

            if(LoanDetailsConstant.VERSION_V2.equalsIgnoreCase(loanDashboardApiVersion.getApiVersion())){
                funnelService.submitEventV3(scopeDataArgs.getMerchant().getId(), null, null,
                        FunnelEnums.StageId.OFFER_PAGE, FunnelEnums.StageEvent.COMPLETED, LocalDateTime.now().toString(), LoanDetailsConstant.FUNNEL_VERSION_TAG);
            }
            else{
                funnelService.submitEvent(scopeDataArgs.getMerchant().getId(), null, null,
                        FunnelEnums.StageId.OFFER_PAGE, FunnelEnums.StageEvent.COMPLETED, LocalDateTime.now().toString());
            }
        } catch (Exception e) {
            log.error("error in getting offer stage data for {} : {}, {}", scopeDataArgs.getMerchant().getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
            eligibilityStateDTO.setErrorString("Something went wrong");
        }
        return new LendingStateDTO<>(eligibilityStateDTO , LendingViewStates.OFFER_PAGE, LendingViewStates.OFFER_PAGE);
    }

}
