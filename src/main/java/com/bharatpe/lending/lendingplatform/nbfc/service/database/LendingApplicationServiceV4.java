package com.bharatpe.lending.lendingplatform.nbfc.service.database;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.dao.LendingApplicationDao;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class LendingApplicationServiceV4 {
    private final LendingApplicationDao lendingApplicationDao;
    @Transactional
    public void save(LendingApplication lendingApplication) {
        try {
            lendingApplicationDao.save(lendingApplication);
        } catch (Exception e) {
            log.error("Error in saving lending application : {} {}", e.getMessage(), lendingApplication);
        }
    }
}
