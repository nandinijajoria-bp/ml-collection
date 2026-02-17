package com.bharatpe.lending.service;

import com.bharatpe.lending.dto.ExperianResponseDTO;
import com.bharatpe.lending.dto.InsertExperianRequestDTO;
import com.bharatpe.lending.dto.UpdateExperianRequestDTO;
import com.bharatpe.lending.dto.underwriting.ApiResponse;
import com.bharatpe.lending.dto.underwriting.SearchRequestDTO;
import com.bharatpe.lending.dto.underwriting.read.ExperianReadDTO;
import com.bharatpe.lending.dto.underwriting.write.ExperianWriteDto;

import java.util.List;

public interface IExperianService {

    ApiResponse<?> searchDynamic(SearchRequestDTO request, boolean useStrongConsistency);

    ExperianResponseDTO findByMerchantId(Long merchantId);

    ExperianResponseDTO updateExperian(UpdateExperianRequestDTO updateExperianRequestDTO);

    ExperianResponseDTO insertExperian(InsertExperianRequestDTO insertExperianRequestDTO);

    ApiResponse<Boolean> saveExperian(ExperianWriteDto experianWriteDto);
}
