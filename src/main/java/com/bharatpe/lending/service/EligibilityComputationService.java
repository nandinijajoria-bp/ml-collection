package com.bharatpe.lending.service;

import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.dto.ComputeEligibilityRequestDto;
import com.bharatpe.lending.loanV2.dto.LoanDetailsRequest;
import com.bharatpe.lending.loanV2.service.LoanDetailsServiceV2;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
public class EligibilityComputationService {

    @Autowired
    LoanDetailsServiceV2 loanDetailsServiceV2;

    @Autowired
    MerchantService merchantService;

    public Boolean computeEligibility(ComputeEligibilityRequestDto dto) {
        try {
            log.info("starting computing eligibility for merchant: {}", dto);
            LoanDetailsRequest loanDetailsRequest = new LoanDetailsRequest();
            loanDetailsRequest.setIOS(false);
            loanDetailsRequest.setPancard(dto.getPan());
            loanDetailsRequest.setPincode(dto.getPincode().toString());
            loanDetailsRequest.setAppVersion(318);
            loanDetailsRequest.setSkipMaskedMobileException(true);
            final Optional<BasicDetailsDto> basicDetailsDto = merchantService.fetchMerchantBasicDetails(dto.getMerchantId());
            BasicDetailsDto merchant;
            if (!basicDetailsDto.isPresent())
                return false;
            merchant = basicDetailsDto.get();
            loanDetailsServiceV2.getLoanDetails(loanDetailsRequest, merchant, null, true);
            log.info("successfully computed eligibility for merchant: {}", dto);
            return true;
        } catch (Exception e) {
            log.error("something went wrong while computing eligibility for  merchant {}, {}", dto.toString(),e);
        }
        return false;
    }
}