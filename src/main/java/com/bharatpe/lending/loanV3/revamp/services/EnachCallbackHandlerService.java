package com.bharatpe.lending.loanV3.revamp.services;

import com.bharatpe.lending.common.Constants.AutoPayStatusEnum;
import com.bharatpe.lending.common.entity.AutoPayUPI;
import com.bharatpe.lending.loanV3.revamp.dto.EnachStatusUpdateRequestDto;
import com.bharatpe.lending.service.DigioAutoPayUPIServiceHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class EnachCallbackHandlerService {

    private final DigioAutoPayUPIServiceHelper digioAutoPayUPIServiceHelper;
    private final EnachStageHelper enachStageHelper;

    public void updateMandateStatus(EnachStatusUpdateRequestDto requestDto){
        if(requestDto == null || !requestDto.isValid()){
            log.error("Invalid request payload received for mandate status update: {}", requestDto);
            return;
        }
        AutoPayUPI autoPayUPI = digioAutoPayUPIServiceHelper.updateAutoPayStatus(requestDto);
        if(Objects.nonNull(autoPayUPI) && AutoPayStatusEnum.ACTIVE.equals(autoPayUPI.getStatus()) && !autoPayUPI.isStandaloneAutopaySetup()){
            log.info("invoking nach success stage handling for application: {} as autopayupi status is active for mandateId: {}"
                    , requestDto.getMandateId(), requestDto.getMandateId());
            enachStageHelper.processNachSuccessStatus(requestDto.getApplicationId());
        }
    }

}
