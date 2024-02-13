package com.bharatpe.lending.loanV3.services.associationsV2.wrapper;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.enums.DocType;
import com.bharatpe.lending.loanV3.factory.LenderAssociationStageFactoryV2;
import com.bharatpe.lending.loanV3.services.associationsV2.AssociationServiceUtil;
import com.bharatpe.lending.loanV3.utils.NbfcUtils;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.*;

@Slf4j
@Service
public class InvokeDigitalSignWrapperService {

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    AssociationServiceUtil associationServiceUtil;

    @Value("${lender.change.enabled:false}")
    Boolean enableLenderChange;

    @Async("lenderPoolTaskExecutor")
    public void invokeDigitalSign(Long applicationId, String reqId) {
        try {
            log.info("Initiating digital sign for applicationId: {}", applicationId);
            try {
                MDC.put("requestId", reqId);
                log.info("Updating lead info for application: {}", applicationId);
                LendingApplication lendingApplication = lendingApplicationDao.findById(applicationId).get();
                if (ObjectUtils.isEmpty(lendingApplication)) {
                    log.info("No application found for : {}", applicationId);
                    return;
                }
                LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(applicationId, Status.ACTIVE.name(), lendingApplication.getLender());
                if (ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
                    log.info("No lendingApplicationLenders details found for lender {} for : {}", lendingApplication.getLender(), lendingApplication.getLender());
                    return;
                }
                lendingApplicationLenderDetails.setStage(LenderAssociationStages.DIGI_SIGN.name());
                lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);
                if (Objects.isNull(lendingApplicationLenderDetails.getESignedSanc()) || !lendingApplicationLenderDetails.getESignedSanc()) {
                    LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto = new LenderAssociationDetailsRequestDto();
                    createRecord(lenderAssociationDetailsRequestDto, applicationId);


                    if(ObjectUtils.isEmpty(lenderAssociationDetailsRequestDto.getLendingApplication()) || ObjectUtils.isEmpty(lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails())) {
                        log.info("No application details found for {}", lenderAssociationDetailsRequestDto.getApplicationId());
                        MDC.clear();
                        return;
                    }

                    List<String> stagesToBeInvokedInOrder = getStageToBeInvokedInOrder(applicationId);
                    Optional<String> failureStage = stagesToBeInvokedInOrder.stream().filter(stage -> !invokeStage(lenderAssociationDetailsRequestDto, stage)).findFirst();
                    if (failureStage.isPresent()) {
                        log.info("lender association failed at {} stage for applicationId {}  with lender {}", failureStage.get(), applicationId, lenderAssociationDetailsRequestDto.getLendingApplication().getLender());
                        MDC.clear();
                        return;
                    }
                    MDC.clear();
                }

            } catch (Exception ex) {
                log.error("Exception occurred while updating lead info: {} {} {}", applicationId, ex.getMessage(), ex);
            } finally {
                MDC.clear();
            }
        } catch (Exception e) {
            log.info("Exception in invoking digital sign flow for applicationId : {} {}", applicationId, Arrays.asList(e.getStackTrace()));
            MDC.clear();
        }
    }

    private void createRecord(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto, Long applicationId) {
        lenderAssociationDetailsDto.setApplicationId(applicationId);
        Optional<LendingApplication> lendingApplicationOptional = lendingApplicationDao.findById(applicationId);
        if (!lendingApplicationOptional.isPresent()) {
            log.info("No application found for application id : {}", applicationId);
            return;
        }
        LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(applicationId, Status.ACTIVE.name(), lendingApplicationOptional.get().getLender());
        if (ObjectUtils.isEmpty(lendingApplicationLenderDetails) || Objects.isNull(lendingApplicationLenderDetails.getLeadId())) {
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

    private boolean invokeStage(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto, String stage) {
        switch (stage) {
            case "POST_CONSENT":
                return associationServiceUtil.invokeConsentPostingService(lenderAssociationDetailsDto.getLendingApplication().getLender(), lenderAssociationDetailsDto);
            case "DIGI_SIGN":
                return associationServiceUtil.invokeDigitalSignService(lenderAssociationDetailsDto.getLendingApplication().getLender(), lenderAssociationDetailsDto, DocType.LOAN_AGREEMENT.name());
            default:
                return true;
        }
    }


    public List<String> getStageToBeInvokedInOrder(Long applicationId) {
        Optional<LendingApplication> lendingApplication = lendingApplicationDao.findById(applicationId);
        if (ObjectUtils.isEmpty(lendingApplication.get())) {
            return new ArrayList<>();
        }
        switch (Lender.valueOf(lendingApplication.get().getLender())) {
            case TRILLIONLOANS:
                return Collections.singletonList(LenderAssociationStages.DIGI_SIGN.name());
            default:
                return Collections.singletonList(LenderAssociationStages.DIGI_SIGN.name());
        }
    }

}
