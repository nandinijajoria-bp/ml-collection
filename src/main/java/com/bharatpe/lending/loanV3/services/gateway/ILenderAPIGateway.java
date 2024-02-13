package com.bharatpe.lending.loanV3.services.gateway;

import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;

public interface ILenderAPIGateway {
    public NBFCResponseDTO invokeStage(NBFCRequestDTO nbfcRequestDto, LenderAssociationStages lenderAssociationStage);
}
