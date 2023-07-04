package com.bharatpe.lending.loanV3.services.gateway.piramal;

import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.loanV3.dto.piramal.NbfcRequestDto;
import com.bharatpe.lending.loanV3.dto.piramal.NbfcResponseDto;

import java.util.Map;

public abstract class ILenderGateway {
    public abstract NbfcResponseDto invokeStage(NbfcRequestDto nbfcRequestDto, LenderAssociationStages.PiramalAssociationStages piramalAssociationStages);
    public abstract NbfcResponseDto invokeStageViaParams(Map<String,String> requestMap, LenderAssociationStages.PiramalAssociationStages piramalAssociationStages, String pathVars);


}
