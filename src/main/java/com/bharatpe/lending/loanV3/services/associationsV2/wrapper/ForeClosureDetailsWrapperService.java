package com.bharatpe.lending.loanV3.services.associationsV2.wrapper;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.loanV3.interfaces.ILenderAssociationService;
import com.bharatpe.lending.loanV3.services.associationsV2.AssociationServiceUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class ForeClosureDetailsWrapperService implements ILenderAssociationService<Double> {

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    AssociationServiceUtil associationServiceUtil;

    public Double invoke(Long applicationId, Map<String, Object> args) {
        Optional<LendingApplication> lendingApplication = lendingApplicationDao.findById(applicationId);
        if (!lendingApplication.isPresent()) {
            log.info("no lending application record found for {}", applicationId);
            return 0d;
        }
        LendingApplicationLenderDetails lendingApplicationLenderDetails =
                lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusOrderByIdDesc(applicationId, Status.ACTIVE.name());
        if (ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
            log.info("no lender assc record found of {} for {}", lendingApplication.get().getLender(), applicationId);
            return 0d;
        }
        Double foreclosureAmount = associationServiceUtil.getForeclosureAmount(lendingApplication.get().getLender(), applicationId);
        log.info("Foreclosure amount fetched for application id {} is {}",applicationId, foreclosureAmount);
        return foreclosureAmount;
    }
}
