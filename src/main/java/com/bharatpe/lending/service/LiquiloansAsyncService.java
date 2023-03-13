package com.bharatpe.lending.service;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dto.NotificationPayloadDto;
import com.bharatpe.lending.common.service.LendingNotificationService;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.entity.LendingKfs;
import com.bharatpe.lending.loanV2.service.LendingApplicationServiceV2;
import com.bharatpe.lending.loanV3.services.associations.AbflDataUploadServiceUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class LiquiloansAsyncService {

    @Autowired
    LendingApplicationServiceV2 lendingApplicationServiceV2;

    @Autowired
    AbflDataUploadServiceUtil abflDataUploadServiceUtil;

    @Autowired
    LendingNotificationService lendingNotificationService;

    @Async
    public void generateWelcomeDocAndNotify(LendingApplication finalLendingApplication, BasicDetailsDto finalBasicDetailDto, LendingKfs lendingKfs) throws Exception {
        lendingApplicationServiceV2.generateWelcomeDocument(finalLendingApplication, lendingKfs, finalBasicDetailDto,null);
        if (ObjectUtils.isEmpty(lendingKfs.getWelcomeDocUrl())) {
            log.info("welcome letter generation failed for {}", finalBasicDetailDto.getId());
            return;
        }
        abflDataUploadServiceUtil.uploadDocuments(finalLendingApplication.getId(), Arrays.asList("WELCOME_LETTER"));
        String identifier = "LENDING_DISBURSAL_WELCOME_LETTER_SMS";
        Map<String, Object> templateParams = new HashMap<>();
        templateParams.put("beneficiary_name", finalBasicDetailDto.getBeneficiaryName());
        templateParams.put("welcome_doc_url", lendingKfs.getWelcomeDocUrl());
        NotificationPayloadDto notificationPayloadDto = new NotificationPayloadDto();
        notificationPayloadDto.setTemplateIdentifier(identifier);
        notificationPayloadDto.setMobile(finalBasicDetailDto.getMobile());
        notificationPayloadDto.setClientName("LENDING");
        notificationPayloadDto.setTemplateParams(templateParams);
        lendingNotificationService.notify(notificationPayloadDto);
        log.info("sms notification sent for welcome letter to {} !", finalLendingApplication.getMerchantId());
        identifier = "LENDING_DISBURSAL_WELCOME_LETTER_PUSH";
        notificationPayloadDto.setTemplateIdentifier(identifier);
        lendingNotificationService.notify(notificationPayloadDto);
        log.info("push notification sent for welcome letter to {} !", finalLendingApplication.getMerchantId());
        identifier = "LENDING_DISBURSAL_WELCOME_LETTER_WA";
        notificationPayloadDto.setTemplateIdentifier(identifier);
        lendingNotificationService.notify(notificationPayloadDto);
        log.info("WA notification sent for welcome letter to {} !", finalLendingApplication.getMerchantId());
    }
}
