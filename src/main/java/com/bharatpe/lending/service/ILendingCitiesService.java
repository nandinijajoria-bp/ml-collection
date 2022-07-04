package com.bharatpe.lending.service;

import com.bharatpe.lending.dto.LendingCitiesResponseDTO;
import com.bharatpe.lending.dto.LendingPincodesResponseDTO;

public interface ILendingCitiesService {
    LendingCitiesResponseDTO findActiveCityByPincode(Integer pincode);

    LendingPincodesResponseDTO findByPincode(Integer pincode);
}
