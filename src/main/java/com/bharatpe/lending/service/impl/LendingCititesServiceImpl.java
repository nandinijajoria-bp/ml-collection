package com.bharatpe.lending.service.impl;

import com.bharatpe.common.dao.LendingCitiesDao;
import com.bharatpe.common.entities.LendingCities;
import com.bharatpe.lending.common.dao.LendingPincodesDao;
import com.bharatpe.lending.common.entity.LendingPincodes;
import com.bharatpe.lending.dto.LendingCitiesResponseDTO;
import com.bharatpe.lending.dto.LendingPincodesResponseDTO;
import com.bharatpe.lending.service.ILendingCitiesService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class LendingCititesServiceImpl implements ILendingCitiesService {

    @Autowired
    LendingCitiesDao lendingCitiesDao;

    @Autowired
    LendingPincodesDao lendingPincodesDao;

    @Override
    public LendingCitiesResponseDTO findActiveCityByPincode(Integer pincode) {

        log.info("findActiveCityByPincode for pincode : {} ", pincode);

        LendingCities lendingCities = lendingCitiesDao.findActiveCityByPincode(pincode);

        return LendingCitiesResponseDTO.from(lendingCities);
    }

    @Override
    public LendingPincodesResponseDTO findByPincode(Integer pincode) {

        log.info("findByPincode for pincode : {} ", pincode);

        final LendingPincodes lendingPincode = lendingPincodesDao.findByPincode(pincode);

        return LendingPincodesResponseDTO.from(lendingPincode);

    }
}
