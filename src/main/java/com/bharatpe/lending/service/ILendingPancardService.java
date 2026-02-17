package com.bharatpe.lending.service;

import com.bharatpe.lending.dto.LendingPancardResponseDTO;
import com.bharatpe.lending.dto.underwriting.ApiResponse;
import com.bharatpe.lending.dto.underwriting.SearchRequestDTO;
import com.bharatpe.lending.dto.underwriting.read.LendingPancardReadDTO;
import com.bharatpe.lending.dto.underwriting.write.LendingPancardWriteDto;

import java.util.List;

public interface ILendingPancardService {

    ApiResponse<?> searchDynamic(SearchRequestDTO request, boolean useStrongConsistency);

    LendingPancardResponseDTO findByMerchantId(Long merchantId);

    ApiResponse<Boolean> savePancardDetails(LendingPancardWriteDto lendingPancardWriteDto);
}
