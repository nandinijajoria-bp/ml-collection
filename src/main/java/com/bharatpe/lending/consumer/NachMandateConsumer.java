package com.bharatpe.lending.consumer;

import com.bharatpe.lending.dao.NachMandateRevokeRequestDao;
import com.bharatpe.lending.entity.NachMandateRevokeRequest;
import com.bharatpe.lending.enums.CleverTapEvents;
import com.bharatpe.lending.service.CleverTapEventService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.MapUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Service
@Slf4j
public class NachMandateConsumer {

    @Autowired
    private NachMandateRevokeRequestDao nachMandateRevokeRequestDao;
    @Autowired
    private CleverTapEventService cleverTapEventService;
    @Autowired
    private ObjectMapper objectMapper;

    @KafkaListener(
            topics = "kafka.nach.mandate.lending",
            containerFactory = "ConfluentKafkaListenerContainer",
            autoStartup = "${kafka.confluent.consumer:false}")
    public void updateCancelNachMandateStatus(String data){
        log.info("Update status for mandate revoke {}", data);
        try{
            Map<String, Object> map = objectMapper.readValue(data, new TypeReference<Map<String, Object>>() {});
            Long merchantId = MapUtils.getLong(map, "merchant_id");
            Long applicationId = MapUtils.getLong(map, "ownerId");
            if (Objects.isNull(merchantId) || Objects.isNull(applicationId)){
                log.info("Invalid request to update mandate revoke request:{}", map);
                return;
            }
            String nachStatus = map.containsKey("nach_status")? String.valueOf(map.get("nach_status")):null;
            String status = map.containsKey("status")?String.valueOf(map.get("status")):null;
            if ("CANCELLED".equalsIgnoreCase(nachStatus) && "CANCELLED".equalsIgnoreCase(status)){
                NachMandateRevokeRequest nachMandateRevokeRequest = nachMandateRevokeRequestDao
                        .findTop1ByMerchantIdAndAndApplicationIdAndStatusOrderByIdDesc(merchantId, applicationId,"INIT");
                if(ObjectUtils.isEmpty(nachMandateRevokeRequest)){
                    log.info("no nach mandate request found for merchant:{}", merchantId);
                    return;
                }
                HashMap<String, Object> cleverTapEvtData = new HashMap<String, Object>() {{
                    put("applicationId", applicationId);
                }};
                cleverTapEventService.sendClevertapEvent(CleverTapEvents.NACH_CANCELLATION_SUCCESS.name(), cleverTapEvtData, merchantId.toString());
                nachMandateRevokeRequest.setStatus("SUCCESS");
                nachMandateRevokeRequestDao.save(nachMandateRevokeRequest);
                return;
            }
        } catch (Exception ex){
            log.info("Exception in updating nach mandate revoke request status {}, {}", ex.getMessage(), Arrays.asList(ex.getStackTrace()));
            return;
            
        }
    }
}