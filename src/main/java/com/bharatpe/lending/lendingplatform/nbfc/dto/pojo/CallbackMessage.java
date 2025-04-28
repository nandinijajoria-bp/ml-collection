package com.bharatpe.lending.lendingplatform.nbfc.dto.pojo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CallbackMessage {
    private String callBackInfoIdentifier;
}
