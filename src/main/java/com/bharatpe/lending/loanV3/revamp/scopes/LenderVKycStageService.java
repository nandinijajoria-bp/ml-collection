package com.bharatpe.lending.loanV3.revamp.scopes;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.dao.LendingApplicationVkycDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.entity.LendingApplicationVkycDetails;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.common.enums.VkycStatus;
import com.bharatpe.lending.lendingplatform.lending.service.VkycServiceV2;
import com.bharatpe.lending.lendingplatform.lending.util.RolloutUtil;
import com.bharatpe.lending.loanV3.revamp.dto.LenderVKycStateDTO;
import com.bharatpe.lending.loanV3.revamp.dto.LendingStateDTO;
import com.bharatpe.lending.loanV3.revamp.dto.ScopeDataArgs;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import com.bharatpe.lending.loanV3.revamp.enums.LoanDetailExceptionEnum;
import com.bharatpe.lending.loanV3.revamp.exception.LoanDetailsException;
import com.bharatpe.lending.loanV3.revamp.services.LendingApplicationServiceV3;
import com.bharatpe.lending.loanV3.revamp.services.LoanDetailsV3Service;
import com.bharatpe.lending.loanV3.services.VKycService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;

import static com.bharatpe.lending.enums.Lender.CREDITSAISON;


@Service
@Slf4j
@RequiredArgsConstructor
public class LenderVKycStageService implements IStageDataService<LenderVKycStateDTO> {
    private final LendingApplicationServiceV3 lendingApplicationServiceV3;
    private final LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;
    private final VKycService vKycService;
    private final LoanDetailsV3Service loanDetailsV3Service;
    private final LendingApplicationVkycDetailsDao lendingApplicationVkycDetailsDao;
    private final RolloutUtil rolloutUtil;
    private final VkycServiceV2 vkycServiceV2;


    @Override
    public LendingStateDTO<LenderVKycStateDTO> processCurrentStage(ScopeDataArgs scopeDataArgs) {
        return fetchScopedData(scopeDataArgs);
    }

    @Override
    public LendingStateDTO<LenderVKycStateDTO> fetchScopedData(ScopeDataArgs scopeDataArgs) {
        try {
            LendingApplication openApplication = lendingApplicationServiceV3.getLendingApplication(scopeDataArgs.getApplicationId(), scopeDataArgs.getMerchant().getId());
            if (ObjectUtils.isEmpty(openApplication) || !Arrays.asList("pending_verification", "approved").contains(openApplication.getStatus())) {
                log.info("Application not found for {}", scopeDataArgs.getMerchant().getId());
                throw new LoanDetailsException(LoanDetailExceptionEnum.APPLICATION_NOT_FOUND.getErrorCode(), LoanDetailExceptionEnum.APPLICATION_NOT_FOUND.getErrorMessage());
            }

            LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(openApplication.getId(), Status.ACTIVE.name(), openApplication.getLender());
            if (ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
                log.info("lender details not found for {}", scopeDataArgs.getMerchant().getId());
                throw new LoanDetailsException(LoanDetailExceptionEnum.LENDER_DETAILS_NOT_FOUND.getErrorCode(), LoanDetailExceptionEnum.LENDER_DETAILS_NOT_FOUND.getErrorMessage());
            }
            LenderVKycStateDTO lenderVKycStateDTO = new LenderVKycStateDTO();
            lenderVKycStateDTO.setLender(openApplication.getLender());
            lenderVKycStateDTO.setApplicationId(openApplication.getId());
            lenderVKycStateDTO.setVkycCompleted(false);
            // If the application is not eligible for vKYC, we return the application status page
            if (!vKycService.isVkycEnabled(openApplication.getMerchantId(), openApplication.getLender())) {
                log.info("vKyc is not enabled for merchantId: {} and lender: {}, returning APPLICATION STATUS scope", openApplication.getMerchantId(), openApplication.getLender());
                loanDetailsV3Service.saveApplicationViewState(null, openApplication.getId(), LendingViewStates.APPLICATION_STATUS_PAGE);
                return new LendingStateDTO<>(lenderVKycStateDTO, LendingViewStates.APPLICATION_STATUS_PAGE, LendingViewStates.LENDER_VKYC_PAGE);
            }
            // After eligibility check, we save the application view state to LENDER_VKYC_PAGE
            loanDetailsV3Service.saveApplicationViewState(null, openApplication.getId(), LendingViewStates.LENDER_VKYC_PAGE);
            LendingApplicationVkycDetails lendingApplicationVkycDetails = lendingApplicationVkycDetailsDao.findByApplicationIdAndLender(openApplication.getId(), openApplication.getLender())
                    .orElseGet(() -> vKycService.createPendingVkycDetailsRecord(openApplication)); // Then we create a new vKYC details record if it doesn't exist

            updateApplicationVKycDetails(openApplication, lendingApplicationVkycDetails, lendingApplicationLenderDetails, scopeDataArgs.getLoanDetailsV3Request().getAppVersion());
            lenderVKycStateDTO.setVKycStatus(lendingApplicationVkycDetails.getStatus());
            lenderVKycStateDTO.setRejectReason(lendingApplicationVkycDetails.getRejectReason());
            lenderVKycStateDTO.setVkycEligible(lendingApplicationVkycDetails.getVkycEligible());
            lenderVKycStateDTO.setDkycEligible(lendingApplicationVkycDetails.getDkycEligible());
            if (VkycStatus.getTerminatedVkycStatusList().contains(lendingApplicationVkycDetails.getStatus())) {
                // If the vKYC status is terminated, we return the application status page with the vKYC status and completion flag
                // VKYC_COMPLETED, DKYC_COMPLETED, VKYC_HARD_FAILED, VKYC_REJECTED, VKYC_SKIPPED

                log.info("vKYC status is terminated for applicationId: {}, status: {}, returning APPLICATION STATUS scope", openApplication.getId(), lendingApplicationVkycDetails.getStatus());
                lenderVKycStateDTO.setVkycCompleted(VkycStatus.getSuccessVkycStatusList().contains(lendingApplicationVkycDetails.getStatus()));
                loanDetailsV3Service.saveApplicationViewState(null, openApplication.getId(), LendingViewStates.APPLICATION_STATUS_PAGE);
                return new LendingStateDTO<>(lenderVKycStateDTO, LendingViewStates.APPLICATION_STATUS_PAGE, LendingViewStates.LENDER_VKYC_PAGE);
            }
            if (vKycService.rejectApplicationIfRequired(openApplication, lendingApplicationVkycDetails, lendingApplicationLenderDetails)) {
                // If the application is rejected, we set the vKYC status and reject reason, then return the application status page
                log.info("vKYC application is rejected/skipped for applicationId: {}, status: {}, returning APPLICATION STATUS scope", openApplication.getId(), lendingApplicationVkycDetails.getStatus());
                lenderVKycStateDTO.setVKycStatus(lendingApplicationVkycDetails.getStatus());
                lenderVKycStateDTO.setRejectReason(lendingApplicationVkycDetails.getRejectReason());
                loanDetailsV3Service.saveApplicationViewState(null, openApplication.getId(), LendingViewStates.APPLICATION_STATUS_PAGE);
                return new LendingStateDTO<>(lenderVKycStateDTO, LendingViewStates.APPLICATION_STATUS_PAGE, LendingViewStates.LENDER_VKYC_PAGE);
            }
            log.info("vKYC is for applicationId: {}, merchantId: {}, status: {}, returning again LENDER_VKYC_PAGE scope", openApplication.getId(), openApplication.getMerchantId(), lendingApplicationVkycDetails.getStatus());
            return new LendingStateDTO<>(lenderVKycStateDTO, LendingViewStates.LENDER_VKYC_PAGE, LendingViewStates.LENDER_VKYC_PAGE);
        } catch (Exception ex) {
            log.error("Exception while initiating vKyc for merchant:{} {} {}", scopeDataArgs.getMerchant().getId(), ex.getMessage(), Arrays.asList(ex.getStackTrace()));
            throw new LoanDetailsException(LoanDetailExceptionEnum.SOMETHING_WENT_WRONG.getErrorCode(), LoanDetailExceptionEnum.SOMETHING_WENT_WRONG.getErrorMessage());
        }
    }

    // This method updates the vKYC details for the lending application.
    private void updateApplicationVKycDetails(LendingApplication lendingApplication, LendingApplicationVkycDetails vkycDetails, LendingApplicationLenderDetails lenderDetails, Integer appVersion) {
        try {
            log.info("Updating vKyc details for applicationId: {}, lender: {}, merchantId: {}", lendingApplication.getId(), lendingApplication.getLender(), lendingApplication.getMerchantId());
            if (VkycStatus.getTerminatedVkycStatusList().contains(vkycDetails.getStatus())) {
                log.info("vkyc already {} of {} for applicationId {} returning ", vkycDetails.getStatus(), lendingApplication.getLender(), lendingApplication.getId());
                return;
            }
            // This is new flow for CreditSaison vKYC :
            if (rolloutUtil.isEligibleForCreditSaisonVkyc(lendingApplication.getMerchantId()) && CREDITSAISON.name().equalsIgnoreCase(lendingApplication.getLender())) {
                log.info("Updating vKyc details for Credit Saison for applicationId: {}", lendingApplication.getId());
                vkycServiceV2.updateVkycDetailsForCreditSaison(lendingApplication, vkycDetails, lenderDetails, appVersion);
                return;
            }

            // This below code is for existing vKYC flow i.e PayU
            if (VkycStatus.VKYC_IN_PROGRESS.equals(vkycDetails.getStatus())) {
                vKycService.statusCheck(lendingApplication, vkycDetails, lenderDetails);
                return;
            }
            vKycService.setVkycEligibility(vkycDetails, lenderDetails.getLeadId());
            if (vKycService.isDisableInitiateVkycSession(vkycDetails, appVersion)) {
                vkycDetails.setVkycEligible(false);
            }
            lendingApplicationVkycDetailsDao.save(vkycDetails);
        } catch (Exception e) {
            log.info("Exception while updating vKyc details of {} for applicationId {}", lendingApplication.getLender(), lendingApplication.getId());
        }
    }

}
