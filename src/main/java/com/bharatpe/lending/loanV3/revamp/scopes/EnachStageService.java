package com.bharatpe.lending.loanV3.revamp.scopes;



import com.bharatpe.common.dao.ExperianDao;
import com.bharatpe.common.entities.Experian;
import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.Constants.AutoPayStatusEnum;
import com.bharatpe.lending.common.Handler.EnachHandler;
import com.bharatpe.lending.common.dao.AutoPayUPIDao;
import com.bharatpe.lending.common.dao.LendingRiskVariablesSnapshotDao;
import com.bharatpe.lending.common.dto.BharatPeEnachResponseDTO;
import com.bharatpe.lending.common.entity.AutoPayUPI;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.entity.LendingRiskVariablesSnapshot;
import com.bharatpe.lending.common.enums.FunnelEnums;
import com.bharatpe.lending.common.enums.MandateType;
import com.bharatpe.lending.common.query.dao.LendingPaymentScheduleDaoSlave;
import com.bharatpe.lending.common.query.entity.LendingPaymentScheduleSlave;
import com.bharatpe.lending.common.service.FunnelService;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.constant.Deeplink;
import com.bharatpe.lending.constant.PaymentConstants;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.NachMandateEligibilityConfigDao;
import com.bharatpe.lending.dto.EnachErrorMessageDTO;
import com.bharatpe.lending.entity.NachMandateEligibilityConfig;
import com.bharatpe.lending.enums.*;
import com.bharatpe.lending.loanV2.dto.BankAccountDetails;
import com.bharatpe.lending.loanV3.revamp.dto.EnachModeDTO;
import com.bharatpe.lending.loanV3.revamp.dto.EnachStateDTO;
import com.bharatpe.lending.loanV3.revamp.dto.LendingStateDTO;
import com.bharatpe.lending.loanV3.revamp.dto.NachDetail;
import com.bharatpe.lending.loanV3.revamp.dto.ScopeDataArgs;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import com.bharatpe.lending.loanV3.revamp.enums.LoanDetailExceptionEnum;
import com.bharatpe.lending.loanV3.revamp.enums.NachStatus;
import com.bharatpe.lending.loanV3.revamp.exception.LoanDetailsException;
import com.bharatpe.lending.loanV3.revamp.services.EnachStageHelper;
import com.bharatpe.lending.loanV3.revamp.services.LendingApplicationServiceV3;
import com.bharatpe.lending.loanV3.revamp.services.LoanDashboardService;
import com.bharatpe.lending.loanV3.revamp.services.LoanDetailsV3Service;
import com.bharatpe.lending.service.APIGatewayService;
import com.bharatpe.lending.service.EnachErrorHandingService;
import com.bharatpe.lending.service.MerchantLoansService;
import com.bharatpe.lending.service.PaymentBankService;
import com.bharatpe.lending.loanV3.services.VKycService;
import com.bharatpe.lending.service.helper.MandateRegistrationHelper;
import com.bharatpe.lending.util.LoanUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import javax.validation.constraints.NotNull;
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

    @Value("${upi.nach.rollout.percent:10}")
    Integer upiNachRolloutPercent;

    @Value("${upi.app.version:709}")
    Integer upiAppVersion;

    @Autowired
    FunnelService funnelService;

    @Value("${upi.nach.lender:-}")
    private Set<String> upiNachLender;

    @Value("${payment.bank.change.flow.applicable:false}")
    boolean isPaymentBankChangeFlowApplicable;

    @Autowired
    LendingRiskVariablesSnapshotDao lendingRiskVariablesSnapshotDao;

    @Autowired
    VKycService vkycService;

    @Autowired
    private MandateRegistrationHelper mandateRegistrationHelper;

    @Autowired
    private AutoPayUPIDao autoPayUPIDao;

    @Autowired
    private EnachStageHelper enachStageHelper;

    @Value("${digio.upi.autopay.progress.wait.time:300}")
    private int digioUpiAutoPayWaitTime;

    @Value("${digio.upi.autopay.progress.poll.time:300}")
    private int digioUpiAutopayPollTime;

    @Value("${digio.upi.autopay.wait.screen.rollout:0}")
    private int nachWaitScreenRolloutPercentage;

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
        if(isActiveApplicationAutoPaySetupFlow(scopeDataArgs)){
            return getScopeDataForNachMigrationFlow(scopeDataArgs.getApplicationId(), enachStateDTO);
        }

        LendingApplication openApplication = lendingApplicationServiceV3.getLendingApplication(scopeDataArgs.getApplicationId(),scopeDataArgs.getMerchant().getId());
        if (ObjectUtils.isEmpty(openApplication)) {
            log.info("Application not found for {}", scopeDataArgs.getMerchant().getId());
            throw new LoanDetailsException(LoanDetailExceptionEnum.APPLICATION_NOT_FOUND.getErrorCode(),LoanDetailExceptionEnum.APPLICATION_NOT_FOUND.getErrorMessage());
        }
        LendingApplicationDetails lendingApplicationDetails = lendingApplicationServiceV3.getLendingApplicationDetailsByApplicationId(openApplication.getId());
        if (ObjectUtils.isEmpty(lendingApplicationDetails)) {
            log.info("Application details not found for merchant{} and application:{}", scopeDataArgs.getMerchant().getId(), openApplication.getId());
            throw new LoanDetailsException(LoanDetailExceptionEnum.APPLICATION_DETAILS_NOT_FOUND.getErrorCode(),LoanDetailExceptionEnum.APPLICATION_DETAILS_NOT_FOUND.getErrorMessage());
        }

        enachStateDTO.setLender(openApplication.getLender());


        Experian experian = experianDao.getByMerchantId(scopeDataArgs.getMerchant().getId());
        NachDetail nachDetail;
        if(easyLoanUtil.percentScaleUp(openApplication.getMerchantId(), nachWaitScreenRolloutPercentage)
            && MandateType.DIGIO_UPI.equals(lendingApplicationDetails.getMandateType())){
            nachDetail = getNachDetailForDigioUpi(openApplication);
            if(PaymentConstants.SUCCESS.equalsIgnoreCase(nachDetail.getMandateStatus())){
                enachStageHelper.processNachSuccessStatus(openApplication, lendingApplicationDetails);
            }else{
                enachStateDTO.setEnachDeeplink(apiGatewayService.getEnachProvider( openApplication.getLender(),openApplication.getMerchantId()));
            }
            enachStateDTO.setNachDetail(nachDetail);
        }else {
            nachDetail = new NachDetail();
            nachDetail.setDailyInstalmentAmount(openApplication.getEdi());
            enachStateDTO.setNachDetail(nachDetail);
            enachStateDTO.setEnachDeeplink(getEnachDeeplinkOrUpdateNachStatus(openApplication,scopeDataArgs.getToken(),scopeDataArgs.getLoanDetailsV3Request().isIOS()));
        }

        if(loanUtil.isInternalMerchant(openApplication.getMerchantId()) || easyLoanUtil.percentScaleUp(openApplication.getMerchantId(), aadharNachRolloutPercentV3)){
            String lender = openApplication.getLender();

            if (!ObjectUtils.isEmpty(lendingApplicationDetails) && MandateType.DIGIO_UPI.equals(lendingApplicationDetails.getMandateType())) {
                enachStateDTO.setEnachModes(Collections.singletonList(new EnachModeDTO(EnachMode.UPI.name(), true, null)));
                enachStateDTO.setNativeMandateRequired(true);
            } else if("TOPUP".equalsIgnoreCase(openApplication.getLoanType()) || Lender.LDC.name().equalsIgnoreCase(lender) || Lender.MAMTA.name().equalsIgnoreCase(lender) ||
                    Lender.MAMTA0.name().equalsIgnoreCase(lender) || Lender.MAMTA1.name().equalsIgnoreCase(lender) || Lender.MAMTA2.name().equalsIgnoreCase(lender)){
                enachStateDTO.setEnachModes(Arrays.asList(new EnachModeDTO(EnachMode.NB_DC.name(), true, null)));
            } else{
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
        enachStateDTO.setEnachDone(loanUtil.isMandateDone(openApplication, lendingApplicationDetails));
        enachStateDTO.setTopup(LoanType.TOPUP.name().equalsIgnoreCase(openApplication.getLoanType()));
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

    private NachDetail getNachDetailForDigioUpi(@NotNull LendingApplication openApplication) {
        NachDetail nachDetail = new NachDetail();
        nachDetail.setRetryEligible(true);
        nachDetail.setLoanAmount(openApplication.getLoanAmount());
        nachDetail.setLoanType(openApplication.getLoanType());
        nachDetail.setTenure(openApplication.getTenureInMonths());
        nachDetail.setDailyInstalmentAmount(openApplication.getEdi());
        AutoPayUPI autoPayUPI = autoPayUPIDao.findTop1ByApplicationIdOrderByIdDesc(openApplication.getId());
        log.info("Fetched AutoPayUPI for applicationId: {} is: {}", openApplication.getId(), autoPayUPI);
        if(Objects.isNull(autoPayUPI)){
            nachDetail.setMandateStatus(PaymentConstants.INIT);
            return nachDetail;
        }
        String mandateStatus = PaymentConstants.UPI_AUTOPAY_MANDATE_STATUS_MAP.get(autoPayUPI.getStatus());
        if(PaymentConstants.INPROGRESS.equalsIgnoreCase(mandateStatus)) {
            long createdTimeSeconds = autoPayUPI.getCreatedAt().toInstant().getEpochSecond();
            long currentTimeSeconds = System.currentTimeMillis() / 1000;
            long diff = currentTimeSeconds - createdTimeSeconds;
            if (diff <= digioUpiAutoPayWaitTime) {
                log.info("Showing waiting screen for digio upiautopay mandate for applicationId: {}, mandateId: {}, createdAt: {}, currentTime: {}",
                        openApplication.getId(), autoPayUPI.getMandateId(), createdTimeSeconds, currentTimeSeconds);
                nachDetail.setWaitTime(digioUpiAutoPayWaitTime);
                nachDetail.setPollingTime(digioUpiAutopayPollTime);
            } else {
                log.info("Digio UPI AutoPay mandate registration TAT exceeded for applicationId: {}, mandateId: {}, createdAt: {}, currentTime: {}",
                        openApplication.getId(), autoPayUPI.getMandateId(), createdTimeSeconds, currentTimeSeconds);
                autoPayUPI.setStatus(AutoPayStatusEnum.FAILED);
                autoPayUPI.setErrorCode(PaymentConstants.TAT_EXCEEDED_ERROR_CODE);
                autoPayUPIDao.save(autoPayUPI);
                mandateStatus = PaymentConstants.FAILED;
            }
        }
        nachDetail.setCreatedAt(autoPayUPI.getCreatedAt().getTime());
        nachDetail.setMandateStatus(mandateStatus);
        return nachDetail;
    }

    /**
     * @param scopeDataArgs
     * @return true in case of active application and non topup case --> migration cases else false
     */
    private boolean isActiveApplicationAutoPaySetupFlow(ScopeDataArgs scopeDataArgs) {
        LendingPaymentScheduleSlave lps = lendingPaymentScheduleDaoSlave.findByMerchantIdAndStatus(scopeDataArgs.getMerchant().getId(), Collections.singletonList(LoanStatus.ACTIVE.name()));
        if(Objects.nonNull(lps) && LoanStatus.ACTIVE.name().equalsIgnoreCase(lps.getStatus())){
            LendingApplication lendingApplication = lendingApplicationDao.findInProgressLoanApplication(scopeDataArgs.getMerchant().getId());
            if(Objects.nonNull(lendingApplication) && LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.getLoanType())){
                log.info("topup case found for active application on enach stage for merchantId: {}, active applicationId: {}, and topup applicationId: {}",
                        scopeDataArgs.getMerchant().getId(), lps.getApplicationId(), lendingApplication.getId());
                return false;
            }else {
                log.info("autopay migration case found on enach stage for merchantId: {}, active applicationId: {}",
                        scopeDataArgs.getMerchant().getId(), lps.getApplicationId());
                if(scopeDataArgs.getLoanDetailsV3Request().isAutoPayPending() &&
                        !mandateRegistrationHelper.isDigioUpiAutopayRequiredForActiveApplication(lps,
                                scopeDataArgs.getLoanDetailsV3Request().isIOS(), scopeDataArgs.getLoanDetailsV3Request().getAppVersion())){
                    throw new LoanDetailsException(LoanDetailExceptionEnum.APPLICATION_NOT_ELIGIBLE_FOR_DIGIO_UPI_AUTO_PAY.getErrorCode(),
                            LoanDetailExceptionEnum.APPLICATION_NOT_ELIGIBLE_FOR_DIGIO_UPI_AUTO_PAY.getErrorMessage());
                }
                scopeDataArgs.setApplicationId(lps.getApplicationId());
                return true;
            }
        }
        return false;
    }
    private LendingStateDTO<EnachStateDTO> getScopeDataForNachMigrationFlow(Long applicationId, EnachStateDTO enachStateDTO) {
        enachStateDTO.setNativeMandateRequired(true);
        enachStateDTO.setCurrentLoanActive(true);
        enachStateDTO.setEnachModes(Collections.singletonList(new EnachModeDTO(EnachMode.UPI.name(), true, null)));
        LendingApplication openApplication = lendingApplicationServiceV3.getLendingApplication(applicationId,enachStateDTO.getMerchantId());
        enachStateDTO.setLender(openApplication.getLender());
        enachStateDTO.setLoanAmount(openApplication.getLoanAmount());
        NachDetail nachDetail;
        if(easyLoanUtil.percentScaleUp(openApplication.getMerchantId(), nachWaitScreenRolloutPercentage)){
            nachDetail = getNachDetailForDigioUpi(openApplication);
            if(PaymentConstants.SUCCESS.equalsIgnoreCase(nachDetail.getMandateStatus())){
                enachStateDTO.setEnachDone(true);
            }else {
                enachStateDTO.setEnachDeeplink(apiGatewayService.getEnachProvider(null, openApplication.getLender(),enachStateDTO.getMerchantId()));
            }
            enachStateDTO.setNachDetail(nachDetail);
        }else{
            nachDetail = new NachDetail();
            nachDetail.setDailyInstalmentAmount(openApplication.getEdi());
            enachStateDTO.setNachDetail(nachDetail);
            enachStateDTO.setEnachDeeplink(apiGatewayService.getEnachProvider(null, openApplication.getLender(),enachStateDTO.getMerchantId()));
            AutoPayUPI autoPayUPI = autoPayUPIDao.findByApplicationIdAndStatus(applicationId, AutoPayStatusEnum.ACTIVE.name());
            enachStateDTO.setEnachDone(Objects.nonNull(autoPayUPI));
        }
        BankAccountDetails accountDetails = loanUtil.getAccountDetails(enachStateDTO.getMerchantId());
        enachStateDTO.setBankDetails(accountDetails);
        enachStateDTO.setMaxMandateAmount(loanUtil.getMaxMandateAmount(enachStateDTO.getMerchantId()));

        if(isPaymentBankChangeFlowApplicable && !LoanType.TOPUP.name().equalsIgnoreCase(openApplication.getLoanType())){
            if(paymentBankService.isPaymentBank(openApplication.getMerchantId(), accountDetails)){
                enachStateDTO.setHasLinkedPaymentBank(true);
                log.info("Setting setHasLinkedPaymentBank to true for merchantId: {}", enachStateDTO.getMerchantId());
            }
            enachStateDTO.setPaymentBank(paymentBankService.changePaymentAccount(openApplication, accountDetails));
            log.info("Payment Bank Change flow is applicable for merchantId: {} and setPaymentBank is: {}", enachStateDTO.getMerchantId(), enachStateDTO.isPaymentBank());
        }
        if (!StringUtils.isEmpty(enachStateDTO.getEnachDeeplink())) {
            Experian experian = experianDao.getByMerchantId(enachStateDTO.getMerchantId());
            enachStateDTO.setEnachErrorResponse(getEnachError(openApplication, experian));
        }
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
            LendingApplicationDetails lendingApplicationDetails = lendingApplicationServiceV3.getLendingApplicationDetailsByApplicationId(applicationId);
            if("TOPUP".equalsIgnoreCase(lendingApplication.getLoanType()) || Lender.LDC.name().equalsIgnoreCase(lender) || Lender.MAMTA.name().equalsIgnoreCase(lender) ||
              Lender.MAMTA0.name().equalsIgnoreCase(lender) || Lender.MAMTA1.name().equalsIgnoreCase(lender) || Lender.MAMTA2.name().equalsIgnoreCase(lender)){
                enachStateDTO.setEnachModes(Arrays.asList(new EnachModeDTO(EnachMode.NB_DC.name(), true, null)));
            }else if (!ObjectUtils.isEmpty(lendingApplicationDetails) && MandateType.DIGIO_UPI.equals(lendingApplicationDetails.getMandateType())) {
                enachStateDTO.setEnachModes(Collections.singletonList(new EnachModeDTO(EnachMode.UPI.name(), true, null)));
                enachStateDTO.setNativeMandateRequired(true);
            } else {
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
                loanUtil.isEligibleForNachSkip(openApplication, openApplication.getLender(), true)) {
            if(ObjectUtils.isEmpty(openApplication.getNachStatus())){
                loanDashboardService.deleteLoanDashboardCache(openApplication.getMerchantId());
            }
            log.info("Skipping enach for merchantId:{}",openApplication.getMerchantId());
            openApplication.setNachStatus(NachStatus.APPROVED.name());
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
            log.info("UPI removed because, App version doesn't support UPI NACH appversion: {}", appVersion);
        }
        log.info("Available EnachModes for merchant: {} are: {}", lendingApplication.getMerchantId(), enachModes);
        return enachModes;
    }
}
