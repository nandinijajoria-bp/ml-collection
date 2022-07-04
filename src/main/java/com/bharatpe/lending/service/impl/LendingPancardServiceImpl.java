package com.bharatpe.lending.service.impl;

import com.bharatpe.common.dao.LendingPancardDao;
import com.bharatpe.common.entities.LendingPancard;
import com.bharatpe.lending.dto.LendingPancardResponseDTO;
import com.bharatpe.lending.service.ILendingPancardService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


@Slf4j
@Service
public class LendingPancardServiceImpl implements ILendingPancardService {

    @Autowired
    LendingPancardDao lendingPancardDao;

    @Override
    public LendingPancardResponseDTO findByMerchantId(Long merchantId) {

        log.info("findByMerchantId for merchantId : {} ", merchantId);

        LendingPancard lendingPancard = lendingPancardDao.findByMerchantId(merchantId);

        return LendingPancardResponseDTO.from(lendingPancard);
    }
}
