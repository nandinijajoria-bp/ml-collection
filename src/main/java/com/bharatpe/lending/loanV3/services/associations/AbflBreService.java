package com.bharatpe.lending.loanV3.services.associations;

import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.loanV3.interfaces.ILenderAssociationService;
import com.bharatpe.lending.loanV3.services.associationsV2.AbflBreServiceV2;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class AbflBreService implements ILenderAssociationService<Optional> {

    @Autowired
    KafkaTemplate kafkaTemplate;

    @Autowired
    LendingApplicationDetailsDao lendingApplicationDetailsDao;

    @Value("${abfl.kafka.enabled:false}")
    private Boolean kafkaEnabled;

    @Autowired
    AbflBreServiceV2 abflBreServiceV2;

    @Override
    public Optional invoke(Long applicationId, Map<String, Object> args) {
        try {
            Map<String,String> request = new HashMap(){{
                put("application_id", applicationId.toString());
            }};
            if (kafkaEnabled) {
                kafkaTemplate.send("invoke_bre", request);
                log.info("request pushed to bre_invoke kafka {}", request);
            } else {
                abflBreServiceV2.invokeBreViaAsyncApi(request);
                log.info("bre invoked via async api for {}", request);
            }
        } catch (Exception e) {
            log.error("exception occurred while initiating bre workflow for  {}", applicationId);
        }
        return Optional.empty();
    }
}
