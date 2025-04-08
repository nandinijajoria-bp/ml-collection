package com.bharatpe.lending.service;


import com.bharatpe.cache.service.LendingCache;
import com.bharatpe.common.dao.ExperianDao;
import com.bharatpe.common.entities.Experian;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.dto.KycDocApprovedTopicDto;
import com.bharatpe.lending.dto.MileStoneEligibilityResponseDto;
import com.bharatpe.lending.dto.RTEProgramDetailsDto;
import com.bharatpe.lending.loanV3.revamp.constants.RTEConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Optional;

@Service
@Slf4j
public class LoanAndRTEEligibilityComputeService {

    @Autowired
    MileStoneHelperServicev3 mileStoneHelperServicev3;

    @Autowired
    LendingCache lendingCache;

    @Autowired
    MerchantService merchant;
    @Autowired
    MileStoneProgramService mileStoneProgramService;

    @Autowired
    ExperianDao experianDao;
    public void computeLoanAndRTEEligibility(KycDocApprovedTopicDto kycDocApprovedTopicDto) {
        Optional<BasicDetailsDto> basicDetailsDto = merchant.fetchMerchantBasicDetails(kycDocApprovedTopicDto.getMerchantId());
        if(!basicDetailsDto.isPresent()) {
            log.info("merchant details not present for {}", kycDocApprovedTopicDto.getMerchantId());
            return;
        }

        Experian experian = experianDao.getByMerchantId(kycDocApprovedTopicDto.getMerchantId());
        if (experian == null) {
            experian = new Experian();
            experian.setMerchantId(kycDocApprovedTopicDto.getMerchantId());
            experian.setPancardNumber(kycDocApprovedTopicDto.getDocIdentifier());
            experianDao.save(experian);
            log.info("Pan card saved in experian for merchantId {}", kycDocApprovedTopicDto.getMerchantId());
        } else if (ObjectUtils.isEmpty(experian.getPancardNumber())) {
            experian.setPancardNumber(kycDocApprovedTopicDto.getDocIdentifier());
            experianDao.save(experian);
            log.info("Pan card updated in experian for merchantId {}", kycDocApprovedTopicDto.getMerchantId());
        }
        MileStoneEligibilityResponseDto responseDto = mileStoneHelperServicev3.calculateEligibility(basicDetailsDto.get(), !ObjectUtils.isEmpty(lendingCache.get(RTEConstants.RTE_V3_AMOUNT + kycDocApprovedTopicDto.getMerchantId())));
        log.info("rte eligibility {}", responseDto);
        RTEProgramDetailsDto rteProgramDetailsDto = new RTEProgramDetailsDto();
        rteProgramDetailsDto.setRouteToEligibilityData(responseDto);
        mileStoneProgramService.checkEligibility(rteProgramDetailsDto, basicDetailsDto.get());
        log.info("loanEligibility {} of a merchant is {}",rteProgramDetailsDto.getLoanEligibility(),kycDocApprovedTopicDto.getMerchantId());
    }
}
