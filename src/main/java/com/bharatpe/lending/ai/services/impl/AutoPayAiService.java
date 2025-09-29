package com.bharatpe.lending.ai.services.impl;

import com.bharatpe.lending.ai.services.IAutoPayApiService;
import com.bharatpe.lending.dao.AutoPayUpiDao;
import com.bharatpe.lending.entity.AutoPayUpi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Slf4j
public class AutoPayAiService implements IAutoPayApiService {
    
    @Autowired
    AutoPayUpiDao autoPayUpiDao;

    @Override
    public Optional<AutoPayUpi> getAutoPayDetails(Long merchantId) {
        return autoPayUpiDao.findTop1ByMerchantIdOrderByIdDesc(merchantId);
    }
}
