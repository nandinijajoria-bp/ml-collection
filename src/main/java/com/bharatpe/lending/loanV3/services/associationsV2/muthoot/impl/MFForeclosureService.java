package com.bharatpe.lending.loanV3.services.associationsV2.muthoot.impl;

import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.request.muthoot.MFForeclosureDetailsRequestDTO;
import com.bharatpe.lending.loanV3.dto.response.muthoot.MFForeclosureDetailsResponseDTO;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;

@Slf4j
@Service
public class MFForeclosureService  {

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Autowired
    ObjectMapper objectMapper;

    public Double getForeclosureDetails(Long applicationId) {
        LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1ByApplicationIdAndLenderOrderByIdDesc(applicationId, Lender.MUTHOOT.name());
        if (ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
            log.info("lender record not exist of MUTHOOT for {}", applicationId);
            return null;
        }
        NBFCRequestDTO nbfcRequestDto = NBFCRequestDTO.builder()
                .productName("LENDING")
                .lender(Lender.MUTHOOT.name())
                .applicationId(applicationId)
                .payload(MFForeclosureDetailsRequestDTO.builder()
                        .customerID(lendingApplicationLenderDetails.getLeadId())
                        .program("EDI")
                        .build())
                .build();
        NBFCResponseDTO nbfcResponseDto = lenderAPIGateway.invokeStage(nbfcRequestDto, LenderAssociationStages.FORECLOSURE_FETCH);
        try {
            if (!ObjectUtils.isEmpty(nbfcResponseDto) && nbfcResponseDto.getSuccess() && !ObjectUtils.isEmpty(nbfcResponseDto.getData())) {
                MFForeclosureDetailsResponseDTO response = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDto.getData()), MFForeclosureDetailsResponseDTO.class);
                if(!ObjectUtils.isEmpty(response.getData())
                        && !ObjectUtils.isEmpty(response.getData().getDetails())
                        && !ObjectUtils.isEmpty(response.getData().getDetails().getDues())
                        && !ObjectUtils.isEmpty(response.getData().getDetails().getDues().getTotalDueAmount())) {
                    return response.getData().getDetails().getDues().getTotalDueAmount();
                }
            }
        } catch (Exception e) {
            log.error("exception occurred while fetching foreclosure details of MUTHOOT for {} {}, {}", applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return 0D;
    }

}
