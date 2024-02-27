package com.bharatpe.lending.loanV3.revamp.config;

import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import com.bharatpe.lending.loanV3.revamp.scopes.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

@Component
public class StageServiceFactory {

    @Autowired
    KFSStageService kfsStageService;

    @Autowired
    RTEPinService rtePinService;

    @Autowired
    PANPINStageService panpinStageService;

    @Autowired
    KYCStageDataService kycStageDataService;

    @Autowired
    AgreementStageDataService agreementStageDataService;

    @Autowired
    ShopDetailsStageDataService shopDetailsStageDataService;

    @Autowired
    ShopPicturesStageDataService shopPicturesStageDataService;

    @Autowired
    ApplicationStatusStageDataService applicationStatusStageDataService;

    @Autowired
    ReferencesStageDataService referencesStageDataService;

    @Autowired
    LenderEvaluationStageDataService lenderEvaluationStageDataService;

    @Autowired
    PermissionStageDataService permissionStageDataService;

    @Autowired
    OfferStageDataService offerStageDataService;

    @Autowired
    private EnachStageService enachStageService;

    @Autowired
    KYCRouteToEligibilityService kycRouteToEligibilityService;

    public IStageDataService getStageService(LendingViewStates lendingViewStates) {
        switch (lendingViewStates) {
            case PAN_PIN_PAGE:
                return panpinStageService;
            case KYC_PAGE:
                return kycStageDataService;
            case AGREEMENT_PAGE:
                return agreementStageDataService;
            case KEY_FACTOR_STATEMENT_PAGE:
                return kfsStageService;
            case SHOP_DETAILS_PAGE:
                return shopDetailsStageDataService;
            case SHOP_PICTURES_PAGE:
                return shopPicturesStageDataService;
            case APPLICATION_STATUS_PAGE:
                return applicationStatusStageDataService;
            case REFERENCE_PAGE:
                return referencesStageDataService;
            case LENDER_EVALUATION_PAGE:
                return lenderEvaluationStageDataService;
            case PERMISSIONS_PAGE:
                return permissionStageDataService;
            case OFFER_PAGE:
                return offerStageDataService;
            case ENACH_PAGE:
                return enachStageService;
            case KYC_ROUTE_TO_ELIGIBILITY:
                return kycRouteToEligibilityService;
            case RTE_PIN_PAGE:
                return rtePinService;
            default:
                return null;
        }
    }
}
