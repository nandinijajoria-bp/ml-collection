package com.bharatpe.lending.loanV3.services.associationsV2.piramal.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.loanV2.dto.ApiResponse;
import com.bharatpe.lending.loanV2.service.InsuranceService;
import com.bharatpe.lending.loanV3.dto.piramal.LoanInsuranceDocumentDTO;
import com.bharatpe.lending.loanV3.dto.piramal.NbfcResponseDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;
import java.util.Date;
import java.util.Optional;

@Service
@Slf4j
public class InsurancePolicyDocService {
    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    InsuranceService insuranceService;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    public ApiResponse<?> insuranceDocCallback(NbfcResponseDto nbfcResponseDto) {
        try {
            log.info("insurance policy doc request for {}", objectMapper.writeValueAsString(nbfcResponseDto));

            LoanInsuranceDocumentDTO loanInsuranceDocument = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDto.getData()), LoanInsuranceDocumentDTO.class);
            Optional<LendingApplication> lendingApplication = lendingApplicationDao.findById(Long.valueOf(nbfcResponseDto.getApplicationId()));
            if (!lendingApplication.isPresent()) {
                log.info("application {} not found for insurance policy doc callback", nbfcResponseDto.getApplicationId());
                return new ApiResponse<>(false, "lending application doesn't exists !");
            }
            LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(lendingApplication.get().getId(), Status.ACTIVE.name(), lendingApplication.get().getLender());
            if(ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
                log.info("lender details {} not found for insurance policy doc callback", nbfcResponseDto.getApplicationId());
                return new ApiResponse<>(false, "lending application lender details doesn't exists !");
            }
            Date commencementDate = ObjectUtils.isEmpty(loanInsuranceDocument.getCommencementDate()) ? new Date() : new Date(loanInsuranceDocument.getCommencementDate());
            Date maturityDate = ObjectUtils.isEmpty(loanInsuranceDocument.getMaturityDate()) ? new Date() : new Date(loanInsuranceDocument.getMaturityDate());
            insuranceService.saveInsuranceDocDetails(loanInsuranceDocument.getFileBlob(), commencementDate, maturityDate, lendingApplication.get().getId(), lendingApplication.get().getLender());
        } catch (Exception e) {
            log.error("Exception occurred while processing insurance doc callback : {}", Arrays.asList(e.getMessage()));
            return new ApiResponse<>(false, e.getMessage());
        }
        return new ApiResponse<>(true);
    }
}
