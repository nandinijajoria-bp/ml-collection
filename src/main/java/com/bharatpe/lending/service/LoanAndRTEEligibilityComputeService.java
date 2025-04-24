package com.bharatpe.lending.service;


import com.bharatpe.cache.DTO.AddCacheDto;
import com.bharatpe.cache.service.LendingCache;
import com.bharatpe.common.dao.ExperianDao;
import com.bharatpe.common.entities.Experian;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.constant.LendingConstants;
import com.bharatpe.lending.dto.KycDocApprovedTopicDto;
import com.bharatpe.lending.dto.MileStoneEligibilityResponseDto;
import com.bharatpe.lending.dto.RTEProgramDetailsDto;
import com.bharatpe.lending.loanV2.dto.ApiResponse;
import com.bharatpe.lending.loanV2.dto.BureauConsentDTO;
import com.bharatpe.lending.loanV3.revamp.constants.LoanDetailsConstant;
import com.bharatpe.lending.loanV3.revamp.constants.RTEConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Objects;
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
    APIGatewayService apiGatewayService;

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
            experian.setRequestedLoanAmount(0);
            experian.setRetryCount(0);
            try {
                experianDao.save(experian);
            }catch (Exception e) {
                log.error("Exception while saving data in experian for {} {}", kycDocApprovedTopicDto.getMerchantId(), Arrays.asList(e.getStackTrace()));
            }
            log.info("Pan card saved in experian for merchantId {}", kycDocApprovedTopicDto.getMerchantId());
        } else if (ObjectUtils.isEmpty(experian.getPancardNumber())) {
            experian.setPancardNumber(kycDocApprovedTopicDto.getDocIdentifier());
            experianDao.save(experian);
            log.info("Pan card updated in experian for merchantId {}", kycDocApprovedTopicDto.getMerchantId());
        }

        if(consentUpdated(kycDocApprovedTopicDto.getDocIdentifier(), basicDetailsDto.get().getMobile(), kycDocApprovedTopicDto.getMerchantId())) {
            log.info("Consent is updated for {}", kycDocApprovedTopicDto.getMerchantId());
            MileStoneEligibilityResponseDto responseDto = mileStoneHelperServicev3.calculateEligibility(basicDetailsDto.get(), !ObjectUtils.isEmpty(lendingCache.get(RTEConstants.RTE_V3_AMOUNT + kycDocApprovedTopicDto.getMerchantId())));
            log.info("rte eligibility {}", responseDto);
            RTEProgramDetailsDto rteProgramDetailsDto = new RTEProgramDetailsDto();
            rteProgramDetailsDto.setRouteToEligibilityData(responseDto);
            mileStoneProgramService.checkEligibility(rteProgramDetailsDto, basicDetailsDto.get());
            log.info("loanEligibility {} of a merchant is {}",rteProgramDetailsDto.getLoanEligibility(),kycDocApprovedTopicDto.getMerchantId());
        }else{
            log.info("Unable to update consent for {}", kycDocApprovedTopicDto.getMerchantId());
        }
    }

    private boolean consentUpdated(String pancard, String mobile, Long merchantId) {
        BureauConsentDTO.Data bureauConsentDTO = BureauConsentDTO.Data.builder()
                .pan(pancard)
                .merchantId(merchantId)
                .mobile(mobile)
                .consent_expired(false)
                .bureau_mobile(mobile)
                .build();
        BureauConsentDTO.Data consentResponse = apiGatewayService.updateConsent(bureauConsentDTO);
        if (Objects.nonNull(consentResponse)) {
            log.info("Bureau consent response {}", consentResponse);
            if (!consentResponse.isConsent_expired()) {
                String loanDetailsCacheKey = LoanDetailsConstant.LENDING_DASHBOARD_DETAILS_V3_KEY_PREFIX + bureauConsentDTO.getMerchantId();
                log.info("deleting cached key of loan dashboard api for merchant: {}",bureauConsentDTO.getMerchantId());
                lendingCache.delete(loanDetailsCacheKey);

                AddCacheDto addCacheDto = new AddCacheDto();
                addCacheDto.setKey(LendingConstants.BUREAU_CONSENT_KEY_PREFIX + bureauConsentDTO.getMerchantId());
                addCacheDto.setTtl(1);
                addCacheDto.setValue(true);
                lendingCache.add(addCacheDto);
            }
            return true;
        }
        return false;
    }
}
