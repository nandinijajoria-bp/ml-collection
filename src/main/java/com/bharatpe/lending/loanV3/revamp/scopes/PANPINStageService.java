package com.bharatpe.lending.loanV3.revamp.scopes;

import com.bharatpe.common.dao.ExperianDao;
import com.bharatpe.common.entities.Experian;
import com.bharatpe.lending.handlers.KycHandler;
import com.bharatpe.lending.loanV3.revamp.dto.*;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import com.bharatpe.lending.loanV3.revamp.services.EligibilityV3Service;
import com.bharatpe.lending.util.LoanUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Component
@Slf4j
public class PANPINStageService implements IStageDataService<EligibilityStateDTO>{

    @Autowired
    EligibilityV3Service eligibilityV3Service;

    @Autowired
    ExperianDao experianDao;

    @Autowired
    KycHandler kycHandler;

    @Autowired
    LoanUtil loanUtil;

    @Override
    public LendingStateDTO<EligibilityStateDTO> fetchScopedData(ScopeDataArgs scopeDataArgs) {
        EligibilityStateDTO eligibilityStateDTO = new EligibilityStateDTO();
        try{
            eligibilityStateDTO.setMerchantName(loanUtil.getBeneficiaryName(scopeDataArgs.getMerchant().getId()));
            Experian experian = experianDao.getByMerchantId(scopeDataArgs.getMerchant().getId());
            if (experian != null) {
                eligibilityStateDTO.setPancard(experian.getPancardNumber());
                eligibilityStateDTO.setPincode(experian.getPincode() != null ? String.valueOf(experian.getPincode()) : null);
                eligibilityStateDTO.setHasExperian(true);
            }
            else{
                String kycPancard = kycHandler.getPanNumber(scopeDataArgs.getMerchant().getId());
                eligibilityStateDTO.setPancard(kycPancard);
            }
        }
        catch (Exception e) {
            log.error("error in getting pan pin stage data for {} : {}, {}", scopeDataArgs.getMerchant().getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
            eligibilityStateDTO.setErrorString("Something went wrong");
        }
        return new LendingStateDTO<>(eligibilityStateDTO , LendingViewStates.PAN_PIN_PAGE, LendingViewStates.PAN_PIN_PAGE);
    }

    @Override
    public LendingStateDTO<EligibilityStateDTO> processCurrentStage(ScopeDataArgs scopeDataArgs) {
        // save any request data if needed
        Experian experian = experianDao.getByMerchantId(scopeDataArgs.getMerchant().getId());
        EligibilityStateDTO eligibilityStateDTO = new EligibilityStateDTO();
        eligibilityStateDTO.setMerchant(scopeDataArgs.getMerchant());
        eligibilityStateDTO.setExperian(experian);
        if (eligibilityV3Service.eligibilityBaseChecksSuccess(scopeDataArgs.getLoanDetailsV3Request(), eligibilityStateDTO)) {
            eligibilityV3Service.savePanPinData(scopeDataArgs.getLoanDetailsV3Request(), eligibilityStateDTO);
        }
        LendingStateDTO<EligibilityStateDTO> lendingStateDTO = new LendingStateDTO<>(eligibilityStateDTO, LendingViewStates.OFFER_PAGE, LendingViewStates.PAN_PIN_PAGE);
        // model all the info in responsev3
        return lendingStateDTO;
    }
}
