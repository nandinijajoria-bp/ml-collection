package com.bharatpe.lending.dao;

import com.bharatpe.common.entities.Experian;
import com.bharatpe.lending.entity.ExperianDummy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ExperianDummyDao extends JpaRepository<ExperianDummy, Long> {

    @Query("SELECT e from ExperianDummy e WHERE e.merchantId=?1")
    ExperianDummy getByMerchantId(Long merchantId);
}
