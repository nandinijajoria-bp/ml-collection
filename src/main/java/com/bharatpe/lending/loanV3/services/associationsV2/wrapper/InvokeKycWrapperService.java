package com.bharatpe.lending.loanV3.services.associationsV2.wrapper;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.loanV3.config.TrillionLoansConfig;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import com.bharatpe.lending.util.EdiUtil;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.*;

@Slf4j
@Service
public class InvokeKycWrapperService {
    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    CommonService commonService;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    private EdiUtil ediUtil;

    @Value("${lender.change.enabled:false}")
    Boolean enableLenderChange;

    @Lazy
    @Autowired
    KycUtils kycUtils;

    @Lazy
    @Autowired
    TrillionLoansConfig trillionLoansConfig;

    @Async("lenderPoolTaskExecutor")
    public void invokeKycAndDocUpload(Map<String, String> request, Map<String, Object> args) {
        try {
            if (!ObjectUtils.isEmpty(args)) {
                if (args.containsKey("requestId")) {
                    MDC.put("requestId", (String) args.get("requestId"));
                }
            }
            Long applicationId = Long.valueOf(request.get("application_id"));
            LenderAssociationDetailsRequestDto lenderAssociationDetailsDto = new LenderAssociationDetailsRequestDto();
            createRecord(lenderAssociationDetailsDto, applicationId);
            if(ObjectUtils.isEmpty(lenderAssociationDetailsDto.getLendingApplication()) || ObjectUtils.isEmpty(lenderAssociationDetailsDto.getLendingApplicationLenderDetails())) {
                log.info("No application details found for {}", lenderAssociationDetailsDto.getApplicationId());
                MDC.clear();
                return;
            }
            if (!Collections.singletonList(Lender.CREDITSAISON.name()).contains(lenderAssociationDetailsDto.getLendingApplication().getLender()) && Objects.isNull(lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getLeadId())) {
                log.info("lead creation was unsuccessful for {} ", applicationId);
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.KYC_FAILED.name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, LenderAssociationStatus.KYC_FAILED);
                MDC.clear();
                return;
            }
            LendingApplicationLenderDetails lenderDetails = lenderAssociationDetailsDto.getLendingApplicationLenderDetails();
            boolean isEligibleForSkipKyc = LenderAssociationStages.SKIP_KYC.name().equalsIgnoreCase(lenderDetails.getKycMode());
            boolean isEligibleForLenderKyc = kycUtils.isEligibleForLenderKyc(lenderDetails.getLender(), lenderAssociationDetailsDto.getLendingApplication().getMerchantId(), LoanType.TOPUP.name().equalsIgnoreCase(lenderAssociationDetailsDto.getLendingApplication().getLoanType()));
            List<String> stagesToBeInvokedInOrder = getStageToBeInvokedInOrder(lenderAssociationDetailsDto.getLendingApplication().getId(),
                    lenderAssociationDetailsDto.getLendingApplication().getLender(),
                    LoanType.TOPUP.name().equalsIgnoreCase(lenderAssociationDetailsDto.getLendingApplication().getLoanType()),
                    isEligibleForLenderKyc,
                    isEligibleForSkipKyc
            );
            Optional<String> failureStage = stagesToBeInvokedInOrder.stream().filter(stage -> !commonService.invokeStage(lenderAssociationDetailsDto, stage)).findFirst();
            if (failureStage.isPresent()) {
                log.info("lender association failed at {} stage for applicationId {}  with lender {}", failureStage.get(), applicationId, lenderAssociationDetailsDto.getLendingApplication().getLender());
                MDC.clear();
                return;
            }
            if(!Arrays.asList(Lender.TRILLIONLOANS.name(), Lender.MUTHOOT.name(), Lender.CREDITSAISON.name(), Lender.UGRO.name()).contains(lenderAssociationDetailsDto.getLendingApplication().getLender())){
                commonService.manageApplicationStateAndPushToNextStage(lenderAssociationDetailsDto);
            }
        } catch (Exception e) {
            log.error("Exception in invoking KYC flow for applicationId : {} {}", request.get("application_id"), Arrays.asList(e.getStackTrace()));
        }
        MDC.clear();
    }

    private void createRecord(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto, Long applicationId) {
        lenderAssociationDetailsDto.setApplicationId(applicationId);
        Optional<LendingApplication> lendingApplicationOptional = lendingApplicationDao.findById(applicationId);
        if (!lendingApplicationOptional.isPresent()) {
            log.info("No application found for application id : {}", applicationId);
            return;
        }
        LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(applicationId, Status.ACTIVE.name(), lendingApplicationOptional.get().getLender());
        if (ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
            log.info("No lender association details found for lender {} of application id : {}", lendingApplicationOptional.get().getLender(), applicationId);
            return;
        }
        LendingApplication lendingApplication = lendingApplicationOptional.get();
        lenderAssociationDetailsDto.setLendingApplication(lendingApplication);
        lenderAssociationDetailsDto.setLendingApplicationLenderDetails(lendingApplicationLenderDetails);
        lenderAssociationDetailsDto.setMerchantId(lendingApplication.getMerchantId());
        lenderAssociationDetailsDto.setManageState(Boolean.TRUE);
        lenderAssociationDetailsDto.setModifyLender(enableLenderChange);
    }

    public static boolean kycDataNeeded(String stage) {
        switch (stage) {
            case "AADHAR_UPLOAD":
            case "SELFIE_UPLOAD":
            case "UPDATED_LEAD":
            case "CREATE_CLIENT":
                return true;
            default:
                return false;
        }
    }

    public List<String> getStageToBeInvokedInOrder(Long applicationId, String lender, boolean isTopUp, boolean isLenderKyc, Boolean isSkipKyc) {
        log.info("Getting stages to be invoked in KYC for applicationId  {} and lender {} ", applicationId, lender);
        if (ObjectUtils.isEmpty(lender)) {
            throw new RuntimeException("Invalid lender " + lender + " for applicationId " + applicationId);
        }
        if(isSkipKyc) return getStageToBeInvokedForSkipKyc(applicationId, Lender.valueOf(lender), isTopUp);
        if(isLenderKyc) return getStageToBeInvokedForLenderKyc(applicationId, Lender.valueOf(lender), isTopUp);
        switch (Lender.valueOf(lender)) {
            case USFB:
                return Arrays.asList(LenderAssociationStages.AADHAR_UPLOAD.name(), LenderAssociationStages.SELFIE_UPLOAD.name(), LenderAssociationStages.SHOP_PHOTO_UPLOAD.name());
            case TRILLIONLOANS:
                return (isTopUp && Boolean.FALSE.equals(trillionLoansConfig.getKycCtaEnabledForTopup())) ?
                        Arrays.asList(LenderAssociationStages.UPDATE_CLIENT.name(), LenderAssociationStages.SELFIE_UPLOAD.name(), LenderAssociationStages.AADHAR_UPLOAD.name()) :
                        Arrays.asList(LenderAssociationStages.UPDATE_CLIENT.name(), LenderAssociationStages.SELFIE_UPLOAD.name(), LenderAssociationStages.AADHAR_UPLOAD.name(), LenderAssociationStages.KYC.name());
            case MUTHOOT:
                return Arrays.asList(LenderAssociationStages.AADHAR_UPLOAD.name(), LenderAssociationStages.KYC.name());
            case CAPRI:
                return Arrays.asList(LenderAssociationStages.AADHAR_UPLOAD.name(), LenderAssociationStages.SELFIE_UPLOAD.name());
            case PAYU:
                return isTopUp ? Collections.emptyList() :
                 Arrays.asList(LenderAssociationStages.AADHAR_UPLOAD.name(), LenderAssociationStages.SHOP_PHOTO_UPLOAD.name(),
                        LenderAssociationStages.SHOP_STOCK_PHOTO_UPLOAD.name(), LenderAssociationStages.SELFIE_UPLOAD.name(), LenderAssociationStages.KYC.name());
            case CREDITSAISON:
            case OXYZO:
                return Collections.singletonList(LenderAssociationStages.KYC.name());
            case UGRO:
                return Arrays.asList(LenderAssociationStages.SELFIE_UPLOAD.name(),
                        LenderAssociationStages.AADHAR_UPLOAD.name(), LenderAssociationStages.SHOP_PHOTO_UPLOAD.name(), LenderAssociationStages.SHOP_STOCK_PHOTO_UPLOAD.name(), LenderAssociationStages.CREATE_CLIENT.name());
            default:
                throw new RuntimeException("Invalid lender " + lender + " for applicationId " + applicationId);
        }
    }

    public List<String> getStageToBeInvokedForLenderKyc(Long applicationId, Lender lender, boolean isTopUp) {
        switch (lender) {
            case TRILLIONLOANS:
                return Arrays.asList(LenderAssociationStages.SELFIE_UPLOAD.name(), LenderAssociationStages.KYC.name());
            default:
                throw new RuntimeException("Invalid lender " + lender + " for applicationId " + applicationId);
        }
    }

    public List<String> getStageToBeInvokedForSkipKyc(Long applicationId, Lender lender, boolean isTopUp) {
        switch (lender) {
            case TRILLIONLOANS:
                return  isTopUp ? Arrays.asList(LenderAssociationStages.SELFIE_UPLOAD.name(), LenderAssociationStages.SKIP_KYC.name()) :
                        Arrays.asList(LenderAssociationStages.SELFIE_UPLOAD.name(), LenderAssociationStages.KYC.name(), LenderAssociationStages.SKIP_KYC.name());
            default:
                throw new RuntimeException("Invalid lender " + lender + " for applicationId " + applicationId);
        }
    }

}
