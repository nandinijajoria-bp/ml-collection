package com.bharatpe.lending.loanV3.utils;

import com.bharatpe.lending.dao.LendingKfsDao;
import com.bharatpe.lending.entity.LendingKfs;
import com.bharatpe.lending.handlers.S3BucketHandler;
import com.bharatpe.lending.service.APIGatewayService;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.pdf.PdfCopy;
import com.itextpdf.text.pdf.PdfReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    public void saveESignedDocs(Long applicationId, String signedKFSUrl, String signedSanctionUrl) {
        try {
            log.info("saving signed docs for applicationId : {}", applicationId);

            LendingKfs lendingKfs = lendingKfsDao.findTop1ByApplicationIdOrderByIdDesc(applicationId);
            if (ObjectUtils.isEmpty(lendingKfs)) {
                throw new Exception("Unable to retrieve LendingKFS details for applicationId : " + applicationId);
            }

            if (!ObjectUtils.isEmpty(signedSanctionUrl)) {
                String sanctionLoanAgreementFileName = ESIGNED_SANCTION_LOAN_AGREEMENT_S3_KEY_PREFIX + applicationId;
                InputStream inputStream = URI.create(signedSanctionUrl).toURL().openConnection().getInputStream();
                s3BucketHandler.uploadToS3PdfBucket(inputStream, sanctionLoanAgreementFileName, bucket);
                String sanctionUrl = s3BucketHandler.getPreSignedPublicURL(sanctionLoanAgreementFileName, bucket);
                String sanctionShortUrl = apiGatewayService.getShortUrl(sanctionUrl);
                if (sanctionShortUrl == null || sanctionShortUrl.isEmpty() || sanctionShortUrl.trim().isEmpty()) {
                    throw new Exception("Unable to create short URL for Sanction Loan Agreement doc link for : " + applicationId);
                }
                lendingKfs.setSignedSanctionDocFile(sanctionLoanAgreementFileName);
                lendingKfs.setSignedSanctionDocUrl(sanctionShortUrl);
                lendingKfs.setNbfcSignedAt(new Date());
                lendingKfsDao.save(lendingKfs);
            }

            if (!ObjectUtils.isEmpty(signedKFSUrl)) {
                String kfsLetterFileName = ESIGNED_KFS_S3_KEY_PREFIX + applicationId;
                InputStream inputStream = URI.create(signedKFSUrl).toURL().openConnection().getInputStream();
                s3BucketHandler.uploadToS3PdfBucket(inputStream, kfsLetterFileName, bucket);
                String kfsUrl = s3BucketHandler.getPreSignedPublicURL(kfsLetterFileName, bucket);
                String kfsShortUrl = apiGatewayService.getShortUrl(kfsUrl);
                if (kfsShortUrl == null || kfsShortUrl.isEmpty() || kfsShortUrl.trim().isEmpty()) {
                    throw new Exception("Unable to create short URL for KFS doc link for : " + applicationId);
                }
                lendingKfs.setSignedKfsDocUrl(kfsShortUrl);
                lendingKfs.setSignedKfsDocFile(kfsLetterFileName);
                lendingKfs.setNbfcSignedAt(new Date());
                lendingKfsDao.save(lendingKfs);
            }
            log.info("Signed loan docs saved for {}", applicationId);

        } catch (Exception e) {
            log.error("Exception in saving signed loan docs for {}, {}, {}", applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }

    }

    public String mergeUnsignedDocs(Long applicationId, String kfsDocKey, String sanctionDocKey)
            throws IOException, DocumentException {
        /*
            1. download files
             from bucket to local storage
            2. merge both file in new merged file using ipdf
            3. set base64 payload to payload object
            4. delete file from local storage
         */

        log.info("Initiating downloading of the docs for application id : {}", applicationId);

        String mergedFileName = "KFS_SANCTION_AGREEMENT_MERGED_" + applicationId + ".pdf";

        try {
            // Download the first PDF file
            log.info("Downloading KFS document from S3 for application id: {}", applicationId);
            URL url1 = new URL(getS3PresignedUrlFromKey(kfsDocKey));
            URLConnection connection1 = url1.openConnection();
            InputStream inputStream1 = connection1.getInputStream();
            PdfReader reader1 = new PdfReader(inputStream1);

            // Download the second PDF file
            log.info("Downloading sanction document from S3 for application id: {}", applicationId);
            URL url2 = new URL(getS3PresignedUrlFromKey(sanctionDocKey));
            URLConnection connection2 = url2.openConnection();
            InputStream inputStream2 = connection2.getInputStream();
            PdfReader reader2 = new PdfReader(inputStream2);

            log.info("Merging docs for application id: {}, reader1 : {}, reader2: {}", applicationId, reader1, reader2);

            // Create the output file
            Document document = new Document();
            PdfCopy copy = new PdfCopy(document, Files.newOutputStream(Paths.get("/data/"+mergedFileName)));
            copy.setCompressionLevel(9);
            document.open();

            // Merge the PDF files
            copy.addDocument(reader1);
            copy.addDocument(reader2);

            // Close the document
            document.close();

            log.info("Uploading merged KFS and sanction letter for application id: {}", applicationId);

            File mergedFile = new File("/data/"+mergedFileName);
            s3BucketHandler.uploadFileToS3(mergedFile, bucket, mergedFileName);
            String mergeDocumentPresignedUrl = s3BucketHandler.getPreSignedPublicURLWithExceptionHandled(mergedFileName, bucket);

            log.info("Pre-signed URL for merged doc for application id: {} - {}", applicationId, mergeDocumentPresignedUrl);

            return mergeDocumentPresignedUrl;
        } catch (IOException | DocumentException e) {
            log.error("Error merging or uploading documents for application id: {}", applicationId, e);
            throw e; // Rethrow the exception to handle it in the caller method
        }
    }

    public String getS3PresignedUrlFromKey(String key) {
        log.info("key to fetch from aws: {}", key);
        return ObjectUtils.isEmpty(key) ? "" : s3BucketHandler.getPreSignedPublicURLWithExceptionHandled(key, bucket);
    }

}
