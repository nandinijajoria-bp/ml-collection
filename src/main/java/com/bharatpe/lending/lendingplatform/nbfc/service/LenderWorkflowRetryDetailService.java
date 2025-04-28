package com.bharatpe.lending.lendingplatform.nbfc.service;

import com.bharatpe.lending.common.dao.lendingplatform.LenderWorkflowRetryDetailDao;
import com.bharatpe.lending.common.entity.lendingplatform.LenderWorkflowRetryDetail;
import com.bharatpe.lending.common.enums.lendingplatform.LenderWorkflowRetryDetailStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class LenderWorkflowRetryDetailService {
    private final LenderWorkflowRetryDetailDao lenderWorkflowRetryDetailDao;

    public void updateStatus(Long id, LenderWorkflowRetryDetailStatus status) {
        try {
            Optional<LenderWorkflowRetryDetail> optional = lenderWorkflowRetryDetailDao.findById(id);
            if (!optional.isPresent()) {
                log.error("Lender workflow retry detail not found for id: {}", id);
                return;
            }
            LenderWorkflowRetryDetail lenderWorkflowRetryDetail = optional.get();
            lenderWorkflowRetryDetail.setStatus(status);
            lenderWorkflowRetryDetailDao.save(lenderWorkflowRetryDetail);
        } catch (Exception e) {
            log.error("Exception occurred while updating status of Lender workflow retry detail: {}", e.getMessage());
        }
    }
}
