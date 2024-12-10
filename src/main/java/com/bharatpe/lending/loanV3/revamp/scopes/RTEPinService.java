package com.bharatpe.lending.loanV3.revamp.scopes;

import com.bharatpe.common.dao.ExperianDao;
import com.bharatpe.common.entities.Experian;
import com.bharatpe.lending.common.enums.FunnelEnums;
import com.bharatpe.lending.common.service.FunnelService;
import com.bharatpe.lending.loanV3.revamp.constants.LoanDetailsConstant;
import com.bharatpe.lending.loanV3.revamp.dto.EligibilityStateDTO;
import com.bharatpe.lending.loanV3.revamp.dto.LendingStateDTO;
import com.bharatpe.lending.loanV3.revamp.dto.ScopeDataArgs;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import com.bharatpe.lending.loanV3.revamp.services.EligibilityV3Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class RTEPinService implements IStageDataService<EligibilityStateDTO>{
    @Autowired
    ExperianDao experianDao;

    @Autowired
    EligibilityV3Service eligibilityV3Service;

    @Autowired
    FunnelService funnelService;

    @Override
    public LendingStateDTO<EligibilityStateDTO> fetchScopedData(ScopeDataArgs scopeDataArgs) {
        Experian experian = experianDao.getByMerchantId(scopeDataArgs.getMerchant().getId());
        EligibilityStateDTO eligibilityStateDTO = new EligibilityStateDTO();
        eligibilityStateDTO.setMerchant(scopeDataArgs.getMerchant());
        eligibilityStateDTO.setMerchantId(scopeDataArgs.getMerchant().getId());

        eligibilityStateDTO.setExperian(experian);
        if (eligibilityV3Service.eligibilityBaseChecksSuccess(scopeDataArgs.getLoanDetailsV3Request(), eligibilityStateDTO)) {
            eligibilityV3Service.savePanPinData(scopeDataArgs.getLoanDetailsV3Request(), eligibilityStateDTO);
        }
        LendingStateDTO<EligibilityStateDTO> lendingStateDTO = new LendingStateDTO<>(eligibilityStateDTO, LendingViewStates.RTE_PIN_PAGE, LendingViewStates.RTE_PIN_PAGE);
        funnelService.submitEventV3(scopeDataArgs.getMerchant().getId(), null, null,
                FunnelEnums.StageId.RTE_PIN, FunnelEnums.StageEvent.RTE_PIN_SAVE, LocalDateTime.now().toString(), LoanDetailsConstant.FUNNEL_VERSION_TAG);
        return lendingStateDTO;
    }

    @Override
    public LendingStateDTO<EligibilityStateDTO> processCurrentStage(ScopeDataArgs scopeDataArgs) {
        return fetchScopedData(scopeDataArgs);
    }
}
