package com.bharatpe.lending.dao;

import com.bharatpe.lending.common.entity.AutoPayUPI;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AutoPayUpiAIDao extends CrudRepository<AutoPayUPI, Long> {
    // Find the latest AutoPayUPI record by merchant ID
    Optional<AutoPayUPI> findTop1ByMerchantIdOrderByIdDesc(Long merchantId);
    }
