package com.bharatpe.lending.service;

import com.bharatpe.lending.common.dao.LendingApplicationKycDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationKycDetails;
import com.bharatpe.lending.handlers.S3BucketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.io.InputStream;
import java.net.URL;

import static com.bharatpe.lending.constant.KycConstants.JPEG;
import static com.bharatpe.lending.constant.KycConstants.SELFIE_IMAGE;

@Service
@Slf4j
@RequiredArgsConstructor
public class LendingApplicationKycDetailsService {
    private final S3BucketHandler s3BucketHandler;
    private final LendingApplicationKycDetailsDao lendingApplicationKycDetailsDao;
    @Value("${aws.s3.bucket:-}")
    private String awsBucket;

    public void copySelfieFromPresignedUrlToS3(String presignedUrl, Long applicationId) {
        String selfieImageFileName = SELFIE_IMAGE + applicationId + JPEG;

        try {
            InputStream inputStream = new URL(presignedUrl).openStream();
            boolean uploadedToS3 = s3BucketHandler.uploadImageToS3Bucket(inputStream, selfieImageFileName, awsBucket);
            if(!uploadedToS3) {
                log.error("Failed to upload selfie image to S3 for application ID: {}", applicationId);
                return;
            }
            updateSelfieImageInKycDetails(applicationId, selfieImageFileName);
        } catch (Exception e) {
            log.error("Error copying selfie image to S3 for application ID {}: {}", applicationId, e.getMessage(), e);
        }
    }

    private void updateSelfieImageInKycDetails(Long applicationId, String selfieImageFileName) {
        LendingApplicationKycDetails kycDetails =
                lendingApplicationKycDetailsDao.findTop1ByApplicationIdOrderByIdDesc(applicationId);
        if (ObjectUtils.isEmpty(kycDetails)) {
            log.error("Lending application kyc details not found for application ID: {}", applicationId);
        }
        kycDetails.setSelfieImage(selfieImageFileName);
        lendingApplicationKycDetailsDao.save(kycDetails);
        log.info("Successfully updated selfie image in lending application kyc details for application ID: {}", applicationId);
    }

    public String getSelfieImage(Long applicationId) {
        LendingApplicationKycDetails kycDetails =
                lendingApplicationKycDetailsDao.findTop1ByApplicationIdOrderByIdDesc(applicationId);
        if (ObjectUtils.isEmpty(kycDetails)) {
            log.error("Kyc details not found for application ID: {}", applicationId);
            return null;
        }
        return kycDetails.getSelfieImage();
    }
}
