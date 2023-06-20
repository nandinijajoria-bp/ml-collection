package com.bharatpe.lending.loanV3.services.associationsV2;

import com.bharatpe.lending.loanV3.consumer.BreRequestKafka;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.Map;


@Service
@Slf4j
public class AbflBreServiceV2 {

    @Autowired
    BreRequestKafka breRequestKafka;

    @Autowired
    ObjectMapper objectMapper;

//    @Async
    @Transactional
    public void invokeBreViaAsyncApi(Map<String,String> request) {
        try{
            breRequestKafka.breRequestListener(objectMapper.writeValueAsString(request));
        } catch (Exception e) {
            log.error("exception occurred while initiating bre v2 workflow for  {}", request);
        }
    }
}
