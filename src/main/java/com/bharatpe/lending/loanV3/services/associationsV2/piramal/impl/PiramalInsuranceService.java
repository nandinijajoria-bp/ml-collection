package com.bharatpe.lending.loanV3.services.associationsV2.piramal.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.dto.LoanInsuranceDTO;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.loanV3.dto.piramal.NbfcRequestDto;
import com.bharatpe.lending.loanV3.dto.piramal.NbfcResponseDto;
import com.bharatpe.lending.loanV3.dto.piramal.PInsuranceRequestDTO;
import com.bharatpe.lending.loanV3.dto.piramal.PInsuranceResponseDTO;
import com.bharatpe.lending.loanV3.services.gateway.PiramalApiGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class PiramalInsuranceService {

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    PiramalApiGateway piramalApiGateway;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    EasyLoanUtil easyLoanUtil;

    @Value("${piramal.insurance.rollout.percent:0}")
    Integer piramalInsuranceRolloutPercent;


    public LoanInsuranceDTO getInsurancePremiums(LendingApplication lendingApplication) {
        if(!easyLoanUtil.percentScaleUp(lendingApplication.getMerchantId(), piramalInsuranceRolloutPercent)) {
            log.info("{} insurance not enabled for merchantId {}",lendingApplication.getLender(), lendingApplication.getMerchantId());
            return null;
        }

        NbfcRequestDto<?> nbfcRequestDto = getPayload(lendingApplication);
        if (ObjectUtils.isEmpty(nbfcRequestDto)) {
            log.error("Failed to create insurance premium payload for applicationId: {}", lendingApplication.getId());
            return null;
        }

        for (int attempt = 1; attempt <= 3; attempt++) {
            NbfcResponseDto<?> response = piramalApiGateway.invokeStage(
                    nbfcRequestDto, LenderAssociationStages.PiramalAssociationStages.INSURANCE_PREMIUM);
            log.info("Attempt {} - Insurance premium response for applicationId {}: {}", attempt, lendingApplication.getId(), response);
            if (!ObjectUtils.isEmpty(response) && response.getSuccess() && !ObjectUtils.isEmpty(response.getData())) {
                PInsuranceResponseDTO insuranceData = objectMapper.convertValue(response.getData(), PInsuranceResponseDTO.class);
                if (!ObjectUtils.isEmpty(insuranceData) && !ObjectUtils.isEmpty(insuranceData.getPremiums())) {
                    List<LoanInsuranceDTO.InsuranceDetails> insurances = insuranceData.getPremiums().stream()
                            .map(this::mapToLoanInsuranceDTO)
                            .collect(Collectors.toList());
                    return LoanInsuranceDTO.builder().insurances(insurances).build();
                }
            }
        }
        log.error("All retry attempts failed for applicationId: {}", lendingApplication.getId());
        return null;
    }

    public NbfcRequestDto getPayload(LendingApplication lendingApplication) {
        LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1ByApplicationIdAndLenderOrderByIdDesc(lendingApplication.getId(), lendingApplication.getLender());
        if (ObjectUtils.isEmpty(lendingApplicationLenderDetails) || ObjectUtils.isEmpty(lendingApplicationLenderDetails.getLeadId())) {
            log.info("No lender details / leadId found for applicationId  {}", lendingApplication.getId());
            return null;
        }
        NbfcRequestDto<?> nbfcRequestDto = NbfcRequestDto.builder()
                .applicationId(lendingApplication.getId())
                .lender(lendingApplication.getLender())
                .productName("LENDING")
                .topup(LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.getLoanType()))
                .payload(PInsuranceRequestDTO.builder()
                        .leadId(lendingApplicationLenderDetails.getLeadId())
                        .loanAmount(lendingApplication.getLoanAmount())
                        .loanTenureInMonths(lendingApplication.getTenureInMonths())
                        .build())
                .build();
        return nbfcRequestDto;
    }

    private LoanInsuranceDTO.InsuranceDetails mapToLoanInsuranceDTO(PInsuranceResponseDTO.PremiumDetails premium) {
        return LoanInsuranceDTO.InsuranceDetails.builder()
                .insurancePremium(premium.getPremiumAmount())
                .sumInsured(premium.getSumInsured())
                .policyTermsInMonths(premium.getPolicyTermInMonths())
                .product(premium.getProduct())
                .provider(premium.getProvider())
                .build();
    }

}
