package com.bharatpe.lending.service.merchant.service;



import com.bharatpe.lending.service.merchant.dto.AddressDetailsDto;
import com.bharatpe.lending.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.service.merchant.dto.MerchantDetailsDto;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface MerchantService {

    BasicDetailsDto fetchMerchantDetails(String token) throws Exception;

    MerchantDetailsDto fetchMerchantDetails(Long merchantId, List<String> scopeList) throws Exception;

    boolean updateDetails(Long merchantId, String operation, String key, String value) throws Exception;

    boolean updateDetails(Long merchantId, List<Map<String, String>> operations) throws Exception;

    MerchantDetailsDto fetchMerchantDetails(Long merchantId) throws Exception;

    Optional<AddressDetailsDto> fetchMerchantAddressDetails(Long merchantId) throws Exception;
}
