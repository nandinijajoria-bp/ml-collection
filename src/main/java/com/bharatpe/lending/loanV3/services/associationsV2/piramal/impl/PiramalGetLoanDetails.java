package com.bharatpe.lending.loanV3.services.associationsV2.piramal.impl;

import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.loanV3.dto.piramal.GetLeadRequestDto;
import com.bharatpe.lending.loanV3.dto.piramal.NbfcRequestDto;
import com.bharatpe.lending.loanV3.dto.piramal.NbfcResponseDto;
import com.bharatpe.lending.loanV3.dto.piramal.PiramalGetLoanResponseDto;
import com.bharatpe.lending.loanV3.services.gateway.piramal.ILenderGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.HashMap;


@Service
@Slf4j
public class PiramalGetLoanDetails {
    private final LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    public PiramalGetLoanDetails(LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao) {
        this.lendingApplicationLenderDetailsDao = lendingApplicationLenderDetailsDao;
    }

    @Autowired
    ILenderGateway iLenderGateway;

    @Autowired
    ObjectMapper objectMapper;

    public PiramalGetLoanResponseDto getLoanDetails(Long  applicationId) {
        LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1ByApplicationIdAndLenderOrderByIdDesc(applicationId, Lender.PIRAMAL.name());
        if (ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
            log.info("lender record doesnt exist for {}", applicationId);
            return null;
        }
        NbfcRequestDto nbfcRequestDto = new NbfcRequestDto<>();
        GetLeadRequestDto getLeadRequestDto = new GetLeadRequestDto();
        getLeadRequestDto.setLoanAccountNumber(lendingApplicationLenderDetails.getLan());
        nbfcRequestDto.setLender("PIRAMAL");
        nbfcRequestDto.setProductName("LENDING");
        nbfcRequestDto.setApplicationId(applicationId);
        nbfcRequestDto.setPayload(getLeadRequestDto);
        NbfcResponseDto nbfcResponseDto = iLenderGateway.invokeStage(nbfcRequestDto,LenderAssociationStages.PiramalAssociationStages.GET_LOAN_DETAILS);
        try {
            if (!ObjectUtils.isEmpty(nbfcResponseDto) && nbfcResponseDto.getSuccess() && !ObjectUtils.isEmpty(nbfcResponseDto.getData())) {
                return objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDto.getData()),PiramalGetLoanResponseDto.class);
            }
        } catch (Exception e) {
            log.info("exception occurred while parsing data for {} {}", e.getMessage(), applicationId);
        }
        return null;
    }
}
