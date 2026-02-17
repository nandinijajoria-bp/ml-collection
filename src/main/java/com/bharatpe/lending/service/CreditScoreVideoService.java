package com.bharatpe.lending.service;

import com.bharatpe.lending.dao.CreditScoreVideoDao;
import com.bharatpe.lending.entity.CreditScoreVideo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class CreditScoreVideoService {

    @Autowired
    private CreditScoreVideoDao creditScoreVideoDao;

    // Save or update video
    public CreditScoreVideo saveVideo(CreditScoreVideo video) {
        Optional<CreditScoreVideo> existingVideoOpt = creditScoreVideoDao
                .findByOrderId(video.getOrderId());

        if (existingVideoOpt.isPresent()) {
            // Update existing record
            CreditScoreVideo existingVideo = existingVideoOpt.get();
            existingVideo.setTemplateId(video.getTemplateId());
            existingVideo.setVideoId(video.getVideoId());
            existingVideo.setVideoUrl(video.getVideoUrl());
            existingVideo.setStatus(video.getStatus());
            existingVideo.setErrorMessage(video.getErrorMessage());
            existingVideo.setCategory(video.getCategory());
            // updatedAt will be set by @PreUpdate
            return creditScoreVideoDao.save(existingVideo);
        } else {
            // Save new record
            // createdAt and updatedAt will be set by @PrePersist
            return creditScoreVideoDao.save(video);
        }
    }

    // Find valid video (status = "SUCCESS" and updatedAt within 30 days)
    public Optional<CreditScoreVideo> findValidByMerchantId(String merchantId) {
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        return creditScoreVideoDao.findValidByMerchantId(merchantId, thirtyDaysAgo);
    }

    // Find latest successful video regardless of age
    public Optional<CreditScoreVideo> findLatestSuccessByMerchantId(String merchantId) {
        return creditScoreVideoDao.findLatestSuccessByMerchantId(merchantId);
    }

    // Check if a video is valid (status = "SUCCESS" and updated within 30 days)
    public boolean isVideoValid(CreditScoreVideo video) {
        if (video == null || !"SUCCESS".equals(video.getStatus())) {
            return false;
        }
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        return video.getUpdatedAt().isAfter(thirtyDaysAgo);
    }
}