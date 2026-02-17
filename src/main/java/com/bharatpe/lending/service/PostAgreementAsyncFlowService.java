package com.bharatpe.lending.service;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.objects.Meta;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.dao.LendingKfsDao;
import com.bharatpe.lending.entity.LendingKfs;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.lendingplatform.lending.service.ENachRegister;
import com.bharatpe.lending.loanV2.service.LendingApplicationServiceV2;
import com.bharatpe.lending.loanV3.factory.LenderAssociationStageFactory;
import com.bharatpe.lending.loanV3.factory.LenderAssociationStageFactoryV2;
import com.bharatpe.lending.loanV3.revamp.enums.NachStatus;
import com.bharatpe.lending.loanV3.utils.NbfcUtils;
import com.bharatpe.lending.util.LoanUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.*;

@Slf4j
@Service
public class PostAgreementAsyncFlowService {
    @Lazy
    @Autowired
    private  LendingApplicationServiceV2 lendingApplicationServiceV2;

    @Lazy
    @Autowired
    private NbfcUtils nbfcUtils;

    @Autowired
    private LoanUtil loanUtil;

    @Autowired
    private LendingKfsDao lendingKfsDao;

    @Autowired
    private ENachRegister eNachRegister;

    @Autowired
    private LendingDelayedMessagePublisher lendingDelayedMessagePublisher;

    @Autowired
    private LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    private LendingApplicationDetailsDao lendingApplicationDetailsDao;

    @Autowired
    @Qualifier("LoanJourneyKafkaTemplate")
    private KafkaTemplate<String, Object> loanJourneyKafkaTemplate;

    @Value("${kafka.topic.postChecks:lending_post_application_submission_checks}")
    private String kafkaTopicPostChecks;

    private final List<String> topupLoans = Arrays.asList(LoanType.TOPUP.name(), LoanType.HALF_TOPUP.name(), LoanType.IO_TOPUP.name());

    public void postAgreementAsyncFlow(BasicDetailsDto merchantBasicDetailsDto, LendingApplication lendingApplication, Meta meta, Integer retryCount) {
        try {
            LendingKfs lendingKfs = lendingKfsDao.findTop1ByApplicationIdOrderByIdDesc(lendingApplication.getId());
            if(ObjectUtils.isEmpty(lendingKfs) || ObjectUtils.isEmpty(lendingKfs.getKfsSignedAt())) {
                log.info("store agreement docs for application {}", lendingApplication.getId());
                lendingKfs = lendingApplicationServiceV2.storeApplicationDocs(lendingApplication.getId(), lendingApplication, merchantBasicDetailsDto, Optional.of(lendingKfs));
            }
            boolean isSuccess = false;
            LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findByApplicationId(lendingApplication.getId());
            if (Objects.nonNull(lendingApplicationDetails) && loanUtil.isMandateDone(lendingApplication, lendingApplicationDetails)) {
                pushApplicationToGuardRailsAndPriorityFlow(merchantBasicDetailsDto.getId(), lendingApplication.getId());
                isSuccess = invokeNextStage(lendingApplication, Optional.of(lendingKfs), Optional.of(lendingApplicationDetails));
            }
            if(Boolean.FALSE.equals(isSuccess)) {
                log.info("next stage invocation pending of {} for applicationId {}", lendingApplication.getLender(), lendingApplication.getId());
                //Pushing application to delayed queue for retry
                pushToQueueForRetry(merchantBasicDetailsDto.getId(), lendingApplication.getId(), meta, retryCount);
                return;
            }
            pushApplicationForLatLong(merchantBasicDetailsDto.getId(), lendingApplication.getId());
            pushApplicationForDuplicatePancardCheck(merchantBasicDetailsDto.getId(), lendingApplication.getId());
            loanUtil.publishApplicationEvent(lendingApplication);
        } catch (Exception e) {
            log.error("Exception in post agreement async flow for applicationId {} {}", lendingApplication.getId(), Arrays.asList(e.getStackTrace()));
            //Pushing application to delayed queue for retry
            pushToQueueForRetry(merchantBasicDetailsDto.getId(), lendingApplication.getId(), meta, retryCount);
        }
    }

    public boolean invokeNextStage(LendingApplication lendingApplication, Optional<LendingKfs> optionalLendingKfs, Optional<LendingApplicationDetails> optionalLad) {
        LendingApplicationDetails lendingApplicationDetails = optionalLad.orElseGet(() -> lendingApplicationDetailsDao.findByApplicationId(lendingApplication.getId()));
        if(Objects.isNull(lendingApplicationDetails)){
            log.error("Lending application details not found while invoking next stage for applicationId {}", lendingApplication.getId());
            return false;
        }
        if (!loanUtil.isMandateDone(lendingApplication, lendingApplicationDetails)) {
            log.info("Mandate status is not approved to invoke next stage for applicationId {}", lendingApplication.getId());
            return false;
        }
        LendingKfs lendingKfs = optionalLendingKfs.orElseGet(() -> lendingKfsDao.findTop1ByApplicationIdOrderByIdDesc(lendingApplication.getId()));
        if(ObjectUtils.isEmpty(lendingKfs) || ObjectUtils.isEmpty(lendingKfs.getKfsSignedAt())) {
            log.info("Agreement docs details not found to invoke next stage for applicationId {}", lendingApplication.getId());
            return false;
        }
        LendingApplicationLenderDetails lenderDetails = lendingApplicationLenderDetailsDao.findTop1ByApplicationIdAndLenderOrderByIdDesc(lendingApplication.getId(), lendingApplication.getLender());
        if (ObjectUtils.isEmpty(lenderDetails)) {
            log.info("Lender details not found for applicationId {}", lendingApplication.getId());
            return false;
        }
        if (!LenderAssociationStages.ASSC_COMPLETED.name().equals(lenderDetails.getStage())) {
            log.info("Incorrect lender stage invoke next stage post agreement for applicationId {}", lendingApplication.getId());
            return true;
        }
        if(BooleanUtils.isTrue(lenderDetails.getRearchFlow())) {
            log.info("Re-arch flow is enabled for application id: {}, invoking next stage from re-arch workflow", lendingApplication.getId());
            eNachRegister.pushDetailsToLender(lendingApplication, lendingApplicationDetails);
            return true;
        }
        Lender lender = Lender.valueOf(lendingApplication.getLender());
        LoanType loanType = LoanType.valueOf(lendingApplication.getLoanType());
        LenderAssociationStages currentStage = LenderAssociationStages.ASSC_COMPLETED;

        //Skipping Sanction stage invocation for ABFL topup
        if (Lender.ABFL.equals(lender) && topupLoans.contains(loanType.name())) {
            currentStage =  LenderAssociationStages.SANCTION_WRAPPER;
            LenderAssociationStages nextStage = LenderAssociationStageFactory.getNextStage(lender, currentStage);
            lenderDetails.setStage(nextStage.name());
            lendingApplicationLenderDetailsDao.save(lenderDetails);
        }
        Boolean autoInvokeNextStage = (Arrays.asList(Lender.ABFL.name(), Lender.PIRAMAL.name()).contains(lendingApplication.getLender()))
                ? LenderAssociationStageFactory.autoInvokeNextStage(Lender.valueOf(lendingApplication.getLender()), currentStage)
                : LenderAssociationStageFactoryV2.autoInvokeNextStage(Lender.valueOf(lendingApplication.getLender()), currentStage);
        nbfcUtils.pushApplicationToNextStage(lendingApplication.getId(), lender.name(), currentStage.name(), autoInvokeNextStage);
        log.info("Pushed next stage for lender {} of applicationId {} from currentStage {}", lender, lendingApplication.getId(), currentStage);
        return true;
    }

    public void pushApplicationForDuplicatePancardCheck(Long merchantId, Long applicationId) {
        try {
            Map<String, Object> detailMap = new HashMap<>();
            detailMap.put("merchantId", merchantId);
            detailMap.put("applicationId", applicationId);
            publishKafkaEvent(detailMap, "check_duplicate_pancard", merchantId, applicationId);
        } catch (Exception e) {
            log.error("Error occurred while pushing application to duplicate pan card check for applicationId {} {}", applicationId, Arrays.asList(e.getStackTrace()));
        }
    }

    public void pushApplicationForLatLong(Long merchantId, Long applicationId) {
        try {
            Map<String, Object> detailMap = new HashMap<>();
            detailMap.put("merchantId", merchantId);
            detailMap.put("applicationId", applicationId);
            publishKafkaEvent(detailMap, "find_lat_long", merchantId, applicationId);
        } catch (Exception e) {
            log.error("Error occurred while pushing application to lat long for applicationId {} {}", applicationId, Arrays.asList(e.getStackTrace()));
        }
    }

    public void pushApplicationToGuardRailsAndPriorityFlow(Long merchantId, Long applicationId) {
        try {
            Map<String, Object> detailMap = new HashMap<>();
            detailMap.put("merchantId", merchantId);
            detailMap.put("applicationId", applicationId);
            publishKafkaEvent(detailMap, kafkaTopicPostChecks, merchantId, applicationId);
        } catch (Exception e) {
            log.error("Error occurred while pushing application to guard rails and priority for applicationId {} {}", applicationId, Arrays.asList(e.getStackTrace()));
        }
    }

    public void publishKafkaEvent(Map<String, Object> request, String topic, Long merchantId, Long applicationId) {
        loanJourneyKafkaTemplate.send(topic, merchantId.toString(), request);
        log.info("Pushed request {} to topic {} for applicationId {}", request, topic, applicationId);
    }

    private void pushToQueueForRetry(Long merchantId, Long applicationId, Meta meta,Integer retryCount) {
        try {
            retryCount++;
            log.info("pushing application to retry queue for post agreement flow with retryCount {} for applicationId {}", retryCount, applicationId);
            Map<String, Object> payload = new HashMap<>();
            payload.put("application_id", applicationId);
            payload.put("merchant_id", merchantId);
            payload.put("meta", meta);
            payload.put("retry_count", retryCount);
            String hashKey = "post_agreement_async_flow_" + applicationId;
            long timeout = retryCount * 10;                      //Incrementing retry time 10 second for each retry.
            lendingDelayedMessagePublisher.publish("post_agreement_async_flow", applicationId.toString(), payload, hashKey, timeout);
        } catch (Exception e) {
            log.error("Exception in pushing application to delayed queue for post agreement async flow for applicationId {} and merchantId {} {}", applicationId, merchantId, Arrays.asList(e.getStackTrace()));
        }
    }

}
