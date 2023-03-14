package com.bharatpe.lending.loanV3.services;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.dao.LendingKfsDao;
import com.bharatpe.lending.entity.LendingKfs;
import com.bharatpe.lending.loanV2.dto.ApiResponse;
import com.bharatpe.lending.loanV2.service.LendingApplicationServiceV2;
import com.bharatpe.lending.loanV3.dto.WelcomeDocDetailsDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Optional;


@Service
@Slf4j
public class LenderDocService {

    @Autowired
    LendingApplicationServiceV2 lendingApplicationServiceV2;

    @Autowired
    LendingKfsDao lendingKfsDao;

    @Autowired
    MerchantService merchantService;

    public ApiResponse<?> fetchWelcomeDoc(LendingApplication lendingApplication) {
        LendingKfs lendingKfs = lendingKfsDao.findTop1ByApplicationIdOrderByIdDesc(lendingApplication.getId());
        if(ObjectUtils.isEmpty(lendingKfs)){
            log.info("KFS details not present for Id: {} for merchant : {}", lendingApplication.getId(), lendingApplication.getMerchantId());
            return new ApiResponse<>(false,"Unable to get lender while generating KFS");
        }
        if (ObjectUtils.isEmpty(lendingApplication.getDisburseTimestamp())) {
            log.info("disbursement not yet started for app Id: {} for merchant : {}", lendingApplication.getId(), lendingApplication.getMerchantId());
            return new ApiResponse<>(false,"Loan not yet disbursed");
        }
        if (!ObjectUtils.isEmpty(lendingKfs.getWelcomeDocUrl())) {
            return new ApiResponse<>(WelcomeDocDetailsDto.builder().s3URL(lendingKfs.getKfsDocUrl()).build());
        }
        try {
            Optional<BasicDetailsDto> merchant = merchantService.fetchMerchantBasicDetails(lendingApplication.getMerchantId());
            if (lendingApplicationServiceV2.generateWelcomeDocument(lendingApplication,lendingKfs,merchant.get(), null)) {
                return new ApiResponse<>(WelcomeDocDetailsDto.builder().s3URL(lendingKfs.getKfsDocUrl()).build());
            }
        } catch (Exception e) {
            log.error("exception occurred {}",e.getMessage());
            return new ApiResponse<>(false,e.getMessage());
        }
        return new ApiResponse<>(false,"welcome doc not found !!");
    }
}
