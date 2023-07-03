package com.bharatpe.lending.loanV3.services.associations.piramal;

import com.bharatpe.lending.loanV3.interfaces.ILenderAssociationService;
import com.bharatpe.lending.loanV3.services.associationsV2.piramal.impl.PiramalAdditionalDocUploadService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class PushAuditService implements ILenderAssociationService<Optional> {

    @Autowired
    PiramalAdditionalDocUploadService piramalAdditionalDocUploadService;

    @Override
    public Optional invoke(Long applicationId, Map<String, Object> args) {
        try {
            log.info("applicationId for additional doc upload: {}",applicationId);
            String requestId;
            if (null != args) {
                requestId = String.valueOf(args.get("requestId"));
            } else {
                requestId =  UUID.randomUUID().toString();
            }
            piramalAdditionalDocUploadService.uploadPiramalDoc(applicationId, requestId);
        } catch (Exception e) {
            log.error("exception occurred while initiating update lead workflow for  {}", applicationId);
            // TODO: 09/03/23 add lender change workflow
        }
        return Optional.empty();
    }
}
