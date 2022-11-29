package com.bharatpe.lending.consumer;

import com.bharatpe.lending.handlers.S3BucketHandler;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

@Service
@Slf4j
public class SaveSignedLoanDocsConsumer {
    @Autowired
    S3BucketHandler s3BucketHandler;

    @Autowired
    ObjectMapper objectMapper;

    @KafkaListener(topics = "save_signed_loan_docs", autoStartup ="${sign_loan_docs.consumer.enabled:false}")
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

            String kfsFileName = "Key_Facts_Statement_" + applicationId;
            URL kfsUrlNbfc = new URL(signedLoanDocsMap.get("signed_kfs"));
            InputStream kfsInstream = kfsUrlNbfc.openStream();
            s3BucketHandler.uploadToS3PdfBucket(kfsInstream, kfsFileName, "loan-document");

            String sanctionLoanAgreementFileName = "Sanction_Cum_Loan_Agreement_" + applicationId;
            URL sanctionLoanAgreementUrl = new URL(signedLoanDocsMap.get("signed_sanction_letter"));
            InputStream sanctionLoanAgreementInstream = sanctionLoanAgreementUrl.openStream();
            s3BucketHandler.uploadToS3PdfBucket(sanctionLoanAgreementInstream, sanctionLoanAgreementFileName, "loan-document");

            log.info("Signed loan docs saved for {}", applicationId);
        } catch (Exception e) {
            log.error("Exception in saving signed loan docs for {}, {}, {}", applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
    }
}