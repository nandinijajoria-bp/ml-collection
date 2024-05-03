package com.bharatpe.lending.loanV3.revamp.scopes;

import com.bharatpe.common.dao.ExperianDao;
import com.bharatpe.common.entities.Experian;
import com.bharatpe.lending.common.enums.FunnelEnums;
import com.bharatpe.lending.common.service.FunnelService;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.dto.PanFetchKYCResponseDto;
import com.bharatpe.lending.handlers.KycHandler;
import com.bharatpe.lending.loanV3.revamp.constants.LoanDetailsConstant;
import com.bharatpe.lending.loanV3.revamp.dto.EligibilityStateDTO;
import com.bharatpe.lending.loanV3.revamp.dto.LendingStateDTO;
import com.bharatpe.lending.loanV3.revamp.dto.ScopeDataArgs;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import com.bharatpe.lending.loanV3.revamp.response.LoanDashboardApiVersion;
import com.bharatpe.lending.loanV3.revamp.services.EligibilityV3Service;
import com.bharatpe.lending.loanV3.revamp.services.LoanDashboardService;
import com.bharatpe.lending.util.LoanUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

import java.time.LocalDateTime;
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

    @Autowired
    LoanDashboardService loanDashboardService;

    @Autowired
    FunnelService funnelService;

    @Autowired
    EasyLoanUtil easyLoanUtil;

    @Override
    public LendingStateDTO<EligibilityStateDTO> fetchScopedData(ScopeDataArgs scopeDataArgs) {
        LoanDashboardApiVersion loanDashboardApiVersion = loanDashboardService.getLoanDashboardApiVersion(scopeDataArgs.getMerchant().getId());
        if(LoanDetailsConstant.VERSION_V2.equalsIgnoreCase(loanDashboardApiVersion.getApiVersion())){
            funnelService.submitEventV3(scopeDataArgs.getMerchant().getId(), null, null,
                    FunnelEnums.StageId.PAN_PIN_PAGE, FunnelEnums.StageEvent.INITIATED, LocalDateTime.now().toString(), LoanDetailsConstant.FUNNEL_VERSION_TAG);
        }
        else{
            funnelService.submitEvent(scopeDataArgs.getMerchant().getId(), null, null,
                    FunnelEnums.StageId.PAN_PIN_PAGE, FunnelEnums.StageEvent.INITIATED, LocalDateTime.now().toString());
        }
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

            if(easyLoanUtil.isDummyMerchant(scopeDataArgs.getMerchant().getId())) {
                eligibilityStateDTO.setIsPanNsdlVerified(true);
                eligibilityStateDTO.setDummyMerchant(true);
            }else{
                try {
                    PanFetchKYCResponseDto response = kycHandler.panFetch(scopeDataArgs.getToken(), eligibilityStateDTO.getPancard(), scopeDataArgs.getMerchant().getId());
                    if (response != null && response.getStatus()) {
                        PanFetchKYCResponseDto.Data data = response.getData();
                        if (data != null) {
                            if (data.getIsPanNsdlVerified() != null) {
                                eligibilityStateDTO.setIsPanNsdlVerified(data.getIsPanNsdlVerified());
                            }
                        }
                    } else if (response != null && response.getData() != null && !response.getStatus() && response.getData().getMessage() != null) {
                        eligibilityStateDTO.setKycMessage(response.getData().getMessage());
                    }
                }catch (HttpClientErrorException.TooManyRequests e) {
                    log.error("Too Many requests error");
                    eligibilityStateDTO.setMaxCountReached(true);
                    eligibilityStateDTO.setMessage("You've reached your daily limit for PAN input. Please try again after 24 hours");
                }
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
        LoanDashboardApiVersion loanDashboardApiVersion = loanDashboardService.getLoanDashboardApiVersion(scopeDataArgs.getMerchant().getId());
        if(LoanDetailsConstant.VERSION_V2.equalsIgnoreCase(loanDashboardApiVersion.getApiVersion())){
            funnelService.submitEventV3(scopeDataArgs.getMerchant().getId(), null, null,
                    FunnelEnums.StageId.PAN_PIN_PAGE, FunnelEnums.StageEvent.SUBMITTED, LocalDateTime.now().toString(), LoanDetailsConstant.FUNNEL_VERSION_TAG);
        }
        else{
            funnelService.submitEvent(scopeDataArgs.getMerchant().getId(), null, null,
                    FunnelEnums.StageId.PAN_PIN_PAGE, FunnelEnums.StageEvent.SUBMITTED, LocalDateTime.now().toString());
        }
        return lendingStateDTO;
    }
}
