package com.bharatpe.lending.loanV3.revamp.scopes;

import com.bharatpe.common.dao.LendingDisbursalStageDao;
import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingDisbursalStage;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.dao.LendingResubmitTaskDao;
import com.bharatpe.lending.common.dto.BharatPeEnachResponseDTO;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.entity.LendingResubmitTask;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.constant.Deeplink;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingCategoryDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.enums.*;
import com.bharatpe.lending.handlers.KycHandler;
import com.bharatpe.lending.loanV2.dto.AdditionalDetails;
import com.bharatpe.lending.loanV2.dto.KycStatusDTO;
import com.bharatpe.lending.loanV2.dto.LoanApplicationDetails;
import com.bharatpe.lending.loanV2.service.LoanDetailsServiceV2;
import com.bharatpe.lending.loanV3.revamp.dto.*;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import com.bharatpe.lending.loanV3.revamp.enums.LoanDetailExceptionEnum;
import com.bharatpe.lending.loanV3.revamp.exception.LoanDetailsException;
import com.bharatpe.lending.loanV3.revamp.services.LendingApplicationServiceV3;
import com.bharatpe.lending.loanV3.revamp.services.LoanDashboardService;
import com.bharatpe.lending.service.APIGatewayService;
import com.bharatpe.lending.util.LoanUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;

@Component
@Slf4j
public class ApplicationStatusStageDataService implements IStageDataService<ApplicationStatusStateDTO>{

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LoanUtil loanUtil;

    @Autowired
    LoanDetailsServiceV2 loanDetailsServiceV2;

    @Autowired
    KycHandler kycHandler;

    @Autowired
    EasyLoanUtil easyLoanUtil;

    @Autowired
    APIGatewayService apiGatewayService;

    @Autowired
    LendingApplicationServiceV3 lendingApplicationServiceV3;

    @Autowired
    private LoanDashboardService loanDashboardService;

    @Override
    public LendingStateDTO<ApplicationStatusStateDTO> processCurrentStage(ScopeDataArgs scopeDataArgs) {
        LendingStateDTO<ApplicationStatusStateDTO> lendingStateDTO = fetchScopedData(scopeDataArgs);
        return lendingStateDTO;
    }

    @Override
    public LendingStateDTO<ApplicationStatusStateDTO> fetchScopedData(ScopeDataArgs scopeDataArgs) {
        ApplicationStatusStateDTO applicationStatusStateDTO = new ApplicationStatusStateDTO();
        try {
            LendingApplication lendingApplication = lendingApplicationServiceV3.getLendingApplication(scopeDataArgs.getApplicationId(), scopeDataArgs.getMerchant().getId());
            if (ObjectUtils.isEmpty(lendingApplication)) {
                log.info("Application not found for {}", scopeDataArgs.getMerchant().getId());
                throw new LoanDetailsException(LoanDetailExceptionEnum.APPLICATION_NOT_FOUND.getErrorCode(),LoanDetailExceptionEnum.APPLICATION_NOT_FOUND.getErrorMessage());
            }

            applicationStatusStateDTO.setApplicationId(lendingApplication.getId());
            applicationStatusStateDTO.setEnachBank(loanUtil.isEnachBank(lendingApplication.getMerchantId()));

            if (applicationStatusStateDTO.getEnachBank()) {
                applicationStatusStateDTO.setEnachDeeplink(getEnachDeeplink(lendingApplication, scopeDataArgs.getToken(), scopeDataArgs.getLoanDetailsV3Request().isIOS()));
            }

            if (ApplicationStatus.PENDING_VERIFICATION.name().equalsIgnoreCase(lendingApplication.getStatus())) {
                applicationStatusStateDTO.setEnachDone("APPROVED".equalsIgnoreCase(lendingApplication.getNachStatus()) || "PENDING_VERIFICATION".equalsIgnoreCase(lendingApplication.getNachStatus()));
            }

            if("TOPUP".equalsIgnoreCase(lendingApplication.getLoanType()) && ApplicationStatus.DRAFT.name().equalsIgnoreCase(lendingApplication.getStatus())){
                applicationStatusStateDTO.setEnachDone("APPROVED".equalsIgnoreCase(lendingApplication.getNachStatus()));
            }

            if (applicationStatusStateDTO.getEnachDeeplink() == null && (ApplicationStatus.PENDING_VERIFICATION.name().equalsIgnoreCase(lendingApplication.getStatus()) || ApplicationStatus.APPROVED.name().equalsIgnoreCase(lendingApplication.getStatus()))) {
                int tat = loanUtil.getApplicationTAT(lendingApplication.getId());
                applicationStatusStateDTO.setTransferDays(tat < 1 ? "next few days." : tat + "-" + (tat + 1) + " Days");
            }
            Long reapplyTime = loanDetailsServiceV2.getReapplyTime(lendingApplication);
            if (Objects.nonNull(reapplyTime)) {
                reapplyTime = reapplyTime > 0 ? reapplyTime : 0;
                applicationStatusStateDTO.setReapplyTime(reapplyTime);
                applicationStatusStateDTO.setReapplyTimeEpoch(LoanUtil.addDays(new Date(), reapplyTime).getTime());
            }
            applicationStatusStateDTO.setReapply(shouldReapply(lendingApplication, reapplyTime));
            return new LendingStateDTO<>(applicationStatusStateDTO , LendingViewStates.APPLICATION_STATUS_PAGE, LendingViewStates.APPLICATION_STATUS_PAGE);
        } catch (Exception e) {
            log.error("Exception in setApplicationDetails for merchant:{}, {}, {}", scopeDataArgs.getMerchant().getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
            throw new LoanDetailsException(LoanDetailExceptionEnum.SOMETHING_WENT_WRONG.getErrorCode(),LoanDetailExceptionEnum.SOMETHING_WENT_WRONG.getErrorMessage());
        }
    }

    private String shouldReapply(LendingApplication openApplication, Long reapplyTime) {

        if (ObjectUtils.isEmpty(reapplyTime)) {
            return null;
        }

        if (ApplicationStatus.REJECTED.name().equalsIgnoreCase(openApplication.getStatus())) {
            if (ApplicationStatus.REJECTED.name().equalsIgnoreCase(openApplication.getManualCibil())) {
                return Reapply.OFFER.name();
            } else if (ApplicationStatus.REJECTED.name().equalsIgnoreCase(openApplication.getManualKyc())) {
                return Reapply.OFFER.name();
            } else if (ApplicationStatus.REJECTED.name().equalsIgnoreCase(openApplication.getCkycStatus())) {
                KycStatusDTO kycStatusDTO = kycHandler.getKycStatus(openApplication.getMerchantId());
                if (KycStatus.REJECTED.equals(kycStatusDTO.getKycStatus()) && KycDocType.PAN_NO.equals(kycStatusDTO.getKycDocType())) {
                    return Reapply.PAN.name();
                } else if ("PANCARD MISMATCH".equalsIgnoreCase(openApplication.getCkycRejectionReason())) {
                    return Reapply.PAN.name();
                } else {
                    return Reapply.OFFER.name();
                }
            } else {
                return Reapply.OFFER.name();
            }
        }
        return null;
    }

    private String getEnachDeeplink(LendingApplication openApplication, String token, boolean isIOS) {
        if (!"TOPUP".equalsIgnoreCase(openApplication.getLoanType()) && !ApplicationStatus.PENDING_VERIFICATION.name().equalsIgnoreCase(openApplication.getStatus())) {
            return null;
        }
        if (easyLoanUtil.isDummyMerchant(openApplication.getMerchantId()) || loanUtil.isEnachDone(openApplication.getMerchantId(), openApplication.getId()) ||
                loanUtil.isEligibleForNachSkip(openApplication, openApplication.getLender())) {
            if(ObjectUtils.isEmpty(openApplication.getNachStatus())){
                loanDashboardService.deleteLoanDashboardCache(openApplication.getMerchantId());
            }
            openApplication.setNachStatus("APPROVED");
            openApplication.setNachType("ENACH");
            openApplication.setNachLender(loanUtil.enachServiceLenderMapper(openApplication.getLender()));
            lendingApplicationDao.save(openApplication);
            return null;
        }
//        BharatPeEnach bharatPeEnach = bharatPeEnachDao.findByMerchantIdAndApplicationId(openApplication.getMerchantId(), openApplication.getId());
//        if (bharatPeEnach != null && BooleanUtils.isTrue(bharatPeEnach.getSkip())) {
//            return null;
//        }
        if (isIOS) return Deeplink.TECHPROCESS;
        return apiGatewayService.getEnachProvider(token, openApplication.getLender(),openApplication.getMerchantId());
    }
}