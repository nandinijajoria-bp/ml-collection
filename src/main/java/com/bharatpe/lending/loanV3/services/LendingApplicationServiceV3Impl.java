package com.bharatpe.lending.loanV3.services;

import com.bharatpe.cache.service.LendingCache;
import com.bharatpe.common.entities.*;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.service.ILenderAssignService;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.dao.LendingKfsDao;
import com.bharatpe.lending.dao.LendingLedgerDao;
import com.bharatpe.lending.entity.LendingKfs;
import com.bharatpe.lending.enums.ApplicationDocType;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.loanV3.dto.InvokeLenderAssociationRequest;
import com.bharatpe.lending.loanV3.factory.LenderAssociationStageFactory;
import com.bharatpe.lending.loanV3.interfaces.ILenderAssociationService;
import com.bharatpe.lending.loanV3.services.associations.AbflDataUploadServiceUtil;
import com.bharatpe.lending.loanV3.utils.NbfcUtils;
import com.bharatpe.lending.service.PaymentService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@Slf4j
public class LendingApplicationServiceV3Impl extends LendingApplicationServiceV3Base {

    @Autowired
    private LendingApplicationDao lendingApplicationDao;

    @Autowired
    LendingCache lendingCache;

    @Autowired
    LenderAssociationStageFactory lenderAssociationStageFactory;

    @Autowired
    KafkaTemplate kafkaTemplate;

    @Autowired
    NbfcUtils nbfcUtils;

    @Autowired
    PaymentService paymentService;

    @Autowired
    LendingKfsDao lendingKfsDao;

    @Autowired
    MerchantService merchantService;

    @Autowired
    LendingLedgerDao lendingLedgerDao;

    @Autowired
    LendingApplicationDetailsDao lendingApplicationDetailsDao;

    @Autowired
    AbflDataUploadServiceUtil abflDataUploadServiceUtil;

    @Value("${invoke.env:prod}")
    public String invokeEnv;


    public void initLenderAssociation(InvokeLenderAssociationRequest invokeLenderAssociationRequest) {
        Long applicationId = invokeLenderAssociationRequest.getApplicationId();
        Boolean forceEnable = invokeLenderAssociationRequest.getForceEnable();
        String  stage = invokeLenderAssociationRequest.getStage();
        Optional<LendingApplication> lendingApplication = lendingApplicationDao.findById(applicationId);
        if (!lendingApplication.isPresent()) {
            log.info("no application found for application {}", applicationId);
            return;
        }
        String currStage = ObjectUtils.isEmpty(stage) ? LenderAssociationStages.INIT.name() : stage;
        if ("uat".equalsIgnoreCase(invokeEnv)) {

            if (forceEnable) {
                LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusOrderByIdDesc(lendingApplication.get().getId(), Status.INACTIVE.name());
                lendingApplicationLenderDetails.setStatus(Status.ACTIVE.name());
                lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);
            }

            if (LenderAssociationStages.DRAWDOWN.name().equalsIgnoreCase(stage)) {
                Map<String,Object> payload = new HashMap<String,Object>(){{
                    put("applicationId",lendingApplication.get().getId());
                    put("merchantId",lendingApplication.get().getMerchantId());
                    put("generateReport",false);
                    put("requestId",lendingApplication.get().getId());
                }};
                try {
                    kafkaTemplate.send("lending_disbursal", payload);
                } catch (Exception e) {
                    log.error("something went wrong for {}", lendingApplication.get().getId(), e);
                }
                return;
            }
            if ("DOC_REGEN".equalsIgnoreCase(stage)) {
                LendingKfs lendingKfs = lendingKfsDao.findTop1ByApplicationIdOrderByIdDesc(lendingApplication.get().getId());
                try {
                    Optional<BasicDetailsDto> merchant = merchantService.fetchMerchantBasicDetails(lendingApplication.get().getMerchantId());
                    switch (invokeLenderAssociationRequest.getSubStage()) {
                        case "WELCOME":
                            lendingApplicationServiceV2.generateWelcomeDocument(lendingApplication.get(),lendingKfs,merchant.get(), null);
                            break;
                        case "SANCTION":
                            lendingApplicationServiceV2.generateSanctionCumLoanAgreementDoc(lendingApplication.get(), merchant.get(), lendingKfs, null);
                            break;
                        case "KFS":
                            lendingApplicationServiceV2.generateKfsDocument(lendingApplication.get(), merchant.get(), lendingKfs, null);
                            break;
                        default:
                            lendingApplicationServiceV2.generateWelcomeDocument(lendingApplication.get(),lendingKfs,merchant.get(), null);
                            lendingApplicationServiceV2.generateSanctionCumLoanAgreementDoc(lendingApplication.get(), merchant.get(), lendingKfs, null);
                            lendingApplicationServiceV2.generateKfsDocument(lendingApplication.get(), merchant.get(), lendingKfs, null);
                    }
                    lendingKfsDao.save(lendingKfs);
                } catch (Exception e) {
                    log.info("exception occurred {} {}", e.getMessage(), Arrays.asList(e.getStackTrace()));
                }
                return;
            }
            if ("REPAY_EVENT".equalsIgnoreCase(stage)) {
                try {
                    kafkaTemplate.send("loan-receipt", new ObjectMapper().readValue(invokeLenderAssociationRequest.getPayload(), new TypeReference<Map<String,Object>>(){}));
                } catch (Exception e) {
                    log.error("exception occurred while posting data {}", e.getMessage());
                }
                return;
            }
            if ("REPAY".equalsIgnoreCase(stage)){
                ILenderAssociationService iLenderAssociationService =
                        lenderAssociationStageFactory.getStageAssociatedLenderService(LenderAssociationStages.RECEIPT.name()).getLenderAssociationService(Lender.ABFL.name());
                if (!ObjectUtils.isEmpty(iLenderAssociationService)) {
                    iLenderAssociationService.invoke(applicationId, new HashMap<String,Object>(){{
                        put("referenceNo", invokeLenderAssociationRequest.getReferenceNo());
                        put("amount", invokeLenderAssociationRequest.getAmount());
                        put("lpsId", invokeLenderAssociationRequest.getLpsId());
                    }});
                }
                return;
            }
            if ("DOC_UPLOAD".equalsIgnoreCase(stage)){
                Map<String,Object> payload = new HashMap<String,Object>(){{
                    put("application_id",lendingApplication.get().getId());
                }};
                try {
                    if (invokeLenderAssociationRequest.getRegenerateDoc()) {
                        LendingKfs lendingKfs = lendingKfsDao.findTop1ByApplicationIdOrderByIdDesc(lendingApplication.get().getId());
                        Optional<BasicDetailsDto> merchant = merchantService.fetchMerchantBasicDetails(lendingApplication.get().getMerchantId());
//                        lendingApplicationServiceV2.generateKfsDocument(lendingApplication.get(), merchant.get(), lendingKfs, lendingKfs.getKfsSignedAt());
                        lendingApplicationServiceV2.generateSanctionCumLoanAgreementDoc(lendingApplication.get(), merchant.get(), lendingKfs, lendingKfs.getSanctionLoanAgreementSignedAt());
//                        lendingApplicationServiceV2.generateWelcomeDocument(lendingApplication.get(),lendingKfs,merchant.get(), lendingKfs.getKfsSignedAt());
                        lendingKfsDao.save(lendingKfs);
                    }
                    abflDataUploadServiceUtil.uploadDocuments(lendingApplication.get().getId(), Arrays.asList("SANCTION_AGREEMENT"), false);
//                    kafkaTemplate.send("invoke_data_upload", payload);
                } catch (Exception e) {
                    log.error("something went wrong for {}", lendingApplication.get().getId(), e);
                }
                return;
            }
            if ("DOC_UPLOAD_TEST".equalsIgnoreCase(stage)){
                try {
                    if (invokeLenderAssociationRequest.getRegenerateDoc()) {
                        LendingKfs lendingKfs = lendingKfsDao.findTop1ByApplicationIdOrderByIdDesc(lendingApplication.get().getId());
                        Optional<BasicDetailsDto> merchant = merchantService.fetchMerchantBasicDetails(lendingApplication.get().getMerchantId());
                        lendingApplicationServiceV2.generateKfsDocument(lendingApplication.get(), merchant.get(), lendingKfs, lendingKfs.getKfsSignedAt());
                        lendingApplicationServiceV2.generateSanctionCumLoanAgreementDoc(lendingApplication.get(), merchant.get(), lendingKfs, lendingKfs.getSanctionLoanAgreementSignedAt());
                        lendingApplicationServiceV2.generateWelcomeDocument(lendingApplication.get(),lendingKfs,merchant.get(), lendingKfs.getKfsSignedAt());
                        lendingKfsDao.save(lendingKfs);
                    }
                    abflDataUploadServiceUtil.uploadDocuments(lendingApplication.get().getId(), Arrays.asList("KFS_SANCTION_AGREEMENT"), true);
                } catch (Exception e) {
                    log.error("something went wrong for {}", lendingApplication.get().getId(), e);
                }
                return;
            }
            if ("DOC_UPLOAD_TEST".equalsIgnoreCase(stage)){
                try {
                    if (invokeLenderAssociationRequest.getRegenerateDoc()) {
                        LendingKfs lendingKfs = lendingKfsDao.findTop1ByApplicationIdOrderByIdDesc(lendingApplication.get().getId());
                        Optional<BasicDetailsDto> merchant = merchantService.fetchMerchantBasicDetails(lendingApplication.get().getMerchantId());
                        lendingApplicationServiceV2.generateKfsDocument(lendingApplication.get(), merchant.get(), lendingKfs, lendingKfs.getKfsSignedAt());
                        lendingApplicationServiceV2.generateSanctionCumLoanAgreementDoc(lendingApplication.get(), merchant.get(), lendingKfs, lendingKfs.getSanctionLoanAgreementSignedAt());
                        lendingApplicationServiceV2.generateWelcomeDocument(lendingApplication.get(),lendingKfs,merchant.get(), lendingKfs.getKfsSignedAt());
                        lendingKfsDao.save(lendingKfs);
                    }
                    abflDataUploadServiceUtil.uploadDocuments(lendingApplication.get().getId(), Arrays.asList("KFS_SANCTION_AGREEMENT"), true);
                } catch (Exception e) {
                    log.error("something went wrong for {}", lendingApplication.get().getId(), e);
                }
                return;
            }
            if ("FORECLOSE".equalsIgnoreCase(stage)){
                Optional<LendingLedger> lendingLedger = lendingLedgerDao.findById(invokeLenderAssociationRequest.getLedgerId());
                Optional<LendingPaymentSchedule> loan = lendingPaymentScheduleDao.findById(invokeLenderAssociationRequest.getLpsId());
                if (lendingLedger.isPresent() && loan.isPresent()) {
                    paymentService.sendForeclosureEvent(loan.get().getApplicationId(), loan.get().getMobile(),lendingLedger.get());
                    return;
                }
            }
            if ("DRAWDOWN_CALLBACK".equalsIgnoreCase(stage)){
                Map<String,Object> payload = new HashMap<String,Object>(){
                    {
                        put("success", true);
                        put("applicationId", lendingApplication.get().getId());
                        put("lender", lendingApplication.get().getLender());
                        put("data", new HashMap<String, Object>() {{
                            put("status", "200");
                            put("message", "Amount has been disbursed succefully");
                            put("data", new HashMap<String, Object>() {{
                                put("AccountID", invokeLenderAssociationRequest.getAccountId());
                                put("UTRNo", invokeLenderAssociationRequest.getUtr());
                                put("Amount", lendingApplication.get().getDisbursalAmount());
                                put("LAN", invokeLenderAssociationRequest.getLan());
                            }});
                        }});
                    }};
                try {
                    kafkaTemplate.send("drawdown-callback", payload);
                } catch (Exception e) {
                    log.error("something went wrong for {}", lendingApplication.get().getId(), e);
                }
                return;
            }
        }
        if (!"uat".equalsIgnoreCase(invokeEnv)) {
            LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusOrderByIdDesc(lendingApplication.get().getId(), Status.ACTIVE.name());
            if (!ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
                log.info("workflow already invoked for application  {}", applicationId);
                return;
            }
            LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(lendingApplication.get().getId());
            if (!ObjectUtils.isEmpty(lendingApplicationDetails) && lendingApplicationDetails.getLenderAssc()) {
                log.info("workflow already invoked for application as lender assc flag is set as {} for {}", lendingApplicationDetails.getLenderAssc(), applicationId);
                return;
            }
        }
        if(Objects.nonNull(lendingApplication.get().getMerchantId())) {
            String loanDetailsCacheKey = "LENDING_LOAN_DETAILS_" + lendingApplication.get().getMerchantId();
            log.info("deleting cached key of loan details in create application for merchant: {}",lendingApplication.get().getMerchantId());
            lendingCache.delete(loanDetailsCacheKey);
        } else {
            log.info("no key exists!");
        }
        nbfcUtils.pushApplicationToNextStage(lendingApplication.get().getId(), lendingApplication.get().getLender(), currStage, Boolean.TRUE);
        }
}

