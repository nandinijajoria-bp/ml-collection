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
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class InvokeAdditionalDocUploadWrapperService {

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    AssociationServiceUtil associationServiceUtil;

    @Autowired
    NbfcUtils nbfcUtils;

    @Async("lenderPoolTaskExecutor")
    public void invokeAdditionalDocUpload(Long applicationId, String reqId) {
        try {
            log.info("Uploading additional doc for applicationId: {}", applicationId);
            try {
                MDC.put("requestId", reqId);
                log.info("Updating lead info for application: {}", applicationId);
                LendingApplication lendingApplication = lendingApplicationDao.findById(applicationId).get();
                if (ObjectUtils.isEmpty(lendingApplication)) {
                    log.info("No application found for : {}", applicationId);
                    return;
                }
                LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.
                        findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(applicationId,
                                Status.ACTIVE.name(), lendingApplication.getLender());
                if (ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
                    log.info("No lendingApplicationLenders details found for lender {} for : {}", lendingApplication.getLender(), lendingApplication.getLender());
                    return;
                }
                lendingApplicationLenderDetails.setStage(LenderAssociationStages.DOC_UPLOAD.name());
                lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);
                boolean isRetry = (!ObjectUtils.isEmpty(lendingApplicationLenderDetails.getFailedUpload())) && LenderAssociationStatus.DOC_UPLOAD_FAILED.name().equalsIgnoreCase(lendingApplicationLenderDetails.getDocUploadStatus());
                log.info("doc upload status {}, isRetry: {}", lendingApplicationLenderDetails.getDocUploadStatus(), isRetry);
                if (Objects.isNull(lendingApplicationLenderDetails.getDocUploadStatus()) || isRetry) {
                    LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest = LenderAssociationDetailsRequestDto.builder()
                            .lendingApplication(lendingApplication)
                            .lendingApplicationLenderDetails(lendingApplicationLenderDetails)
                            .build();

                    List<DocType> docs = isRetry ? getFailedDocList(lendingApplicationLenderDetails) : getDocList(lendingApplication.getLender());
                    lendingApplicationLenderDetails.setFailedUpload(null);
                    lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);
                    for (DocType docType : docs) {
                        try {
                            log.info("processing doc {} {}", lendingApplication.getId(), docType);
                            lendingApplicationLenderDetails.setLeadStatus(docType.name());
                            lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);
                            boolean status = associationServiceUtil.invokeAdditionalDocUpload(lendingApplication.getLender(), lenderAssociationDetailsRequest, docType.name());
                            if (!status) {
                                log.info("doc upload failed of {} for {} {}", lendingApplication.getLender(), lendingApplication.getId(), docType);
                                lendingApplicationLenderDetails.setFailedUpload(Objects.isNull(lendingApplicationLenderDetails.getFailedUpload()) ?
                                        docType.name() : lendingApplicationLenderDetails.getFailedUpload() + ";" + docType.name());
                            }
                        } catch (Exception e) {
                            log.error("Exception occurred while doc upload posting for application: {} {} {} {}", docType, applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
                        }
                    }
                    lendingApplicationLenderDetails.setDocUploadStatus(Objects.isNull(lendingApplicationLenderDetails.getFailedUpload()) ?
                            LenderAssociationStatus.DOC_UPLOAD_COMPLETE.name() : LenderAssociationStatus.DOC_UPLOAD_FAILED.name());

                    // For callback handling of doc upload in case of Muthoot
                    if(Lender.MUTHOOT.name().equalsIgnoreCase(lendingApplication.getLender())) {
                        lendingApplicationLenderDetails.setDocUploadStatus(LenderAssociationStatus.DOC_UPLOAD_IN_PROGRESS.name());
                    }
                    if (LenderAssociationStatus.DOC_UPLOAD_COMPLETE.name().equals(lendingApplicationLenderDetails.getDocUploadStatus())) {
                        lendingApplicationLenderDetails.setStage(LenderAssociationStageFactoryV2.getNextStage(Lender.valueOf(lendingApplication.getLender()), LenderAssociationStages.DOC_UPLOAD).name());
                        nbfcUtils.pushApplicationToNextStage(lendingApplication.getId(), lendingApplication.getLender(), LenderAssociationStages.DOC_UPLOAD.name(), LenderAssociationStageFactoryV2.autoInvokeNextStage(Lender.valueOf(lendingApplication.getLender()), LenderAssociationStages.DOC_UPLOAD));
                    }
                    lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);
                }

            } catch (Exception ex) {
                log.error("Exception occurred while updating lead info: {} {} {}", applicationId, ex.getMessage(), ex);
            } finally {
                MDC.clear();
            }
        } catch (Exception e) {
            log.info("Exception in invoking createLead and doc upload flow for applicationId : {} {}", applicationId, Arrays.asList(e.getStackTrace()));
            MDC.clear();
        }
    }

    private List<DocType> getDocList(String lender) {
        switch (lender) {
            case "USFB":
                return Arrays.asList(DocType.KEY_FACT_STATEMENT, DocType.LOAN_AGREEMENT);
            case "TRILLIONLOANS":
                return Collections.singletonList(DocType.LOAN_AGREEMENT);
            case "MUTHOOT":
                return Collections.singletonList(DocType.KEY_FACT_STATEMENT_LOAN_AGREEMENT_MERGED);
            case "CAPRI":
                return Arrays.asList(DocType.KEY_FACT_STATEMENT, DocType.LOAN_AGREEMENT);
            default:
                return new ArrayList<>();
        }
    }

    private List<DocType> getFailedDocList(LendingApplicationLenderDetails lendingApplicationLenderDetails) {
        return Arrays.stream(lendingApplicationLenderDetails.getFailedUpload().split(";")).map(DocType :: valueOf).collect(Collectors.toList());
    }
}
