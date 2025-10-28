package com.bharatpe.lending.service;

import com.bharatpe.lending.dao.CreditScoreVideoDao;
import com.bharatpe.lending.entity.CreditScoreVideo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;

@Service
public class CreditScoreVideoService {

    @Autowired
    private CreditScoreVideoDao creditScoreVideoDao;

    // Save or update video
    public CreditScoreVideo saveVideo(CreditScoreVideo video) {
        Optional<CreditScoreVideo> existingVideoOpt = creditScoreVideoDao
                .findByMerchantId(video.getMerchantId());

        if (existingVideoOpt.isPresent()) {
            // Update existing record
            CreditScoreVideo existingVideo = existingVideoOpt.get();
            existingVideo.setVideoLink(video.getVideoLink());
            existingVideo.setCategory(video.getCategory());
            existingVideo.setIsValid(video.getIsValid());
            existingVideo.setVideoGeneratedDate(video.getVideoGeneratedDate() != null ? video.getVideoGeneratedDate() : LocalDate.now());
            existingVideo.setValidTill(existingVideo.getVideoGeneratedDate().plusDays(30));
            return creditScoreVideoDao.save(existingVideo);
        } else {
            // Save new record
            if (video.getVideoGeneratedDate() == null) {
                video.setVideoGeneratedDate(LocalDate.now());
            }
            video.setValidTill(video.getVideoGeneratedDate().plusDays(30));
            return creditScoreVideoDao.save(video);
        }
    }

    // Find valid video (also check validTill)
    public Optional<CreditScoreVideo> findValidByMerchantId(String merchantId) {
        Optional<CreditScoreVideo> validVideoOpt = creditScoreVideoDao
                .findByMerchantIdAndIsValidTrue(merchantId);

        if (validVideoOpt.isPresent()) {
            CreditScoreVideo video = validVideoOpt.get();
            if (video.getValidTill() != null && !video.getValidTill().isBefore(LocalDate.now())) {
                return validVideoOpt;
            } else {
                // Expired -> mark invalid
                video.setIsValid(false);
                creditScoreVideoDao.save(video);
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}