package com.bharatpe.lending.loanV3.revamp.scopes;



import com.bharatpe.common.dao.ExperianDao;
import com.bharatpe.common.entities.Experian;
import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.Handler.EnachHandler;
import com.bharatpe.lending.common.dto.BharatPeEnachResponseDTO;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.constant.Deeplink;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dto.EnachErrorMessageDTO;
import com.bharatpe.lending.enums.ApplicationStatus;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.loanV3.revamp.dto.EnachStateDTO;
import com.bharatpe.lending.loanV3.revamp.dto.LendingStateDTO;
import com.bharatpe.lending.loanV3.revamp.dto.ScopeDataArgs;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import com.bharatpe.lending.loanV3.revamp.enums.LoanDetailExceptionEnum;
import com.bharatpe.lending.loanV3.revamp.exception.LoanDetailsException;
import com.bharatpe.lending.loanV3.revamp.services.LendingApplicationServiceV3;
import com.bharatpe.lending.loanV3.revamp.services.LoanDetailsV3Service;
import com.bharatpe.lending.service.APIGatewayService;
import com.bharatpe.lending.service.EnachErrorHandingService;
import com.bharatpe.lending.util.LoanUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.Objects;

@Service
@Slf4j
public class EnachStageService implements IStageDataService<EnachStateDTO>{


    @Autowired
    private EasyLoanUtil easyLoanUtil;
    @Autowired
    private LoanUtil loanUtil;

    @Autowired
    private LendingApplicationDao lendingApplicationDao;

    @Autowired
    private APIGatewayService apiGatewayService;

    @Autowired
    private EnachHandler enachHandler;

    @Autowired
    private LendingApplicationServiceV3 lendingApplicationServiceV3;

    @Value("${v3.aadharNach.rollout.percent:10}")
    Integer aadharNachRolloutPercentV3;

    @Autowired
    EnachErrorHandingService enachErrorHandingService;

    @Autowired
    ExperianDao experianDao;

    @Autowired
    LoanDetailsV3Service loanDetailsV3Service;


    @Override
    public LendingStateDTO<EnachStateDTO> processCurrentStage(ScopeDataArgs scopeDataArgs) {
        LendingStateDTO<EnachStateDTO> lendingStateDTO = fetchScopedData(scopeDataArgs);
        lendingStateDTO.setLendingViewStates(LendingViewStates.APPLICATION_STATUS_PAGE);
        return lendingStateDTO;
    }

    @Override
    public LendingStateDTO<EnachStateDTO> fetchScopedData(ScopeDataArgs scopeDataArgs) {
        EnachStateDTO enachStateDTO=new EnachStateDTO();
        LendingApplication openApplication = lendingApplicationServiceV3.getLendingApplication(scopeDataArgs.getApplicationId(),scopeDataArgs.getMerchant().getId());
        if (ObjectUtils.isEmpty(openApplication)) {
            log.info("Application not found for {}", scopeDataArgs.getMerchant().getId());
            throw new LoanDetailsException(LoanDetailExceptionEnum.APPLICATION_NOT_FOUND.getErrorCode(),LoanDetailExceptionEnum.APPLICATION_NOT_FOUND.getErrorMessage());
        }

        Experian experian = experianDao.getByMerchantId(scopeDataArgs.getMerchant().getId());
        enachStateDTO.setEnachDeeplink(getEnachDeeplink(openApplication,scopeDataArgs.getToken(),scopeDataArgs.getLoanDetailsV3Request().isIOS()));

        if(loanUtil.isInternalMerchant(openApplication.getMerchantId()) || easyLoanUtil.percentScaleUp(openApplication.getMerchantId(), aadharNachRolloutPercentV3)){
            String lender = openApplication.getLender();
            if("TOPUP".equalsIgnoreCase(openApplication.getLoanType()) || Lender.LDC.name().equalsIgnoreCase(lender) || Lender.MAMTA.name().equalsIgnoreCase(lender) ||
                    Lender.MAMTA0.name().equalsIgnoreCase(lender) || Lender.MAMTA1.name().equalsIgnoreCase(lender) || Lender.MAMTA2.name().equalsIgnoreCase(lender)){
                enachStateDTO.setEnachMode("NB_DC");
            }
            else{
                String enachMode = loanUtil.getEnachBankMode(openApplication.getMerchantId());
                if("BOTH".equalsIgnoreCase(enachMode))
                    enachStateDTO.setEnachMode("NB_DC");
                else if ("NB_DC".equalsIgnoreCase(enachMode))
                    enachStateDTO.setEnachMode("NB_DC");
                else if("ADHAAR".equalsIgnoreCase(enachMode))enachStateDTO.setEnachMode("ADHAAR");
            }
            BharatPeEnachResponseDTO bharatPeEnach = enachHandler.findByMerchantIdAndApplicationId(openApplication.getMerchantId(), openApplication.getId());
            if(ObjectUtils.isEmpty(bharatPeEnach)){
                enachStateDTO.setNachStartedAt(null);
                enachStateDTO.setNachSessionStatus(null);
                enachStateDTO.setNachSessionMode(null);
            }
            else{
                Long nachStartedAtEpoch = bharatPeEnach.getCreatedAt().getTime();
                enachStateDTO.setNachStartedAt(nachStartedAtEpoch);
                enachStateDTO.setNachSessionStatus(bharatPeEnach.getSessionStatus());
                enachStateDTO.setNachSessionMode(bharatPeEnach.getMode());
            }
        }

        if (ApplicationStatus.PENDING_VERIFICATION.name().equalsIgnoreCase(openApplication.getStatus()) || ApplicationStatus.APPROVED.name().equalsIgnoreCase(openApplication.getStatus())) {
            enachStateDTO.setEnachDone("APPROVED".equalsIgnoreCase(openApplication.getNachStatus()) || "PENDING_VERIFICATION".equalsIgnoreCase(openApplication.getNachStatus()));
        }

        if("TOPUP".equalsIgnoreCase(openApplication.getLoanType()) && ApplicationStatus.DRAFT.name().equalsIgnoreCase(openApplication.getStatus())){
            enachStateDTO.setEnachDone("APPROVED".equalsIgnoreCase(openApplication.getNachStatus()));
        }

        if (!StringUtils.isEmpty(enachStateDTO.getEnachDeeplink())) {
            enachStateDTO.setEnachErrorResponse(getEnachError(openApplication, experian));
        }

        if(Objects.nonNull(enachStateDTO.getEnachDone()) && enachStateDTO.getEnachDone()){
            loanDetailsV3Service.saveApplicationViewState(null, openApplication.getId(), LendingViewStates.APPLICATION_STATUS_PAGE);
        }

        enachStateDTO.setBankDetails(loanUtil.getAccountDetails(scopeDataArgs.getMerchant().getId()));

        log.info("Enach Stage Response for {} : {}", scopeDataArgs.getMerchant().getId(), enachStateDTO);
        return new LendingStateDTO<>(enachStateDTO , LendingViewStates.ENACH_PAGE, LendingViewStates.ENACH_PAGE);
    }

    private String getEnachDeeplink(LendingApplication openApplication, String token, boolean isIOS) {
        if (!"TOPUP".equalsIgnoreCase(openApplication.getLoanType()) && !ApplicationStatus.PENDING_VERIFICATION.name().equalsIgnoreCase(openApplication.getStatus())) {
            return null;
        }
        if (easyLoanUtil.isDummyMerchant(openApplication.getMerchantId()) || loanUtil.isEnachDone(openApplication.getMerchantId(), openApplication.getId()) ||
                loanUtil.isEligibleForNachSkip(openApplication, openApplication.getLender())) {
            log.info("Skipping enach for merchantId:{}",openApplication.getMerchantId());
            openApplication.setNachStatus("APPROVED");
            openApplication.setNachType("ENACH");
            openApplication.setNachLender(loanUtil.enachServiceLenderMapper(openApplication.getLender()));
            lendingApplicationDao.save(openApplication);
            return null;
        }

        if (isIOS) return Deeplink.TECHPROCESS;
        return apiGatewayService.getEnachProvider(token, openApplication.getLender(),openApplication.getMerchantId());
    }

    private EnachErrorMessageDTO getEnachError(LendingApplication openApplication, Experian experian) {
        try {
            BharatPeEnachResponseDTO bharatPeEnach = enachHandler.findByMerchantIdAndApplicationId(openApplication.getMerchantId(), openApplication.getId());
            if (bharatPeEnach != null) {
                return enachErrorHandingService.enachErrorResponse(bharatPeEnach, openApplication.getMerchantId(),
                        openApplication, experian);
            }
        } catch (Exception e) {
            log.error("Exception in getEnachError for merchant:{}", openApplication.getMerchantId());
        }
        return null;
    }
}
