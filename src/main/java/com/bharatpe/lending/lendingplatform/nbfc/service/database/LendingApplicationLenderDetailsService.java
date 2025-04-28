package com.bharatpe.lending.lendingplatform.nbfc.service.database;

import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.dto.projection.LALDWorkflowDetails;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class LendingApplicationLenderDetailsService {
    private final LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    public LALDWorkflowDetails getLaldWorkflowDetailsByApplicationId(Long applicationId) {
        try {
            return lendingApplicationLenderDetailsDao
                    .findLendingApplicationLenderDetailsByApplicationId(applicationId);
        } catch (Exception e) {
            log.error("Error while fetching LALD details for applicationId: {}", applicationId, e);
        }
        return null;
    }

    @Transactional
    public LendingApplicationLenderDetails save(LendingApplicationLenderDetails lendingApplicationLenderDetails) {
        try {
            return lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);
        } catch (Exception e) {
            log.error("Error in saving lending application lender details: {} {}", e.getMessage(), lendingApplicationLenderDetails);
        }
        return null;
    }
}
