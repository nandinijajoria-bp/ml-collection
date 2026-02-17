package com.bharatpe.lending.service;

import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;

class MerchantLoansServiceTest {

    @InjectMocks
    private MerchantLoansService merchantLoansService;


    @Test
    void getDueAmount() {
        BasicDetailsDto basicDetailsDto = new BasicDetailsDto();
        basicDetailsDto.setId(123L);
        merchantLoansService.getDueAmount(123L, null, basicDetailsDto);

    }
}