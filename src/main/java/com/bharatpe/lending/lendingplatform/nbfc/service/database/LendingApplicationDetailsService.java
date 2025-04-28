package com.bharatpe.lending.lendingplatform.nbfc.service.database;

import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class LendingApplicationDetailsService {
    private final LendingApplicationDetailsDao lendingApplicationDetailsDao;

    @Transactional
    public void save(LendingApplicationDetails lendingApplicationDetails) {
        try {
            lendingApplicationDetailsDao.save(lendingApplicationDetails);
        } catch (Exception e) {
            log.error("Error in saving lending application details: {} {}", e.getMessage(), lendingApplicationDetails);
        }
    }
}
