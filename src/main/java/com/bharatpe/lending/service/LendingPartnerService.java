package com.bharatpe.lending.service;

import com.bharatpe.lending.common.dao.LendingPartnerDetailsDao;
import com.bharatpe.lending.common.entity.LendingPartnerDetails;
import com.bharatpe.lending.dto.PartnerDetailsRequestDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LendingPartnerService {

    Logger logger = LoggerFactory.getLogger(LendingPartnerService.class);

    @Autowired
    LendingPartnerDetailsDao lendingPartnerDetailsDao;

    public void saveDetails(PartnerDetailsRequestDTO partnerDetailsRequestDTO) {
        lendingPartnerDetailsDao.save(new LendingPartnerDetails(partnerDetailsRequestDTO.getPartner(), partnerDetailsRequestDTO.getName(), partnerDetailsRequestDTO.getMobile()));
        logger.info("Successfully saved partner details for mobile: {}", partnerDetailsRequestDTO.getMobile());
    }
}
