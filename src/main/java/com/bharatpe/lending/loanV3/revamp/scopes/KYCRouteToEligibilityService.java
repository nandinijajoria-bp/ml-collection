package com.bharatpe.lending.loanV3.revamp.scopes;

import com.bharatpe.cache.service.LendingCache;
import com.bharatpe.common.dao.ExperianDao;
import com.bharatpe.common.entities.Experian;
import com.bharatpe.lending.enums.KycDocType;
import com.bharatpe.lending.enums.KycStatus;
import com.bharatpe.lending.handlers.KycHandler;
import com.bharatpe.lending.loanV2.dto.InitiateKycDTO;
import com.bharatpe.lending.loanV3.revamp.constants.RTEConstants;
import com.bharatpe.lending.loanV3.revamp.dto.KYCRTEDto;
import com.bharatpe.lending.loanV3.revamp.dto.KYCStateDTO;
import com.bharatpe.lending.loanV3.revamp.dto.LendingStateDTO;
import com.bharatpe.lending.loanV3.revamp.dto.ScopeDataArgs;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import com.bharatpe.lending.loanV3.revamp.enums.LoanDetailExceptionEnum;
import com.bharatpe.lending.loanV3.revamp.exception.KYCException;
import com.bharatpe.lending.loanV3.revamp.exception.LoanDetailsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;

import java.util.*;


@Component
@Slf4j
public class KYCRouteToEligibilityService implements IStageDataService<KYCRTEDto> {


    @Autowired
    KycHandler kycHandler;

    @Autowired
    Environment env;

    @Autowired
    ExperianDao experianDao;


    @Value("${route.eligibility.callback}")
    private String callback;

    @Value("${kyc.deeplink}")
    String kycDeepLink;

    @Autowired
    LendingCache lendingCache;

    private KYCStateDTO initiateKyc(Long merchantId) {
        KYCStateDTO initiateKycResponse = new KYCStateDTO();
        List<KycDocType> docTypes = new ArrayList<>();
        docTypes.add(KycDocType.PAN_NO);
//        docTypes.add(KycDocType.SELFIE);
//        docTypes.add(KycDocType.EKYC);
//        String callBackURL = callback + "&wroute=program-summary&backFrom=kyc";
        String callBackURL = callback + "&backFrom=kyc";
        Experian experian = experianDao.getByMerchantId(merchantId);

        String panCard;
        if (experian == null || experian.getPancardNumber() == null) {
            log.info("Pan card set to null for merchantId {}",merchantId);
            panCard=null;
//            throw new LoanDetailsException(LoanDetailExceptionEnum.PANCARD_DOES_NOT_EXIST.getErrorCode(), LoanDetailExceptionEnum.PANCARD_DOES_NOT_EXIST.getErrorMessage());
        }
        else{
            panCard=experian.getPancardNumber();
        }

        String uniqueID = UUID.randomUUID().toString();

//        String wroute =
        InitiateKycDTO initiateKycDTO = InitiateKycDTO.builder()
                .referenceId(uniqueID)
                .panNumber(panCard)
                .callBackUrl(callBackURL)
                .merchantId(merchantId.toString()).build();
        log.info("request for initiateKYC is {}", initiateKycDTO);
        Map<String, String> ckycResponseObj = kycHandler.initiateKyc(merchantId, initiateKycDTO, docTypes);
        log.info("response for initiateKyc is {}",ckycResponseObj);
        if (ckycResponseObj.containsKey("ckycId")) {
            initiateKycResponse.setShowKycPage(true);
            initiateKycResponse.setKycStatus(KycStatus.DRAFT);
            initiateKycResponse.setDeeplink(kycDeepLink);
            return initiateKycResponse;
        }
        log.error("Unable to initiate kyc for merchant :{} with error message:{}", merchantId, ckycResponseObj.get("message"));
        throw new LoanDetailsException(LoanDetailExceptionEnum.INITIATE_KYC_FAILED.getErrorCode(), LoanDetailExceptionEnum.INITIATE_KYC_FAILED.getErrorMessage());
    }

    @Override
    @Transactional
    public LendingStateDTO<KYCRTEDto> fetchScopedData(ScopeDataArgs scopeDataArgs) {
        KYCRTEDto initiateKycResponse = new KYCRTEDto();
        try {
            KycStatus doc = kycHandler.getPanStatus(scopeDataArgs.getMerchant().getId());
            log.info("kycStatus is {}",doc);
            if (!ObjectUtils.isEmpty(doc) && (!"APPROVED".equalsIgnoreCase(doc.toString()))) {
                    initiateKycResponse = KYCRTEDto.from(initiateKyc(scopeDataArgs.getMerchant().getId()));
                    log.info("kyc response is {}",initiateKycResponse);
                    return new LendingStateDTO<>(initiateKycResponse, LendingViewStates.KYC_ROUTE_TO_ELIGIBILITY,
                            LendingViewStates.KYC_ROUTE_TO_ELIGIBILITY);

            }
            if (ObjectUtils.isEmpty(doc)) {
                initiateKycResponse = KYCRTEDto.from(initiateKyc(scopeDataArgs.getMerchant().getId()));
                return new LendingStateDTO<>(initiateKycResponse, LendingViewStates.KYC_ROUTE_TO_ELIGIBILITY,
                        LendingViewStates.KYC_ROUTE_TO_ELIGIBILITY);

            }
            initiateKycResponse.setKycStatus(doc);
            initiateKycResponse.setShowKycPage(false);
            String mileStoneCacheKey = RTEConstants.RTE_PROGRAM_DETAILS_CACHE + scopeDataArgs.getMerchant().getId();
            Object mileStoneCacheResponse = lendingCache.get(mileStoneCacheKey);
            if (!ObjectUtils.isEmpty(mileStoneCacheResponse)) {
                lendingCache.delete(mileStoneCacheKey);
            }
            return new LendingStateDTO<>(initiateKycResponse, LendingViewStates.KYC_ROUTE_TO_ELIGIBILITY,
                    LendingViewStates.KYC_ROUTE_TO_ELIGIBILITY);

        } catch (Exception ex) {
            log.error("Exception while initiating kyc for merchant:{} {} {}", scopeDataArgs.getMerchant().getId(), ex.getMessage(), Arrays.asList(ex.getStackTrace()));
            throw new KYCException(LoanDetailExceptionEnum.SOMETHING_WENT_WRONG.getErrorCode(), LoanDetailExceptionEnum.SOMETHING_WENT_WRONG.getErrorMessage());
        }
    }

    @Override
    public LendingStateDTO<KYCRTEDto> processCurrentStage(ScopeDataArgs scopeDataArgs) {
        return fetchScopedData(scopeDataArgs);
    }


}
