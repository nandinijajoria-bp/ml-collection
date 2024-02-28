package com.bharatpe.lending.loanV3.utils;

import com.bharatpe.lending.dao.LendingKfsDao;
import com.bharatpe.lending.entity.LendingKfs;
import com.bharatpe.lending.handlers.S3BucketHandler;
import com.bharatpe.lending.service.APIGatewayService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Date;

import static com.bharatpe.lending.constant.KfsConstants.ESIGNED_KFS_S3_KEY_PREFIX;
import static com.bharatpe.lending.constant.KfsConstants.ESIGNED_SANCTION_LOAN_AGREEMENT_S3_KEY_PREFIX;


@Slf4j
@Component
public class DocUploadUtils {

    @Autowired
    S3BucketHandler s3BucketHandler;

    @Autowired
    LendingKfsDao lendingKfsDao;

    @Autowired
    APIGatewayService apiGatewayService;

    @Value("${aws.s3.bucket:loan-document}")
    private String bucket;

    public void saveESignedDocs(Long applicationId, byte[] signedKFSBytes, byte[] signedSanctionBytes) {
        try {
            log.info("saving signed docs for applicationId : {}", applicationId);

            LendingKfs lendingKfs = lendingKfsDao.findTop1ByApplicationIdOrderByIdDesc(applicationId);
            if (ObjectUtils.isEmpty(lendingKfs)) {
                throw new Exception("Unable to retrieve LendingKFS details for applicationId : " + applicationId);
            }

            if (!ObjectUtils.isEmpty(signedSanctionBytes)) {
                String sanctionLoanAgreementFileName = ESIGNED_SANCTION_LOAN_AGREEMENT_S3_KEY_PREFIX + applicationId + ".pdf";
                InputStream inputStream = new ByteArrayInputStream(signedSanctionBytes);
                s3BucketHandler.uploadToS3PdfBucket(inputStream, sanctionLoanAgreementFileName, bucket);
                String sanctionUrl = s3BucketHandler.getPreSignedPublicURL(sanctionLoanAgreementFileName, bucket);
                String sanctionShortUrl = apiGatewayService.getShortUrl(sanctionUrl);
                if (sanctionShortUrl == null || sanctionShortUrl.isEmpty() || sanctionShortUrl.trim().isEmpty()) {
                    throw new Exception("Unable to create short URL for Sanction Loan Agreement doc link for : " + applicationId);
                }
                lendingKfs.setSignedSanctionDocFile(sanctionLoanAgreementFileName);
                lendingKfs.setSignedSanctionDocUrl(sanctionShortUrl);
                lendingKfsDao.save(lendingKfs);
            }

            if (!ObjectUtils.isEmpty(signedKFSBytes)) {
                String kfsLetterFileName = ESIGNED_KFS_S3_KEY_PREFIX + applicationId + ".pdf";
                InputStream inputStream = new ByteArrayInputStream(signedKFSBytes);
                s3BucketHandler.uploadToS3PdfBucket(inputStream, kfsLetterFileName, bucket);
                String kfsUrl = s3BucketHandler.getPreSignedPublicURL(kfsLetterFileName, bucket);
                String kfsShortUrl = apiGatewayService.getShortUrl(kfsUrl);
                if (kfsShortUrl == null || kfsShortUrl.isEmpty() || kfsShortUrl.trim().isEmpty()) {
                    throw new Exception("Unable to create short URL for KFS doc link for : " + applicationId);
                }
                lendingKfs.setSignedKfsDocUrl(kfsShortUrl);
                lendingKfs.setSignedKfsDocFile(kfsLetterFileName);
                lendingKfsDao.save(lendingKfs);
            }
            log.info("Signed loan docs saved for {}", applicationId);

        } catch (Exception e) {
            log.error("Exception in saving signed loan docs for {}, {}, {}", applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }

    }
}
