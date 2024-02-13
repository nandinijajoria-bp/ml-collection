package com.bharatpe.lending.loanV3.services.associations;

import com.bharatpe.lending.loanV3.interfaces.ILenderAssociationService;
import com.bharatpe.lending.loanV3.services.associationsV2.wrapper.InvokeAdditionalDocUploadWrapperService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class AdditionalDocUploadService implements ILenderAssociationService {

    @Autowired
    @Lazy
    InvokeAdditionalDocUploadWrapperService invokeAdditionalDocUploadWrapperService;

    @Override
    public Object invoke(Long applicationId, Map args) {
        try {
            log.info("invoking doc upload api for {}", applicationId);
            invokeAdditionalDocUploadWrapperService.invokeAdditionalDocUpload(applicationId, UUID.randomUUID().toString());
        } catch (Exception e) {
            log.error("exception occurred while initiating additional doc upload workflow for  {}, {}", applicationId, Arrays.asList(e.getStackTrace()));
        }
        return Optional.empty();
    }
}
