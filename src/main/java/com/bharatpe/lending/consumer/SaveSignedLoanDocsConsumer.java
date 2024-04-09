package com.bharatpe.lending.consumer;

import com.bharatpe.lending.dao.LendingKfsDao;
import com.bharatpe.lending.entity.LendingKfs;
import com.bharatpe.lending.handlers.S3BucketHandler;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import java.util.Objects;

import static com.bharatpe.lending.constant.KfsConstants.KFS_S3_KEY_PREFIX;
import static com.bharatpe.lending.constant.KfsConstants.SANCTION_LOAN_AGREEMENT_S3_KEY_PREFIX;

@Service
@Slf4j
public class SaveSignedLoanDocsConsumer {
    @Autowired
    S3BucketHandler s3BucketHandler;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    LendingKfsDao lendingKfsDao;

    @KafkaListener(
            topics = "save_signed_loan_docs",
            autoStartup ="${sign_loan_docs.consumer.enabled:false}",
            containerFactory = "ConfluentKafkaListenerContainer")
    public void saveSignedLoanDocs(String data) {
        log.info("saving loan docs : {}", data);
        Long applicationId = null;
        try {
            Map<String, String> signedLoanDocsMap = objectMapper.readValue(data, new TypeReference<Map<String, Object>>() {});
            if (Objects.isNull(signedLoanDocsMap.get("application_id")) ||
                    Objects.isNull(signedLoanDocsMap.get("signed_kfs")) ||
                    Objects.isNull(signedLoanDocsMap.get("signed_sanction_letter"))) {
                log.info("Invalid request to save loan docs");
                return;
            }
            applicationId = Long.parseLong(String.valueOf(signedLoanDocsMap.get("application_id")));

            String kfsFileName = KFS_S3_KEY_PREFIX + applicationId;
            URL kfsUrlNbfc = new URL(signedLoanDocsMap.get("signed_kfs"));
            InputStream kfsInstream = kfsUrlNbfc.openStream();
            s3BucketHandler.uploadToS3PdfBucket(kfsInstream, kfsFileName, "loan-document");

            String sanctionLoanAgreementFileName = SANCTION_LOAN_AGREEMENT_S3_KEY_PREFIX + applicationId;
            URL sanctionLoanAgreementUrl = new URL(signedLoanDocsMap.get("signed_sanction_letter"));
            InputStream sanctionLoanAgreementInstream = sanctionLoanAgreementUrl.openStream();
            s3BucketHandler.uploadToS3PdfBucket(sanctionLoanAgreementInstream, sanctionLoanAgreementFileName, "loan-document");

            LendingKfs lendingKfs = lendingKfsDao.findTop1ByApplicationIdOrderByIdDesc(applicationId);
            if(ObjectUtils.isEmpty(lendingKfs)){
                throw new Exception("Unable to retrieve KFS details for applicationId : " + applicationId);
            }
            lendingKfs.setNbfcSignedAt(new Date());
            lendingKfsDao.save(lendingKfs);

            log.info("Signed loan docs saved for {}", applicationId);
        } catch (Exception e) {
            log.error("Exception in saving signed loan docs for {}, {}, {}", applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
    }
}