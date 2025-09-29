package com.bharatpe.lending.loanV3.utils;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.entity.LendingShopDocuments;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.dao.LendingKfsDao;
import com.bharatpe.lending.entity.LendingKfs;
import com.bharatpe.lending.handlers.S3BucketHandler;
import com.bharatpe.lending.loanV2.service.LendingApplicationServiceV2;
import com.bharatpe.lending.loanV3.dto.BusinessDocsDTO;
import com.bharatpe.lending.loanV3.dto.CKycResponseDto;
import com.bharatpe.lending.loanV3.enums.DocType;
import com.bharatpe.lending.service.APIGatewayService;
import com.bharatpe.util.pdf.PdfMergerUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static com.bharatpe.lending.constant.KfsConstants.*;


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

    @Lazy
    @Autowired
    LendingApplicationServiceV2 lendingApplicationServiceV2;

    @Autowired
    PdfMergerUtil pdfMergerUtil;

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
            log.info("saving signed docs for applicationId : {} signedKFSUrl: {} signedSanctionUrl: {}", applicationId,signedKFSUrl,signedSanctionUrl);

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
            throws IOException {
        /*
            1. downloads files from bucket to local storage
            2. merge both file in new merged file using PdfMergerUtil
            3. set base64 payload to payload object
            4. delete file from local storage
         */

        log.info("Starting PDF merge process using PdfMergerUtil for application id: {}, kfsDocKey: {}, sanctionDocKey: {}", 
                applicationId, kfsDocKey, sanctionDocKey);

        String mergedFileName = "KFS_SANCTION_AGREEMENT_MERGED_" + applicationId + ".pdf";

        try {
            // Download the first PDF file
            log.info("Downloading KFS document from S3 for application id: {} using key: {}", applicationId, kfsDocKey);
            URL url1 = new URL(getS3PresignedUrlFromKey(kfsDocKey));
            URLConnection connection1 = url1.openConnection();
            byte[] pdf1Bytes = org.apache.commons.io.IOUtils.toByteArray(connection1.getInputStream());
            log.info("Successfully downloaded KFS document - application id: {}, size: {} bytes", applicationId, pdf1Bytes.length);

            // Download the second PDF file
            log.info("Downloading sanction document from S3 for application id: {} using key: {}", applicationId, sanctionDocKey);
            URL url2 = new URL(getS3PresignedUrlFromKey(sanctionDocKey));
            URLConnection connection2 = url2.openConnection();
            byte[] pdf2Bytes = org.apache.commons.io.IOUtils.toByteArray(connection2.getInputStream());
            log.info("Successfully downloaded sanction document - application id: {}, size: {} bytes", applicationId, pdf2Bytes.length);

            log.info("Starting PDF merge using PdfMergerUtil for application id: {} - merging {} bytes + {} bytes", 
                    applicationId, pdf1Bytes.length, pdf2Bytes.length);

            // Merge the PDF files using PdfMergerUtil
            byte[] mergedPdfBytes = pdfMergerUtil.mergePdfs(Arrays.asList(pdf1Bytes, pdf2Bytes));
            log.info("Successfully merged PDFs using PdfMergerUtil - application id: {}, merged size: {} bytes", 
                    applicationId, mergedPdfBytes.length);

            // Write merged PDF to file
            log.info("Writing merged PDF to local file for application id: {}, fileName: {}", applicationId, mergedFileName);
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream("/data/" + mergedFileName)) {
                fos.write(mergedPdfBytes);
            }
            log.info("Successfully wrote merged PDF to local file - application id: {}", applicationId);

            log.info("Uploading merged KFS and sanction letter to S3 for application id: {}", applicationId);

            File mergedFile = new File("/data/"+mergedFileName);
            s3BucketHandler.uploadFileToS3(mergedFile, bucket, mergedFileName);
            log.info("Successfully uploaded merged document to S3 - application id: {}, bucket: {}, fileName: {}", 
                    applicationId, bucket, mergedFileName);
            
            String mergeDocumentPresignedUrl = s3BucketHandler.getPreSignedPublicURLWithExceptionHandled(mergedFileName, bucket);
            log.info("Generated pre-signed URL for merged document - application id: {}, url: {}", applicationId, mergeDocumentPresignedUrl);

            return mergeDocumentPresignedUrl;
        } catch (IOException e) {
            log.error("Error merging or uploading documents for application id: {} - kfsDocKey: {}, sanctionDocKey: {}", 
                    applicationId, kfsDocKey, sanctionDocKey, e);
            throw e; // Rethrow the exception to handle it in the caller method
        }
    }

    public String mergeUnsignedDocs(Long applicationId, List<String> docKeyList)
            throws IOException {
        /*
            1. downloads files from bucket to local storage
            2. merge list of files in new merged file using PdfMergerUtil
            3. set base64 payload to payload object
            4. delete file from local storage
         */

        log.info("Starting PDF merge process using PdfMergerUtil for application id: {} with {} documents: {}", 
                applicationId, docKeyList.size(), docKeyList);

        String mergedFileName = "LOAN_DOCUMENTS_MERGED_" + applicationId + ".pdf";
        try {
            List<byte[]> pdfBytesList = new ArrayList<>();
            int totalBytes = 0;
            
            for(int i = 0; i < docKeyList.size(); i++) {
                String docKey = docKeyList.get(i);
                log.info("Downloading document {}/{} from S3 for application id: {} using key: {}", 
                        i+1, docKeyList.size(), applicationId, docKey);
                URL url = new URL(getS3PresignedUrlFromKey(docKey));
                URLConnection connection = url.openConnection();
                byte[] pdfBytes = org.apache.commons.io.IOUtils.toByteArray(connection.getInputStream());
                pdfBytesList.add(pdfBytes);
                totalBytes += pdfBytes.length;
                log.info("Successfully downloaded document {}/{} - application id: {}, size: {} bytes, total so far: {} bytes", 
                        i+1, docKeyList.size(), applicationId, pdfBytes.length, totalBytes);
            }

            log.info("Starting PDF merge using PdfMergerUtil for application id: {} - merging {} documents, total size: {} bytes", 
                    applicationId, pdfBytesList.size(), totalBytes);

            // Merge the PDF files using PdfMergerUtil
            byte[] mergedPdfBytes = pdfMergerUtil.mergePdfs(pdfBytesList);
            log.info("Successfully merged {} PDFs using PdfMergerUtil - application id: {}, merged size: {} bytes", 
                    pdfBytesList.size(), applicationId, mergedPdfBytes.length);

            // Write merged PDF to file
            log.info("Writing merged PDF to local file for application id: {}, fileName: {}", applicationId, mergedFileName);
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream("/data/" + mergedFileName)) {
                fos.write(mergedPdfBytes);
            }
            log.info("Successfully wrote merged PDF to local file - application id: {}", applicationId);

            log.info("Uploading merged document to S3 for application id: {}", applicationId);

            File mergedFile = new File("/data/"+mergedFileName);
            s3BucketHandler.uploadFileToS3(mergedFile, bucket, mergedFileName);
            log.info("Successfully uploaded merged document to S3 - application id: {}, bucket: {}, fileName: {}", 
                    applicationId, bucket, mergedFileName);
            
            String mergeDocumentPresignedUrl = s3BucketHandler.getPreSignedPublicURLWithExceptionHandled(mergedFileName, bucket);
            log.info("Generated pre-signed URL for merged document - application id: {}, url: {}", applicationId, mergeDocumentPresignedUrl);

            return mergeDocumentPresignedUrl;
        } catch (IOException e) {
            log.error("Error merging or uploading documents for application id: {} - docKeyList: {}", applicationId, docKeyList, e);
            throw e; // Rethrow the exception to handle it in the caller method
        }
    }

    public String getS3PresignedUrlFromKey(String key) {
        log.info("key to fetch from aws: {}", key);
        return ObjectUtils.isEmpty(key) ? "" : s3BucketHandler.getPreSignedPublicURLWithExceptionHandled(key, bucket);
    }


    public void saveAgreementDocs(LendingApplication application, String lender, InputStream lenderKFSStream, InputStream lenderSanctionStream, Boolean preSigned) {
        try {
            log.info("saving lender agreement docs for applicationId : {}", application.getId());

            LendingKfs lendingKfs = lendingKfsDao.findTop1ByApplicationIdAndLenderOrderByIdDesc(application.getId(), lender);
            if(ObjectUtils.isEmpty(lendingKfs)){
                log.info("Lending KFS details not present, Saving KFS details for Id: {} for merchant : {}", application.getId(), application.getMerchantId());
                lendingKfs = lendingApplicationServiceV2.saveKfsDetails(application.getMerchantId(), application);
            }

            if (!ObjectUtils.isEmpty(lenderSanctionStream)) {
                String sanctionLoanAgreementFileName = (preSigned ? SANCTION_LOAN_AGREEMENT_S3_KEY_PREFIX  : ESIGNED_SANCTION_LOAN_AGREEMENT_S3_KEY_PREFIX) + application.getId() + ".pdf";
                s3BucketHandler.uploadToS3PdfBucket(lenderSanctionStream, sanctionLoanAgreementFileName, bucket);
                String sanctionUrl = s3BucketHandler.getPreSignedPublicURL(sanctionLoanAgreementFileName, bucket);
                String sanctionShortUrl = apiGatewayService.getShortUrl(sanctionUrl);
                if (sanctionShortUrl == null || sanctionShortUrl.isEmpty() || sanctionShortUrl.trim().isEmpty()) {
                    throw new Exception("Unable to create short URL for Sanction Loan Agreement doc link for : " + application.getId());
                }
                if(preSigned) {
                    lendingKfs.setSanctionLoanAgreementDocUrl(sanctionShortUrl);
                    lendingKfs.setSanctionLoanAgreementDocFile(sanctionLoanAgreementFileName);
                } else {
                    lendingKfs.setSignedSanctionDocFile(sanctionLoanAgreementFileName);
                    lendingKfs.setSignedSanctionDocUrl(sanctionShortUrl);
                    lendingKfs.setNbfcSignedAt(new Date());
                }
                lendingKfsDao.save(lendingKfs);
            }

            if (!ObjectUtils.isEmpty(lenderKFSStream)) {
                String kfsLetterFileName = (preSigned ? KFS_S3_KEY_PREFIX : ESIGNED_KFS_S3_KEY_PREFIX) + application.getId() + ".pdf";
                s3BucketHandler.uploadToS3PdfBucket(lenderKFSStream, kfsLetterFileName, bucket);
                String kfsUrl = s3BucketHandler.getPreSignedPublicURL(kfsLetterFileName, bucket);
                String kfsShortUrl = apiGatewayService.getShortUrl(kfsUrl);
                if (kfsShortUrl == null || kfsShortUrl.isEmpty() || kfsShortUrl.trim().isEmpty()) {
                    throw new Exception("Unable to create short URL for KFS doc link for : " + application.getId());
                }
                if(preSigned) {
                    lendingKfs.setKfsDocUrl(kfsShortUrl);
                    lendingKfs.setKfsDocFile(kfsLetterFileName);
                } else {
                    lendingKfs.setSignedKfsDocUrl(kfsShortUrl);
                    lendingKfs.setSignedKfsDocFile(kfsLetterFileName);
                    lendingKfs.setNbfcSignedAt(new Date());
                }
                lendingKfsDao.save(lendingKfs);
            }
            log.info("Lender agreement docs saved for {}", application.getId());

        } catch (Exception e) {
            log.error("Exception in saving lender agreement docs for {}, {}, {}", application.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }

    }

    public LenderAssociationStatus getStatusForDocumentUpload(DocType docType, String currentStage) {
        switch (docType.name()) {
            case "DIGILOCKER_AADHAAR_XML":
                switch (currentStage) {
                    case "FAILED":
                        return LenderAssociationStatus.AADHAR_UPLOAD_FAILED;
                    case "SUCCESS":
                        return LenderAssociationStatus.AADHAR_UPLOAD_SUCCESS;
                    default:
                        return LenderAssociationStatus.AADHAR_UPLOAD_PENDING;
                }
            case "SELFIE":
                switch (currentStage) {
                    case "FAILED":
                        return LenderAssociationStatus.SELFIE_UPLOAD_FAILED;
                    case "SUCCESS":
                        return LenderAssociationStatus.SELFIE_UPLOAD_SUCCESS;
                    default:
                        return LenderAssociationStatus.SELFIE_UPLOAD_PENDING;
                }
            case "SHOP_PHOTO":
                switch (currentStage) {
                    case "FAILED":
                        return LenderAssociationStatus.SHOP_PHOTO_UPLOAD_FAILED;
                    case "SUCCESS":
                        return LenderAssociationStatus.SHOP_PHOTO_UPLOAD_SUCCESS;
                    default:
                        return LenderAssociationStatus.SHOP_PHOTO_UPLOAD_PENDING;
                }
            case "BUSINESS_DOC":
                switch (currentStage) {
                    case "FAILED":
                        return LenderAssociationStatus.BUSINESS_DOC_UPLOAD_FAILED;
                    case "SUCCESS":
                        return LenderAssociationStatus.BUSINESS_DOC_UPLOAD_SUCCESS;
                    default:
                        return LenderAssociationStatus.BUSINESS_DOC_UPLOAD_PENDING;
                }
            case "AUDIT_TRAIL_DOC":
                switch (currentStage) {
                    case "FAILED":
                        return LenderAssociationStatus.AUDIT_TRAIL_FAILED;
                    case "SUCCESS":
                        return LenderAssociationStatus.AUDIT_TRAIL_SUCCESS;
                    default:
                        return LenderAssociationStatus.AUDIT_TRAIL_PENDING;
                }
            default:
                return null;
        }
    }

    public String getFileBlob(DocType fileBlob, CKycResponseDto cKycResponseDto, LendingKfs lendingKfs, LendingShopDocuments lendingShopDocument, BusinessDocsDTO businessDocs) {
        String key = null;
        switch (fileBlob.name()) {
            case "DIGILOCKER_AADHAAR_XML":
                return cKycResponseDto.getPoaString();
            case "SELFIE":
                return cKycResponseDto.getSelfieString();
            case "KEY_FACT_STATEMENT":
                key = lendingKfs.getKfsDocFile();
                break;
            case "LOAN_AGREEMENT":
                key = lendingKfs.getSanctionLoanAgreementDocFile();
                break;
            case "SHOP_PHOTO":
                key =  lendingShopDocument.getProofFrontSide();
                break;
            case "BUSINESS_DOC":
                return businessDocs.getPdfUrl();
            case "AUDIT_TRAIL_DOC":
                key = lendingKfs.getLoaDocFile();
                break;
            default:
                return null;
        }
        return getS3PresignedUrlFromKey(key);
    }

    public String mergeDocs(Long applicationId, InputStream inputStream1, InputStream inputStream2, String mergedFileName)
            throws IOException {
        /*
            1. merge both file in new merged file using PdfMergerUtil
            3. delete file from local storage
         */

        log.info("Starting PDF merge process using PdfMergerUtil for application id: {} with fileName: {}", 
                applicationId, mergedFileName);
        try {
            // Read PDF bytes from input streams
            log.info("Reading PDF bytes from input streams for application id: {}", applicationId);
            byte[] pdf1Bytes = org.apache.commons.io.IOUtils.toByteArray(inputStream1);
            byte[] pdf2Bytes = org.apache.commons.io.IOUtils.toByteArray(inputStream2);
            log.info("Successfully read PDF bytes from input streams - application id: {}, stream1 size: {} bytes, stream2 size: {} bytes", 
                    applicationId, pdf1Bytes.length, pdf2Bytes.length);

            log.info("Starting PDF merge using PdfMergerUtil for application id: {} - merging {} bytes + {} bytes", 
                    applicationId, pdf1Bytes.length, pdf2Bytes.length);
            
            // Merge the PDF files using PdfMergerUtil
            byte[] mergedPdfBytes = pdfMergerUtil.mergePdfs(Arrays.asList(pdf1Bytes, pdf2Bytes));
            log.info("Successfully merged PDFs using PdfMergerUtil - application id: {}, merged size: {} bytes", 
                    applicationId, mergedPdfBytes.length);

            // Write merged PDF to file
            log.info("Writing merged PDF to local file for application id: {}, fileName: {}", applicationId, mergedFileName);
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream("/data/" + mergedFileName)) {
                fos.write(mergedPdfBytes);
            }
            log.info("Successfully wrote merged PDF to local file - application id: {}", applicationId);
            
            log.info("Uploading merged document to S3 for application id: {}", applicationId);
            File mergedFile = new File("/data/" + mergedFileName);
            s3BucketHandler.uploadFileToS3(mergedFile, bucket, mergedFileName);
            log.info("Successfully uploaded merged document to S3 - application id: {}, bucket: {}, fileName: {}", 
                    applicationId, bucket, mergedFileName);
            
            String mergeDocumentPresignedUrl = s3BucketHandler.getPreSignedPublicURLWithExceptionHandled(mergedFileName, bucket);
            log.info("Generated pre-signed URL for merged document - application id: {}, url: {}", applicationId, mergeDocumentPresignedUrl);
            return mergeDocumentPresignedUrl;
        } catch (IOException e) {
            log.error("Error merging documents for application id: {} - fileName: {}", applicationId, mergedFileName, e);
            throw e; // Rethrow the exception to handle it in the caller method
        }
    }

}
