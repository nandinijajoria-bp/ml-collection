package com.bharatpe.lending.lendingplatform.nbfc.dto.pojo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DelayedMessage {
    private String hashKey;
    private String partitionKey;
    private String destinationTopic;
    private Object payload;
    private String processAt;
    private String expiryMinute;
}
