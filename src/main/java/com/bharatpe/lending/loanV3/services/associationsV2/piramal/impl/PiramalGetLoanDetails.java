package com.bharatpe.lending.loanV3.services.associationsV2.piramal.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.loanV3.dto.piramal.*;
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

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    public PiramalGetLoanResponseDto getLoanDetails(Long  applicationId) {
        LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1ByApplicationIdAndLenderOrderByIdDesc(applicationId, Lender.PIRAMAL.name());
        LendingApplication lendingApplication = lendingApplicationDao.findById(applicationId).orElse(null);
        if (ObjectUtils.isEmpty(lendingApplicationLenderDetails) || ObjectUtils.isEmpty(lendingApplication)) {
            log.info("piramal : lending application or lender record doesnt exist for {}", applicationId);
            return null;
        }
        NbfcRequestDto nbfcRequestDto = new NbfcRequestDto<>();
        GetLeadRequestDto getLeadRequestDto = new GetLeadRequestDto();
        getLeadRequestDto.setLoanAccountNumber(lendingApplicationLenderDetails.getLan());
        nbfcRequestDto.setLender("PIRAMAL");
        nbfcRequestDto.setProductName("LENDING");
        nbfcRequestDto.setApplicationId(applicationId);
        nbfcRequestDto.setPayload(getLeadRequestDto);
        nbfcRequestDto.setTopup(LoanType.TOPUP.name().equals(lendingApplication.getLoanType()));
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

    public PiramalGetForeclosureResponseDTO getForeclosureDetails(Long  applicationId) {
        LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1ByApplicationIdAndLenderOrderByIdDesc(applicationId, Lender.PIRAMAL.name());
        LendingApplication lendingApplication = lendingApplicationDao.findById(applicationId).orElse(null);
        if (ObjectUtils.isEmpty(lendingApplicationLenderDetails) || ObjectUtils.isEmpty(lendingApplication)) {
            log.info("piramal : lending application or lender record doesnt exist for {}", applicationId);
            return null;
        }
        NbfcRequestDto nbfcRequestDto = new NbfcRequestDto<>();
        GetLeadRequestDto getLeadRequestDto = new GetLeadRequestDto();
        getLeadRequestDto.setLoanAccountNumber(lendingApplicationLenderDetails.getLan());
        nbfcRequestDto.setLender("PIRAMAL");
        nbfcRequestDto.setProductName("LENDING");
        nbfcRequestDto.setApplicationId(applicationId);
        nbfcRequestDto.setPayload(getLeadRequestDto);
        nbfcRequestDto.setTopup(LoanType.TOPUP.name().equals(lendingApplication.getLoanType()));
        NbfcResponseDto nbfcResponseDto = iLenderGateway.invokeStage(nbfcRequestDto,LenderAssociationStages.PiramalAssociationStages.GET_FORECLOSURE_DETAILS);
        try {
            if (!ObjectUtils.isEmpty(nbfcResponseDto) && nbfcResponseDto.getSuccess() && !ObjectUtils.isEmpty(nbfcResponseDto.getData())) {
                return objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDto.getData()),PiramalGetForeclosureResponseDTO.class);
            }
        } catch (Exception e) {
            log.info("foreclosure : exception occurred while parsing data for {} {}", e.getMessage(), applicationId);
        }
        return null;
    }
}
