package com.bharatpe.lending.loanV3.services.associationsV2.piramal.impl;

import com.bharatpe.common.constants.RequestConstants;
import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.LendingEnum;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.loanV3.enums.DocType;
import com.bharatpe.lending.loanV3.factory.LenderAssociationStageFactory;
import com.bharatpe.lending.loanV3.utils.NbfcUtils;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@Slf4j
public class PiramalAdditionalDocUploadService {

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    PiramalDocumentUploadService piramalDocumentUploadService;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LendingApplicationDetailsDao lendingApplicationDetailsDao;

    @Autowired
    UpdateLeadAdditionalDataService updateLeadAdditionalDataService;

    @Lazy
    @Autowired
    NbfcUtils nbfcUtils;

    @Async("piramalPoolTaskExecutor")
    public void uploadPiramalDoc(Long applicationId, String reqId) {
        log.info("Updating additonal doc for applicationId: {}", applicationId);
        try {
            MDC.put("requestId", reqId);
            log.info("Updating lead info for application: {}", applicationId);
            LendingApplication lendingApplication = lendingApplicationDao.findById(applicationId).get();
            LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.
                    findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(applicationId,
                            Status.ACTIVE.name(), LendingEnum.LENDER.PIRAMAL.name());
            log.info("doc upload status {}", lendingApplicationLenderDetails.getDocUploadStatus());
            if (Objects.isNull(lendingApplicationLenderDetails.getDocUploadStatus())) {
                boolean updateLeadStatus = updateLeadAdditionalDataService.updateLeadAditionalData(lendingApplication, lendingApplicationLenderDetails);
                if (!updateLeadStatus) {
                    return;
                }
                // TODO: 05/04/23 enable shop photo uploads later
                List<DocType> docs = Arrays.asList(DocType.KEY_FACT_STATEMENT, DocType.LOAN_AGREEMENT,
                        DocType.SHOP_PHOTO, DocType.SHOP_STOCK
                );
                for (DocType docType : docs) {
                    try {
                        log.info("processing doc {} {}", lendingApplication.getId(), docType);
                        boolean status = piramalDocumentUploadService.invokeAdditionalDocUpload(lendingApplication, lendingApplicationLenderDetails, docType.name());
                        if (!status) {
                            log.info("doc upload failed for {} {}", lendingApplication.getId(), docType);
                            lendingApplicationLenderDetails.setFailedUpload(Objects.isNull(lendingApplicationLenderDetails.getFailedUpload()) ?
                                    docType.name() : lendingApplicationLenderDetails.getFailedUpload() + ";" + docType.name());
                        }
                    } catch (Exception e) {
                        log.error("Exception occured while doc upload posting for application: {} {} {} {}", docType, applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
                    }
                }
                lendingApplicationLenderDetails.setDocUploadStatus(Objects.isNull(lendingApplicationLenderDetails.getFailedUpload()) ?
                        LenderAssociationStatus.DOC_UPLOAD_PARTIAL.name() : LenderAssociationStatus.DOC_UPLOAD_FAILED.name());
                if (LenderAssociationStatus.DOC_UPLOAD_PARTIAL.name().equals(lendingApplicationLenderDetails.getDocUploadStatus())) {
                    lendingApplicationLenderDetails.setStage(LenderAssociationStages.DRAWDOWN.name());
                    nbfcUtils.pushApplicationToNextStage(lendingApplication.getId(), lendingApplication.getLender(), LenderAssociationStages.PUSH_AUDIT.name(), Boolean.FALSE);
                }
                lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);
            }

        } catch (Exception ex) {
            log.error("Exception occurred while updating lead info: {} {} {}", applicationId, ex.getMessage(), ex);
        }
        finally {
            MDC.clear();
        }
    }
}
