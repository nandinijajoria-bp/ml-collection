package com.bharatpe.lending.service;

import com.bharatpe.lending.dto.ExperianResponseDTO;
import com.bharatpe.lending.dto.InsertExperianRequestDTO;
import com.bharatpe.lending.dto.UpdateExperianRequestDTO;

public interface IExperianService {

    ExperianResponseDTO findByMerchantId(Long merchantId);

    ExperianResponseDTO updateExperian(UpdateExperianRequestDTO updateExperianRequestDTO);

    ExperianResponseDTO insertExperian(InsertExperianRequestDTO insertExperianRequestDTO);
}
