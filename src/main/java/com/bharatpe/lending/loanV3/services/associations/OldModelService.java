package com.bharatpe.lending.loanV3.services.associations;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.loanV3.interfaces.ILenderAssociationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class OldModelService  implements ILenderAssociationService<Optional> {

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    LendingApplicationDetailsDao lendingApplicationDetailsDao;

    @Override
    public Optional invoke(Long applicationId, Map<String, Object> args) {
        // create new record and mark everything there
        try {
            Optional<LendingApplication> lendingApplication = lendingApplicationDao.findById(applicationId);
            if (!lendingApplication.isPresent()) {
                log.error("no application found for this id {}", applicationId);
                return Optional.empty();
            }
            LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusOrderByIdDesc(applicationId,Status.ACTIVE.name());
            if (!ObjectUtils.isEmpty(lendingApplicationLenderDetails) &&
                    (!lendingApplicationLenderDetails.getLender().equalsIgnoreCase(lendingApplication.get().getLender()) || LenderAssociationStages.COMPLETED.name().equalsIgnoreCase(lendingApplicationLenderDetails.getStage()))) {
                log.error("already processed event for {}", applicationId);
                return Optional.empty();
            }
            if (ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
                log.info("creating new record with old model svc !");
                lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
                lendingApplicationLenderDetails.setApplicationId(applicationId);
                lendingApplicationLenderDetails.setLender(lendingApplication.get().getLender());
            }
            lendingApplicationLenderDetails.setStatus(Status.ACTIVE.name());
            lendingApplicationLenderDetails.setBreStatus(LenderAssociationStatus.OLD_MODEL.name());
            lendingApplicationLenderDetails.setKycStatus(LenderAssociationStatus.OLD_MODEL.name());
            lendingApplicationLenderDetails.setStage(LenderAssociationStages.COMPLETED.name());
            lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);
            log.info("OldModel Service invoked marking lender assc. completed for {}", applicationId);
            LendingApplicationDetails lendingApplicationDetails =
                    lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(lendingApplication.get().getId());
            if (!ObjectUtils.isEmpty(lendingApplicationDetails)) {
                lendingApplicationDetails.setStage(LenderAssociationStages.COMPLETED.name());
                lendingApplicationDetailsDao.save(lendingApplicationDetails);
            }
        } catch (Exception e) {
            log.error("error occurred while creating lender record for application {} {}", applicationId, Arrays.asList(e.getStackTrace()));
        }
        return Optional.empty();
    }
}
