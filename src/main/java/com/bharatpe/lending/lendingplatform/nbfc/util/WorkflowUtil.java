package com.bharatpe.lending.lendingplatform.nbfc.util;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.lendingplatform.nbfc.exception.LendingApplicationDetailsNotFoundException;
import com.bharatpe.lending.lendingplatform.nbfc.exception.LendingApplicationLenderDetailsNotFoundException;
import com.bharatpe.lending.lendingplatform.nbfc.exception.LendingApplicationNotFoundException;
import com.bharatpe.lending.lendingplatform.nbfc.service.workflow.Workflow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class WorkflowUtil {
    private final LendingApplicationDao lendingApplicationDao;
    private final LendingApplicationLenderDetailsDao laldDao;
    private final LendingApplicationDetailsDao lendingApplicationDetailsDao;

    public LendingApplication getLendingApplication(String applicationId) {
        Long id = Long.valueOf(applicationId);
        Optional<LendingApplication> lendingApplicationOptional = lendingApplicationDao.findById(id);
        if(!lendingApplicationOptional.isPresent()) {
            throw new LendingApplicationNotFoundException("Lending application not found");
        }
        return lendingApplicationOptional.get();
    }
    public LendingApplicationDetails getLendingApplicationDetails(String applicationId) {
        Long id = Long.valueOf(applicationId);
        LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findByApplicationId(id);
        if(ObjectUtils.isEmpty(lendingApplicationDetails)) {
            throw new LendingApplicationDetailsNotFoundException("Lending application details not found");
        }
        return lendingApplicationDetails;
    }
    public LendingApplication getLendingApplication(Long applicationId) {
        Optional<LendingApplication> lendingApplicationOptional = lendingApplicationDao.findById(applicationId);
        if(!lendingApplicationOptional.isPresent()) {
            throw new LendingApplicationNotFoundException("Lending application not found");
        }
        return lendingApplicationOptional.get();
    }

    public LendingApplicationLenderDetails getLendingApplicationLenderDetails(String applicationId, String lender) {
        Long id = Long.valueOf(applicationId);
        LendingApplicationLenderDetails lald = laldDao
                .findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(
                        id, Status.ACTIVE.name(), lender);
        if(ObjectUtils.isEmpty(lald)) {
            throw new LendingApplicationLenderDetailsNotFoundException("Lald not found");
        }
        return lald;
    }

    public static void invokeWorkflows(List<Workflow> workflows, Long applicationId) {
        if (ObjectUtils.isEmpty(workflows)) {
            log.warn("No workflows found for application id {}", applicationId);
            return;
        }
        for (Workflow workflow : workflows) {
            try {
                log.info("Invoking workflow for applicationId {} - {}", applicationId, workflow.getWorkflowName());
                if (!workflow.invoke(String.valueOf(applicationId))) {
                    break;
                }
            } catch (Exception e) {
                log.error("Error in invoking workflow for applicationId {} - {} - {}", applicationId, workflow.getWorkflowName(),
                        e.getStackTrace());
                break;
            }
        }
    }
}
