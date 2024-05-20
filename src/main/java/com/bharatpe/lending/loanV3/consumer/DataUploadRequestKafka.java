package com.bharatpe.lending.loanV3.consumer;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingGstDetail;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.dao.LendingMerchantPermissionsDao;
import com.bharatpe.lending.common.dao.LendingMerchantReferencesDao;
import com.bharatpe.lending.common.dao.LendingShopDocumentsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.entity.LendingMerchantPermissions;
import com.bharatpe.lending.common.entity.LendingMerchantReferences;
import com.bharatpe.lending.common.entity.LendingShopDocuments;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.common.enums.StatusCheckResponse;
import com.bharatpe.lending.common.service.merchant.dto.MerchantDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.common.util.ConfigResolver;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingGstDao;
import com.bharatpe.lending.dao.LendingKfsDao;
import com.bharatpe.lending.entity.LendingKfs;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.handlers.S3BucketHandler;
import com.bharatpe.lending.loanV3.dto.*;
import com.bharatpe.lending.loanV3.factory.LenderAssociationStageFactory;
import com.bharatpe.lending.loanV3.factory.LenderGatewayFactory;
import com.bharatpe.lending.loanV3.interfaces.ILenderAssociationService;
import com.bharatpe.lending.loanV3.services.INbfcLenderGateway;
import com.bharatpe.lending.loanV3.utils.ConverterUtils;
import com.bharatpe.lending.loanV3.utils.NbfcUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.io.FileNotFoundException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class DataUploadRequestKafka {

    @Autowired
    ConfigResolver configResolver;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LenderAssociationStageFactory lenderAssociationStageFactory;

    ExecutorService executorService = Executors.newFixedThreadPool(2);

    @KafkaListener(topics = "${abfl.dataupload.topic:invoke_data_upload}", concurrency = "5")
    @KafkaListener(
            topics = "${abfl.dataupload.topic:invoke_data_upload}",
            concurrency = "5",
            autoStartup = "${kafka.confluent.consumer:false}",
            containerFactory = "ConfluentKafkaListenerContainer")
    public void invokeDocUpload(String request) {
        MDC.put("requestId", UUID.randomUUID().toString());
        log.info("Received doc upload request:{}", request);
        Map<String, String> docUploadRequestString = configResolver.getConfig(request, new TypeReference<Map<String, String>>() {
        });
        Optional<LendingApplication> lendingApplication = lendingApplicationDao.findById(Long.valueOf(docUploadRequestString.get("application_id")));
        if (!lendingApplication.isPresent()) {
            log.info("no application found for id {}", docUploadRequestString.get("application_id"));
        }
        ILenderAssociationService iLenderAssociationService =
                lenderAssociationStageFactory.getStageAssociatedLenderService(LenderAssociationStages.DATA_UPLOAD.name()).getLenderAssociationService(lendingApplication.get().getLender());

        iLenderAssociationService.invoke(lendingApplication.get().getId(), docUploadRequestString);
    }

}
