package com.bharatpe.lending.loanV3.services.associationsV2.piramal.impl;

import com.bharatpe.cache.service.LendingCache;
import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingKfsDao;
import com.bharatpe.lending.entity.LendingKfs;
import com.bharatpe.lending.handlers.S3BucketHandler;
import com.bharatpe.lending.loanV2.dto.ApiResponse;
import com.bharatpe.lending.loanV3.dto.piramal.EsignDocumentDTO;
import com.bharatpe.lending.loanV3.dto.piramal.NbfcResponseDto;
import com.bharatpe.lending.loanV3.enums.DocType;
import com.bharatpe.lending.service.APIGatewayService;
import com.bharatpe.lending.service.LiquiloansService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.Optional;

import static com.bharatpe.lending.loanV3.enums.DocType.*;

@Service
@Slf4j
public class ESignDocService {
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    LiquiloansService liquiloansService;
    @Autowired
    LendingApplicationDao lendingApplicationDao;
    @Autowired
    LendingCache lendingCache;
    @Autowired
    S3BucketHandler s3BucketHandler;
    @Autowired
    LendingKfsDao lendingKfsDao;
    @Autowired
    APIGatewayService apiGatewayService;
    @Autowired
    private LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;
    @Value("${aws.s3.bucket:loan-document}")
    private String bucket;

    @Value("${enable.esignDoc.recordLock:false}")
    private Boolean enableLock;

    public ApiResponse<?> persistAndSendCommunicationForESignDoc(NbfcResponseDto nbfcResponseDto) {
        try {
            log.info("esign doc request for {}", objectMapper.writeValueAsString(nbfcResponseDto));
            EsignDocumentDTO esignDocumentDTO = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDto.getData()), EsignDocumentDTO.class);
            Optional<LendingApplication> lendingApplication = lendingApplicationDao.findById(Long.valueOf(nbfcResponseDto.getApplicationId()));
            if (!lendingApplication.isPresent()) {
                log.info("application {} not found for esign doc event", nbfcResponseDto.getApplicationId());
                return new ApiResponse<>(false, "lending application doesn't exists !");
            }
            LendingKfs lendingKfs = lendingKfsDao.findTop1ByApplicationIdOrderByIdDesc(lendingApplication.get().getId());
            LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(lendingApplication.get().getId(), Status.ACTIVE.name(), lendingApplication.get().getLender());
            if (ObjectUtils.isEmpty(lendingKfs)) {
                log.info("lendingKfs {} not found for esign doc event", nbfcResponseDto.getApplicationId());
                return new ApiResponse<>(false, "lending kfs doesn't exists !");
            }
            if (ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
                log.info("lendingApplicationLenderDetails {} not found for esign doc event", nbfcResponseDto.getApplicationId());
                return new ApiResponse<>(false, "lending lender app details doesn't exists !");
            }
            if (KEY_FACT_STATEMENT.toString().equalsIgnoreCase(esignDocumentDTO.getDocumentType()) && Boolean.TRUE.equals(lendingApplicationLenderDetails.getESignedKfs())) {
                log.info("esigned Kfs already exists for {}", nbfcResponseDto.getApplicationId());
                return new ApiResponse<>(true, "event already acknowledged !");
            }
            if (LOAN_AGREEMENT.toString().equalsIgnoreCase(esignDocumentDTO.getDocumentType()) && Boolean.TRUE.equals(lendingApplicationLenderDetails.getESignedSanc())) {
                log.info("esigned sanc already exists for {}", nbfcResponseDto.getApplicationId());
                return new ApiResponse<>(true, "event already acknowledged !");
            }
            acquireLockAndUploadFileToS3AndUpdateLALD(lendingApplicationLenderDetails, valueOf(esignDocumentDTO.getDocumentType()), lendingKfs, esignDocumentDTO.getFileBlob());
            sendComms(lendingApplication.get(), lendingApplicationLenderDetails);
            return new ApiResponse<>(true, "esign document processed successfully !");
        } catch (Exception e) {
            log.error("exception occurred while processing esign doc event for {} {}", nbfcResponseDto.getApplicationId(), Arrays.asList(e.getStackTrace()));
            return new ApiResponse<>(false, e.getMessage());
        }
    }

    private void acquireLockAndUploadFileToS3AndUpdateLALD(LendingApplicationLenderDetails lendingApplicationLenderDetails, DocType docType, LendingKfs lendingKfs,
                                                           String fileBlob) throws FileNotFoundException {
        String eSignDocKeyInRedis = "ESIGN_DOC_" + lendingApplicationLenderDetails.getApplicationId();
        // TODO: 20/04/23 after scaling activity decide on locking (as suggested by Ashish)!
        if (enableLock && lendingCache.acquireLock(eSignDocKeyInRedis, 5)) {
            uploadDocumentAndUpdateLALDState(lendingApplicationLenderDetails, docType, lendingKfs, fileBlob);
            lendingCache.releaseLock(eSignDocKeyInRedis);
        } else if (!enableLock) {
            uploadDocumentAndUpdateLALDState(lendingApplicationLenderDetails, docType, lendingKfs, fileBlob);
        }
    }

    private void uploadDocumentAndUpdateLALDState(LendingApplicationLenderDetails lendingApplicationLenderDetails, DocType docType, LendingKfs lendingKfs, String fileBlob) throws FileNotFoundException {
        switch (docType) {
            case LOAN_AGREEMENT:
                log.info("marking sanc doc as esigned for {} !", lendingApplicationLenderDetails.getApplicationId());
                lendingApplicationLenderDetails.setESignedSanc(true);
                s3BucketHandler.uploadToS3Bucket(fileBlob, lendingKfs.getSanctionLoanAgreementDocFile(), bucket);
                lendingKfs.setSanctionLoanAgreementDocUrl(
                        apiGatewayService.getShortUrl(
                                s3BucketHandler.getPreSignedPublicURL(lendingKfs.getSanctionLoanAgreementDocFile(), bucket)));
                break;
            case KEY_FACT_STATEMENT:
                log.info("marking kfs doc as esigned for {}!", lendingApplicationLenderDetails.getApplicationId());
                lendingApplicationLenderDetails.setESignedKfs(true);
                s3BucketHandler.uploadToS3Bucket(fileBlob, lendingKfs.getKfsDocFile(), bucket);
                lendingKfs.setKfsDocUrl(
                        apiGatewayService.getShortUrl(
                                s3BucketHandler.getPreSignedPublicURL(lendingKfs.getKfsDocFile(), bucket)));
                break;
            default:
                log.error("invalid doc type in esigned doc for {}", lendingApplicationLenderDetails.getApplicationId());
        }
        lendingKfsDao.save(lendingKfs);
        lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);
    }

    private void sendComms(LendingApplication lendingApplication, LendingApplicationLenderDetails lendingApplicationLenderDetails) {
        if (Boolean.TRUE.equals(lendingApplicationLenderDetails.getESignedKfs()) && Boolean.TRUE.equals(lendingApplicationLenderDetails.getESignedSanc())) {
            log.info("esigned kfs and esigned sanc found, initializing notification workflow for {}", lendingApplication.getId());
            liquiloansService.sendWPAndSMSNotification(lendingApplication, false);
            lendingApplicationLenderDetails.setCommsSent(true);
            lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);
        } else {
            log.info("lending kfs {}, lending sanc {}, skipping comms for {}", lendingApplicationLenderDetails.getESignedKfs(),
                    lendingApplicationLenderDetails.getESignedSanc(), lendingApplication.getId());
        }
    }

}
