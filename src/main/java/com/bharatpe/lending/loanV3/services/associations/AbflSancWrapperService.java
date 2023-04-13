package com.bharatpe.lending.loanV3.services.associations;

import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.loanV3.interfaces.ILenderAssociationService;
import com.bharatpe.lending.loanV3.services.associationsV2.AbflSanctionServiceV2;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class AbflSancWrapperService implements ILenderAssociationService<Optional> {
    @Autowired
    KafkaTemplate kafkaTemplate;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Value("${abfl.kafka.enabled:false}")
    private Boolean kafkaEnabled;

    @Autowired
    @Lazy
    AbflSanctionServiceV2 abflSanctionServiceV2;

    @Override
    public Optional invoke(Long applicationId, Map<String, Object> args) {

        try {
            Map<String,String> request = new HashMap(){{
                put("application_id", applicationId.toString());
            }};
            if (kafkaEnabled) {
                kafkaTemplate.send("invoke_sanction", request);
                log.info("request pushed to invoke_sanction kafka {}", request);
            } else {
                abflSanctionServiceV2.invokeSanctionViaAsyncApi(request);
                log.info("sanction invoked via async api for {}", request);
            }
        } catch (Exception e) {
            log.error("exception occurred while initiating sanction workflow for  {}", applicationId);
        }
        return Optional.empty();
    }

}
