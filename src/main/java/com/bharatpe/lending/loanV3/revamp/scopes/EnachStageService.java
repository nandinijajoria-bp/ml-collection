package com.bharatpe.lending.loanV3.revamp.scopes;



import com.bharatpe.common.dao.ExperianDao;
import com.bharatpe.common.entities.Experian;
import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.Handler.EnachHandler;
import com.bharatpe.lending.common.dao.LendingRiskVariablesSnapshotDao;
import com.bharatpe.lending.common.dto.BharatPeEnachResponseDTO;
import com.bharatpe.lending.common.entity.LendingRiskVariablesSnapshot;
import com.bharatpe.lending.common.enums.FunnelEnums;
import com.bharatpe.lending.common.query.dao.LendingPaymentScheduleDaoSlave;
import com.bharatpe.lending.common.query.entity.LendingPaymentScheduleSlave;
import com.bharatpe.lending.common.service.FunnelService;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.constant.Deeplink;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.NachMandateEligibilityConfigDao;
import com.bharatpe.lending.dto.EnachErrorMessageDTO;
import com.bharatpe.lending.entity.NachMandateEligibilityConfig;
import com.bharatpe.lending.enums.ApplicationStatus;
import com.bharatpe.lending.enums.EnachMode;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.loanV2.dto.BankAccountDetails;
import com.bharatpe.lending.loanV3.revamp.dto.EnachModeDTO;
import com.bharatpe.lending.loanV3.revamp.dto.EnachStateDTO;
import com.bharatpe.lending.loanV3.revamp.dto.LendingStateDTO;
import com.bharatpe.lending.loanV3.revamp.dto.ScopeDataArgs;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import com.bharatpe.lending.loanV3.revamp.enums.LoanDetailExceptionEnum;
import com.bharatpe.lending.loanV3.revamp.exception.LoanDetailsException;
import com.bharatpe.lending.loanV3.revamp.services.LendingApplicationServiceV3;
import com.bharatpe.lending.loanV3.revamp.services.LoanDashboardService;
import com.bharatpe.lending.loanV3.revamp.services.LoanDetailsV3Service;
import com.bharatpe.lending.service.APIGatewayService;
import com.bharatpe.lending.service.EnachErrorHandingService;
import com.bharatpe.lending.service.MerchantLoansService;
import com.bharatpe.lending.service.PaymentBankService;
import com.bharatpe.lending.loanV3.services.VKycService;
import com.bharatpe.lending.util.LoanUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.*;

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

    @Autowired
    private LoanDashboardService loanDashboardService;

    @Autowired
    private PaymentBankService paymentBankService;

    @Autowired
    LendingPaymentScheduleDaoSlave lendingPaymentScheduleDaoSlave;

    @Autowired
    NachMandateEligibilityConfigDao nachMandateEligibilityConfigDao;

    @Value("${renach.rollout.date}")
    String renachRolloutDate;

    @Autowired
    MerchantLoansService merchantLoansService;

    @Value("${upinach.max.loan.amount:50000}")
    Double maxLoanAmountForNachUPI;

    @Value("${upi.nach.rollout.percent:10}")
    Integer upiNachRolloutPercent;

    @Value("${upi.app.version:709}")
    Integer upiAppVersion;

    @Autowired
    FunnelService funnelService;

    @Value("${upi.nach.lender:-}")
    private Set<String> upiNachLender;

    @Value("${payment.bank.change.flow.applicable:false}")
    private boolean isPaymentBankChangeFlowApplicable;

    @Autowired
    LendingRiskVariablesSnapshotDao lendingRiskVariablesSnapshotDao;

    @Autowired
    VKycService vkycService;


    @Override
    public LendingStateDTO<EnachStateDTO> processCurrentStage(ScopeDataArgs scopeDataArgs) {
        LendingStateDTO<EnachStateDTO> lendingStateDTO = fetchScopedData(scopeDataArgs);
        boolean isPaymentBankFlow = lendingStateDTO.getData().isPaymentBank() || lendingStateDTO.getData().isHasLinkedPaymentBank();
        if (lendingStateDTO.getData().isTopup()) {
            lendingStateDTO.setLendingViewStates(LendingViewStates.AGREEMENT_PAGE);
        } else {
            if (isPaymentBankFlow) {
                lendingStateDTO.setLendingViewStates(LendingViewStates.ENACH_PAGE);
                log.info("Setting LendingViewStates to ENACH_PAGE for merchantId: {}", scopeDataArgs.getMerchant().getId());
            } else {
                lendingStateDTO.setLendingViewStates(vkycService.getLenderVkycPageOrDefault(LendingViewStates.APPLICATION_STATUS_PAGE, lendingStateDTO.getData().getMerchantId(), lendingStateDTO.getData().getLender(), false));
            }
        }
        return lendingStateDTO;
    }

    @Override
    public LendingStateDTO<EnachStateDTO> fetchScopedData(ScopeDataArgs scopeDataArgs) {
        EnachStateDTO enachStateDTO=new EnachStateDTO();
        enachStateDTO.setMerchantId(scopeDataArgs.getMerchant().getId());
        if (loanUtil.reNachEnabledMerchants().contains(scopeDataArgs.getMerchant().getId()) ) {
            LendingPaymentScheduleSlave activeLoan =  lendingPaymentScheduleDaoSlave.findByMerchantIdAndStatus(scopeDataArgs.getMerchant().getId(), Collections.singletonList("ACTIVE"));

            if (!ObjectUtils.isEmpty(activeLoan)) {
                if (merchantLoansService.showRenachBanner(scopeDataArgs.getMerchant().getId(), activeLoan.getNbfc(), false)) {
                    return fetchRenachData(activeLoan.getApplicationId(), activeLoan.getMerchantId(), scopeDataArgs.getToken(), scopeDataArgs);
                }
            }

        }

        LendingApplication openApplication = lendingApplicationServiceV3.getLendingApplication(scopeDataArgs.getApplicationId(),scopeDataArgs.getMerchant().getId());
        if (ObjectUtils.isEmpty(openApplication)) {
            log.info("Application not found for {}", scopeDataArgs.getMerchant().getId());
            throw new LoanDetailsException(LoanDetailExceptionEnum.APPLICATION_NOT_FOUND.getErrorCode(),LoanDetailExceptionEnum.APPLICATION_NOT_FOUND.getErrorMessage());
        }

        enachStateDTO.setLender(openApplication.getLender());


        Experian experian = experianDao.getByMerchantId(scopeDataArgs.getMerchant().getId());
        enachStateDTO.setEnachDeeplink(getEnachDeeplinkOrUpdateNachStatus(openApplication,scopeDataArgs.getToken(),scopeDataArgs.getLoanDetailsV3Request().isIOS()));

        if(loanUtil.isInternalMerchant(openApplication.getMerchantId()) || easyLoanUtil.percentScaleUp(openApplication.getMerchantId(), aadharNachRolloutPercentV3)){
            String lender = openApplication.getLender();
            if("TOPUP".equalsIgnoreCase(openApplication.getLoanType()) || Lender.LDC.name().equalsIgnoreCase(lender) || Lender.MAMTA.name().equalsIgnoreCase(lender) ||
                    Lender.MAMTA0.name().equalsIgnoreCase(lender) || Lender.MAMTA1.name().equalsIgnoreCase(lender) || Lender.MAMTA2.name().equalsIgnoreCase(lender)){
                enachStateDTO.setEnachModes(Arrays.asList(new EnachModeDTO(EnachMode.NB_DC.name(), true, null)));
            }
            else{
                List<EnachModeDTO> enachModes = getAvailableEnachMode(openApplication, scopeDataArgs.getLoanDetailsV3Request().getAppVersion());
                enachStateDTO.setEnachModes(enachModes);
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
            enachStateDTO.setTopup(true);
        }

        if (!StringUtils.isEmpty(enachStateDTO.getEnachDeeplink())) {
            enachStateDTO.setEnachErrorResponse(getEnachError(openApplication, experian));
        }

        if(Objects.nonNull(enachStateDTO.getEnachDone()) && enachStateDTO.getEnachDone()){
            if(LoanType.TOPUP.name().equalsIgnoreCase(openApplication.getLoanType())){
                loanDetailsV3Service.saveApplicationViewState(null, openApplication.getId(), LendingViewStates.AGREEMENT_PAGE);
            }
            else loanDetailsV3Service.saveApplicationViewState(null, openApplication.getId(), vkycService.getLenderVkycPageOrDefault(LendingViewStates.APPLICATION_STATUS_PAGE, openApplication.getMerchantId(), openApplication.getLender(), LoanType.TOPUP.name().equalsIgnoreCase(openApplication.getLoanType())));
        }
        BankAccountDetails accountDetails = loanUtil.getAccountDetails(scopeDataArgs.getMerchant().getId());
        enachStateDTO.setBankDetails(accountDetails);
        if(isPaymentBankChangeFlowApplicable && !LoanType.TOPUP.name().equalsIgnoreCase(openApplication.getLoanType())){
            if(paymentBankService.isPaymentBank(openApplication.getMerchantId(), accountDetails)){
                enachStateDTO.setHasLinkedPaymentBank(true);
                log.info("Setting setHasLinkedPaymentBank to true for merchantId: {}", scopeDataArgs.getMerchant().getId());
                }
            enachStateDTO.setPaymentBank(paymentBankService.changePaymentAccount(openApplication, accountDetails));
            log.info("Payment Bank Change flow is applicable for merchantId: {} and setPaymentBank is: {}", scopeDataArgs.getMerchant().getId(), enachStateDTO.isPaymentBank());
        }
        log.info("Enach Stage Response for {} : {}", scopeDataArgs.getMerchant().getId(), enachStateDTO);
        return new LendingStateDTO<>(enachStateDTO , LendingViewStates.ENACH_PAGE, LendingViewStates.ENACH_PAGE);
    }

    public LendingStateDTO<EnachStateDTO> fetchRenachData(Long applicationId, Long merchantId, String token, ScopeDataArgs scopeDataArgs) {

        log.info("fetchRenachData for applicationId : {} merchantId : {} scopeDataArgs : {}", applicationId, merchantId, scopeDataArgs);

        EnachStateDTO enachStateDTO=new EnachStateDTO();
        LendingApplication lendingApplication = lendingApplicationServiceV3.getLendingApplication(applicationId,merchantId);



        if (ObjectUtils.isEmpty(lendingApplication)) {
            log.info("Application not found for {}", merchantId);
            throw new LoanDetailsException(LoanDetailExceptionEnum.APPLICATION_NOT_FOUND.getErrorCode(),LoanDetailExceptionEnum.APPLICATION_NOT_FOUND.getErrorMessage());
        }

        lendingApplication.setNachStatus(null);
        lendingApplication.setNachReferenceNumber(null);
        lendingApplicationDao.save(lendingApplication);
        enachStateDTO.setLender(lendingApplication.getLender());

        Experian experian = experianDao.getByMerchantId(merchantId);
        enachStateDTO.setEnachDeeplink(getEnachDeeplinkForRenach(lendingApplication,token,scopeDataArgs.getLoanDetailsV3Request().isIOS()));

        BharatPeEnachResponseDTO bharatPeEnach = enachHandler.findByMerchantIdAndApplicationId(lendingApplication.getMerchantId(), lendingApplication.getId());


        if(loanUtil.isInternalMerchant(lendingApplication.getMerchantId()) || easyLoanUtil.percentScaleUp(lendingApplication.getMerchantId(), aadharNachRolloutPercentV3)){
            String lender = lendingApplication.getLender();
            if("TOPUP".equalsIgnoreCase(lendingApplication.getLoanType()) || Lender.LDC.name().equalsIgnoreCase(lender) || Lender.MAMTA.name().equalsIgnoreCase(lender) ||
              Lender.MAMTA0.name().equalsIgnoreCase(lender) || Lender.MAMTA1.name().equalsIgnoreCase(lender) || Lender.MAMTA2.name().equalsIgnoreCase(lender)){
                enachStateDTO.setEnachModes(Arrays.asList(new EnachModeDTO(EnachMode.NB_DC.name(), true, null)));
            }
            else {
                List<EnachModeDTO> enachModes = getAvailableEnachMode(lendingApplication, scopeDataArgs.getLoanDetailsV3Request().getAppVersion());
                enachStateDTO.setEnachModes(enachModes);
            }

            final Date renachRolloutDate = loanUtil.parseRolloutDate(this.renachRolloutDate);

            if(ObjectUtils.isEmpty(bharatPeEnach) || bharatPeEnach.getCreatedAt().before(renachRolloutDate)){
                log.info("Enach initiated before rollout date for merchantId : {} applicationId : {} bharatPeEnach : {}", merchantId, applicationId, bharatPeEnach);
                enachStateDTO.setNachStartedAt(null);
                enachStateDTO.setNachSessionStatus(null);
                enachStateDTO.setNachSessionMode(null);
                bharatPeEnach = null;
            }
            else{
                Long nachStartedAtEpoch = bharatPeEnach.getCreatedAt().getTime();
                enachStateDTO.setNachStartedAt(nachStartedAtEpoch);
                enachStateDTO.setNachSessionStatus(bharatPeEnach.getSessionStatus());
                enachStateDTO.setNachSessionMode(bharatPeEnach.getMode());
            }
        }

        if (ApplicationStatus.PENDING_VERIFICATION.name().equalsIgnoreCase(lendingApplication.getStatus()) || ApplicationStatus.APPROVED.name().equalsIgnoreCase(lendingApplication.getStatus())) {
            enachStateDTO.setEnachDone("APPROVED".equalsIgnoreCase(lendingApplication.getNachStatus()) || "PENDING_VERIFICATION".equalsIgnoreCase(lendingApplication.getNachStatus()));
        }

        if("TOPUP".equalsIgnoreCase(lendingApplication.getLoanType()) && ApplicationStatus.DRAFT.name().equalsIgnoreCase(lendingApplication.getStatus())){
            enachStateDTO.setEnachDone("APPROVED".equalsIgnoreCase(lendingApplication.getNachStatus()));
            enachStateDTO.setTopup(true);
        }

        if (!StringUtils.isEmpty(enachStateDTO.getEnachDeeplink())) {
            enachStateDTO.setEnachErrorResponse(getEnachErrorForReEnach(lendingApplication, experian, bharatPeEnach));
        }

        enachStateDTO.setBankDetails(loanUtil.getAccountDetails(scopeDataArgs.getMerchant().getId()));

        log.info("ReEnach Stage Response for {} : {}", scopeDataArgs.getMerchant().getId(), enachStateDTO);
        return new LendingStateDTO<>(enachStateDTO , LendingViewStates.ENACH_PAGE, LendingViewStates.ENACH_PAGE);
    }

    /**
     *
     * @param openApplication application to process
     * @param token
     * @param isIOS flag to check request is from ios or not
     * @return null in case of skip nach flow else deeplink (datasource nach service). in case of skip nach, update application status.
     */

    private String getEnachDeeplinkOrUpdateNachStatus(LendingApplication openApplication, String token, boolean isIOS) {
        if (!"TOPUP".equalsIgnoreCase(openApplication.getLoanType()) && !ApplicationStatus.PENDING_VERIFICATION.name().equalsIgnoreCase(openApplication.getStatus())) {
            return null;
        }
        if (easyLoanUtil.isDummyMerchant(openApplication.getMerchantId()) || loanUtil.isEnachDone(openApplication.getMerchantId(), openApplication.getId()) ||
                loanUtil.isEligibleForNachSkip(openApplication, openApplication.getLender())) {
            if(ObjectUtils.isEmpty(openApplication.getNachStatus())){
                loanDashboardService.deleteLoanDashboardCache(openApplication.getMerchantId());
            }
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

    private String getEnachDeeplinkForRenach(LendingApplication openApplication, String token, boolean isIOS) {
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

    private EnachErrorMessageDTO getEnachErrorForReEnach(LendingApplication openApplication, Experian experian, BharatPeEnachResponseDTO bharatPeEnach) {
        try {
            if (bharatPeEnach != null) {
                return enachErrorHandingService.enachErrorResponse(bharatPeEnach, openApplication.getMerchantId(),
                  openApplication, experian);
            }
        } catch (Exception e) {
            log.error("Exception in getEnachError for merchant:{}", openApplication.getMerchantId());
        }
        return null;
    }

    private List<EnachModeDTO> getAvailableEnachMode(LendingApplication lendingApplication, Integer appVersion) {

        log.info("Fetching EnachModes for merchant: {}", lendingApplication.getMerchantId());
        List<EnachModeDTO> enachModes = loanUtil.getEnachModes(lendingApplication.getMerchantId());

        long last24hrs = System.currentTimeMillis() - (24 * 60 * 60 * 1000); // for last 24 hours record.
        String finalLender = loanUtil.enachServiceLenderMapper(lendingApplication.getLender());
        Integer upiNachAttempts = enachHandler.getNachAttemptsbyMode(lendingApplication.getId(), lendingApplication.getMerchantId(), finalLender, EnachMode.UPI.name(), last24hrs);

        if (upiNachAttempts == null) {
            log.error("getNachAttemptsbyMode API failed nachAttempts: {}", upiNachAttempts);
            upiNachAttempts = 0;
        }
        funnelService.submitEvent(lendingApplication.getMerchantId(), null, lendingApplication.getId(),
                FunnelEnums.StageId.NACH, FunnelEnums.StageEvent.ATTEMPTS, upiNachAttempts.toString(), "UPI");

        if (upiNachAttempts >= 3) {
            enachModes.stream()
                    .filter(mode -> mode.getName().equalsIgnoreCase(EnachMode.UPI.name()))
                    .forEach(mode -> {
                        mode.setEnabled(false);
                        mode.setReason("Retry after 24 hours");
                    });
        }

        LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = lendingRiskVariablesSnapshotDao.findByApplicationId(lendingApplication.getId());
        if (lendingRiskVariablesSnapshot == null) {
            log.info("No LendingRiskVariablesSnapshot found for applicationId: {}", lendingApplication.getId());
        }

        String loanSegment = (lendingRiskVariablesSnapshot != null) ? lendingRiskVariablesSnapshot.getLoanSegment() : null;

        NachMandateEligibilityConfig nachMandateEligibilityConfig = nachMandateEligibilityConfigDao.findNachMandateEligibilityConfigLenderAndLoanSegmentAndLoanAmountWise(lendingApplication.getLender(), lendingApplication.getLoanAmount(), loanSegment);

        // UPI NACH for Loan Amount <= 50000
        if (ObjectUtils.isEmpty(nachMandateEligibilityConfig)
            || !nachMandateEligibilityConfig.getStatus()
            || !nachMandateEligibilityConfig.getUpiAutopayNachRequired()
            || !easyLoanUtil.percentScaleUp(lendingApplication.getMerchantId(), upiNachRolloutPercent)
            || (!ObjectUtils.isEmpty(appVersion) && appVersion < upiAppVersion)
            || !upiNachLender.contains(lendingApplication.getLender())
            || loanUtil.checkIfUpiAutoPayNachNotRequired(lendingApplication)) {

            enachModes.removeIf(mode -> mode.getName().equals(EnachMode.UPI.name()));
            log.info("UPI removed because, Amount > {} or App version doesn't support UPI NACH appversion: {}", maxLoanAmountForNachUPI, appVersion);
        }
        log.info("Available EnachModes for merchant: {} are: {}", lendingApplication.getMerchantId(), enachModes);
        return enachModes;
    }
}
